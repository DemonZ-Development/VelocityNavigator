/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyColorConverterTest {

    @Test
    void detectsLegacyCodesCorrectly() {
        assertTrue(LegacyColorConverter.hasLegacyCodes("&aHello"));
        assertTrue(LegacyColorConverter.hasLegacyCodes("§6Gold"));
        assertTrue(LegacyColorConverter.hasLegacyCodes("Mixed &cRed and §lBold"));
        assertFalse(LegacyColorConverter.hasLegacyCodes("Plain text"));
        assertFalse(LegacyColorConverter.hasLegacyCodes("MiniMessage <green>text</green>"));
        assertFalse(LegacyColorConverter.hasLegacyCodes(null));
        assertFalse(LegacyColorConverter.hasLegacyCodes(""));
    }

    @Test
    void convertsAllTwentyTwoCodes() {
        assertEquals("<black>", LegacyColorConverter.convert("&0"));
        assertEquals("<dark_blue>", LegacyColorConverter.convert("&1"));
        assertEquals("<dark_green>", LegacyColorConverter.convert("&2"));
        assertEquals("<dark_aqua>", LegacyColorConverter.convert("&3"));
        assertEquals("<dark_red>", LegacyColorConverter.convert("&4"));
        assertEquals("<dark_purple>", LegacyColorConverter.convert("&5"));
        assertEquals("<gold>", LegacyColorConverter.convert("&6"));
        assertEquals("<gray>", LegacyColorConverter.convert("&7"));
        assertEquals("<dark_gray>", LegacyColorConverter.convert("&8"));
        assertEquals("<blue>", LegacyColorConverter.convert("&9"));
        assertEquals("<green>", LegacyColorConverter.convert("&a"));
        assertEquals("<aqua>", LegacyColorConverter.convert("&b"));
        assertEquals("<red>", LegacyColorConverter.convert("&c"));
        assertEquals("<light_purple>", LegacyColorConverter.convert("&d"));
        assertEquals("<yellow>", LegacyColorConverter.convert("&e"));
        assertEquals("<white>", LegacyColorConverter.convert("&f"));
        assertEquals("<obfuscated>", LegacyColorConverter.convert("&k"));
        assertEquals("<bold>", LegacyColorConverter.convert("&l"));
        assertEquals("<strikethrough>", LegacyColorConverter.convert("&m"));
        assertEquals("<underlined>", LegacyColorConverter.convert("&n"));
        assertEquals("<italic>", LegacyColorConverter.convert("&o"));
        assertEquals("<reset>", LegacyColorConverter.convert("&r"));
    }

    @Test
    void convertsSectionSignCodes() {
        assertEquals("<red>", LegacyColorConverter.convert("§c"));
        assertEquals("<bold>", LegacyColorConverter.convert("§l"));
    }

    @Test
    void handlesCaseInsensitivity() {
        assertEquals("<red>", LegacyColorConverter.convert("&C"));
        assertEquals("<bold>", LegacyColorConverter.convert("&L"));
    }

    @Test
    void convertsInlineAndBungeeHexColors() {
        assertTrue(LegacyColorConverter.hasLegacyCodes("&#55FFFFHello"));
        assertEquals("<#55FFFF>Hello", LegacyColorConverter.convert("&#55FFFFHello"));
        assertEquals("<#55FFFF>Hello", LegacyColorConverter.convert("&x&5&5&F&F&F&FHello"));
        assertEquals("<#55FFFF>Hello", LegacyColorConverter.convert("§x§5§5§F§F§F§FHello"));
    }

    @Test
    void convertsMixedAndComplexStrings() {
        assertEquals("<red><bold>Hello<reset> World<red>", LegacyColorConverter.convert("&c&lHello&r World&c"));
        assertEquals("<green>Welcome <yellow>player<green>", LegacyColorConverter.convert("&aWelcome &eplayer&a"));
    }

    @Test
    void ignoresInvalidCodes() {
        assertEquals("&zHello", LegacyColorConverter.convert("&zHello"));
        assertEquals("<dark_blue>23", LegacyColorConverter.convert("&123"));
    }

    @Test
    void preservesEscapedMiniMessageEntities() {
        String usage = "Use /party invite &lt;player&gt; or /p &lt;message&gt;.";
        assertFalse(LegacyColorConverter.hasLegacyCodes(usage));
        assertEquals(usage, LegacyColorConverter.convert(usage));
    }

    @Test
    void handlesTrailingSymbolsAndNullEmptyInputs() {
        assertNull(LegacyColorConverter.convert(null));
        assertEquals("", LegacyColorConverter.convert(""));
        assertEquals("Hello &", LegacyColorConverter.convert("Hello &"));
        assertEquals("Hello §", LegacyColorConverter.convert("Hello §"));
        assertEquals("&", LegacyColorConverter.convert("&"));
        assertEquals("§", LegacyColorConverter.convert("§"));
        assertEquals("&&", LegacyColorConverter.convert("&&"));
        assertEquals("§§", LegacyColorConverter.convert("§§"));
    }
}
