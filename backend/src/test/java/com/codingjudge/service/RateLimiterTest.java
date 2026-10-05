package com.codingjudge.service;

import com.codingjudge.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure unit tests: no Spring, no clock mocking, real short sleeps. */
class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();

    @Test
    void firstActionPassesSecondInsideCooldownThrows() {
        limiter.check("submit:u@uni.edu", 60);

        TooManyRequestsException thrown = assertThrows(
                TooManyRequestsException.class,
                () -> limiter.check("submit:u@uni.edu", 60));
        assertThat(thrown.getMessage()).contains("wait");
    }

    @Test
    void differentKeysAreIndependent() {
        limiter.check("submit:a@uni.edu", 60);

        limiter.check("submit:b@uni.edu", 60);
        limiter.check("run:a@uni.edu", 60);
    }

    @Test
    void expiryAllowsAgain() throws InterruptedException {
        limiter.check("custom:u@uni.edu", 1);

        Thread.sleep(1100);

        limiter.check("custom:u@uni.edu", 1);
    }
}
