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
import com.demonz.velocitynavigator.common.MenuBridgeProtocol;
import com.demonz.velocitynavigator.commands.AuthCommand;
import com.demonz.velocitynavigator.commands.NavigatorAdminCommand;
import com.demonz.velocitynavigator.commands.PartyChatCommand;
import com.demonz.velocitynavigator.commands.PartyCommand;
import com.demonz.velocitynavigator.commands.QueueCommand;
import com.demonz.velocitynavigator.config.AdvancedConfig;
import com.demonz.velocitynavigator.config.Config;
import com.demonz.velocitynavigator.config.ConfigLoadResult;
import com.demonz.velocitynavigator.config.ConfigManager;
import com.demonz.velocitynavigator.config.GuiConfig;
import com.demonz.velocitynavigator.config.RuntimeConfigValidator;
import com.demonz.velocitynavigator.health.CircuitBreaker;
import com.demonz.velocitynavigator.health.DrainService;
import com.demonz.velocitynavigator.health.ServerHealthService;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.demonz.velocitynavigator.menu.JavaInventoryMenuService;
import com.demonz.velocitynavigator.observability.DashboardServer;
import com.demonz.velocitynavigator.observability.MetricsService;
import com.demonz.velocitynavigator.observability.PrometheusExporter;
import com.demonz.velocitynavigator.observability.UpdateChecker;
import com.demonz.velocitynavigator.observability.UpdateStatus;
import com.demonz.velocitynavigator.party.PartyService;
import com.demonz.velocitynavigator.queue.QueueService;
import com.demonz.velocitynavigator.redis.RedisSyncService;
import com.demonz.velocitynavigator.routing.ConnectionRateTracker;
import com.demonz.velocitynavigator.routing.ConnectionWorkflow;
import com.demonz.velocitynavigator.routing.ConsistentHashRing;
import com.demonz.velocitynavigator.routing.GeoRoutingService;
import com.demonz.velocitynavigator.routing.LobbyCommand;
import com.demonz.velocitynavigator.routing.LobbyRouter;
import com.demonz.velocitynavigator.routing.PlayerAffinityService;
import com.demonz.velocitynavigator.routing.RouteDecision;
import com.demonz.velocitynavigator.routing.RoutePlanner;
import com.demonz.velocitynavigator.routing.RouteSelectionStrategy;
import com.demonz.velocitynavigator.routing.RoutingStats;
import com.demonz.velocitynavigator.routing.ServerLoadTracker;
import com.demonz.velocitynavigator.util.CooldownService;
import com.demonz.velocitynavigator.util.FirstRunHandler;
import com.demonz.velocitynavigator.util.ManagedServerService;
import com.demonz.velocitynavigator.storage.*;
import com.demonz.velocitynavigator.auth.AuthAttemptService;
import com.demonz.velocitynavigator.auth.AuthService;
import com.demonz.velocitynavigator.auth.BedrockAuthForm;
import com.demonz.velocitynavigator.health.MaintenanceService;
import com.demonz.velocitynavigator.health.MaintenanceEvacuation;
import com.demonz.velocitynavigator.motd.MotdManager;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Plugin(
        id = "velocitynavigator",
        name = "VelocityNavigator",
        version = "4.5.0",
        description = "Lobby routing and load balancing for Velocity proxies.",
        authors = {"DemonZDevelopment"},
        dependencies = {
                @Dependency(id = "floodgate", optional = true),
                @Dependency(id = "geyser", optional = true),
                @Dependency(id = "georestrict", optional = true)
        }
)
public final class VelocityNavigator implements NavigatorAPI {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;
    private final String pluginVersion;
    private final CooldownService cooldownService = new CooldownService();
    private final RouteSelectionStrategy selectionStrategy = new RouteSelectionStrategy();
    private final RoutingStats routingStats = new RoutingStats();
    private final DrainService drainService = new DrainService();
    private final PartyService partyService = new PartyService();
    private final AtomicLong playerJoins = new AtomicLong(0);
    private final AtomicLong playerLeaves = new AtomicLong(0);

    private final Set<String> registeredCommands = new LinkedHashSet<>();
    private final ConcurrentMap<UUID, MenuSession> menuSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BridgeStatus> backendBridges = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, RouteDecision> pendingInitialQueues = new ConcurrentHashMap<>();
    private final Set<UUID> maintenanceEvacuations = ConcurrentHashMap.newKeySet();
    private final ReentrantLock reloadLock = new ReentrantLock();

    private volatile long startedAt;

    private volatile ConfigManager configManager;
    private volatile ServerHealthService healthService;
    private volatile LobbyRouter lobbyRouter;
    private volatile RoutePlanner routePlanner;
    private volatile UpdateChecker updateChecker;
    private volatile StorageProvider storageProvider;
    private volatile PlayerAffinityService affinityService;
    private volatile QueueService queueService;
    private volatile MaintenanceService maintenanceService;
    private volatile RedisSyncService redisSyncService;
    private volatile MetricsService metricsService;
    private volatile CircuitBreaker circuitBreaker;
    private volatile ServerLoadTracker loadTracker;
    private volatile ConsistentHashRing hashRing;
    private volatile ConnectionRateTracker rateTracker;
    private volatile GeoRoutingService geoRoutingService;
    private volatile BedrockHandler bedrockHandler;
    private volatile PrometheusExporter prometheusExporter;
    private volatile DashboardServer dashboardServer;
    private volatile ManagedServerService managedServerService;
    private volatile AuthService authService;
    private volatile MotdManager motdManager;

    private volatile Config config;
    private volatile AdvancedConfig advancedConfig = AdvancedConfig.defaults();
    private volatile Config previousConfig;
    private ScheduledTask cacheWarmTask;
    private ScheduledTask purgeTask;
    private ScheduledTask startupUpdateTask;
    private ScheduledTask affinitySaveTask;

