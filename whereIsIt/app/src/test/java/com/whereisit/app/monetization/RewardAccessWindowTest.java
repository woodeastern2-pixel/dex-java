package com.whereisit.app.monetization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RewardAccessWindowTest {
    @Test
    public void rewardLastsExactlySixtyMinutes() {
        long grantedAt = 1_000_000L;
        long until = grantedAt + RewardAccessWindow.ACCESS_DURATION_MS;

        assertTrue(RewardAccessWindow.isActive(grantedAt, until, grantedAt));
        assertTrue(RewardAccessWindow.isActive(grantedAt, until, until - 1L));
        assertFalse(RewardAccessWindow.isActive(grantedAt, until, until));
    }

    @Test
    public void rejectsExtendedOrRolledBackClockWindows() {
        long grantedAt = 1_000_000L;

        assertFalse(RewardAccessWindow.isActive(
                grantedAt,
                grantedAt + RewardAccessWindow.ACCESS_DURATION_MS + 1_001L,
                grantedAt));
        assertFalse(RewardAccessWindow.isActive(
                grantedAt,
                grantedAt + RewardAccessWindow.ACCESS_DURATION_MS,
                grantedAt - 1L));
    }

    @Test
    public void roundsRemainingTimeUpToVisibleMinutes() {
        assertEquals(60L, RewardAccessWindow.remainingMinutes(3_600_000L, 0L));
        assertEquals(1L, RewardAccessWindow.remainingMinutes(60_000L, 59_999L));
        assertEquals(0L, RewardAccessWindow.remainingMinutes(60_000L, 60_000L));
    }
}
