package com.codingjudge.service;

import com.codingjudge.exception.DuplicateResourceException;
import com.codingjudge.model.dto.request.LoginRequest;
import com.codingjudge.model.dto.request.RegisterRequest;
import com.codingjudge.model.dto.response.AuthResponse;
import com.codingjudge.model.dto.response.UserResponse;
import com.codingjudge.model.entity.User;
import com.codingjudge.model.enums.Role;
import com.codingjudge.repository.UserRepository;
import com.codingjudge.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // In-memory OTP storage for verification requests (keyed by email + otpType)
    // In production, this would be backed by a database or Redis with TTL
    private final ConcurrentMap<String, OtpVerificationState> otpRequests = new ConcurrentHashMap<>();

    // Separate maps for rate limiting (keyed by normalized email)
    private final ConcurrentMap<String, Integer> otpSendCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> otpCooldownExpiry = new ConcurrentHashMap<>();

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_OTP_SENDS_PER_HOUR = 5;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.emailService = emailService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username is already taken");
        }

        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(Role.STUDENT);
        user.setEmailVerified(false);

        UserResponse response = UserResponse.from(userRepository.save(user));

        // Best-effort: send the verification OTP without blocking registration.
        try {
            sendVerificationOtp(request.getEmail());
        } catch (Exception ignored) {
            // OTP can be re-requested via the resend endpoint.
        }

        return response;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        // Block login if email is not verified
        if (!user.isEmailVerified()) {
            throw new IllegalStateException("EMAIL_NOT_VERIFIED");
        }

        String token = tokenProvider.generateToken(email);
        return new AuthResponse(token, tokenProvider.getExpirationSeconds(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return UserResponse.from(user);
    }

    /**
     * Send verification OTP to the user's email.
     */
    @Transactional
    public String sendVerificationOtp(String email) {
        String normalizedEmail = normalizeEmail(email);

        // Check if account already verified
        var existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent() && existing.get().isEmailVerified()) {
            throw new IllegalStateException("EMAIL_ALREADY_VERIFIED");
        }

        System.out.println("[AuthService] sendVerificationOtp called for: " + normalizedEmail);
        enforceOtpRateLimit(normalizedEmail);
        System.out.println("[AuthService] Rate limit check passed");

        // Generate cryptographically secure 6-digit numeric OTP
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        System.out.println("[AuthService] Generated OTP: " + otp);

        // Store OTP verification state
        String otpKey = normalizedEmail + ":verify";
        OtpVerificationState request = new OtpVerificationState();
        request.setOtpHash(hashOtp(otp));
        request.setSentAt(Instant.now());
        request.setExpiresAt(Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60));
        request.setAttempts(0);
        request.setMaxAttempts(MAX_VERIFICATION_ATTEMPTS);
        request.setUsed(false);

        otpRequests.put(otpKey, request);

        // Send OTP via email service
        try {
            emailService.sendVerificationOtp(normalizedEmail, otp, OTP_EXPIRY_MINUTES);
        } catch (Exception e) {
            otpRequests.remove(normalizedEmail + ":verify");
            System.out.println("OTP email send failed: " + e);
            throw new IllegalStateException("FAILED_TO_SEND_OTP: " + e.getMessage());
        }

        return normalizedEmail;
    }

    /**
     * Verify the OTP sent to the user's email.
     * Returns the normalized email if verification succeeds, null otherwise.
     */
    @Transactional
    public String verifyOtp(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);
        String otpKey = normalizedEmail + ":verify";

        OtpVerificationState request = otpRequests.get(otpKey);
        if (request == null) {
            return null; // No OTP request found
        }

        // Check if already used
        if (request.isUsed()) {
            return null;
        }

        // Check expiry
        if (Instant.now().isAfter(request.getExpiresAt())) {
            otpRequests.remove(otpKey);
            return null; // OTP expired
        }

        // Check attempt limit
        if (request.getAttempts() >= request.getMaxAttempts()) {
            otpRequests.remove(otpKey);
            return null; // Too many attempts
        }

        // Increment attempts
        request.setAttempts(request.getAttempts() + 1);

        // Compare submitted OTP hash with stored hash
        String expectedHash = request.getOtpHash();
        boolean matches = passwordEncoder.matches(otp, expectedHash);

        if (matches) {
            // OTP verified successfully - mark as used and mark email as verified
            request.setUsed(true);
            userRepository.findByEmail(normalizedEmail).ifPresent(u -> {
                u.setEmailVerified(true);
                userRepository.save(u);
            });
            otpRequests.remove(otpKey);
            return normalizedEmail;
        } else {
            // OTP does not match - continue allowing attempts until limit
            return null;
        }
    }

    /**
     * Send password reset OTP to the user's email.
     * Returns a generic message to avoid email enumeration.
     */
    @Transactional
    public String sendPasswordResetOtp(String email) {
        String normalizedEmail = normalizeEmail(email);

        enforceOtpRateLimit(normalizedEmail);

        // Generate OTP for password reset
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        System.out.println("[AuthService] sendPasswordResetOtp called for: " + normalizedEmail + ", OTP: " + otp);

        // Store OTP verification state
        String otpKey = "reset:" + normalizedEmail + ":otp";
        OtpVerificationState request = new OtpVerificationState();
        request.setOtpHash(hashOtp(otp));
        request.setSentAt(Instant.now());
        request.setExpiresAt(Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60));
        request.setAttempts(0);
        request.setMaxAttempts(MAX_VERIFICATION_ATTEMPTS);
        request.setUsed(false);

        otpRequests.put(otpKey, request);

        // Send OTP via email service
        try {
            emailService.sendPasswordResetOtp(normalizedEmail, otp, OTP_EXPIRY_MINUTES);
        } catch (Exception e) {
            // If email fails, still "success" to avoid email enumeration
        }

        return normalizedEmail;
    }

    @Transactional
    public boolean verifyPasswordResetOtp(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);
        String otpKey = "reset:" + normalizedEmail + ":otp";

        OtpVerificationState request = otpRequests.get(otpKey);
        if (request == null) {
            return false;
        }

        // Check if already used
        if (request.isUsed()) {
            return false;
        }

        // Check expiry
        if (Instant.now().isAfter(request.getExpiresAt())) {
            otpRequests.remove(otpKey);
            return false;
        }

        // Check attempt limit
        if (request.getAttempts() >= request.getMaxAttempts()) {
            otpRequests.remove(otpKey);
            return false;
        }

        // Increment attempts
        request.setAttempts(request.getAttempts() + 1);

        // Compare OTP
        String expectedHash = request.getOtpHash();
        boolean matches = passwordEncoder.matches(otp, expectedHash);

        if (matches) {
            // OTP verified - invalidate the request
            request.setUsed(true);
            otpRequests.remove(otpKey);
            return true;
        } else {
            request.setAttempts(request.getAttempts() + 1);
            return false;
        }
    }

    /**
     * Reset the user's password after OTP verification.
     */
    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        if (!verifyPasswordResetOtp(email, otp)) {
            throw new IllegalStateException("INVALID_OR_EXPIRED_OTP");
        }
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Per-email OTP rate limiting: one resend per cooldown window and a hard cap per hour.
     */
    private void enforceOtpRateLimit(String normalizedEmail) {
        long now = Instant.now().getEpochSecond();
        Long cooldownUntil = otpCooldownExpiry.get(normalizedEmail);
        if (cooldownUntil != null && now < cooldownUntil) {
            throw new IllegalStateException("OTP_RESEND_COOLDOWN");
        }
        int sends = otpSendCounts.getOrDefault(normalizedEmail, 0);
        if (sends >= MAX_OTP_SENDS_PER_HOUR) {
            throw new IllegalStateException("OTP_RATE_LIMITED");
        }
        otpSendCounts.put(normalizedEmail, sends + 1);
        otpCooldownExpiry.put(normalizedEmail, now + RESEND_COOLDOWN_SECONDS);
    }

    /**
     * Hash the OTP using a secure one-way function.
     * Uses the injected PasswordEncoder (BCrypt) for OTP hashing.
     * OTPs are short-lived (10 minutes) and single-use, so using BCrypt is acceptable.
     */
    private String hashOtp(String otp) {
        return passwordEncoder.encode(otp);
    }

    /**
     * Compare submitted OTP with stored hash.
     */
    private boolean otpMatches(String storedHash, String submittedOtp) {
        return passwordEncoder.matches(submittedOtp, storedHash);
    }

    /**
     * Normalize email to lowercase for case-insensitive handling.
     */
    private String normalizeEmail(String email) {
        return email != null ? email.toLowerCase().trim() : null;
    }

    /**
     * Data holder for OTP verification request state.
     */
    private static class OtpVerificationState {
        private String otpHash;
        private Instant sentAt;
        private Instant expiresAt;
        private int attempts;
        private int maxAttempts = MAX_VERIFICATION_ATTEMPTS;
        private boolean used;

        public String getOtpHash() { return otpHash; }
        public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
        public Instant getSentAt() { return sentAt; }
        public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public boolean isUsed() { return used; }
        public void setUsed(boolean used) { this.used = used; }
    }
}