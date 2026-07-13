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

import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownServiceTest {

    @Test
    void secondsRemainingReturnsEmptyWhenExactlyExpired() throws InterruptedException {
        CooldownService service = new CooldownService();
        UUID playerId = UUID.randomUUID();

        service.apply(playerId, 1);
        Thread.sleep(1100);

        OptionalLong remaining = service.secondsRemaining(playerId);
        assertTrue(remaining.isEmpty(), "Cooldown should be expired after 1100ms for a 1-second cooldown");
    }

    @Test
    void secondsRemainingNeverReturnsZero() {
        CooldownService service = new CooldownService();
        UUID playerId = UUID.randomUUID();

        service.apply(playerId, 60);
        OptionalLong remaining = service.secondsRemaining(playerId);

        assertTrue(remaining.isPresent());
        assertTrue(remaining.getAsLong() >= 1,
                "secondsRemaining should never return zero; got " + remaining.getAsLong());
    }

    @Test
    void secondsRemainingCapturesInstantOnce() {
        CooldownService service = new CooldownService();
        UUID playerId = UUID.randomUUID();

        service.apply(playerId, 2);

        for (int i = 0; i < 100; i++) {
            OptionalLong remaining = service.secondsRemaining(playerId);
            if (remaining.isPresent()) {
                assertTrue(remaining.getAsLong() >= 1,
                        "secondsRemaining should never return < 1; got " + remaining.getAsLong() + " on iteration " + i);
            }
        }
    }
}
