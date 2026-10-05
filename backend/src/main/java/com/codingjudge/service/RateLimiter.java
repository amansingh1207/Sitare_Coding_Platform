package com.codingjudge.service;

import com.codingjudge.exception.TooManyRequestsException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-key cooldowns for abuse-prone endpoints (submit, run, custom-run).
 *
 * <p>Same in-memory pattern as the OTP resend limits in {@link AuthService}:
 * single instance, restart resets, no DB round-trip. This guards shared
 * resources (judge queue, hosted custom-run quotas) — it is not a
 * distributed limiter, and the deployment does not need one.
 */
@Service
public class RateLimiter {

    private final ConcurrentMap<String, Long> cooldownExpiry = new ConcurrentHashMap<>();

    /**
     * Allow one action per {@code cooldownSeconds} for {@code key}.
     *
     * @throws TooManyRequestsException when the previous action is still
     *         inside its cooldown window (message carries the wait time)
     */
    public void check(String key, long cooldownSeconds) {
        long now = System.currentTimeMillis();
        Long until = cooldownExpiry.get(key);
        if (until != null && now < until) {
            long waitSeconds = (until - now + 999) / 1000;
            throw new TooManyRequestsException(
                    "Too many requests. Please wait " + waitSeconds
                            + " seconds before retrying.");
        }
        cooldownExpiry.put(key, now + cooldownSeconds * 1000);
    }
}
