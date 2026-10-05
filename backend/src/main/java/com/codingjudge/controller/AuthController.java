package com.codingjudge.controller;

import com.codingjudge.model.dto.ApiResponse;
import com.codingjudge.model.dto.request.EmailRequest;
import com.codingjudge.model.dto.request.LoginRequest;
import com.codingjudge.model.dto.request.RegisterRequest;
import com.codingjudge.model.dto.request.ResetPasswordRequest;
import com.codingjudge.model.dto.request.VerifyOtpRequest;
import com.codingjudge.model.dto.response.AuthResponse;
import com.codingjudge.model.dto.response.UserResponse;
import com.codingjudge.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse auth = authService.login(request);
            return ResponseEntity.ok(ApiResponse.ok(auth));
        } catch (IllegalStateException e) {
            // EMAIL_NOT_VERIFIED - return specific error code
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail("EMAIL_NOT_VERIFIED", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserResponse user = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<String>> heartbeat(
            @AuthenticationPrincipal UserDetails userDetails) {
        authService.heartbeat(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("heartbeat recorded"));
    }

    @PostMapping("/send-verification-otp")
    public ResponseEntity<ApiResponse<String>> sendVerificationOtp(
            @Valid @RequestBody EmailRequest request) {
        try {
            authService.sendVerificationOtp(request.getEmail());
            return ResponseEntity.ok(ApiResponse.ok("Verification OTP sent"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("OTP_ERROR", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail("INTERNAL_ERROR", "Email service error: " + e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<String>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        String verified = authService.verifyOtp(request.getEmail(), request.getOtp());
        if (verified == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("INVALID_OTP", "Invalid or expired OTP"));
        }
        return ResponseEntity.ok(ApiResponse.ok("Email verified successfully"));
    }

    @PostMapping("/send-password-reset-otp")
    public ResponseEntity<ApiResponse<String>> sendPasswordResetOtp(
            @Valid @RequestBody EmailRequest request) {
        try {
            authService.sendPasswordResetOtp(request.getEmail());
            return ResponseEntity.ok(ApiResponse.ok(
                    "If the account exists, a password reset OTP has been sent"));
        } catch (Exception e) {
            System.err.println("[AuthController] sendPasswordResetOtp failed: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(ApiResponse.ok(
                    "If the account exists, a password reset OTP has been sent"));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
            return ResponseEntity.ok(ApiResponse.ok("Password reset successfully"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("RESET_FAILED", e.getMessage()));
        }
    }
}
