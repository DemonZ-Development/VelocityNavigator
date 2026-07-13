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

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class UpdateStatus {

    public record Snapshot(
            Instant lastCheckedAt,
            String latestKnownVersion,
            boolean updateAvailable,
            String lastError
    ) {
        public static final Snapshot INITIAL = new Snapshot(null, "unknown", false, "");
    }

    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.INITIAL);

    public Snapshot snapshot() {
        return current.get();
    }

    public Instant lastCheckedAt() {
        return current.get().lastCheckedAt();
    }

    public String latestKnownVersion() {
        return current.get().latestKnownVersion();
    }

    public boolean updateAvailable() {
        return current.get().updateAvailable();
    }

    public String lastError() {
        return current.get().lastError();
    }

    public void recordSuccess(String latestKnownVersion, boolean updateAvailable) {
        current.set(new Snapshot(Instant.now(), latestKnownVersion, updateAvailable, ""));
    }

    public void recordFailure(String lastError) {
        String error = lastError == null ? "Unknown error" : lastError;
        current.updateAndGet(prev -> new Snapshot(
                Instant.now(), prev.latestKnownVersion(), false, error));
    }
}
