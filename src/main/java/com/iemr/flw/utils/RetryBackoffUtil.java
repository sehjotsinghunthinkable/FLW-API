package com.iemr.flw.utils;

import java.sql.Timestamp;

/**
 * Generic exponential-backoff schedule (1, 2, 4, 8... minutes, capped), independent of any
 * specific retry job so it can be reused wherever a "retry later" timestamp is needed.
 */
public final class RetryBackoffUtil {

    private RetryBackoffUtil() {
    }

    public static Timestamp computeNextAttempt(int retryCount, int capMinutes) {
        long delayMinutes = Math.min((long) Math.pow(2, Math.max(retryCount - 1, 0)), capMinutes);
        return new Timestamp(System.currentTimeMillis() + delayMinutes * 60_000L);
    }
}
