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

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManagedServerService {
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern SERVER_LINE = Pattern.compile("^\\s*(?:\"([A-Za-z0-9_.-]+)\"|([A-Za-z0-9_.-]+))\\s*=.*$");
    private final VelocityNavigator plugin;
    private final Path registryFile;
    private final Map<String, ManagedLobby> lobbies = new LinkedHashMap<>();
    private volatile AdvancedConfig.ServerManagement settings = AdvancedConfig.defaults().serverManagement();

    public ManagedServerService(VelocityNavigator plugin) {
        this.plugin = plugin;
        this.registryFile = plugin.getDataDirectory().resolve("servers.toml");
    }

    public synchronized void configure(AdvancedConfig.ServerManagement next) throws IOException {
        settings = next;
        lobbies.values().forEach(lobby -> plugin.unregisterDynamicLobby(lobby.name()));
        lobbies.clear();
        if (!next.enabled()) return;
        loadRegistry();
        for (ManagedLobby lobby : lobbies.values()) applyRuntime(lobby);
    }

    public synchronized Result addGame(String name, String address) {
        if (!settings.enabled()) return Result.failure("Server management is disabled in navigator.toml.");
        ParsedAddress parsed = parse(name, address);
        if (!parsed.valid()) return Result.failure(parsed.error());
        try {
            backup(velocityConfigPath());
            Result persisted = writeVelocity(parsed.name(), parsed.host(), parsed.port(), false);
            if (!persisted.success()) return persisted;
            registerRuntime(parsed.name(), parsed.host(), parsed.port());
            return Result.success("Game server '" + parsed.name() + "' was added to velocity.toml only.");
        } catch (IOException error) {
            return Result.failure("Could not update velocity.toml: " + error.getMessage());
        }
    }

    public synchronized Result addLobby(String name, String address, String group, int maxPlayers, int weight) {
        if (!settings.enabled()) return Result.failure("Server management is disabled in navigator.toml.");
        ParsedAddress parsed = parse(name, address);
        if (!parsed.valid()) return Result.failure(parsed.error());
        String normalizedGroup = group == null || group.isBlank() ? "default" : group.trim().toLowerCase(Locale.ROOT);
        ManagedLobby lobby = new ManagedLobby(parsed.name(), parsed.host(), parsed.port(), normalizedGroup, maxPlayers, weight);
        byte[] velocityBefore = null;
        byte[] registryBefore = null;
        boolean captured = false;
        Map<String, ManagedLobby> lobbiesBefore = new LinkedHashMap<>(lobbies);
        try {
            velocityBefore = snapshot(velocityConfigPath());
            registryBefore = snapshot(registryFile);
            captured = true;
            backup(velocityConfigPath());
            backup(registryFile);
            Result persisted = writeVelocity(parsed.name(), parsed.host(), parsed.port(), false);
            if (!persisted.success()) return persisted;
            lobbies.put(parsed.name().toLowerCase(Locale.ROOT), lobby);
            writeRegistry();
            applyRuntime(lobby);
            return Result.success("Lobby '" + parsed.name() + "' was added to velocity.toml and VelocityNavigator group '" + normalizedGroup + "'.");
        } catch (IOException | RuntimeException error) {
            if (captured) {
                restore(velocityConfigPath(), velocityBefore);
                restore(registryFile, registryBefore);
            }
            lobbies.clear();
            lobbies.putAll(lobbiesBefore);
            plugin.unregisterDynamicLobby(parsed.name());
            lobbiesBefore.values().forEach(this::applyRuntime);
            return Result.failure("Could not persist lobby: " + error.getMessage());
        }
    }

    public synchronized Result remove(String name) {
        if (!settings.enabled()) return Result.failure("Server management is disabled in navigator.toml.");
        if (name == null || !SERVER_NAME.matcher(name).matches()) return Result.failure("Invalid server name.");
        byte[] velocityBefore = null;
        byte[] registryBefore = null;
        boolean captured = false;
        Map<String, ManagedLobby> lobbiesBefore = new LinkedHashMap<>(lobbies);
        try {
            velocityBefore = snapshot(velocityConfigPath());
            registryBefore = snapshot(registryFile);
            captured = true;
            List<String> forcedHosts = forcedHostReferences(name);
            backup(velocityConfigPath());
            backup(registryFile);
            Result removed = removeVelocity(name);
            if (!removed.success()) return removed;
            lobbies.remove(name.toLowerCase(Locale.ROOT));
            writeRegistry();
            plugin.unregisterDynamicLobby(name);
            plugin.server().getServer(name).ifPresent(server -> plugin.server().unregisterServer(server.getServerInfo()));
            String warning = forcedHosts.isEmpty() ? "" : " Forced-host entries still reference it: " + String.join(", ", forcedHosts) + ".";
            return Result.success("Server '" + name + "' was removed from managed configuration." + warning);
        } catch (IOException | RuntimeException error) {
            if (captured) {
                restore(velocityConfigPath(), velocityBefore);
                restore(registryFile, registryBefore);
            }
            lobbies.clear();
            lobbies.putAll(lobbiesBefore);
            lobbiesBefore.values().forEach(this::applyRuntime);
            return Result.failure("Could not remove server: " + error.getMessage());
        }
    }

    public synchronized Result dryRun(String type, String name, String address, String group, int maxPlayers, int weight) {
        if (!settings.enabled()) return Result.failure("Server management is disabled in navigator.toml.");
        ParsedAddress parsed = parse(name, address);
        if (!parsed.valid()) return Result.failure(parsed.error());
        try {
            Result check = inspectVelocity(parsed.name(), parsed.host(), parsed.port());
            if (!check.success()) return check;
            if ("game".equalsIgnoreCase(type)) return Result.success("Dry run passed: game server will update " + velocityConfigPath() + " only.");
            if (!"lobby".equalsIgnoreCase(type)) return Result.failure("Type must be game or lobby.");
            String targetGroup = group == null || group.isBlank() ? "default" : group.toLowerCase(Locale.ROOT);
            return Result.success("Dry run passed: lobby will update " + velocityConfigPath() + " and " + registryFile + " with group=" + targetGroup + ", max_players=" + maxPlayers + ", weight=" + weight + ".");
        } catch (IOException error) {
            return Result.failure("Dry run failed: " + error.getMessage());
        }
    }

    public synchronized Validation validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Path velocity = velocityConfigPath();
        if (!Files.exists(velocity)) errors.add("Velocity config not found: " + velocity);
        else {
            try {
                Toml toml = new Toml().read(velocity.toFile());
                if (toml.getTable("servers") == null) warnings.add("velocity.toml has no [servers] table.");
                for (ManagedLobby lobby : lobbies.values()) {
                    if (plugin.server().getServer(lobby.name()).isEmpty()) warnings.add("Managed lobby is not registered at runtime: " + lobby.name());
                }
            } catch (RuntimeException error) {
                errors.add("velocity.toml is invalid: " + error.getMessage());
            }
        }
        try {
            if (Files.exists(registryFile)) new Toml().read(registryFile.toFile());
        } catch (RuntimeException error) {
            errors.add("servers.toml is invalid: " + error.getMessage());
        }
        if (settings.allowOverwrite()) warnings.add("server_management.allow_overwrite is enabled.");
        return new Validation(List.copyOf(errors), List.copyOf(warnings));
    }

    public synchronized List<ManagedLobby> lobbies() {
        return List.copyOf(lobbies.values());
    }

    public Path velocityConfigPath() {
        Path configured = Path.of(settings.velocityConfig());
        if (configured.isAbsolute()) return configured.normalize();
        Path data = plugin.getDataDirectory().toAbsolutePath().normalize();
        Path plugins = data.getParent();
        Path root = plugins == null ? Path.of("").toAbsolutePath() : plugins.getParent();
        if (root == null) root = Path.of("").toAbsolutePath();
        return root.resolve(configured).normalize();
    }

    private void loadRegistry() throws IOException {
        if (!Files.exists(registryFile)) {
            writeRegistry();
            return;
        }
        Toml toml = new Toml().read(registryFile.toFile());
        List<Map<String, Object>> values = toml.getList("lobbies");
        if (values == null) return;
        for (Map<String, Object> value : values) {
            String name = string(value.get("name"));
            String host = string(value.get("host"));
            int port = number(value.get("port"), 25565);
            String group = string(value.get("group"));
            int maxPlayers = number(value.get("max_players"), Config.LobbyEntry.UNCAPPED);
            int weight = number(value.get("weight"), Config.LobbyEntry.DEFAULT_WEIGHT);
            ParsedAddress parsed = parse(name, formatAddress(host, port));
            if (parsed.valid()) lobbies.put(name.toLowerCase(Locale.ROOT), new ManagedLobby(name, host, port, group.isBlank() ? "default" : group, maxPlayers, weight));
        }
    }

    private void writeRegistry() throws IOException {
        Files.createDirectories(registryFile.getParent());
        StringBuilder output = new StringBuilder("config_version = 1\n");
        output.append("lobbies = [\n");
        for (ManagedLobby lobby : lobbies.values()) {
            output.append("  { name = ").append(quote(lobby.name())).append(", host = ").append(quote(lobby.host())).append(", port = ").append(lobby.port()).append(", group = ").append(quote(lobby.group())).append(", max_players = ").append(lobby.maxPlayers()).append(", weight = ").append(lobby.weight()).append(" },\n");
        }
        output.append("]\n");
        atomicWrite(registryFile, output.toString());
    }

    private Result writeVelocity(String name, String host, int port, boolean removing) throws IOException {
        Path file = velocityConfigPath();
        return editVelocityConfig(file, name, host, port, settings.allowOverwrite(), removing);
    }

    private Result inspectVelocity(String name, String host, int port) throws IOException {
        Path file = velocityConfigPath();
        if (!Files.exists(file)) return Result.failure("Velocity config was not found at " + file + ".");
        List<String> lines = Files.readAllLines(file);
        int section = findServersSection(lines);
        if (section < 0) return Result.success("");
        int existing = findServerLine(lines, section + 1, findSectionEnd(lines, section), name);
        if (existing < 0) return Result.success("");
        String current = lines.get(existing);
        String currentAddress = current.substring(current.indexOf('=') + 1).trim();
        if (currentAddress.startsWith("\"") && currentAddress.endsWith("\"")) currentAddress = currentAddress.substring(1, currentAddress.length() - 1);
        if (!settings.allowOverwrite() && !currentAddress.equalsIgnoreCase(formatAddress(host, port))) return Result.failure("Server '" + name + "' already exists with a different address and overwrite protection is enabled.");
        return Result.success("");
    }

    static Result editVelocityConfig(Path file, String name, String host, int port, boolean allowOverwrite, boolean removing) throws IOException {
        if (!Files.exists(file)) return Result.failure("Velocity config was not found at " + file + ". Set server_management.velocity_config correctly.");
        List<String> lines = new ArrayList<>(Files.readAllLines(file));
        int section = findServersSection(lines);
        if (section < 0) {
            lines.add("");
            lines.add("[servers]");
            section = lines.size() - 1;
        }
        int end = findSectionEnd(lines, section);
        int existing = findServerLine(lines, section + 1, end, name);
        if (removing) {
            if (existing >= 0) lines.remove(existing);
        } else {
            String entry = quote(name) + " = " + quote(formatAddress(host, port));
            if (existing >= 0) {
                String current = lines.get(existing);
                String currentAddress = current.substring(current.indexOf('=') + 1).trim();
                if (currentAddress.startsWith("\"") && currentAddress.endsWith("\"")) currentAddress = currentAddress.substring(1, currentAddress.length() - 1);
                if (!allowOverwrite && !currentAddress.equalsIgnoreCase(formatAddress(host, port))) return Result.failure("Server '" + name + "' already exists in velocity.toml. Enable server_management.allow_overwrite to replace it.");
                lines.set(existing, entry);
            } else {
                lines.add(end, entry);
            }
        }
        atomicWrite(file, String.join(System.lineSeparator(), lines) + System.lineSeparator());
        return Result.success("");
    }

    private Result removeVelocity(String name) throws IOException {
        return writeVelocity(name, "", 25565, true);
    }

    private static int findServersSection(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).trim().equalsIgnoreCase("[servers]")) return i;
        return -1;
    }

    private static int findSectionEnd(List<String> lines, int section) {
        for (int i = section + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("[") && line.endsWith("]")) return i;
        }
        return lines.size();
    }

    private static int findServerLine(List<String> lines, int start, int end, String name) {
        for (int i = start; i < end; i++) {
            Matcher matcher = SERVER_LINE.matcher(lines.get(i));
            if (!matcher.matches()) continue;
            String found = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            if (found.equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private void applyRuntime(ManagedLobby lobby) {
        registerRuntime(lobby.name(), lobby.host(), lobby.port());
        plugin.registerDynamicLobby(lobby.name(), lobby.group(), lobby.maxPlayers(), lobby.weight());
    }

    private void registerRuntime(String name, String host, int port) {
        Optional<RegisteredServer> existing = plugin.server().getServer(name);
        InetSocketAddress address = InetSocketAddress.createUnresolved(host, port);
        if (existing.isPresent()
                && existing.get().getServerInfo().getAddress().getPort() == port
                && existing.get().getServerInfo().getAddress().getHostString().equalsIgnoreCase(host)) return;
        existing.ifPresent(server -> plugin.server().unregisterServer(server.getServerInfo()));
        plugin.server().registerServer(new ServerInfo(name, address));
    }

    private ParsedAddress parse(String name, String address) {
        if (name == null || !SERVER_NAME.matcher(name).matches()) return ParsedAddress.invalid("Server names may only contain letters, numbers, dots, underscores, and hyphens.");
        if (address == null || address.isBlank()) return ParsedAddress.invalid("Address must use host:port format.");
        String value = address.trim();
        String host;
        String rawPort;
        if (value.startsWith("[")) {
            int bracket = value.indexOf(']');
            if (bracket < 0 || bracket + 2 > value.length() || value.charAt(bracket + 1) != ':') return ParsedAddress.invalid("Invalid IPv6 address. Use [host]:port.");
            host = value.substring(1, bracket);
            rawPort = value.substring(bracket + 2);
        } else {
            int colon = value.lastIndexOf(':');
            if (colon <= 0) return ParsedAddress.invalid("Address must use host:port format.");
            host = value.substring(0, colon);
            rawPort = value.substring(colon + 1);
        }
        try {
            int port = Integer.parseInt(rawPort);
            if (host.isBlank() || port < 1 || port > 65535) return ParsedAddress.invalid("Host or port is invalid.");
            return new ParsedAddress(name, host, port, "");
        } catch (NumberFormatException error) {
            return ParsedAddress.invalid("Port must be a number between 1 and 65535.");
        }
    }

    private static String formatAddress(String host, int port) {
        return host.contains(":") ? "[" + host + "]:" + port : host + ":" + port;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static void atomicWrite(Path file, String content) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, content);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private byte[] snapshot(Path file) {
        try {
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (IOException error) {
            throw new IllegalStateException("Could not snapshot " + file + ": " + error.getMessage(), error);
        }
    }

    private void restore(Path file, byte[] content) {
        try {
            if (content == null) Files.deleteIfExists(file);
            else atomicWrite(file, new String(content, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException error) {
            plugin.logger().error("[VelocityNavigator] Transaction rollback failed for {}: {}", file, error.getMessage());
        }
    }

    private void backup(Path file) throws IOException {
        if (!Files.exists(file)) return;
        Path directory = plugin.getDataDirectory().resolve("backups");
        Files.createDirectories(directory);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path target = directory.resolve(file.getFileName() + "." + timestamp + ".bak");
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private List<String> forcedHostReferences(String serverName) {
        List<String> references = new ArrayList<>();
        Path file = velocityConfigPath();
        if (!Files.exists(file)) return references;
        try {
            Toml forced = new Toml().read(file.toFile()).getTable("forced-hosts");
            if (forced == null) return references;
            for (Map.Entry<String, Object> entry : forced.toMap().entrySet()) {
                if (entry.getValue() instanceof List<?> values && values.stream().anyMatch(value -> serverName.equalsIgnoreCase(String.valueOf(value)))) references.add(entry.getKey());
            }
        } catch (RuntimeException ignored) {
        }
        return references;
    }

    public record ManagedLobby(String name, String host, int port, String group, int maxPlayers, int weight) {
        public ManagedLobby {
            group = group == null || group.isBlank() ? "default" : group.toLowerCase(Locale.ROOT);
            if (maxPlayers < 0) maxPlayers = Config.LobbyEntry.UNCAPPED;
            if (weight < 1) weight = Config.LobbyEntry.DEFAULT_WEIGHT;
        }
    }

    public record Result(boolean success, String message) {
        public static Result success(String message) { return new Result(true, message); }
        public static Result failure(String message) { return new Result(false, message); }
    }

    public record Validation(List<String> errors, List<String> warnings) {
        public boolean valid() { return errors.isEmpty(); }
    }

    private record ParsedAddress(String name, String host, int port, String error) {
        private static ParsedAddress invalid(String error) { return new ParsedAddress("", "", 0, error); }
        private boolean valid() { return error.isBlank(); }
    }
}
