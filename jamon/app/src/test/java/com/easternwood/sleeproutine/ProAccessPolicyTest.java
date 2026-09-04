package com.easternwood.sleeproutine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProAccessPolicyTest {
    @Test
    public void rewardGrantsExactlyTwentyFourHours() {
        long now = 1_000_000L;
        assertEquals(now + 86_400_000L, ProAccessPolicy.expiryAfterReward(now));
    }

    @Test
    public void accessExpiresAtBoundary() {
        long expiry = 100_000L;
        assertTrue(ProAccessPolicy.isActive(expiry - 1L, expiry));
        assertFalse(ProAccessPolicy.isActive(expiry, expiry));
        assertEquals(0L, ProAccessPolicy.remainingMillis(expiry + 1L, expiry));
    }
}
