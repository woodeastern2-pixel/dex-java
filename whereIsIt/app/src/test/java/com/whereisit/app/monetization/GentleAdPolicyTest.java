package com.whereisit.app.monetization;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GentleAdPolicyTest {
    @Test
    public void blocksAdDuringShortSession() {
        assertFalse(GentleAdPolicy.isEligible(
                GentleAdPolicy.MIN_SESSION_MS - 1,
                Long.MAX_VALUE,
                GentleAdPolicy.MIN_MEANINGFUL_ACTIONS));
    }

    @Test
    public void blocksAdBeforeEnoughMeaningfulActions() {
        assertFalse(GentleAdPolicy.isEligible(
                GentleAdPolicy.MIN_SESSION_MS,
                Long.MAX_VALUE,
                GentleAdPolicy.MIN_MEANINGFUL_ACTIONS - 1));
    }

    @Test
    public void blocksAdInsideCooldown() {
        assertFalse(GentleAdPolicy.isEligible(
                GentleAdPolicy.MIN_SESSION_MS,
                GentleAdPolicy.MIN_INTERVAL_MS - 1,
                GentleAdPolicy.MIN_MEANINGFUL_ACTIONS));
    }

    @Test
    public void allowsAdOnlyAfterAllConditions() {
        assertTrue(GentleAdPolicy.isEligible(
                GentleAdPolicy.MIN_SESSION_MS,
                GentleAdPolicy.MIN_INTERVAL_MS,
                GentleAdPolicy.MIN_MEANINGFUL_ACTIONS));
    }
}
