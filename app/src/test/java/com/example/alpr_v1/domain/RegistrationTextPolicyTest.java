package com.example.alpr_v1.domain;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RegistrationTextPolicyTest {
    @Test
    public void fourCharactersCanBeDisplayedBeforeConsensus() {
        assertTrue(RegistrationTextPolicy.displayable("WA3G"));
        assertFalse(RegistrationTextPolicy.plausibleLength(4));
    }

    @Test
    public void threeCharacterFragmentIsNotDisplayed() {
        assertFalse(RegistrationTextPolicy.displayable("A-3G"));
    }
}
