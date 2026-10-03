package com.codingjudge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;

    @Value("${spring.mail.from:codingjudgesitare@gmail.com}")
    private String mailFrom;

    @Value("${spring.mail.from-name:CodingJudge}")
    private String mailFromName;

    public EmailService() {
        System.out.println("[EmailService] Initialized with SendGrid");
    }

    /**
     * Send verification OTP email to the user via SendGrid API.
     */
    public void sendVerificationOtp(String email, String otp, long expiryMinutes) {
        String htmlContent = buildVerificationTemplate(email, otp, expiryMinutes);
        sendEmail(email, "Coding Judge - OTP Verification", htmlContent);
    }

    /**
     * Send password reset OTP email to the user.
     */
    public void sendPasswordResetOtp(String email, String otp, long expiryMinutes) {
        String htmlContent = buildPasswordResetTemplate(email, otp, expiryMinutes);
        sendEmail(email, "Coding Judge - Password Reset OTP", htmlContent);
    }

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        System.out.println("[EmailService] Sending email to: " + toEmail + ", subject: " + subject);
        
        if (sendGridApiKey == null || sendGridApiKey.isBlank()) {
            System.err.println("[EmailService] WARNING: SendGrid API key not configured, skipping email");
            return;
        }

        try {
            // Build SendGrid API request
            Map<String, Object> personalization = new HashMap<>();
            Map<String, String> to = new HashMap<>();
            to.put("email", toEmail);
            personalization.put("to", new Object[]{to});
            personalization.put("subject", subject);

            Map<String, Object> content = new HashMap<>();
            content.put("type", "text/html");
            content.put("value", htmlContent);

            Map<String, Object> requestBody = new HashMap<>();
            Map<String, String> from = new HashMap<>();
            from.put("email", mailFrom);
            from.put("name", mailFromName);
            requestBody.put("from", from);
            requestBody.put("personalizations", new Object[]{personalization});
            requestBody.put("content", new Object[]{content});

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(sendGridApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            String url = "https://api.sendgrid.com/v3/mail/send";
            restTemplate.postForEntity(url, request, String.class);
            
            System.out.println("[EmailService] Email sent successfully to: " + toEmail);
        } catch (Exception e) {
            System.err.println("[EmailService] Failed to send email: " + e.getMessage());
            e.printStackTrace();
            // Rethrow so callers surface the failure instead of leaving the
            // user in a silent dead-end (OTP stored but never delivered).
            throw new IllegalStateException("FAILED_TO_SEND_OTP: " + e.getMessage(), e);
        }
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