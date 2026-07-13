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

public record ServerCandidate(String name, int playerCount, int effectiveWeight, double emaLoad, long latency) {

    public ServerCandidate(String name, int playerCount) {
        this(name, playerCount, Config.LobbyEntry.DEFAULT_WEIGHT, playerCount, -1L);
    }

    public ServerCandidate(String name, int playerCount, int effectiveWeight) {
        this(name, playerCount, effectiveWeight, playerCount, -1L);
    }
}
