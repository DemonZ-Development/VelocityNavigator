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

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DrainService {

    private final ConcurrentMap<String, Boolean> drainState = new ConcurrentHashMap<>();

    public boolean isDrained(String serverName) {
        Boolean state = drainState.get(normalize(serverName));
        return state != null && state;
    }

    public void drain(String serverName) {
        drainState.put(normalize(serverName), Boolean.TRUE);
    }

    public void undrain(String serverName) {
        drainState.remove(normalize(serverName));
    }

    public ConcurrentMap<String, Boolean> drainState() {
        return new ConcurrentHashMap<>(drainState);
    }

    public void clear() {
        drainState.clear();
    }

    private String normalize(String serverName) {
        return serverName == null ? "" : serverName.toLowerCase(Locale.ROOT);
    }
}
