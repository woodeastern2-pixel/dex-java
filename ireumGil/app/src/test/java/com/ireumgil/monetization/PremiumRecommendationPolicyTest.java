package com.ireumgil.monetization;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PremiumRecommendationPolicyTest {

    @Test
    public void locksOnlyNinetyFiveAndAbove() {
        assertFalse(PremiumRecommendationPolicy.requiresPro(94));
        assertTrue(PremiumRecommendationPolicy.requiresPro(95));
        assertTrue(PremiumRecommendationPolicy.requiresPro(100));
    }
}
