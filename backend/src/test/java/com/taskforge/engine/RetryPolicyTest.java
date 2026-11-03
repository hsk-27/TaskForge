package com.taskforge.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void testExponentialGrowth() {
        RetryPolicy policy = new RetryPolicy();
        long baseMs = 1000;
        long maxMs = 30000;
        double jitter = 0.0; // zero jitter for deterministic check

        Instant now = Instant.now();
        Instant retry1 = policy.calculateNextRetryTime(1, baseMs, maxMs, jitter);
        Instant retry2 = policy.calculateNextRetryTime(2, baseMs, maxMs, jitter);
        Instant retry3 = policy.calculateNextRetryTime(3, baseMs, maxMs, jitter);

        long diff1 = Duration.between(now, retry1).toMillis();
        long diff2 = Duration.between(now, retry2).toMillis();
        long diff3 = Duration.between(now, retry3).toMillis();

        // Check relative growth (allowing small execution timing drift)
        assertTrue(diff1 >= 950 && diff1 <= 1200, "Attempt 1 should be ~1000ms, was " + diff1);
        assertTrue(diff2 >= 1950 && diff2 <= 2200, "Attempt 2 should be ~2000ms, was " + diff2);
        assertTrue(diff3 >= 3950 && diff3 <= 4200, "Attempt 3 should be ~4000ms, was " + diff3);
    }

    @Test
    void testMaxBackoffCap() {
        RetryPolicy policy = new RetryPolicy();
        long baseMs = 1000;
        long maxMs = 5000;
        double jitter = 0.0;

        Instant now = Instant.now();
        Instant retry10 = policy.calculateNextRetryTime(10, baseMs, maxMs, jitter);
        long diff = Duration.between(now, retry10).toMillis();

        assertTrue(diff <= 5100, "Delay must be capped at maxBackoffMs, was " + diff);
    }
}
