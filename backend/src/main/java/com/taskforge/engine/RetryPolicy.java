package com.taskforge.engine;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CS Theory: Exponential Backoff with Decorrelated Jitter
 *
 * In distributed computing, when multiple jobs fail due to transient issues
 * (e.g., temporary database connection drop, external API rate limiting),
 * retrying them at fixed intervals causes the "Thundering Herd" problem:
 * all jobs wake up at the exact same instant and crash the recovering service again.
 *
 * Mathematical Model:
 * 1. Base Exponential Delay:
 *    delay = min(maxBackoffMs, baseBackoffMs * 2^(attempt - 1))
 *    - Attempt 1: 1000ms * 2^0 = 1000ms (1.0s)
 *    - Attempt 2: 1000ms * 2^1 = 2000ms (2.0s)
 *    - Attempt 3: 1000ms * 2^2 = 4000ms (4.0s)
 *
 * 2. Full Randomized Jitter:
 *    offset = jitterFactor * delay * (2 * random() - 1)
 *    Adds a +/- uniform distribution around the delay so retries spread out smoothly over time.
 */
@Component
public class RetryPolicy {

    /**
     * Calculates the timestamp for the next retry attempt using exponential backoff with jitter.
     *
     * @param attemptNumber the current attempt count (1-based)
     * @param baseBackoffMs base backoff duration in milliseconds (e.g. 1000ms)
     * @param maxBackoffMs maximum backoff duration in milliseconds (e.g. 30000ms)
     * @param jitterFactor jitter variation percentage (e.g. 0.1 for +/- 10%)
     * @return Instant representing the scheduled time for the next attempt
     */
    public Instant calculateNextRetryTime(int attemptNumber, long baseBackoffMs, long maxBackoffMs, double jitterFactor) {
        // Bit-shift 1L << (attempt - 1) computes 2^(attempt - 1) in O(1) CPU cycles
        long exponentialMultiplier = 1L << Math.min(20, Math.max(0, attemptNumber - 1));
        long uncappedDelay;
        try {
            uncappedDelay = Math.multiplyExact(baseBackoffMs, exponentialMultiplier);
        } catch (ArithmeticException e) {
            uncappedDelay = maxBackoffMs; // Prevent 64-bit integer overflow
        }

        long baseDelay = Math.min(maxBackoffMs, Math.max(100L, uncappedDelay));

        // Add random jitter around base delay to break thread synchronization
        double jitterRange = baseDelay * Math.max(0.0, Math.min(0.5, jitterFactor));
        double randomOffset = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * jitterRange;
        long finalDelayMs = Math.round(baseDelay + randomOffset);
        finalDelayMs = Math.min(maxBackoffMs, Math.max(50L, finalDelayMs));

        return Instant.now().plus(Duration.ofMillis(finalDelayMs));
    }
}
