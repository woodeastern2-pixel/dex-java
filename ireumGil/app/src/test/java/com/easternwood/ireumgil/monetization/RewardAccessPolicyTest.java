package com.easternwood.ireumgil.monetization;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RewardAccessPolicyTest {

    @Test
    public void requiresRewardOnlyForNinetyFiveAndAbove() {
        assertFalse(RewardAccessPolicy.requiresReward(94));
        assertTrue(RewardAccessPolicy.requiresReward(95));
        assertTrue(RewardAccessPolicy.requiresReward(100));
    }
}
