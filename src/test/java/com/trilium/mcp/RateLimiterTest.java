package com.trilium.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void allowsRequestsUnderLimit() {
        var limiter = new RateLimiter(5, 100);
        limiter.checkAndRecord();
        limiter.checkAndRecord();
        assertEquals(2, limiter.getRequestCountLastMinute());
    }

    @Test
    void throwsWhenMinuteLimitExceeded() {
        var limiter = new RateLimiter(2, 100);
        limiter.checkAndRecord();
        limiter.checkAndRecord();

        var ex = assertThrows(RateLimiter.RateLimitExceededException.class, limiter::checkAndRecord);
        assertTrue(ex.getMessage().contains("last minute"));
    }

    @Test
    void throwsWhenHourLimitExceeded() {
        var limiter = new RateLimiter(1000, 2);
        limiter.checkAndRecord();
        limiter.checkAndRecord();

        var ex = assertThrows(RateLimiter.RateLimitExceededException.class, limiter::checkAndRecord);
        assertTrue(ex.getMessage().contains("last hour"));
    }
}
