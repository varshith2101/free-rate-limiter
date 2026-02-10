package com.varshith.ratelimiter.service;

import com.varshith.ratelimiter.dto.SignUpRequest;
import com.varshith.ratelimiter.dto.VerifyOtpRequest;
import com.varshith.ratelimiter.dto.LoginRequest;
import com.varshith.ratelimiter.dto.AuthResponse;
import com.varshith.ratelimiter.dto.UserDto;
import com.varshith.ratelimiter.model.Tenant;
import com.varshith.ratelimiter.model.User;
import com.varshith.ratelimiter.repository.TenantRepository;
import com.varshith.ratelimiter.repository.UserRepository;
import com.varshith.ratelimiter.util.JwtUtil;
import com.varshith.ratelimiter.util.AuthUtil;
import com.varshith.ratelimiter.util.EmailUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    @Value("${app.auth.mode:standard}")
    private String authMode;

    @Value("${app.auth.admin.email:}")
    private String adminEmail;

    @Value("${app.auth.admin.password:}")
    private String adminPassword;
    
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtUtil jwtUtil;
    private final AuthUtil authUtil;
    private final EmailUtil emailUtil;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    
    public AuthService(UserRepository userRepository, TenantRepository tenantRepository, JwtUtil jwtUtil, AuthUtil authUtil, EmailUtil emailUtil, RefreshTokenService refreshTokenService, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.jwtUtil = jwtUtil;
        this.authUtil = authUtil;
        this.emailUtil = emailUtil;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
    }
    
    /**
     * Step 1: Send OTP to email
     */
    public void sendOtp(SignUpRequest request) {
        if (isSoloMode()) {
            throw new RuntimeException("Solo auth mode is enabled. OTP sign-up is disabled.");
        }
        // Generate OTP (6 digits)
        String otp = authUtil.generateOtp();
        LocalDateTime otpExpiry = LocalDateTime.now().plusMinutes(10);
        
        // Find or create user with OTP (unverified)
        User user = userRepository.findByEmail(request.getEmail())
            .orElse(User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .isEmailVerified(false)
                .build());

        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new RuntimeException("Email already registered");
        }

        user.setName(request.getName());

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            user.setPasswordHash(authUtil.encodePassword(UUID.randomUUID().toString()));
        }
        
        user.setOtpCode(otp);
        user.setOtpExpiry(otpExpiry);
        userRepository.save(user);
        
        // Send OTP email
        emailUtil.sendOtpEmail(request.getEmail(), otp, request.getName());

        auditLogService.log(user.getId(), user.getTenantId(), "AUTH_SEND_OTP", "SUCCESS",
            "OTP sent", "USER", user.getId().toString(), null);
    }
    
    /**
     * Step 2: Verify OTP and set password
     */
    public AuthResponse verifyOtpAndSignUp(VerifyOtpRequest request) {
        if (isSoloMode()) {
            throw new RuntimeException("Solo auth mode is enabled. OTP sign-up is disabled.");
        }
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Validate OTP
        if (user.getOtpCode() == null || !user.getOtpCode().equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }
        
        // Check OTP expiry
        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new RuntimeException("OTP expired");
        }
        
        // Set password and mark as verified
        user.setPasswordHash(authUtil.encodePassword(request.getPassword()));
        user.setIsEmailVerified(true);
        user.setOtpCode(null);
        user.setOtpExpiry(null);

        // Create tenant for user if not exists
        if (user.getTenantId() == null) {
            Tenant tenant = new Tenant();
            tenant.setName(user.getName() + " (FREE)");
            tenant.setTier(Tenant.TenantTier.FREE);
            tenant.setUserId(user.getId());
            Tenant savedTenant = tenantRepository.save(tenant);
            user.setTenantId(savedTenant.getId());
        }
        
        User savedUser = userRepository.save(user);
        
        // Generate JWT tokens
        String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(savedUser.getId());
        refreshTokenService.create(savedUser.getId(), refreshToken);

        auditLogService.log(savedUser.getId(), savedUser.getTenantId(), "AUTH_SIGNUP", "SUCCESS",
            "User verified OTP and signed up", "USER", savedUser.getId().toString(), null);
        
        return AuthResponse.builder()
            .token(token)
            .refreshToken(refreshToken)
            .user(mapUserToDto(savedUser))
            .build();
    }
    
    /**
     * Login with email and password
     */
    public AuthResponse login(LoginRequest request) {
        if (isSoloMode()) {
            return loginWithSoloAdmin(request);
        }
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        
        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new RuntimeException("Email not verified");
        }

        // Validate password
        if (!authUtil.matchPassword(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }
        
        // Generate JWT tokens
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        refreshTokenService.create(user.getId(), refreshToken);

        auditLogService.log(user.getId(), user.getTenantId(), "AUTH_LOGIN", "SUCCESS",
            "User logged in", "USER", user.getId().toString(), null);
        
        return AuthResponse.builder()
            .token(token)
            .refreshToken(refreshToken)
            .user(mapUserToDto(user))
            .build();
    }

    private AuthResponse loginWithSoloAdmin(LoginRequest request) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            throw new RuntimeException("Solo auth mode requires ADMIN_EMAIL and ADMIN_PASSWORD");
        }

        if (!adminEmail.equalsIgnoreCase(request.getEmail())) {
            throw new RuntimeException("Solo auth mode only allows the admin email to sign in");
        }

        if (!adminPassword.equals(request.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        User user = userRepository.findByEmail(adminEmail)
            .orElseGet(() -> User.builder()
                .email(adminEmail)
                .name("Admin")
                .isEmailVerified(true)
                .build());

        user.setName("Admin");
        user.setIsEmailVerified(true);
        user.setPasswordHash(authUtil.encodePassword(adminPassword));

        User savedUser = userRepository.save(user);

        if (savedUser.getTenantId() == null) {
            Tenant tenant = new Tenant();
            tenant.setName("Admin (FREE)");
            tenant.setTier(Tenant.TenantTier.FREE);
            tenant.setUserId(savedUser.getId());
            Tenant savedTenant = tenantRepository.save(tenant);
            savedUser.setTenantId(savedTenant.getId());
            savedUser = userRepository.save(savedUser);
        }

        String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(savedUser.getId());
        refreshTokenService.create(savedUser.getId(), refreshToken);

        auditLogService.log(savedUser.getId(), savedUser.getTenantId(), "AUTH_LOGIN", "SUCCESS",
            "User logged in (solo mode)", "USER", savedUser.getId().toString(), null);

        return AuthResponse.builder()
            .token(token)
            .refreshToken(refreshToken)
            .user(mapUserToDto(savedUser))
            .build();
    }

    private boolean isSoloMode() {
        return "solo".equalsIgnoreCase(authMode);
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken) || !refreshTokenService.isValid(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        UUID userId = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Rotate refresh token
        refreshTokenService.revoke(refreshToken);
        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());
        refreshTokenService.create(user.getId(), newRefreshToken);

        auditLogService.log(user.getId(), user.getTenantId(), "AUTH_REFRESH", "SUCCESS",
            "Access token refreshed", "USER", user.getId().toString(), null);

        return AuthResponse.builder()
            .token(newAccessToken)
            .refreshToken(newRefreshToken)
            .user(mapUserToDto(user))
            .build();
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        auditLogService.log(null, null, "AUTH_LOGOUT", "SUCCESS", "User logged out", null, null, null);
    }
    
    /**
     * Get user by ID
     */
    public UserDto getUserById(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return mapUserToDto(user);
    }
    
    /**
     * Map User entity to DTO
     */
    private UserDto mapUserToDto(User user) {
        return UserDto.builder()
            .id(user.getId().toString())
            .email(user.getEmail())
            .name(user.getName())
            .isEmailVerified(user.getIsEmailVerified())
            .tenantId(user.getTenantId() != null ? user.getTenantId().toString() : null)
            .build();
    }
}
