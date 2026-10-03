package com.codingjudge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private final JavaMailSender javaMailSender;

    private final String mailFrom = "codingjudgesitare@gmail.com";

    @Value("${spring.mail.from-name:CodingJudge}")
    private String mailFromName;

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
        System.out.println("[EmailService] Initialized - mailFrom: " + mailFrom + ", mailFromName: " + mailFromName);
    }

    /**
     * Send verification OTP email to the user through Gmail SMTP.
     */
    public void sendVerificationOtp(String email, String otp, long expiryMinutes) throws MessagingException, java.io.UnsupportedEncodingException {
        String htmlContent = buildVerificationTemplate(email, otp, expiryMinutes);
        sendHtml(email, "Coding Judge - OTP Verification", htmlContent);
    }

    /**
     * Send password reset OTP email to the user through Gmail SMTP.
     */
    public void sendPasswordResetOtp(String email, String otp, long expiryMinutes) throws MessagingException, java.io.UnsupportedEncodingException {
        String htmlContent = buildPasswordResetTemplate(email, otp, expiryMinutes);
        sendHtml(email, "Coding Judge - OTP Verification", htmlContent);
    }

    private void sendHtml(String toEmail, String subject, String htmlContent) throws MessagingException, java.io.UnsupportedEncodingException {
        System.out.println("[EmailService] sendHtml called - toEmail: " + toEmail + ", mailFrom: " + (mailFrom != null && !mailFrom.isBlank() ? "SET (" + mailFrom + ")" : "NOT SET") + ", mailFromName: " + mailFromName);
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new IllegalStateException("spring.mail.from is not configured");
        }
        System.out.println("[EmailService] Creating MimeMessage");
        MimeMessage message = javaMailSender.createMimeMessage();
        System.out.println("[EmailService] MimeMessage created, creating MimeMessageHelper");
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        System.out.println("[EmailService] Setting from: " + mailFrom + " (" + mailFromName + ")");
        helper.setFrom(mailFrom, mailFromName);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        System.out.println("[EmailService] Sending message via JavaMailSender");
        javaMailSender.send(message);
        System.out.println("[EmailService] Message sent successfully");
    }

    /**
     * Build verification OTP email template.
     */
    private String buildVerificationTemplate(String email, String otp, long expiryMinutes) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: #f4f4f4; padding: 20px; text-align: center;">
                        <h1 style="color: #333;">Coding Practice</h1>
                    </div>
                    <div style="background: white; padding: 40px 20px; margin: -40px 0 0 0;">
                        <h2 style="color: #2c3e50;">Verify your account</h2>
                        <p style="color: #666; font-size: 16px;">
                            Thanks for registering! Please use the OTP below to verify your email address.
                        </p>
                        <div style="text-align: center; margin: 30px 0;">
                            <span style="font-size: 24px; font-weight: bold; color: #e74c3c; letter-spacing: 5px;">
                                {{otp}}
                            </span>
                        </div>
                        <p style="color: #666; font-size: 14px;">
                            This OTP will expire in <strong>{{expiryMinutes}} minutes</strong>.
                        </p>
                        <p style="color: #666; font-size: 14px;">
                            <strong>Important:</strong> Do not share this OTP with anyone. 
                            Our team will never ask for your OTP via phone, email, or chat.
                        </p>
                        <div style="margin-top: 30px; padding-top: 30px; border-top: 1px solid #eee; font-size: 12px; color: #999;">
                            <p>If you did not request this email, please ignore this message.</p>
                            <p>This is an automated message from Coding Practice.</p>
                        </div>
                    </div>
                </div>
                """.replace("{{otp}}", otp).replace("{{expiryMinutes}}", String.valueOf(expiryMinutes));
    }

    /**
     * Build password reset OTP email template.
     */
    private String buildPasswordResetTemplate(String email, String otp, long expiryMinutes) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: #f4f4f4; padding: 20px; text-align: center;">
                        <h1 style="color: #333;">Coding Practice</h1>
                    </div>
                    <div style="background: white; padding: 40px 20px; margin: -40px 0 0 0;">
                        <h2 style="color: #2c3e50;">Reset your password</h2>
                        <p style="color: #666; font-size: 16px;">
                            We received a request to reset your password. Please use the OTP below 
                            to create a new password for your account.
                        </p>
                        <div style="text-align: center; margin: 30px 0;">
                            <span style="font-size: 24px; font-weight: bold; color: #e74c3c; letter-spacing: 5px;">
                                {{otp}}
                            </span>
                        </div>
                        <p style="color: #666; font-size: 14px;">
                            This OTP will expire in <strong>{{expiryMinutes}} minutes</strong>.
                        </p>
                        <p style="color: #666; font-size: 14px;">
                            <strong>Important:</strong> Do not share this OTP with anyone. 
                            Our team will never ask for your OTP via phone, email, or chat.
                        </p>
                        <div style="margin-top: 30px; padding-top: 30px; border-top: 1px solid #eee; font-size: 12px; color: #999;">
                            <p>If you did not request this password reset, please ignore this message.</p>
                            <p>This is an automated message from Coding Practice.</p>
                        </div>
                    </div>
                </div>
                """.replace("{{otp}}", otp).replace("{{expiryMinutes}}", String.valueOf(expiryMinutes));
    }
}
