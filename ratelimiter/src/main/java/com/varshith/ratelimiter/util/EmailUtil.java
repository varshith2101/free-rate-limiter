package com.varshith.ratelimiter.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class EmailUtil {

    private static final Logger logger = LoggerFactory.getLogger(EmailUtil.class);
    
    @Value("${app.mail.from:noreply@ratelimiter.local}")
    private String fromEmail;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    private final JavaMailSender mailSender;

    public EmailUtil(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    
    /**
     * Sends an OTP email to the user
     * In production, integrate with SendGrid, Mailgun, or AWS SES
     */
    public void sendOtpEmail(String email, String otp, String name) {
        if (!mailEnabled || mailUsername == null || mailPassword == null || mailUsername.isBlank() || mailPassword.isBlank()) {
            logger.warn("SMTP not configured. Skipping OTP email. email={} otp={}", email, otp);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Your Rate Limiter OTP Code");
        message.setText("Hi " + name + ",\n\n" +
            "Your OTP code is: " + otp + "\n" +
            "It expires in 10 minutes.\n\n" +
            "If you did not request this, you can ignore this email.\n\n" +
            "— Rate Limiter Team");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            logger.warn("Failed to send OTP email. Skipping. email={} error={}", email, ex.getMessage());
        }
    }
    
    /**
     * Sends verification email with token
     */
    public void sendVerificationEmail(String email, String verificationToken, String verificationUrl) {
        if (!mailEnabled || mailUsername == null || mailPassword == null || mailUsername.isBlank() || mailPassword.isBlank()) {
            logger.warn("SMTP not configured. Skipping verification email. email={} token={}", email, verificationToken);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Verify your Rate Limiter backend");
        message.setText("Please verify your backend by visiting:\n" +
            verificationUrl + "?token=" + verificationToken + "\n\n" +
            "— Rate Limiter Team");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            logger.warn("Failed to send verification email. Skipping. email={} error={}", email, ex.getMessage());
        }
    }
}
