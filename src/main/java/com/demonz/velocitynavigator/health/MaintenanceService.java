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
package com.demonz.velocitynavigator.health;

import java.util.Collections;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MaintenanceService {

    private volatile boolean globalMaintenance = false;
    private volatile String globalReason = "Network under maintenance";
    private final Set<String> maintenanceServers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, String> perServerReasons = new ConcurrentHashMap<>();

    public boolean isGlobalMaintenance() {
        return globalMaintenance;
    }

    public void setGlobalMaintenance(boolean enabled, String reason) {
        if (reason != null && !reason.isBlank()) {
            this.globalReason = reason;
        }
        this.globalMaintenance = enabled;
    }

    public String globalReason() {
        return globalReason;
    }

    public boolean isServerInMaintenance(String serverId) {
        return globalMaintenance || (serverId != null && maintenanceServers.contains(serverId.toLowerCase(Locale.ROOT)));
    }

    public void setServerMaintenance(String serverId, boolean enabled, String reason) {
        if (serverId == null) return;
        String s = serverId.toLowerCase(Locale.ROOT);
        if (enabled) {
            if (reason != null && !reason.isBlank()) {
                perServerReasons.put(s, reason);
            }
            maintenanceServers.add(s);
        } else {
            maintenanceServers.remove(s);
            perServerReasons.remove(s);
        }
    }

    public String serverReason(String serverId) {
        if (globalMaintenance) {
            return globalReason;
        }
        if (serverId != null) {
            return perServerReasons.getOrDefault(serverId.toLowerCase(Locale.ROOT), "Server is under maintenance");
        }
        return "Server is under maintenance";
    }

    public Set<String> maintenanceServers() {
        return Set.copyOf(maintenanceServers);
    }
}
