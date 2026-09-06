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
package com.demonz.velocitynavigator.commands;
import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.config.Config;
import com.demonz.velocitynavigator.config.RuntimeConfigValidator;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.demonz.velocitynavigator.menu.MenuConfigValidator;
import com.demonz.velocitynavigator.observability.GrafanaDashboardCreator;
import com.demonz.velocitynavigator.redis.RedisSyncService;
import com.demonz.velocitynavigator.util.ManagedServerService;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class NavigatorAdminCommand implements SimpleCommand {

    private static final List<String> ROOT_SUBCOMMANDS = List.of("reload", "status", "health", "bridge", "redis", "version", "updatecheck", "debug", "drain", "undrain", "maintenance", "server", "servers", "config", "menu", "affinity", "setup", "motd", "help");
    private static final List<String> DEBUG_TYPES = List.of("player", "server");
    private static final List<String> DRAIN_SUBCOMMANDS = List.of("status");

    private final VelocityNavigator plugin;

    public NavigatorAdminCommand(VelocityNavigator plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] arguments = invocation.arguments();
        if (!publicInformation(arguments) && !invocation.source().hasPermission("velocitynavigator.admin")) {
            invocation.source().sendMessage(Component.text("You do not have permission to use VelocityNavigator admin commands.", NamedTextColor.RED));
            return;
        }
        if (invocation.source() instanceof com.velocitypowered.api.proxy.Player player
                && plugin.isAuthenticationPending(player)) {
            player.sendMessage(MessageFormatter.render(
                    "<yellow>Please authenticate before using administration commands.</yellow>", player));
            return;
        }

        if (arguments.length == 0) {
            invocation.source().sendMessage(plugin.buildHelpComponent());
            return;
        }

        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(invocation.source());
            case "status" -> invocation.source().sendMessage(plugin.buildStatusComponent());
            case "health" -> invocation.source().sendMessage(plugin.buildHealthComponent());
            case "bridge" -> bridge(invocation.source(), arguments);
            case "redis" -> redis(invocation.source(), arguments);
            case "version" -> invocation.source().sendMessage(plugin.buildVersionComponent());
            case "updatecheck" -> updateCheck(invocation.source());
            case "debug" -> debug(invocation.source(), arguments);
            case "drain" -> drain(invocation.source(), arguments);
            case "undrain" -> undrain(invocation.source(), arguments);
            case "maintenance" -> maintenance(invocation.source(), arguments);
            case "server" -> server(invocation.source(), arguments);
            case "servers" -> ServersSubCommand.execute(invocation.source(), arguments, plugin);
            case "config" -> config(invocation.source(), arguments);
            case "menu" -> menu(invocation.source(), arguments);
            case "affinity" -> affinity(invocation.source(), arguments);
            case "setup" -> setup(invocation.source(), arguments);
            case "motd" -> motd(invocation.source(), arguments);
            case "help" -> invocation.source().sendMessage(plugin.buildHelpComponent());
            default -> invocation.source().sendMessage(Component.text("Unknown subcommand. Use /velocitynavigator help.", NamedTextColor.AQUA));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (!invocation.source().hasPermission("velocitynavigator.admin")) {
            return args.length <= 1 ? matching(List.of("help", "version"), args.length == 0 ? "" : args[0]) : List.of();
        }

        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return ROOT_SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if ("debug".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return DEBUG_TYPES.stream()
                        .filter(s -> s.startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (args.length == 3) {
                String type = args[1].toLowerCase(Locale.ROOT);
                String partial = args[2].toLowerCase(Locale.ROOT);
                if ("player".equals(type)) {
                    return plugin.server().getAllPlayers().stream()
                            .map(Player::getUsername)
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                            .collect(Collectors.toList());
                }
                if ("server".equals(type)) {
                    return plugin.server().getAllServers().stream()
                            .map(rs -> rs.getServerInfo().getName())
                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                            .collect(Collectors.toList());
                }
            }
        }

        if ("drain".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                List<String> options = new ArrayList<>(DRAIN_SUBCOMMANDS);
                options.addAll(plugin.server().getAllServers().stream()
                        .map(rs -> rs.getServerInfo().getName())
                        .toList());
                return options.stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        if ("undrain".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return plugin.drainService().drainState().keySet().stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        if ("maintenance".equalsIgnoreCase(args[0])) {
            List<String> servers = plugin.server().getAllServers().stream()
                    .map(rs -> rs.getServerInfo().getName()).toList();
            if (args.length == 2) {
                List<String> options = new ArrayList<>(List.of("status", "global", "on", "off"));
                options.addAll(servers);
                return matching(options, args[1]);
            }
            if (args.length == 3 && "server".equalsIgnoreCase(args[1])) return matching(servers, args[2]);
            if (args.length == 3 && ("global".equalsIgnoreCase(args[1])
                    || servers.stream().anyMatch(name -> name.equalsIgnoreCase(args[1])))) {
                return matching(List.of("on", "off"), args[2]);
            }
            if (args.length == 4 && "server".equalsIgnoreCase(args[1])) return matching(List.of("on", "off"), args[3]);
        }

        if ("motd".equalsIgnoreCase(args[0])) {
            if (args.length == 2) return matching(List.of("reload", "list", "add", "remove", "setmode"), args[1]);
            if (args.length == 3 && "setmode".equalsIgnoreCase(args[1])) return matching(List.of("ROTATING", "RANDOM", "SEQUENTIAL"), args[2]);
            if (args.length == 3 && "remove".equalsIgnoreCase(args[1]) && plugin.motdManager() != null) {
                return matching(java.util.stream.IntStream.range(0, plugin.motdManager().config().motds().size())
                        .mapToObj(String::valueOf).toList(), args[2]);
            }
        }

        if ("setup".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return "grafana".startsWith(partial) ? List.of("grafana") : List.of();
            }
        }

        if ("bridge".equalsIgnoreCase(args[0]) && args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return "status".startsWith(partial) ? List.of("status") : List.of();
        }
        if ("redis".equalsIgnoreCase(args[0]) && args.length == 2) return List.of("status", "test").stream().filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();

        if ("server".equalsIgnoreCase(args[0])) {
            if (args.length == 2) return List.of("add", "dry-run", "remove", "list").stream().filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            if (args.length == 3 && ("add".equalsIgnoreCase(args[1]) || "dry-run".equalsIgnoreCase(args[1]))) return List.of("game", "lobby").stream().filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
            if (args.length == 3 && "remove".equalsIgnoreCase(args[1])) return plugin.server().getAllServers().stream().map(server -> server.getServerInfo().getName()).filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if ("config".equalsIgnoreCase(args[0]) && args.length == 2) return "validate".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("validate") : List.of();
        if ("menu".equalsIgnoreCase(args[0]) && args.length == 2) return "validate".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("validate") : List.of();
        if ("affinity".equalsIgnoreCase(args[0]) && args.length == 2) return "clean".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("clean") : List.of();

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return publicInformation(invocation.arguments()) || invocation.source().hasPermission("velocitynavigator.admin");
    }

    private static boolean publicInformation(String[] arguments) {
        return arguments.length == 0 || "help".equalsIgnoreCase(arguments[0]) || "version".equalsIgnoreCase(arguments[0]);
    }

    private static List<String> matching(List<String> choices, String partial) {
        return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))).distinct().toList();
    }

    private void reload(CommandSource source) {
        try {
            plugin.reloadConfiguration();
            source.sendMessage(MessageFormatter.render(plugin.config().messages().reloadSuccess()));
        } catch (IOException exception) {
            source.sendMessage(MessageFormatter.render(plugin.config().messages().reloadFailed()));
            plugin.logger().error("VelocityNavigator reload failed.", exception);
        }
    }

    private void updateCheck(CommandSource source) {
        source.sendMessage(MessageFormatter.render("<gray>Checking Modrinth for updates...</gray>"));
        plugin.updateChecker().checkAsync(plugin.config().updateChecker())
                .thenRun(() -> source.sendMessage(plugin.buildVersionComponent()))
                .exceptionally(throwable -> {
                    source.sendMessage(Component.text("Update check failed: " + throwable.getMessage(), NamedTextColor.RED));
                    return null;
                });
    }

    private void bridge(CommandSource source, String[] arguments) {
        if (arguments.length == 1 || "status".equalsIgnoreCase(arguments[1])) {
            source.sendMessage(plugin.buildBridgeStatusComponent());
            return;
        }
        source.sendMessage(MessageFormatter.render("<aqua>Usage: /vn bridge status</aqua>"));
    }

    private void redis(CommandSource source, String[] arguments) {
        if (arguments.length < 2 || "status".equalsIgnoreCase(arguments[1])) {
            RedisSyncService.Status status = plugin.redisStatus();
            source.sendMessage(Component.text("Redis: " + (status.enabled() ? (status.connected() ? "connected" : "disconnected") : "disabled"), status.connected() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            source.sendMessage(Component.text("Published=" + status.publishedMessages() + ", received=" + status.receivedMessages() + ", reconnects=" + status.reconnects() + ", rejected registrations=" + status.rejectedRegistrations(), NamedTextColor.GRAY));
            if (!status.lastError().isBlank()) source.sendMessage(Component.text("Last error: " + status.lastError(), NamedTextColor.RED));
            return;
        }
        if ("test".equalsIgnoreCase(arguments[1])) {
            source.sendMessage(Component.text("Testing Redis connection...", NamedTextColor.GRAY));
            plugin.redisSyncService().testConnection().thenAccept(result -> source.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED)));
            return;
        }
        source.sendMessage(Component.text("Usage: /vn redis <status|test>", NamedTextColor.AQUA));
    }

    private void server(CommandSource source, String[] arguments) {
        ManagedServerService service = plugin.managedServerService();
        if (service == null || !plugin.advancedConfig().serverManagement().enabled()) {
            source.sendMessage(Component.text("Server management is disabled in navigator.toml.", NamedTextColor.RED));
            return;
        }
        if (arguments.length >= 2 && "list".equalsIgnoreCase(arguments[1])) {
            List<ManagedServerService.ManagedLobby> lobbies = service.lobbies();
            source.sendMessage(Component.text("Managed lobby servers: " + (lobbies.isEmpty() ? "none" : lobbies.stream().map(value -> value.name() + "@" + value.group()).collect(Collectors.joining(", "))), NamedTextColor.AQUA));
            source.sendMessage(Component.text("Velocity config: " + service.velocityConfigPath(), NamedTextColor.GRAY));
            return;
        }
        if (arguments.length >= 3 && "remove".equalsIgnoreCase(arguments[1])) {
            sendServerResult(source, service.remove(arguments[2]));
            return;
        }
        if (arguments.length < 5 || !"add".equalsIgnoreCase(arguments[1])) {
            if (arguments.length < 5 || !"dry-run".equalsIgnoreCase(arguments[1])) {
                serverUsage(source);
                return;
            }
        }
        boolean dryRun = "dry-run".equalsIgnoreCase(arguments[1]);
        String type = arguments[2].toLowerCase(Locale.ROOT);
        String name = arguments[3];
        String address = arguments[4];
        if ("game".equals(type)) {
            sendServerResult(source, dryRun ? service.dryRun(type, name, address, "default", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT) : service.addGame(name, address));
            return;
        }
        if (!"lobby".equals(type)) {
            serverUsage(source);
            return;
        }
        String group = arguments.length >= 6 ? arguments[5] : "default";
        try {
            int maxPlayers = arguments.length >= 7 ? Integer.parseInt(arguments[6]) : Config.LobbyEntry.UNCAPPED;
            int weight = arguments.length >= 8 ? Integer.parseInt(arguments[7]) : Config.LobbyEntry.DEFAULT_WEIGHT;
            sendServerResult(source, dryRun ? service.dryRun(type, name, address, group, maxPlayers, weight) : service.addLobby(name, address, group, maxPlayers, weight));
        } catch (NumberFormatException error) {
            source.sendMessage(Component.text("max_players and weight must be whole numbers.", NamedTextColor.RED));
        }
    }

    private void sendServerResult(CommandSource source, ManagedServerService.Result result) {
        source.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void serverUsage(CommandSource source) {
        source.sendMessage(Component.text("Usage: /vn server add game <name> <host:port>", NamedTextColor.AQUA));
        source.sendMessage(Component.text("Usage: /vn server add lobby <name> <host:port> [group] [max_players] [weight]", NamedTextColor.AQUA));
        source.sendMessage(Component.text("Usage: /vn server dry-run <game|lobby> <name> <host:port> [group] [max_players] [weight]", NamedTextColor.AQUA));
        source.sendMessage(Component.text("Usage: /vn server remove <name> | /vn server list", NamedTextColor.AQUA));
    }

    private void config(CommandSource source, String[] arguments) {
        if (arguments.length < 2 || !"validate".equalsIgnoreCase(arguments[1])) {
            source.sendMessage(Component.text("Usage: /vn config validate", NamedTextColor.AQUA));
            return;
        }
        RuntimeConfigValidator.Validation runtime = plugin.validateRuntimeConfiguration();
        ManagedServerService.Validation managed = plugin.advancedConfig().serverManagement().enabled()
                ? plugin.managedServerService().validate()
                : new ManagedServerService.Validation(List.of(), List.of());
        List<String> errors = new ArrayList<>(runtime.errors());
        errors.addAll(managed.errors());
        List<String> warnings = new ArrayList<>(runtime.warnings());
        warnings.addAll(managed.warnings());
        if (errors.isEmpty()) source.sendMessage(Component.text("Configuration validation passed with " + warnings.size() + " warning(s).", NamedTextColor.GREEN));
        else source.sendMessage(Component.text("Configuration validation found " + errors.size() + " error(s) and " + warnings.size() + " warning(s).", NamedTextColor.RED));
        errors.forEach(error -> source.sendMessage(Component.text("Error: " + error, NamedTextColor.RED)));
        warnings.forEach(warning -> source.sendMessage(Component.text("Warning: " + warning, NamedTextColor.YELLOW)));
    }

    private void menu(CommandSource source, String[] arguments) {
        if (arguments.length != 2 || !"validate".equalsIgnoreCase(arguments[1])) {
            source.sendMessage(Component.text("Usage: /vn menu validate", NamedTextColor.AQUA));
            return;
        }
        java.util.Set<String> registeredServers = plugin.server().getAllServers().stream()
                .map(server -> server.getServerInfo().getName())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        MenuConfigValidator.Validation validation = MenuConfigValidator.validate(
                plugin.dataDirectory().resolve("gui.toml"), plugin.guiConfig(), registeredServers
        );
        source.sendMessage(Component.text(validation.summary(),
                validation.valid() ? NamedTextColor.GREEN : NamedTextColor.RED));
        validation.errors().forEach(error -> source.sendMessage(Component.text("Error: " + error, NamedTextColor.RED)));
        validation.warnings().forEach(warning -> source.sendMessage(Component.text("Warning: " + warning, NamedTextColor.YELLOW)));
    }

    private void debug(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.sendMessage(Component.text("Usage: /velocitynavigator debug player <name> | /velocitynavigator debug server <name>", NamedTextColor.AQUA));
            return;
        }

        String targetType = arguments[1].toLowerCase(Locale.ROOT);
        String targetName = arguments[2];
        if ("player".equals(targetType)) {
            Player player = plugin.server().getPlayer(targetName).orElse(null);
            if (player == null) {
                source.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
                return;
            }
            plugin.previewRoute(player).thenAccept(decision -> source.sendMessage(plugin.buildPlayerDebugComponent(decision)))
                    .exceptionally(throwable -> {
                        source.sendMessage(Component.text("Debug route preview failed: " + throwable.getMessage(), NamedTextColor.RED));
                        return null;
                    });
            return;
        }

        if ("server".equals(targetType)) {
            plugin.inspectServer(targetName).thenAccept(status -> source.sendMessage(plugin.buildServerDebugComponent(status)))
                    .exceptionally(throwable -> {
                        source.sendMessage(Component.text("Debug server inspection failed: " + throwable.getMessage(), NamedTextColor.RED));
                        return null;
                    });
            return;
        }

        source.sendMessage(Component.text("Usage: /velocitynavigator debug player <name> | /velocitynavigator debug server <name>", NamedTextColor.AQUA));
    }

    private void drain(CommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.sendMessage(Component.text("Usage: /vn drain <server> | /vn undrain <server> | /vn drain status", NamedTextColor.AQUA));
            return;
        }

        String subCmd = arguments[1].toLowerCase(Locale.ROOT);

        boolean isRegisteredServer = plugin.server().getAllServers().stream()
                .anyMatch(rs -> rs.getServerInfo().getName().equalsIgnoreCase(subCmd));

        if ("status".equals(subCmd) && !isRegisteredServer) {
            Map<String, Boolean> state = plugin.drainService().drainState();
            if (state.isEmpty()) {
                source.sendMessage(Component.text("No servers are currently drained.", NamedTextColor.GREEN));
            } else {
                source.sendMessage(MessageFormatter.render("<gradient:#8EF7FF:#D9F7FF><bold>Drained Servers</bold></gradient>"));
                for (Map.Entry<String, Boolean> entry : state.entrySet()) {
                    if (entry.getValue()) {
                        source.sendMessage(Component.text("  - " + entry.getKey(), NamedTextColor.RED));
                    }
                }
            }
            return;
        }

        if ("undrain".equals(subCmd) && !isRegisteredServer) {
            if (arguments.length < 3) {
                source.sendMessage(Component.text("Usage: /vn undrain <server>", NamedTextColor.AQUA));
                return;
            }
            String serverName = arguments[2];
            plugin.drainService().undrain(serverName.toLowerCase(Locale.ROOT));
            source.sendMessage(Component.text("Server '" + serverName + "' is no longer drained.", NamedTextColor.GREEN));
            return;
        }

        String serverName = arguments[1];
        plugin.drainService().drain(serverName.toLowerCase(Locale.ROOT));
        source.sendMessage(Component.text("Server '" + serverName + "' is now drained. No players will be routed to it.", NamedTextColor.YELLOW));
    }

    private void undrain(CommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.sendMessage(Component.text("Usage: /vn undrain <server>", NamedTextColor.AQUA));
            return;
        }
        String serverName = arguments[1];
        plugin.drainService().undrain(serverName.toLowerCase(Locale.ROOT));
        source.sendMessage(Component.text("Server '" + serverName + "' is no longer drained.", NamedTextColor.GREEN));
    }

    private void maintenance(CommandSource source, String[] arguments) {
        if (arguments.length < 2 || "status".equalsIgnoreCase(arguments[1])) {
            String global = plugin.maintenanceService().isGlobalMaintenance()
                    ? "ON (" + plugin.maintenanceService().globalReason() + ")"
                    : "OFF";
            source.sendMessage(Component.text("Global maintenance: " + global, NamedTextColor.YELLOW));
            source.sendMessage(Component.text(
                    "Server maintenance: " + plugin.maintenanceService().maintenanceServers(),
                    NamedTextColor.AQUA));
            return;
        }

        String target = arguments[1].toLowerCase(Locale.ROOT);
        int globalStateIndex = globalMaintenanceStateIndex(arguments);
        if (globalStateIndex >= 0) {
            if (arguments.length <= globalStateIndex
                    || (!"on".equalsIgnoreCase(arguments[globalStateIndex])
                    && !"off".equalsIgnoreCase(arguments[globalStateIndex]))) {
                source.sendMessage(Component.text(
                        "Usage: /vn maintenance global <on|off> [reason...]",
                        NamedTextColor.AQUA));
                return;
            }
            boolean enabled = "on".equalsIgnoreCase(arguments[globalStateIndex]);
            String reason = arguments.length > globalStateIndex + 1
                    ? String.join(" ", Arrays.copyOfRange(arguments, globalStateIndex + 1, arguments.length))
                    : "Network maintenance in progress";
            setGlobalMaintenance(source, enabled, reason);
            return;
        }

        int stateIndex = "server".equals(target) && arguments.length >= 3
                && !"on".equalsIgnoreCase(arguments[2]) && !"off".equalsIgnoreCase(arguments[2]) ? 3 : 2;
        if (arguments.length <= stateIndex
                || (!"on".equalsIgnoreCase(arguments[stateIndex]) && !"off".equalsIgnoreCase(arguments[stateIndex]))) {
            source.sendMessage(Component.text("Usage: /vn maintenance global <on|off> [reason]", NamedTextColor.AQUA));
            source.sendMessage(Component.text("Usage: /vn maintenance <server> <on|off> [reason]", NamedTextColor.AQUA));
            return;
        }
        if (stateIndex == 3) target = arguments[2].toLowerCase(Locale.ROOT);
        String requestedTarget = target;
        boolean registered = plugin.server().getAllServers().stream()
                .anyMatch(rs -> rs.getServerInfo().getName().equalsIgnoreCase(requestedTarget));
        boolean enabled = "on".equalsIgnoreCase(arguments[stateIndex]);
        if (!registered && (enabled || !plugin.maintenanceService().maintenanceServers().contains(target))) {
            source.sendMessage(Component.text("Unknown backend: " + target, NamedTextColor.RED));
            return;
        }
        String reason = arguments.length > stateIndex + 1
                ? String.join(" ", Arrays.copyOfRange(arguments, stateIndex + 1, arguments.length))
                : "Server is under maintenance";
        plugin.maintenanceService().setServerMaintenance(target, enabled, reason);
        source.sendMessage(Component.text(
                "Maintenance for '" + target + "' is now "
                        + (enabled ? "enabled: " + reason : "disabled") + ".",
                enabled ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        if (enabled) {
            plugin.evacuateServerForMaintenance(target, reason);
        }
    }

    static int globalMaintenanceStateIndex(String[] arguments) {
        if (arguments == null || arguments.length < 2) {
            return -1;
        }
        if ("global".equalsIgnoreCase(arguments[1])) {
            return 2;
        }
        return "on".equalsIgnoreCase(arguments[1]) || "off".equalsIgnoreCase(arguments[1]) ? 1 : -1;
    }

    private void setGlobalMaintenance(CommandSource source, boolean enabled, String reason) {
        if (enabled && plugin.motdManager() != null) {
            if (!plugin.motdManager().reload()) {
                source.sendMessage(Component.text(plugin.motdManager().lastError(), NamedTextColor.RED));
            }
        }
        plugin.maintenanceService().setGlobalMaintenance(enabled, reason);
        source.sendMessage(Component.text(
                "Global maintenance is now " + (enabled ? "enabled: " + reason : "disabled") + ".",
                enabled ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        if (enabled) {
            plugin.evacuateGlobalMaintenance(reason);
        }
    }

    private void affinity(CommandSource source, String[] arguments) {
        if (arguments.length == 1 || "clean".equalsIgnoreCase(arguments[1])) {
            if (plugin.affinityService() == null) {
                source.sendMessage(Component.text("Player affinity is disabled in navigator.toml.", NamedTextColor.YELLOW));
                return;
            }
            int before = plugin.affinityService().getAll().size();
            plugin.affinityService().purgeExpired();
            int after = plugin.affinityService().getAll().size();
            int purged = Math.max(0, before - after);
            source.sendMessage(Component.text(
                    "Purged " + purged + " expired affinity entr" + (purged == 1 ? "y" : "ies") + ". " + after + " remain.",
                    purged > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            return;
        }
        source.sendMessage(Component.text("Usage: /vn affinity clean", NamedTextColor.AQUA));
    }

    private void setup(CommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.sendMessage(Component.text("Usage: /velocitynavigator setup grafana", NamedTextColor.AQUA));
            return;
        }

        String target = arguments[1].toLowerCase(Locale.ROOT);
        if ("grafana".equals(target)) {
            java.nio.file.Path targetFile = plugin.dataDirectory().resolve("grafana-dashboard.json");
            try {
                java.nio.file.Files.writeString(targetFile, GrafanaDashboardCreator.getDashboardJson());
                source.sendMessage(MessageFormatter.render("<green>Successfully generated grafana-dashboard.json in the plugin directory!</green>"));
            } catch (IOException e) {
                source.sendMessage(Component.text("Failed to generate Grafana dashboard file: " + e.getMessage(), NamedTextColor.RED));
                plugin.logger().error("Failed to generate Grafana dashboard file", e);
            }
            return;
        }

        source.sendMessage(Component.text("Usage: /velocitynavigator setup grafana", NamedTextColor.AQUA));
    }

    private void motd(CommandSource source, String[] arguments) {
        if (plugin.motdManager() == null) {
            source.sendMessage(Component.text("MOTD Manager is not initialized.", NamedTextColor.RED));
            return;
        }

        if (arguments.length < 2) {
            source.sendMessage(Component.text("Usage: /vn motd reload | list | add <text> | remove <index> | setmode <mode>", NamedTextColor.AQUA));
            return;
        }

        String sub = arguments[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                motdResult(source, plugin.motdManager().reload(), "Reloaded and formatted motd.toml.");
            }
            case "list" -> {
                List<String> list = plugin.motdManager().config().motds();
                source.sendMessage(Component.text("Configured MOTDs (" + list.size() + ")", NamedTextColor.AQUA));
                for (int i = 0; i < list.size(); i++) {
                    source.sendMessage(MessageFormatter.render("<gray>[" + i + "]</gray> " + list.get(i)));
                }
            }
            case "add" -> {
                if (arguments.length < 3) {
                    source.sendMessage(Component.text("Usage: /vn motd add <text...>", NamedTextColor.AQUA));
                    return;
                }
                String text = String.join(" ", Arrays.copyOfRange(arguments, 2, arguments.length));
                motdResult(source, plugin.motdManager().addMotd(text), "Added MOTD to motd.toml.");
            }
            case "remove" -> {
                if (arguments.length < 3) {
                    source.sendMessage(Component.text("Usage: /vn motd remove <index>", NamedTextColor.AQUA));
                    return;
                }
                try {
                    int idx = Integer.parseInt(arguments[2]);
                    motdResult(source, plugin.motdManager().removeMotd(idx), "Removed MOTD at index " + idx + ".");
                } catch (NumberFormatException e) {
                    source.sendMessage(Component.text("Index must be an integer.", NamedTextColor.RED));
                }
            }
            case "setmode" -> {
                if (arguments.length < 3) {
                    source.sendMessage(Component.text("Usage: /vn motd setmode <ROTATING|RANDOM|SEQUENTIAL>", NamedTextColor.AQUA));
                    return;
                }
                String mode = arguments[2].toUpperCase(Locale.ROOT);
                motdResult(source, plugin.motdManager().setMode(mode), "MOTD rotation mode set to " + mode + ".");
            }
            default -> source.sendMessage(Component.text("Usage: /vn motd reload | list | add <text> | remove <index> | setmode <mode>", NamedTextColor.AQUA));
        }
    }

    private void motdResult(CommandSource source, boolean success, String message) {
        source.sendMessage(Component.text(success ? message : plugin.motdManager().lastError(),
                success ? NamedTextColor.AQUA : NamedTextColor.RED));
    }
}
