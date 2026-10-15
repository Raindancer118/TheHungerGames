package com.hungergames.platform.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeysTest {

    @Test
    void generiertLange40ZeichenSchluessel() {
        String key = ApiKeys.generate();
        assertEquals(40, key.length());
        assertTrue(key.matches("[a-zA-Z0-9]+"));
        assertNotEquals(key, ApiKeys.generate());
    }

    @Test
    void vergleichtKorrekt() {
        assertTrue(ApiKeys.matches("geheim123", "geheim123"));
        assertFalse(ApiKeys.matches("geheim123", "falsch"));
        assertFalse(ApiKeys.matches("geheim123", null));
    }

    @Test
    void leererKonfigurierterSchluesselMatchtNie() {
        assertFalse(ApiKeys.matches("", ""));
        assertFalse(ApiKeys.matches("  ", "  "));
        assertFalse(ApiKeys.matches(null, "x"));
    }
}
