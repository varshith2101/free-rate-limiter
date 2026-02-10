package com.varshith.ratelimiter.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

@Component
public class AuthUtil {
    
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final SecureRandom random = new SecureRandom();
    
    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }
    
    public boolean matchPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    public String generateOtp() {
        // 6-digit OTP
        return String.format("%06d", random.nextInt(1000000));
    }
    
    public String generateVerificationToken() {
        return UUID.randomUUID().toString();
    }
}
