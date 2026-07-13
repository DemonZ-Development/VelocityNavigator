/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguagePacksTest {

    @Test
    void everyBuiltInPackContainsEveryMessageAndList() {
        LanguageBundle english = LanguageBundle.defaults();
        for (String code : LanguagePacks.supportedCodes()) {
            LanguageBundle pack = LanguagePacks.bundle(code);
            assertEquals(code, pack.language());
            assertEquals(english.strings().keySet(), pack.strings().keySet());
            assertEquals(english.lists().keySet(), pack.lists().keySet());
            assertFalse(pack.strings().values().stream().anyMatch(String::isBlank));
            assertFalse(pack.lists().values().stream().anyMatch(java.util.List::isEmpty));
        }
    }

    @Test
    void languageSelectionIsExplicitAndCustomCodesAreNotBuiltIn() {
        assertTrue(LanguagePacks.isSupported("RU"));
        assertTrue(LanguagePacks.isSupported("pt-BR"));
        assertFalse(LanguagePacks.isSupported("pirate"));
    }
}