    @Inject
    public VelocityNavigator(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.metricsFactory = metricsFactory;
        Plugin annotation = getClass().getAnnotation(Plugin.class);
        this.pluginVersion = annotation == null ? "unknown" : annotation.version();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        startedAt = System.currentTimeMillis();
        try {
            this.configManager = new ConfigManager(dataDirectory, logger);
            this.healthService = new ServerHealthService(server, logger);
            this.routePlanner = new RoutePlanner(selectionStrategy);
            this.lobbyRouter = new LobbyRouter(healthService, routePlanner);
            this.updateChecker = new UpdateChecker(logger, pluginVersion);
            this.prometheusExporter = new PrometheusExporter(this);
            this.dashboardServer = new DashboardServer(this);
            this.queueService = new QueueService(this);
            this.redisSyncService = new RedisSyncService(this);
            this.managedServerService = new ManagedServerService(this);
            this.maintenanceService = new MaintenanceService();
            this.motdManager = new MotdManager(dataDirectory, pluginVersion, logger);

            ConfigLoadResult loadResult = configManager.load();
            applyLoadedConfiguration(loadResult);
            
            StorageProvider sp = createStorageProvider(config);
            sp.initialize();
            migrateFileStorageIfNeeded(sp);
            this.storageProvider = sp;
            this.authService = new AuthService(
                    storageProvider,
                    logger,
                    config.auth().algorithm(),
                    config.auth().minPasswordLength(),
                    config.auth().sessionTimeoutMinutes()
            );
            
            server.getChannelRegistrar().register(JavaInventoryMenuService.CHANNEL);
            logger.info("VelocityNavigator universal JAR is running in VELOCITY PROXY mode.");

            if (affinityService != null) {
                int restored = affinityService.loadFrom(affinityStorePath(), logger);
                if (restored > 0) {
                    logger.info("[VelocityNavigator] Restored {} player affinity mapping(s) from disk.", restored);
                }
            }

            this.bedrockHandler = new BedrockHandler(server);
            if (this.bedrockHandler.isBedrockSupported(config)) {
                logger.info("[VelocityNavigator] Bedrock/Geyser support: enabled (auto-detected)");
            } else {
                logger.info("[VelocityNavigator] Bedrock/Geyser support: disabled");
            }

            if (config != null && config.startup() != null) {
                FirstRunHandler.checkAndShowWelcome(logger, dataDirectory, pluginVersion, config.startup().welcomeEnabled(), config.startup().wikiUrl());
            }

            if (metricsFactory != null) {
                this.metricsService = new MetricsService(metricsFactory, this::config, logger);
                this.metricsService.configure(this, config);
            }

            scheduleCacheWarming();

            scheduleCachePurge();
            scheduleAffinitySave();

            NavigatorAPIProvider.set(this);

            long startupMillis = System.currentTimeMillis() - startedAt;
            logger.info("VelocityNavigator v{} enabled in {}ms.", pluginVersion, startupMillis);
            logger.info("[VelocityNavigator] We would love to hear your feedback! Join our Discord: https://discord.com/invite/GYsTt96ypf");
        } catch (IOException exception) {
            logger.error("VelocityNavigator could not start because navigator.toml could not be loaded.", exception);
        } catch (Exception exception) {
            logger.error("VelocityNavigator could not start due to an unexpected error.", exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        NavigatorAPIProvider.clear();
        server.getChannelRegistrar().unregister(JavaInventoryMenuService.CHANNEL);
        if (storageProvider != null) {
            storageProvider.shutdown();
        }
        if (cacheWarmTask != null) {
            cacheWarmTask.cancel();
        }
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        if (startupUpdateTask != null) {
            startupUpdateTask.cancel();
        }
        if (affinitySaveTask != null) {
            affinitySaveTask.cancel();
        }
        if (affinityService != null) {
            affinityService.saveTo(affinityStorePath(), logger);
        }
        if (queueService != null) queueService.stop();
        if (redisSyncService != null) redisSyncService.close();
        if (geoRoutingService != null) geoRoutingService.close();
        if (healthService != null) {
            healthService.clearCache();
        }
        if (prometheusExporter != null) {
            prometheusExporter.stop();
        }
        if (dashboardServer != null) {
            dashboardServer.stop();
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        cancelAuthTimeout(event.getPlayer());
        AuthAttemptService.reset(event.getPlayer().getUniqueId());
        playerLeaves.incrementAndGet();
        menuSessions.remove(event.getPlayer().getUniqueId());
        pendingInitialQueues.remove(event.getPlayer().getUniqueId());
        if (queueService != null) queueService.remove(event.getPlayer().getUniqueId());
        partyService.disconnect(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (isServerInMaintenance(event.getServer().getServerInfo().getName())) {
            if (isGlobalMaintenance()) {
                disconnectForServerMaintenance(event.getPlayer(), maintenanceService.globalReason());
            } else {
                evacuateServerForMaintenance(event.getServer().getServerInfo().getName(),
                        maintenanceService.serverReason(event.getServer().getServerInfo().getName()));
            }
            return;
        }
        partyService.reconnect(event.getPlayer().getUniqueId());
        server.getScheduler().buildTask(this, () -> syncPartyPlaceholder(event.getPlayer()))
                .delay(1, TimeUnit.SECONDS)
                .schedule();
        if (affinityService != null && event.getServer() != null) {
            String connectedServer = event.getServer().getServerInfo().getName();
            if (configuredLobbyServerNames(config).contains(connectedServer)) {
                affinityService.setAffinity(event.getPlayer().getUniqueId(), connectedServer);
            }
        }
        if (isAuthenticationPending(event.getPlayer())
                && event.getServer().getServerInfo().getName().equalsIgnoreCase(config.auth().holdingServer())) {
            server.getScheduler().buildTask(this, () -> {
                sendAuthStatus(event.getPlayer(), false);
                showAuthenticationPrompt(event.getPlayer());
            })
                    .delay(1, TimeUnit.SECONDS)
                    .schedule();
            return;
        }
        RouteDecision pendingQueue = pendingInitialQueues.remove(event.getPlayer().getUniqueId());
        if (pendingQueue != null && queueService != null && !queueService.enqueue(event.getPlayer(), pendingQueue)) {
            event.getPlayer().sendMessage(MessageFormatter.render(config.language().text("queue.full"), event.getPlayer()));
        }
        if (!advancedConfig.party().enabled() || !advancedConfig.party().followLeader() || event.getPreviousServer().isEmpty()) return;
        Player leader = event.getPlayer();
        if (!partyService.isLeader(leader.getUniqueId())) return;
        RegisteredServer target = event.getServer();
        long delayMs = advancedConfig.party().joinDelayMs();
        for (UUID memberId : partyService.followers(leader.getUniqueId())) {
            server.getPlayer(memberId).filter(Player::isActive).filter(member -> member.getCurrentServer().map(current -> !current.getServerInfo().getName().equalsIgnoreCase(target.getServerInfo().getName())).orElse(true)).ifPresent(member -> server.getScheduler().buildTask(this, () -> member.createConnectionRequest(target).fireAndForget()).delay(delayMs, TimeUnit.MILLISECONDS).schedule());
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!JavaInventoryMenuService.CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection source)
                || !(event.getTarget() instanceof Player player)
                || config == null
                || !source.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        boolean currentBackend = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName()
                        .equalsIgnoreCase(source.getServerInfo().getName()))
                .orElse(false);
        if (!currentBackend) {
            return;
        }
        try {
            MenuBridgeProtocol.PacketType type = MenuBridgeProtocol.packetType(event.getData());
            String backendName = source.getServerInfo().getName().toLowerCase(Locale.ROOT);
            if (type == MenuBridgeProtocol.PacketType.HELLO) {
                MenuBridgeProtocol.Hello hello = MenuBridgeProtocol.decodeHello(event.getData());
                backendBridges.put(backendName, new BridgeStatus(hello.version(), Instant.now()));
                if (!pluginVersion.equals(hello.version())) {
                    logger.warn("[VelocityNavigator] Backend server '{}' is running v{} but the proxy is v{}. Update the backend JAR.",
                            source.getServerInfo().getName(), hello.version(), pluginVersion);
                }
                syncPartyPlaceholder(player);
                return;
            }
            if (type == MenuBridgeProtocol.PacketType.AUTH_REQUEST) {
                MenuBridgeProtocol.AuthRequest authRequest = MenuBridgeProtocol.decodeAuthRequest(event.getData());
                handleBackendAuthRequest(player, authRequest);
                return;
            }
            if (type != MenuBridgeProtocol.PacketType.SELECT) {
                return;
            }
            backendBridges.compute(backendName, (name, existing) -> new BridgeStatus(
                    existing == null ? "unknown" : existing.version(), Instant.now()));
            MenuBridgeProtocol.Selection selection = MenuBridgeProtocol.decodeSelection(event.getData());
            if (selection.target().startsWith("@")) {
                handleInventoryAction(player, selection);
                return;
            }
            if (MenuBridgeProtocol.BACKEND_SELECTION_TOKEN.equals(selection.token())) {
                connectFromBackendSelection(player, selection.target());
                return;
            }
            server.getCommandManager().executeAsync(player,
                    config.commands().primary() + " connect " + selection.target() + " " + selection.token());
        } catch (IOException exception) {
            if (config.debug().verboseLogging()) {
                logger.debug("[VelocityNavigator] Rejected malformed inventory selector response from {}.",
                        player.getUsername());
            }
        }
    }

    private void handleBackendAuthRequest(Player player, MenuBridgeProtocol.AuthRequest request) {
        if (authService == null || config == null || !config.auth().enabled()) {
            return;
        }
        AuthAttemptService.Result result = request.register()
                ? AuthAttemptService.register(authService, player.getUniqueId(), request.password(), request.repeatPassword())
                : AuthAttemptService.login(authService, player.getUniqueId(), request.password());
        if (request.register()) {
            switch (result) {
                case SUCCESS -> {
                    player.sendMessage(MessageFormatter.render(config.language().text("auth.register_success"), player));
                    completeAuthentication(player);
                }
                case PASSWORD_MISMATCH -> sendAuthMessage(player, "auth.password_mismatch");
                case INVALID_PASSWORD -> sendAuthMessage(player, "auth.invalid_registration_password",
                        Map.of("min", Integer.toString(config.auth().minPasswordLength())));
                case ALREADY_REGISTERED -> sendAuthMessage(player, "auth.already_registered");
                case TOO_MANY_ATTEMPTS -> sendTooManyAttempts(player);
                default -> sendAuthMessage(player, "auth.form.try_again");
            }
        } else if (result == AuthAttemptService.Result.SUCCESS) {
            player.sendMessage(MessageFormatter.render(config.language().text("auth.login_success"), player));
            completeAuthentication(player);
        } else if (result == AuthAttemptService.Result.NOT_REGISTERED) {
            sendAuthMessage(player, "auth.not_registered");
        } else if (result == AuthAttemptService.Result.TOO_MANY_ATTEMPTS) {
            sendTooManyAttempts(player);
        } else {
            sendAuthMessage(player, "auth.invalid_credentials");
        }
    }

    private void sendAuthMessage(Player player, String key) {
        sendAuthMessage(player, key, Map.of());
    }

    private void sendAuthMessage(Player player, String key, Map<String, String> values) {
        if (player.isActive()) {
            player.sendMessage(MessageFormatter.render(config.language().text(key), values, player));
        }
    }

    private void sendTooManyAttempts(Player player) {
        if (!player.isActive()) {
            return;
        }
        long seconds = AuthAttemptService.lockoutRemaining(player.getUniqueId()).toSeconds();
        player.sendMessage(MessageFormatter.render(
                config.language().text("auth.too_many_attempts"),
                Map.of("time", Long.toString(Math.max(1, seconds))),
                player
        ));
    }

    private void handleInventoryAction(Player player, MenuBridgeProtocol.Selection selection) {
        if (!validateMenuToken(player, selection.token()) || "@disabled".equals(selection.target())) {
            return;
        }
        int separator = selection.target().indexOf(':');
        if (separator < 0) {
            return;
        }
        String action = selection.target().substring(1, separator);
        if (!"page".equals(action) && !"refresh".equals(action)) {
            return;
        }
        int page;
        try {
            page = Integer.parseInt(selection.target().substring(separator + 1));
        } catch (NumberFormatException exception) {
            return;
        }
        previewRoute(player).thenAccept(decision -> JavaInventoryMenuService.showLobbyMenu(player, this, decision, page))
                .exceptionally(throwable -> {
                    logger.debug("[VelocityNavigator] Inventory selector refresh failed for {}: {}",
                            player.getUsername(), throwable.getMessage());
                    return null;
                });
    }

    @Subscribe
    public EventTask onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (config == null) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        if (maintenanceService != null && maintenanceService.isGlobalMaintenance()) {
            event.getPlayer().disconnect(MessageFormatter.render(
                    "<red>Network maintenance: <reason></red>",
                    Map.of("reason", maintenanceService.globalReason()),
                    event.getPlayer()
            ));
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        if (config.auth().enabled() && authService != null
                && !authService.isAuthenticated(event.getPlayer().getUniqueId())) {
            routeToAuthHolding(event);
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        if (!config.routing().balanceInitialJoin()) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }

        CompletableFuture<String> countryFuture = CompletableFuture.completedFuture(null);
        GeoRoutingService geo = geoRoutingService;
        if (geo != null && config.geoRouting().enabled() && RoutePlanner.mayUseGeoRouting(config)) {
            countryFuture = geo.lookupCountryAsync(event.getPlayer().getRemoteAddress().getAddress());
        }
        CompletableFuture<Void> routingFuture = countryFuture
                .exceptionally(error -> {
                    logger.debug("[VelocityNavigator] Initial-join geo lookup failed for {}: {}",
                            event.getPlayer().getUsername(), error.getMessage());
                    return null;
                })
                .thenAccept(country -> routeInitialJoin(event, country));
        return EventTask.resumeWhenComplete(routingFuture);
    }

    private void routeToAuthHolding(PlayerChooseInitialServerEvent event) {
        String holding = config.auth().holdingServer();
        Optional<RegisteredServer> target = server.getServer(holding);
        if (holding.isBlank() || target.isEmpty()) {
            event.getPlayer().disconnect(MessageFormatter.render(
                    "<red>Authentication is enabled, but its holding server is unavailable.</red>",
                    event.getPlayer()
            ));
            logger.error("[VelocityNavigator] Authentication rejected {} because holding_server '{}' is unavailable.",
                    event.getPlayer().getUsername(), holding);
            return;
        }
        event.setInitialServer(target.get());
    }

    private void showAuthenticationPrompt(Player player) {
        if (!isAuthenticationPending(player)) {
            return;
        }
        scheduleAuthTimeout(player);
        sendAuthStatus(player, false);
        if (FloodgateIntegration.isAvailable() && FloodgateIntegration.isBedrockPlayer(player)
                && BedrockAuthForm.open(player, this)) {
            return;
        }
        String prompt = authService.isRegistered(player.getUniqueId())
                ? config.language().text("auth.login_prompt")
                : config.language().text("auth.register_prompt");
        player.sendMessage(MessageFormatter.render(prompt, player));
    }

    public void completeAuthentication(Player player) {
        if (player == null || !player.isActive() || authService == null
                || !authService.isAuthenticated(player.getUniqueId())) {
            return;
        }
        cancelAuthTimeout(player);
        sendAuthStatus(player, true);
        previewRoute(player).thenAccept(decision -> {
            if (!decision.hasSelection() || !player.isActive()) {
                player.sendMessage(MessageFormatter.render(
                        "<red>Authentication succeeded, but no lobby is currently available.</red>", player));
                return;
            }
            server.getServer(decision.selectedServer())
                    .ifPresent(target -> player.createConnectionRequest(target).fireAndForget());
        }).exceptionally(error -> {
            logger.warn("[VelocityNavigator] Post-authentication routing failed for {}: {}",
                    player.getUsername(), error.getMessage());
            return null;
        });
    }

    public void moveToAuthHolding(Player player) {
        if (player == null || !player.isActive() || config == null) {
            return;
        }
        server.getServer(config.auth().holdingServer()).ifPresentOrElse(
                target -> {
                    boolean alreadyHolding = player.getCurrentServer()
                            .map(current -> current.getServerInfo().getName()
                                    .equalsIgnoreCase(target.getServerInfo().getName()))
                            .orElse(false);
                    if (alreadyHolding) {
                        showAuthenticationPrompt(player);
                    } else {
                        player.createConnectionRequest(target).fireAndForget();
                    }
                },
                () -> player.disconnect(MessageFormatter.render(
                        "<red>The authentication holding server is unavailable.</red>", player))
        );
    }

    private final Map<UUID, com.velocitypowered.api.scheduler.ScheduledTask> authTimeoutTasks = new ConcurrentHashMap<>();

    public void scheduleAuthTimeout(Player player) {
        cancelAuthTimeout(player);
        if (authService == null || !authService.isEnabled() || !isAuthenticationPending(player)) return;
        com.velocitypowered.api.scheduler.ScheduledTask task = server.getScheduler().buildTask(this, () -> {
            if (player.isActive() && isAuthenticationPending(player)) {
                player.disconnect(MessageFormatter.render("<red>Authentication timed out. Please log in promptly.</red>", player));
            }
        }).delay(java.time.Duration.ofSeconds(60)).schedule();
        authTimeoutTasks.put(player.getUniqueId(), task);
    }

    public void cancelAuthTimeout(Player player) {
        if (player == null) return;
        com.velocitypowered.api.scheduler.ScheduledTask task = authTimeoutTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void sendAuthStatus(Player player, boolean authenticated) {
        if (player == null || player.getCurrentServer().isEmpty()) return;
        try {
            byte[] bytes = MenuBridgeProtocol.encodeAuthStatus(authenticated);
            player.getCurrentServer().get().sendPluginMessage(
                    com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier.from(MenuBridgeProtocol.CHANNEL),
                    bytes
            );
        } catch (Exception ignored) {}
    }

    public boolean isAuthenticationPending(Player player) {
        return player != null && config != null && config.auth().enabled()
                && authService != null && !authService.isAuthenticated(player.getUniqueId());
    }

    private void routeInitialJoin(PlayerChooseInitialServerEvent event, String countryCode) {
        if (maintenanceService != null && maintenanceService.isGlobalMaintenance()) {
            event.getPlayer().disconnect(MessageFormatter.render(
                    "<red>Network maintenance: <reason></red>",
                    Map.of("reason", maintenanceService.globalReason()),
                    event.getPlayer()
            ));
            return;
        }
        Map<String, Integer> routeableServers = healthService.getCachedOnlineServers();
        if (routeableServers.isEmpty()) {
            routeableServers = healthService.getRegisteredOnlineServers(configuredLobbyServerNames(config));
        }

        UUID affinityUuid = event.getPlayer().getUniqueId();
        if (bedrockHandler != null && bedrockHandler.isBedrockSupported(config) && config.bedrock().affinityUseJavaUuid()) {
            if (bedrockHandler.isBedrockPlayer(event.getPlayer(), config)) {
                affinityUuid = FloodgateIntegration.getJavaUUID(event.getPlayer());
            }
        }
        RouteDecision decision = routePlanner.plan("", config, routeableServers, affinityUuid, countryCode);
        if (!decision.hasSelection()) {
            String holding = advancedConfig.queue().holdingServer();
            if (queueService.canQueue(decision, config) && !holding.isBlank()) {
                Optional<RegisteredServer> holdingServer = server.getServer(holding);
                if (holdingServer.isPresent()) {
                    pendingInitialQueues.put(event.getPlayer().getUniqueId(), decision);
                    event.setInitialServer(holdingServer.get());
                    return;
                }
            }
            disconnectInitialJoin(event, decision);
            return;
        }

        server.getServer(decision.selectedServer()).ifPresentOrElse(target -> {
            event.setInitialServer(target);
            routingStats.recordRedirect("initial_join", decision.selectedServer());
            if (rateTracker != null) {
                rateTracker.recordConnection(decision.selectedServer());
            }
            if (config.debug().verboseLogging()) {
                logger.info("[VelocityNavigator] Balanced initial join for {} -> {} (country={}, reason={}, candidates={})",
                        event.getPlayer().getUsername(),
                        decision.selectedServer(),
                        countryCode == null || countryCode.isBlank() ? "unknown" : countryCode,
                        decision.reason(),
                        decision.orderedCandidates());
            }
        }, () -> disconnectInitialJoin(event, decision));
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        playerJoins.incrementAndGet();
        if (config == null || updateChecker == null || !config.notifyAdminsOnJoin() || !config.updateChecker().notifyAdmins() || config.updateChecker().silent()) {
            return;
        }
        if (!event.getPlayer().hasPermission("velocitynavigator.admin")) {
            return;
        }

        UpdateStatus status = updateChecker.status();

        if (status.lastCheckedAt() != null) {
            if (status.updateAvailable()) {
                server.getScheduler().buildTask(this, () -> {
                    event.getPlayer().sendMessage(MessageFormatter.render(
                            "<yellow>[VelocityNavigator]</yellow> <white>Update available: " + pluginVersion
                                    + " → " + status.latestKnownVersion() + ". Use /vn updatecheck for details.</white>"
                    ));
                }).delay(2, TimeUnit.SECONDS).schedule();
            }
        } else {
            updateChecker.checkAsync(config.updateChecker())
                    .thenRun(() -> {
                        if (updateChecker.status().updateAvailable()) {
                            server.getScheduler().buildTask(this, () -> {
                                if (event.getPlayer().isActive()) {
                                    event.getPlayer().sendMessage(MessageFormatter.render(
                                            "<yellow>[VelocityNavigator]</yellow> <white>Update available: " + pluginVersion
                                                    + " → " + updateChecker.status().latestKnownVersion() + ". Use /vn updatecheck for details.</white>"
                                    ));
                                }
                            }).delay(1, TimeUnit.SECONDS).schedule();
                        }
                    })
                    .exceptionally(throwable -> {
                        logger.debug("VelocityNavigator admin join update check failed: {}", throwable.getMessage());
                        return null;
                    });
        }
    }

    public ConfigLoadResult reloadConfiguration() throws IOException {
        reloadLock.lock();
        try {
            if (motdManager != null && !motdManager.reload()) {
                throw new IOException(motdManager.lastError());
            }
            ConfigLoadResult loadResult = configManager.load();
            applyLoadedConfiguration(loadResult);
            if (metricsService != null) {
                metricsService.configure(this, config);
            }
            if (circuitBreaker != null) {
                healthService.setCircuitBreaker(circuitBreaker);
            }
            if (loadTracker != null) {
                healthService.setLoadTracker(loadTracker);
            }
            scheduleCacheWarming();
            scheduleCachePurge();
            scheduleAffinitySave();
            return loadResult;
        } finally {
            reloadLock.unlock();
        }
    }

    public java.nio.file.Path dataDirectory() {
        return dataDirectory;
    }

    public ProxyServer server() {
        return server;
    }

    public Logger logger() {
        return logger;
    }

    public String pluginVersion() {
        return pluginVersion;
    }

    public Config config() {
        return config;
    }

    public AdvancedConfig advancedConfig() {
        return advancedConfig;
    }

    public PartyService partyService() {
        return partyService;
    }

    public void syncPartyPlaceholdersForAll() {
        server.getAllPlayers().forEach(this::syncPartyPlaceholder);
    }

    private void syncPartyPlaceholder(Player player) {
        if (player == null || !player.isActive()) return;
        List<UUID> memberIds = partyService.members(player.getUniqueId());
        boolean inParty = !memberIds.isEmpty();
        Optional<UUID> leaderId = partyService.leader(player.getUniqueId());
        String leaderName = leaderId
                .flatMap(server::getPlayer)
                .map(Player::getUsername)
                .orElseGet(() -> leaderId.map(UUID::toString).orElse("None"));
        List<String> memberNames = memberIds.stream()
                .map(id -> server.getPlayer(id).map(Player::getUsername).orElse(id.toString()))
                .toList();
        com.demonz.velocitynavigator.party.Role role = partyService.getRole(player.getUniqueId());
        MenuBridgeProtocol.PartyState state = new MenuBridgeProtocol.PartyState(
                inParty,
                inParty ? partyService.partyName(player.getUniqueId(), leaderName) : "None",
                leaderName,
                memberIds.size(),
                partyService.maxSize(),
                partyService.isLeader(player.getUniqueId()),
                role == null ? "None" : role.displayName(),
                partyService.isPartyOpen(player.getUniqueId()),
                memberNames
        );
        try {
            byte[] payload = MenuBridgeProtocol.encodePartyState(state);
            player.getCurrentServer().ifPresent(connection ->
                    connection.sendPluginMessage(JavaInventoryMenuService.CHANNEL, payload));
        } catch (IOException | IllegalArgumentException error) {
            logger.debug("[VelocityNavigator] Could not synchronize party placeholders for {}: {}",
                    player.getUsername(), error.getMessage());
        }
    }

    public QueueService queueService() {
        return queueService;
    }

    public ManagedServerService managedServerService() {
        return managedServerService;
    }

    public StorageProvider storageProvider() { return storageProvider; }
    public AuthService authService() { return authService; }
    public MaintenanceService maintenanceService() { return maintenanceService; }
    public MotdManager motdManager() { return motdManager; }

    @Subscribe(order = PostOrder.LAST)
    public void onMaintenancePreConnect(ServerPreConnectEvent event) {
        RegisteredServer target = event.getResult().getServer().orElse(null);
        if (target == null || !isServerInMaintenance(target.getServerInfo().getName())) return;
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        String reason = maintenanceService.serverReason(target.getServerInfo().getName());
        Player player = event.getPlayer();
        if (isGlobalMaintenance() || player.getCurrentServer().isEmpty()) {
            disconnectForServerMaintenance(player, reason);
        } else {
            player.sendMessage(MessageFormatter.render("<aqua>This server is in maintenance: <reason></aqua>",
                    Map.of("reason", reason), player));
        }
    }

    public void evacuateGlobalMaintenance(String reason) {
        Component message = MessageFormatter.render(
                "<red><bold>NETWORK MAINTENANCE</bold></red>\n<gray><reason></gray>",
                Map.of("reason", reason == null || reason.isBlank() ? "Maintenance in progress" : reason));
        for (Player player : List.copyOf(server.getAllPlayers())) {
            if (player.isActive()) {
                player.disconnect(message);
            }
        }
    }

    public void evacuateServerForMaintenance(String serverName, String reason) {
        if (serverName == null || serverName.isBlank()) {
            return;
        }
        String normalized = serverName.toLowerCase(Locale.ROOT);
        CompletableFuture<Void> sequence = CompletableFuture.completedFuture(null);
        for (Player player : List.copyOf(server.getAllPlayers())) {
            if (!needsMaintenanceEvacuation(player, normalized) || !maintenanceEvacuations.add(player.getUniqueId())) continue;
            player.sendMessage(MessageFormatter.render(
                    "<aqua>This server entered maintenance. Moving you to an available lobby...</aqua>", player));
            sequence = sequence.handle((unused, error) -> null).thenCompose(unused ->
                    new MaintenanceEvacuation(
                            () -> needsMaintenanceEvacuation(player, normalized),
                            () -> isAuthenticationPending(player) ? CompletableFuture.completedFuture(List.of())
                                    : previewRoute(player).thenApply(decision -> {
                                        LinkedHashSet<String> targets = new LinkedHashSet<>();
                                        if (decision.hasSelection()) targets.add(decision.selectedServer());
                                        if (decision.orderedCandidates() != null) targets.addAll(decision.orderedCandidates());
                                        return targets.stream().filter(name -> !name.equalsIgnoreCase(normalized))
                                                .filter(name -> !isServerInMaintenance(name) && !drainService.isDrained(name))
                                                .toList();
                                    }),
                            name -> server.getServer(name)
                                    .map(target -> player.createConnectionRequest(target).connect().thenApply(result -> {
                                        if (result.isSuccessful()) routingStats.recordRedirect("maintenance", name);
                                        return result.isSuccessful();
                                    })).orElseGet(() -> CompletableFuture.completedFuture(false)),
                            () -> disconnectForServerMaintenance(player, reason),
                            config == null ? 3 : Math.min(config.routing().maxRetries(), 15) + 1,
                            Duration.ofSeconds(10)).run()
            ).whenComplete((unused, error) -> maintenanceEvacuations.remove(player.getUniqueId()));
        }
    }

    private boolean needsMaintenanceEvacuation(Player player, String source) {
        return player.isActive() && isServerInMaintenance(source) && player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(source)).orElse(false);
    }

    private void disconnectForServerMaintenance(Player player, String reason) {
        if (player.isActive()) {
            player.disconnect(MessageFormatter.render(
                    "<red>Server maintenance: <reason></red>",
                    Map.of("reason", reason == null || reason.isBlank() ? "Maintenance in progress" : reason),
                    player));
        }
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        if (motdManager == null) return;
        boolean isMaint = isGlobalMaintenance();
        String reason = maintenanceService != null ? maintenanceService.globalReason() : null;
        int online = server.getPlayerCount();
        int max = event.getPing().getPlayers().map(ServerPing.Players::getMax).orElse(500);
        Component motd = motdManager.resolveMotd(isMaint, reason, online, max);
        if (motd != null) {
            ServerPing ping = event.getPing();
            event.setPing(ping.asBuilder().description(motd).build());
        }
    }

    public RedisSyncService.Status redisStatus() {
        return redisSyncService == null ? new RedisSyncService.Status(false, false, 0, 0, 0, 0, 0, "") : redisSyncService.status();
    }

    public RedisSyncService redisSyncService() {
        return redisSyncService;
    }

    public RuntimeConfigValidator.Validation validateRuntimeConfiguration() {
        Set<String> registered = server.getAllServers().stream().map(value -> value.getServerInfo().getName()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return RuntimeConfigValidator.validate(config, advancedConfig, registered);
    }

    public void registerDynamicLobby(String name, String group, int maxPlayers, int weight) {
        if (routePlanner != null) routePlanner.registerDynamicServer(name, group, maxPlayers, weight);
    }

    public void unregisterDynamicLobby(String name) {
        if (routePlanner != null) routePlanner.unregisterDynamicServer(name);
    }

    public Optional<Config.LobbyEntry> dynamicLobby(String name) {
        return routePlanner == null ? Optional.empty() : routePlanner.dynamicServer(name);
    }

    public Set<String> dynamicLobbyNames() {
        return routePlanner == null ? Set.of() : routePlanner.dynamicServerNames();
    }

    public Optional<Config.LobbyEntry> lobbyEntry(String name) {
        Optional<Config.LobbyEntry> dynamic = dynamicLobby(name);
        if (dynamic.isPresent()) return dynamic;
        if (config == null || name == null) return Optional.empty();
        for (Config.LobbyEntry entry : config.routing().defaultLobbies()) if (entry.server().equalsIgnoreCase(name)) return Optional.of(entry);
        for (Config.GroupConfig group : config.routing().contextual().groups().values()) {
            for (Config.LobbyEntry entry : group.servers()) if (entry.server().equalsIgnoreCase(name)) return Optional.of(entry);
        }
        return Optional.empty();
    }

    public GuiConfig guiConfig() {
        return configManager == null ? GuiConfig.defaults() : configManager.guiConfig();
    }

    public CooldownService cooldowns() {
        return cooldownService;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }

    public BedrockHandler bedrockHandler() {
        return bedrockHandler;
    }

    public RoutingStats routingStats() {
        return routingStats;
    }

    public DrainService drainService() {
        return drainService;
    }

    public PlayerAffinityService affinityService() {
        return affinityService;
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public ServerLoadTracker loadTracker() {
        return loadTracker;
    }

    public ConnectionRateTracker rateTracker() {
        return rateTracker;
    }

    public GeoRoutingService geoRoutingService() {
        return geoRoutingService;
    }

    public ServerHealthService healthService() {
        return healthService;
    }

    public String createMenuToken(Player player, List<String> serverNames) {
        Set<String> allowedServers = new LinkedHashSet<>();
        if (serverNames != null) {
            for (String serverName : serverNames) {
                if (serverName != null && !serverName.isBlank()) {
                    allowedServers.add(serverName.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        int sessionSeconds = config().menuToken().sessionSeconds();
        String token = UUID.randomUUID().toString().replace("-", "");
        menuSessions.put(player.getUniqueId(), new MenuSession(token, Set.copyOf(allowedServers), Instant.now().plusSeconds(sessionSeconds)));
        return token;
    }

    public boolean consumeMenuToken(Player player, String targetServer, String token) {
        if (player == null || targetServer == null || token == null || token.isBlank()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        String normalizedTarget = targetServer.toLowerCase(Locale.ROOT);
        AtomicBoolean consumed = new AtomicBoolean(false);
        menuSessions.compute(playerId, (id, session) -> {
            if (session == null) {
                return null;
            }
            Instant now = Instant.now();
            if (now.isAfter(session.expiresAt())) {
                return null;
            }
            if (!session.token().equals(token) || !session.allowedServers().contains(normalizedTarget)) {
                return session;
            }
            consumed.set(true);
            return null;
        });
        return consumed.get();
    }

    public void connectFromBackendSelection(Player player, String targetServer) {
        if (player == null || targetServer == null || targetServer.isBlank()) {
            return;
        }
        previewRoute(player)
                .thenAccept(decision -> ConnectionWorkflow.connectFromSelection(
                        this, player, config(), decision, targetServer, "chat_menu"))
                .exceptionally(throwable -> {
                    logger.debug("[VelocityNavigator] Backend selection for {} failed: {}",
                            player.getUsername(), throwable.getMessage());
                    return null;
                });
    }

    public boolean validateMenuToken(Player player, String token) {
        if (player == null || token == null || token.isBlank()) {
            return false;
        }
        MenuSession session = menuSessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (Instant.now().isAfter(session.expiresAt())) {
            menuSessions.remove(player.getUniqueId(), session);
            return false;
        }
        return session.token().equals(token);
    }

    public Component buildBridgeStatusComponent() {
        StringBuilder output = new StringBuilder("<gradient:#8EF7FF:#D9F7FF><bold>Backend GUI Bridge Status</bold></gradient>\n");
        if (server.getAllServers().isEmpty()) {
            output.append("<gray>No backend servers are registered.</gray>");
            return MessageFormatter.render(output.toString());
        }
        server.getAllServers().stream()
                .sorted(java.util.Comparator.comparing(value -> value.getServerInfo().getName().toLowerCase(Locale.ROOT)))
                .forEach(registered -> {
                    String name = registered.getServerInfo().getName();
                    BridgeStatus status = backendBridges.get(name.toLowerCase(Locale.ROOT));
                    if (status == null) {
                        output.append("<red>✗</red> <white>").append(name)
                                .append("</white> <gray>not detected; a player must join after bridge startup</gray>\n");
                    } else if (!pluginVersion.equals(status.version())) {
                        output.append("<yellow>⚠</yellow> <white>").append(name)
                                .append("</white> <yellow>v").append(status.version())
                                .append(" (outdated — proxy is v").append(pluginVersion).append(")</yellow>")
                                .append(" <gray>last seen ").append(status.lastSeen()).append("</gray>\n");
                    } else {
                        output.append("<green>✓</green> <white>").append(name)
                                .append("</white> <green>v").append(status.version())
                                .append(" (up to date)</green>")
                                .append(" <gray>last seen ").append(status.lastSeen()).append("</gray>\n");
                    }
                });
        return MessageFormatter.render(output.toString());
    }

    public CompletableFuture<RouteDecision> previewRoute(Player player) {
        return lobbyRouter.preview(player, config);
    }

    public CompletableFuture<ServerHealthStatus> inspectServer(String name) {
        return healthService.inspectServer(name, config.healthChecks());
    }

    @Override
    public Config.Routing getRoutingConfig() {
        return config.routing();
    }

    @Override
    public Config.SelectionMode getSelectionMode() {
        return config.routing().selectionMode();
    }

    @Override
    public Map<String, Long> getRoutingDistribution() {
        return routingStats.getDistribution();
    }

    @Override
    public Map<String, Long> getHealthCheckLatencies() {
        if (healthService == null) {
            return Map.of();
        }
        return healthService.getLatencies();
    }

    @Override
    public Map<String, CircuitBreakerState> getCircuitBreakerStatuses() {
        if (circuitBreaker == null) {
            return Map.of();
        }
        Map<String, CircuitBreakerState> statuses = new LinkedHashMap<>();
        for (String serverName : server.getAllServers().stream()
                .map(rs -> rs.getServerInfo().getName()).toList()) {
            statuses.put(serverName, circuitBreaker.getState(serverName));
        }
        return statuses;
    }

    @Override
    public boolean isGlobalMaintenance() {
        return maintenanceService != null && maintenanceService.isGlobalMaintenance();
    }

    @Override
    public boolean isServerInMaintenance(String serverName) {
        return maintenanceService != null && maintenanceService.isServerInMaintenance(serverName);
    }

    @Override
    public Set<String> getMaintenanceServers() {
        if (maintenanceService == null) return Set.of();
        return maintenanceService.maintenanceServers();
    }

    public long getPlayerJoins() {
        return playerJoins.get();
    }

    public long getPlayerLeaves() {
        return playerLeaves.get();
    }

    public Component buildHelpComponent() {
        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Commands</bold></gradient>
                <aqua>/vn reload</aqua> <gray>Reload all configuration</gray>
                <aqua>/vn status</aqua> <gray>Runtime and connections</gray>
                <aqua>/vn health</aqua> <gray>Network health</gray>
                <aqua>/vn maintenance global <on|off> [reason]</aqua>
                <gray>  Network-wide maintenance</gray>
                <aqua>/vn maintenance <server> <on|off> [reason]</aqua>
                <gray>  Evacuate one backend</gray>
                <aqua>/vn bridge status</aqua> <gray>Backend bridges</gray>
                <aqua>/vn redis status|test</aqua> <gray>Redis connectivity</gray>
                <aqua>/vn server add game <name> <host:port></aqua>
                <aqua>/vn server add lobby <name> <host:port></aqua>
                <gray>  Optional: [group] [max] [weight]</gray>
                <aqua>/vn server remove <name></aqua>
                <aqua>/vn server list</aqua>
                <aqua>/vn config validate</aqua>
                <aqua>/vn menu validate</aqua>
                <aqua>/vn motd reload|list|add|remove|setmode</aqua>
                <aqua>/vn drain|undrain <server></aqua>
                <aqua>/vn drain status</aqua>
                <aqua>/vn version</aqua>
                """);
    }

    public Component buildStatusComponent() {
        UpdateStatus status = updateChecker.status();
        String lastChecked = status.lastCheckedAt() == null ? "never" : status.lastCheckedAt().toString();
        long uptimeMillis = System.currentTimeMillis() - startedAt;
        long uptimeSeconds = Math.max(0L, uptimeMillis / 1000L);
        long uptimeHours = uptimeSeconds / 3600L;
        long uptimeMinutes = (uptimeSeconds % 3600L) / 60L;
        String uptime = uptimeHours + "h " + uptimeMinutes + "m";

        Map<String, Long> distribution = routingStats.getDistribution();
        StringBuilder distBuilder = new StringBuilder();
        if (distribution.isEmpty()) {
            distBuilder.append("No connections recorded yet.");
        } else {
            for (Map.Entry<String, Long> entry : distribution.entrySet()) {
                if (!distBuilder.isEmpty()) distBuilder.append(", ");
                distBuilder.append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }

        String cbStatus = "N/A";
        if (circuitBreaker != null) {
            long open = 0, halfOpen = 0, closed = 0;
            for (String sn : server.getAllServers().stream().map(rs -> rs.getServerInfo().getName()).toList()) {
                switch (circuitBreaker.getState(sn)) {
                    case OPEN -> open++;
                    case HALF_OPEN -> halfOpen++;
                    case CLOSED -> closed++;
                }
            }
            cbStatus = "closed=" + closed + " half_open=" + halfOpen + " open=" + open;
        }

        Map<String, Boolean> drainState = drainService.drainState();
        String drainStatus = drainState.isEmpty() ? "None" : String.join(", ", drainState.keySet());

        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Status</bold></gradient>
                <gray>Plugin version:</gray> <white>%s</white>
                <gray>Config version:</gray> <white>%s</white>
                <gray>Routing mode:</gray> <white>%s</white>
                <gray>Default lobbies:</gray> <white>%s</white>
                <gray>Contextual routing:</gray> <white>%s</white>
                <gray>Health checks:</gray> <white>%s</white>
                <gray>bStats:</gray> <white>%s</white>
                <gray>Update checker:</gray> <white>%s (last check: %s)</white>
                <gray>Circuit breaker:</gray> <white>%s</white>
                <gray>Drained servers:</gray> <white>%s</white>
                <gray>Routing distribution:</gray> <white>%s</white>
                """.formatted(
                pluginVersion,
                config.configVersion(),
                config.routing().selectionMode().configValue(),
                lobbyEntryNames(config.routing().defaultLobbies()),
                config.routing().contextual().enabled(),
                config.healthChecks().enabled(),
                metricsService == null ? "Unavailable" : metricsService.statusLine(),
                config.updateChecker().channel().configValue(),
                lastChecked,
                cbStatus,
                drainStatus,
                distBuilder.toString()
        ));
    }

    public Component buildVersionComponent() {
        UpdateStatus status = updateChecker.status();
        String remote = status.latestKnownVersion();
        String availability = status.updateAvailable() ? "<green>Update available</green>" : "<gray>No newer version found</gray>";
        String errorLine = status.lastError().isBlank() ? "<gray>No update-check errors recorded.</gray>" : "<red>" + status.lastError() + "</red>";
        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Version</bold></gradient>
                <gray>Installed:</gray> <white>%s</white>
                <gray>Latest allowed remote:</gray> <white>%s</white>
                %s
                %s
                """.formatted(pluginVersion, remote, availability, errorLine));
    }

    public Component buildPlayerDebugComponent(RouteDecision decision) {
        String selected = decision.hasSelection() ? decision.selectedServer() : "none";
        String reason = decision.reason() == null || decision.reason().isBlank() ? "none" : decision.reason();
        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Route Debug</bold></gradient>
                <gray>Source server:</gray> <white>%s</white>
                <gray>Requested group:</gray> <white>%s</white>
                <gray>Used group:</gray> <white>%s</white>
                <gray>Configured candidates:</gray> <white>%s</white>
                <gray>Online candidates:</gray> <white>%s</white>
                <gray>Selected server:</gray> <white>%s</white>
                <gray>Fallback to default:</gray> <white>%s</white>
                <gray>Selection mode:</gray> <white>%s</white>
                <gray>Reason:</gray> <white>%s</white>
                """.formatted(
                decision.sourceServer().isBlank() ? "none" : decision.sourceServer(),
                decision.requestedGroup(),
                decision.usedGroup(),
                decision.configuredCandidates(),
                decision.onlineCandidates(),
                selected,
                decision.fallbackToDefault(),
                decision.selectionMode().configValue(),
                reason
        ));
    }

    public Component buildServerDebugComponent(ServerHealthStatus status) {
        String checkedAt = status.checkedAt() == null ? "never" : status.checkedAt().toString();
        long ageSeconds = status.checkedAt() == null ? -1 : Duration.between(status.checkedAt(), Instant.now()).toSeconds();
        String ageText = ageSeconds < 0 ? "n/a" : ageSeconds + "s ago";

        String cbState = "N/A";
        if (circuitBreaker != null) {
            cbState = circuitBreaker.getState(status.serverName()).name();
        }

        String drainState = drainService.isDrained(status.serverName()) ? "DRAINED" : "active";

        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Server Debug</bold></gradient>
                <gray>Server:</gray> <white>%s</white>
                <gray>Registered:</gray> <white>%s</white>
                <gray>Online:</gray> <white>%s</white>
                <gray>Cached:</gray> <white>%s</white>
                <gray>Checked at:</gray> <white>%s</white>
                <gray>Sample age:</gray> <white>%s</white>
                <gray>Players connected:</gray> <white>%s</white>
                <gray>Circuit breaker:</gray> <white>%s</white>
                <gray>Drain status:</gray> <white>%s</white>
                """.formatted(
                status.serverName(),
                status.exists(),
                status.online(),
                status.cached(),
                checkedAt,
                ageText,
                status.playersConnected(),
                cbState,
                drainState
        ));
    }

    public Component buildHealthComponent() {
        int lobbyCount = config.routing().defaultLobbies().size();
        int contextualGroups = config.routing().contextual().enabled()
                ? config.routing().contextual().groups().size()
                : 0;

        String cbSummary;
        if (circuitBreaker == null) {
            cbSummary = "disabled";
        } else {
            long open = 0, halfOpen = 0, closed = 0;
            for (String sn : server.getAllServers().stream().map(rs -> rs.getServerInfo().getName()).toList()) {
                switch (circuitBreaker.getState(sn)) {
                    case OPEN -> open++;
                    case HALF_OPEN -> halfOpen++;
                    case CLOSED -> closed++;
                }
            }
            cbSummary = "closed=" + closed + " half_open=" + halfOpen + " open=" + open;
        }

        int drained = drainService.drainState().size();
        int cacheSize = healthService == null ? 0 : healthService.cacheSize();
        int activePings = healthService == null ? 0 : healthService.activePingCount();
        int trackedLatencies = healthService == null ? 0 : healthService.latencyCount();
        int affinityEntries = affinityService == null ? 0 : affinityService.getAll().size();
        int routingDistributionEntries = routingStats.getDistribution().size();
        int queuedPlayers = queueService == null ? 0 : queueService.size();
        int parties = partyService.partyCount();
        int backendStateEntries = healthService == null ? 0 : healthService.getBackendStates().size();
        int managedLobbies = managedServerService == null ? 0 : managedServerService.lobbies().size();
        RedisSyncService.Status redisRuntime = redisStatus();

        return MessageFormatter.render("""
                <gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Health</bold></gradient>
                <gray>Routing mode:</gray> <white>%s</white>
                <gray>Default lobbies:</gray> <white>%d</white>
                <gray>Contextual groups:</gray> <white>%d</white>
                <gray>Circuit breaker:</gray> <white>%s</white>
                <gray>Drained servers:</gray> <white>%d</white>
                <gray>Health cache size:</gray> <white>%d</white>
                <gray>Active pings:</gray> <white>%d</white>
                <gray>Tracked latencies:</gray> <white>%d</white>
                <gray>Player affinity entries:</gray> <white>%d</white>
                <gray>Routing distribution entries:</gray> <white>%d</white>
                <gray>Active parties:</gray> <white>%d</white>
                <gray>Queued players:</gray> <white>%d</white>
                <gray>Redis multi-proxy sync:</gray> <white>%s</white>
                <gray>Backend state markers:</gray> <white>%d</white>
                <gray>Managed lobbies:</gray> <white>%d</white>
                <gray>Party / queue:</gray> <white>%s / %s</white>
                <gray>Bedrock form / server management:</gray> <white>%s / %s</white>
                """.formatted(
                config.routing().selectionMode().configValue(),
                lobbyCount,
                contextualGroups,
                cbSummary,
                drained,
                cacheSize,
                activePings,
                trackedLatencies,
                affinityEntries,
                routingDistributionEntries,
                parties,
                queuedPlayers,
                advancedConfig.redis().enabled() ? (redisRuntime.connected() ? "connected" : "disconnected, reconnects=" + redisRuntime.reconnects()) : "disabled",
                backendStateEntries,
                managedLobbies,
                advancedConfig.party().enabled() ? "enabled" : "disabled",
                advancedConfig.queue().enabled() ? "enabled" : "disabled",
                guiConfig().bedrock().enabled() ? "enabled" : "disabled",
                advancedConfig.serverManagement().enabled() ? "enabled" : "disabled"
        ));
    }

    private void applyLoadedConfiguration(ConfigLoadResult loadResult) {
        reloadLock.lock();
        try {
            this.previousConfig = this.config;
            this.config = loadResult.config();
            try {
                this.advancedConfig = AdvancedConfig.load(dataDirectory.resolve("navigator.toml"));
            } catch (IOException | RuntimeException error) {
                this.advancedConfig = AdvancedConfig.defaults();
                logger.warn("[VelocityNavigator] Advanced settings could not be read; defaults are active: {}", error.getMessage());
            }
            configManager.logWarnings(loadResult);

            if (previousConfig == null || !lobbyTopologyUnchanged(previousConfig, config)) {
                selectionStrategy.reset();
            }

            healthService.clearCache();

            Config.CircuitBreakerSettings cbSettings = config.circuitBreaker();
            if (cbSettings.enabled()) {
                if (previousConfig == null || !previousConfig.circuitBreaker().equals(cbSettings) || this.circuitBreaker == null) {
                    this.circuitBreaker = new CircuitBreaker(
                            cbSettings.failureThreshold(),
                            cbSettings.cooldownSeconds(),
                            cbSettings.halfOpenMaxTests()
                    );
                }
            } else {
                this.circuitBreaker = null;
            }

            if (this.loadTracker == null) {
                this.loadTracker = new ServerLoadTracker(0.3);
            }

            if (this.hashRing == null) {
                this.hashRing = new ConsistentHashRing();
            }

            if (config.routing().affinity().enabled()) {
                double stickiness = config.routing().affinity().stickiness();
                if (this.affinityService == null) {
                    this.affinityService = new PlayerAffinityService(stickiness);
                } else {
                    PlayerAffinityService oldService = this.affinityService;
                    this.affinityService = new PlayerAffinityService(stickiness);
                    oldService.getAll().forEach(this.affinityService::setAffinity);
                }
            } else {
                this.affinityService = null;
            }

            if (this.rateTracker == null) {
                this.rateTracker = new ConnectionRateTracker(60);
            }
            this.rateTracker.retainServers(configuredLobbyServerNames(config));

            GeoRoutingService previousGeoRoutingService = this.geoRoutingService;
            String configuredGeoDatabase = config.geoRouting().databasePath();
            String resolvedGeoDatabase = configuredGeoDatabase;
            if (configuredGeoDatabase != null && !configuredGeoDatabase.isBlank()) {
                Path databasePath = Path.of(configuredGeoDatabase);
                resolvedGeoDatabase = databasePath.isAbsolute()
                        ? databasePath.normalize().toString()
                        : dataDirectory.resolve(databasePath).normalize().toString();
            }
            this.geoRoutingService = new GeoRoutingService(
                    config.geoRouting().enabled(),
                    resolvedGeoDatabase,
                    config.geoRouting().provider(),
                    config.geoRouting().fallbackEnabled(),
                    config.geoRouting().fallbackMode(),
                    logger
            );
            if (lobbyRouter != null) {
                lobbyRouter.setGeoRoutingService(this.geoRoutingService);
            }
            if (previousGeoRoutingService != null) {
                previousGeoRoutingService.close();
            }
            if (config.geoRouting().enabled()) {
                logger.info("[VelocityNavigator] geo_routing.enabled is true. Geo routing is enabled.");
            }

            if (routePlanner != null) {
                routePlanner.setDrainService(drainService);
                routePlanner.setMaintenanceService(maintenanceService);
                routePlanner.setCircuitBreaker(circuitBreaker);
                routePlanner.setLoadTracker(loadTracker);
                routePlanner.setHashRing(hashRing);
                routePlanner.setAffinityService(affinityService);
                routePlanner.setRateTracker(rateTracker);
                routePlanner.setHealthService(healthService);

            }
            if (healthService != null) {
                healthService.setCircuitBreaker(circuitBreaker);
                healthService.setLoadTracker(loadTracker);
                healthService.setBackendStateSettings(advancedConfig.backendStates());
            }

            partyService.configure(advancedConfig.party());
            if (queueService != null) queueService.configure(advancedConfig.queue());
            if (redisSyncService != null) redisSyncService.configure(advancedConfig.redis());
            if (storageProvider != null) {
                AuthService previousAuthService = this.authService;
                this.authService = new AuthService(
                        storageProvider,
                        logger,
                        config.auth().algorithm(),
                        config.auth().minPasswordLength(),
                        config.auth().sessionTimeoutMinutes()
                );
                if (previousAuthService != null) {
                    this.authService.transferSessionsFrom(previousAuthService);
                }
            }
            if (managedServerService != null) {
                try {
                    managedServerService.configure(advancedConfig.serverManagement());
                } catch (IOException | RuntimeException error) {
                    logger.warn("[VelocityNavigator] Managed server registry could not be loaded: {}", error.getMessage());
                }
            }

            registerCommands();
            if (prometheusExporter != null) {
                prometheusExporter.start(config.metrics().prometheus());
            }
            if (dashboardServer != null) {
                dashboardServer.start(config.dashboard());
            }
            schedulePeriodicUpdateCheck();
        } finally {
            reloadLock.unlock();
        }
    }

    private boolean lobbyTopologyUnchanged(Config previous, Config current) {
        List<String> prevDefaults = lobbyEntryNames(previous.routing().defaultLobbies());
        List<String> currDefaults = lobbyEntryNames(current.routing().defaultLobbies());
        if (!prevDefaults.equals(currDefaults)) {
            return false;
        }

        Set<String> prevGroups = previous.routing().contextual().groups().keySet();
        Set<String> currGroups = current.routing().contextual().groups().keySet();
        if (!prevGroups.equals(currGroups)) {
            return false;
        }

        for (String group : currGroups) {
            Config.GroupConfig prevConfig = previous.routing().contextual().groups().get(group);
            Config.GroupConfig currConfig = current.routing().contextual().groups().get(group);
            if (prevConfig == null || currConfig == null) {
                return false;
            }
            List<String> prevServers = lobbyEntryNames(prevConfig.servers());
            List<String> currServers = lobbyEntryNames(currConfig.servers());
            if (!prevServers.equals(currServers)) {
                return false;
            }
        }

        return true;
    }

    private List<String> lobbyEntryNames(List<Config.LobbyEntry> entries) {
        List<String> names = new ArrayList<>();
        for (Config.LobbyEntry entry : entries) {
            names.add(entry.server());
        }
        return names;
    }

    private Set<String> configuredLobbyServerNames(Config currentConfig) {
        Set<String> names = new LinkedHashSet<>();
        if (currentConfig == null || currentConfig.routing() == null) {
            return names;
        }
        addLobbyNames(names, currentConfig.routing().defaultLobbies());
        if (currentConfig.lobbyFallback() != null
                && "fallback_server".equalsIgnoreCase(currentConfig.lobbyFallback().noServerStrategy())
                && !currentConfig.lobbyFallback().fallbackServer().isBlank()) {
            names.add(currentConfig.lobbyFallback().fallbackServer());
        }
        Config.Contextual contextual = currentConfig.routing().contextual();
        if (contextual != null && contextual.groups() != null) {
            for (Config.GroupConfig groupConfig : contextual.groups().values()) {
                if (groupConfig != null) {
                    addLobbyNames(names, groupConfig.servers());
                }
            }
        }
        return names;
    }

    private void addLobbyNames(Set<String> names, List<Config.LobbyEntry> entries) {
        if (entries == null) {
            return;
        }
        for (Config.LobbyEntry entry : entries) {
            if (entry != null && entry.server() != null && !entry.server().isBlank()) {
                names.add(entry.server());
            }
        }
    }

    private void disconnectInitialJoin(PlayerChooseInitialServerEvent event, RouteDecision decision) {
        String reason = decision == null || decision.reason() == null || decision.reason().isBlank()
                ? "No lobby servers are currently available."
                : decision.reason();
        String mode = decision == null || decision.selectionMode() == null
                ? config.routing().selectionMode().configValue()
                : decision.selectionMode().configValue();
        String message = config.lobbyFallback() == null
                ? config.messages().noLobbyFound()
                : config.lobbyFallback().noServerMessage();
        event.getPlayer().disconnect(MessageFormatter.render(
                message,
                Map.of(
                        "reason", reason,
                        "mode", mode,
                        "player", event.getPlayer().getUsername()
                ),
                event.getPlayer()
        ));
        if (config.debug().verboseLogging()) {
            logger.info("[VelocityNavigator] Initial join for {} denied: {}",
                    event.getPlayer().getUsername(), reason);
        }
    }

    private void registerCommands() {
        reloadLock.lock();
        try {
            CommandManager commandManager = server.getCommandManager();
            unregisterCommands(commandManager);

            List<String> adminNames = adminCommandNames(config.commands().adminAliases());
            registerCommandSet(commandManager, adminNames, new NavigatorAdminCommand(this), "admin");

            List<String> lobbyNames = new ArrayList<>();
            lobbyNames.add(config.commands().primary());
            lobbyNames.addAll(config.commands().aliases());
            registerCommandSet(commandManager, lobbyNames, new LobbyCommand(this), "lobby");

            if (advancedConfig.party().enabled()) {
                String partyCommand = advancedConfig.party().command();
                String partyChatCommand = advancedConfig.party().chatCommand();
                registerCommandSet(commandManager, List.of(partyCommand), new PartyCommand(this), "party");
                registerCommandSet(commandManager, List.of(partyChatCommand), new PartyChatCommand(this), "party chat");
            }
            if (advancedConfig.queue().enabled()) {
                String queueCommand = advancedConfig.queue().command();
                registerCommandSet(commandManager, List.of(queueCommand), new QueueCommand(this), "queue");
            }
            if (config.auth().enabled()) {
                registerCommandSet(commandManager, List.of("register"),
                        new AuthCommand(this, AuthCommand.Action.REGISTER), "authentication");
                registerCommandSet(commandManager, List.of("login"),
                        new AuthCommand(this, AuthCommand.Action.LOGIN), "authentication");
                registerCommandSet(commandManager, List.of("logout"),
                        new AuthCommand(this, AuthCommand.Action.LOGOUT), "authentication");
            }
        } finally {
            reloadLock.unlock();
        }
    }

    static List<String> adminCommandNames(List<String> configuredAliases) {
        LinkedHashSet<String> names = new LinkedHashSet<>(List.of("velocitynavigator", "vn"));
        if (configuredAliases != null) {
            configuredAliases.stream()
                    .filter(Objects::nonNull)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isEmpty())
                    .forEach(names::add);
        }
        return List.copyOf(names);
    }

    private void registerCommandSet(CommandManager commandManager, List<String> names, Command command, String label) {
        List<String> available = names.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().filter(value -> !registeredCommands.contains(value)).toList();
        List<String> skipped = names.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().filter(registeredCommands::contains).toList();
        skipped.forEach(value -> logger.error("[VelocityNavigator] Command /{} for {} was not registered because another VelocityNavigator command already uses it.", value, label));
        if (available.isEmpty()) return;
        CommandMeta.Builder builder = commandManager.metaBuilder(available.get(0));
        if (available.size() > 1) builder.aliases(available.subList(1, available.size()).toArray(String[]::new));
        commandManager.register(builder.build(), command);
        registeredCommands.addAll(available);
        if (config.debug().verboseLogging()) {
            logger.info("[VelocityNavigator] Registered {} command names: {}", label, available);
        }
    }

    private void unregisterCommands(CommandManager commandManager) {
        for (String command : registeredCommands) {
            commandManager.unregister(command);
        }
        registeredCommands.clear();
    }

    private void scheduleCacheWarming() {
        if (cacheWarmTask != null) {
            cacheWarmTask.cancel();
            cacheWarmTask = null;
        }
        if (config.healthChecks().cacheSeconds() <= 0) {
            return;
        }
        int intervalSeconds = Math.max(5, config.healthChecks().cacheSeconds());
        warmHealthCache();
        cacheWarmTask = server.getScheduler()
                .buildTask(this, this::warmHealthCache)
                .delay(intervalSeconds, TimeUnit.SECONDS)
                .repeat(intervalSeconds, TimeUnit.SECONDS)
                .schedule();
    }

    private void warmHealthCache() {
        try {
            lobbyRouter.preview("", config).thenAccept(decision -> {
                if (config.debug().verboseLogging() && decision.hasSelection()) {
                    logger.debug("[VelocityNavigator] Cache warming: selected {}", decision.selectedServer());
                }
            }).exceptionally(throwable -> {
                logger.debug("[VelocityNavigator] Cache warming failed: {}", throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            logger.debug("[VelocityNavigator] Cache warming failed: {}", e.getMessage());
        }
    }

    private void scheduleCachePurge() {
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        purgeTask = server.getScheduler()
                .buildTask(this, () -> {
                    Duration ttl = Duration.ofSeconds(Math.max(300, config.healthChecks().cacheSeconds() * 5));
                    healthService.purgeExpiredCache(ttl);
                    if (affinityService != null) {
                        affinityService.purgeExpired();
                    }
                    if (rateTracker != null) {
                        rateTracker.retainServers(configuredLobbyServerNames(config));
                        rateTracker.purge();
                    }
                    if (partyService != null) {
                        partyService.purgeStaleData();
                    }
                    AuthAttemptService.purgeExpired();
                })
                .delay(60, TimeUnit.SECONDS)
                .repeat(60, TimeUnit.SECONDS)
                .schedule();
    }

    private java.nio.file.Path affinityStorePath() {
        return dataDirectory.resolve("affinity-store.json");
    }

    private void scheduleAffinitySave() {
        if (affinitySaveTask != null) {
            affinitySaveTask.cancel();
        }
        affinitySaveTask = server.getScheduler()
                .buildTask(this, () -> {
                    if (affinityService != null) {
                        affinityService.saveTo(affinityStorePath(), logger);
                    }
                })
                .delay(5, TimeUnit.MINUTES)
                .repeat(5, TimeUnit.MINUTES)
                .schedule();
    }

    private void schedulePeriodicUpdateCheck() {
        if (config == null || config.updateChecker() == null) {
            return;
        }
        if (startupUpdateTask != null) {
            startupUpdateTask.cancel();
        }
        if (!config.updateChecker().enabled()) {
            return;
        }
        int intervalMinutes = Math.max(30, config.updateChecker().checkIntervalMinutes());
        var taskBuilder = server.getScheduler()
                .buildTask(this, () -> {
                    updateChecker.checkAsync(config.updateChecker());
                })
                .repeat(intervalMinutes, TimeUnit.MINUTES);
        this.startupUpdateTask = config.notifyOnStartup()
                ? taskBuilder.delay(5, TimeUnit.SECONDS).schedule()
                : taskBuilder.delay(intervalMinutes, TimeUnit.MINUTES).schedule();
    }

    private void migrateFileStorageIfNeeded(StorageProvider target) {
        if (target instanceof FileStorageProvider) return;
        FileStorageProvider fileSource = new FileStorageProvider(dataDirectory, logger);
        if (!StorageMigrator.hasMigratableFileData(fileSource)) return;
        try {
            new StorageMigrator(logger).migrate(fileSource, target);
        } catch (RuntimeException error) {
            logger.warn("[VelocityNavigator] File-to-SQL storage migration could not be completed: {}", error.getMessage());
        }
    }

    private StorageProvider createStorageProvider(Config config) {
        String provider = config.storage().type();
        switch (provider) {
            case "sqlite":
                return new SqliteStorageProvider(
                        dataDirectory.resolve(config.storage().sqliteFile()),
                        logger
                );
            case "mysql":
            case "mariadb":
            case "postgresql":
                boolean isPg = provider.equals("postgresql");
                String jdbcUrl;
                if (isPg) {
                    jdbcUrl = "jdbc:postgresql://" + config.storage().host() + ":" + config.storage().port() + "/" + config.storage().database();
                } else if (provider.equals("mariadb")) {
                    jdbcUrl = "jdbc:mariadb://" + config.storage().host() + ":" + config.storage().port() + "/" + config.storage().database();
                } else {
                    jdbcUrl = "jdbc:mysql://" + config.storage().host() + ":" + config.storage().port() + "/" + config.storage().database();
                }
                HikariConnectionPool pool = new HikariConnectionPool(
                        jdbcUrl,
                        config.storage().username(),
                        config.storage().password(),
                        config.storage().poolSize(),
                        30000L
                );
                return new SqlStorageProvider(pool, logger, isPg);
            default:
                return new FileStorageProvider(dataDirectory, logger);
        }
    }

    private record MenuSession(String token, Set<String> allowedServers, Instant expiresAt) {
    }

    private record BridgeStatus(String version, Instant lastSeen) {
    }
}
