package com.ireumgil.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NameScoreCalculatorTest {

    @Test
    public void usesExplicitOneHundredPointBreakdownWithoutHiddenNormalization() {
        NameScoreCalculator calculator = new NameScoreCalculator();

        assertEquals(100, calculator.calculateTotal(35, 30, 15, 20));
        assertEquals(84, calculator.calculateTotal(26, 23, 15, 20));
    }
}
