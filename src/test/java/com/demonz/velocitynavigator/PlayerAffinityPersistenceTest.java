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
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAffinityPersistenceTest {

    @TempDir
    Path tempDir;

    private Logger noopLogger() {
        return (Logger) java.lang.reflect.Proxy.newProxyInstance(
                Logger.class.getClassLoader(),
                new Class<?>[]{Logger.class},
                (proxy, method, args) -> null
        );
    }

    @Test
    void savesAndRestoresAffinityEntries() {
        Path store = tempDir.resolve("affinity-store.json");
        PlayerAffinityService original = new PlayerAffinityService(1.0);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        original.setAffinity(id1, "lobby-1");
        original.setAffinity(id2, "lobby-2");

        original.saveTo(store, noopLogger());
        assertTrue(java.nio.file.Files.exists(store));

        PlayerAffinityService restored = new PlayerAffinityService(1.0);
        int loaded = restored.loadFrom(store, noopLogger());
        assertEquals(2, loaded);
        assertEquals("lobby-1", restored.getAffinity(id1).orElseThrow());
        assertEquals("lobby-2", restored.getAffinity(id2).orElseThrow());
    }

    @Test
    void returnsZeroWhenStoreFileMissing() {
        Path store = tempDir.resolve("nonexistent.json");
        PlayerAffinityService service = new PlayerAffinityService(1.0);
        int loaded = service.loadFrom(store, noopLogger());
        assertEquals(0, loaded);
    }

    @Test
    void handlesCorruptedStoreGracefully() throws Exception {
        Path store = tempDir.resolve("corrupt.json");
        java.nio.file.Files.writeString(store, "{ this is not valid JSON");
        PlayerAffinityService service = new PlayerAffinityService(1.0);
        int loaded = service.loadFrom(store, noopLogger());
        assertEquals(0, loaded);
    }

    @Test
    void expiredEntriesAreSkippedOnSave() throws Exception {
        Path store = tempDir.resolve("affinity-store.json");
        PlayerAffinityService original = new PlayerAffinityService(1.0, java.time.Duration.ofMillis(1));
        original.setAffinity(UUID.randomUUID(), "lobby-1");
        Thread.sleep(50);
        original.saveTo(store, noopLogger());
        PlayerAffinityService restored = new PlayerAffinityService(1.0);
        int loaded = restored.loadFrom(store, noopLogger());
        assertEquals(0, loaded, "expired entries should not be persisted");
    }
}
