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
package com.demonz.velocitynavigator.queue;

import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.config.AdvancedConfig;
import com.demonz.velocitynavigator.config.Config;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.demonz.velocitynavigator.routing.ConnectionWorkflow;
import com.demonz.velocitynavigator.routing.RouteDecision;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class QueueService {
    private final VelocityNavigator plugin;
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();
    private volatile AdvancedConfig.Queue settings = AdvancedConfig.defaults().queue();
    private ScheduledTask task;
    private long ticks;
    private final Object lock = new Object();

    public QueueService(VelocityNavigator plugin) {
        this.plugin = plugin;
    }

    public void configure(AdvancedConfig.Queue next) {
        synchronized (lock) {
            settings = next;
            if (task != null) task.cancel();
            task = null;
            if (!next.enabled()) {
                entries.clear();
                return;
            }
            task = plugin.server().getScheduler().buildTask(plugin, this::process)
                    .delay(next.pollSeconds(), TimeUnit.SECONDS)
                    .repeat(next.pollSeconds(), TimeUnit.SECONDS)
                    .schedule();
        }
    }

    public boolean enqueue(Player player, RouteDecision decision) {
        synchronized (lock) {
            if (!settings.enabled() || entries.size() >= settings.maxSize()) return false;
            entries.putIfAbsent(player.getUniqueId(), new Entry(player.getUniqueId(), decision.sourceServer(), Instant.now()));
        }
        notifyPlayer(player);
        return true;
    }

    public boolean canQueue(RouteDecision decision, Config config) {
        if (!settings.enabled() || decision == null || decision.configuredCandidates().isEmpty()) return false;
        Map<String, Config.LobbyEntry> configured = new LinkedHashMap<>();
        collect(configured, config.routing().defaultLobbies());
        config.routing().contextual().groups().values().forEach(group -> collect(configured, group.servers()));
        boolean found = false;
        for (String name : decision.configuredCandidates()) {
            Config.LobbyEntry lobby = configured.get(name.toLowerCase(Locale.ROOT));
            if (lobby == null) lobby = plugin.dynamicLobby(name).orElse(null);
            Optional<RegisteredServer> registered = plugin.server().getServer(name);
            if (lobby == null || registered.isEmpty() || lobby.maxPlayers() == Config.LobbyEntry.UNCAPPED) return false;
            found = true;
            if (!lobby.isFull(registered.get().getPlayersConnected().size())) return false;
        }
        return found;
    }

    private void collect(Map<String, Config.LobbyEntry> target, List<Config.LobbyEntry> entries) {
        entries.forEach(entry -> target.putIfAbsent(entry.server().toLowerCase(Locale.ROOT), entry));
    }

    public boolean remove(UUID player) {
        synchronized (lock) {
            return entries.remove(player) != null;
        }
    }

    public int position(UUID player) {
        int index = 1;
        synchronized (lock) {
            for (UUID id : entries.keySet()) {
                if (id.equals(player)) return index;
                index++;
            }
        }
        return 0;
    }

    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    public void stop() {
        synchronized (lock) {
            if (task != null) task.cancel();
            task = null;
            entries.clear();
        }
    }

    private void process() {
        List<Entry> snapshot;
        long currentTicks;
        boolean enabled;
        int maxSize;
        int notifySeconds;
        int pollSeconds;
        synchronized (lock) {
            enabled = settings.enabled();
            currentTicks = ++ticks;
            maxSize = settings.maxSize();
            notifySeconds = settings.notifySeconds();
            pollSeconds = settings.pollSeconds();
            snapshot = new ArrayList<>(entries.values());
        }
        if (!enabled) return;
        int batchLimit = Math.min(snapshot.size(), 50);
        for (int i = 0; i < batchLimit; i++) {
            Entry entry = snapshot.get(i);
            Optional<Player> optional = plugin.server().getPlayer(entry.playerId());
            if (optional.isEmpty()) {
                remove(entry.playerId());
                continue;
            }
            Player player = optional.get();
            plugin.previewRoute(player).thenAccept(decision -> {
                if (!decision.hasSelection()) {
                    long notifyEvery = Math.max(1, (notifySeconds + pollSeconds - 1L) / pollSeconds);
                    if (currentTicks % notifyEvery == 0) notifyPlayer(player);
                    return;
                }
                Optional<RegisteredServer> target = plugin.server().getServer(decision.selectedServer());
                if (target.isEmpty() || !remove(player.getUniqueId())) return;
                player.sendMessage(MessageFormatter.render(plugin.config().language().text("queue.connecting"), Map.of("server", target.get().getServerInfo().getName()), player));
                ConnectionWorkflow.connectWithRetry(plugin, player, plugin.config(), target.get(), decision, "queue");
            }).exceptionally(error -> {
                plugin.logger().error("[QueueService] Failed to preview route for player " + player.getUsername(), error);
                return null;
            });
        }
    }

    private void notifyPlayer(Player player) {
        int position = position(player.getUniqueId());
        if (position == 0) return;
        Map<String, String> values = Map.of("position", String.valueOf(position), "size", String.valueOf(size()));
        player.sendActionBar(MessageFormatter.render(plugin.config().language().text("queue.position"), values, player));
    }

    private record Entry(UUID playerId, String sourceServer, Instant joinedAt) {
    }
}
