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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class DashboardServer {

    private final VelocityNavigator plugin;
    private final Logger logger;
    private final AtomicLong recentEventCounter = new AtomicLong(0);
    private HttpServer server;
    private volatile String bearerToken = "";

    public DashboardServer(VelocityNavigator plugin) {
        this.plugin = plugin;
        this.logger = plugin.logger();
    }

    public synchronized void start(Config.DashboardSettings settings) {
        if (server != null) {
            stop();
        }
        if (!settings.enabled()) {
            return;
        }
        bearerToken = settings.bearerToken();

        try {
            server = HttpServer.create(new InetSocketAddress(settings.bindHost(), settings.port()), 0);
            server.createContext("/", new DashboardHandler(settings));
            server.createContext("/api/state.json", new ApiHandler(settings));
            server.setExecutor(null);
            server.start();
            if (isLoopback(settings.bindHost()) && bearerToken.isBlank()) {
                logger.info("[VelocityNavigator] Dashboard started on {}:{} (no auth, loopback only).",
                        settings.bindHost(), settings.port());
            } else if (bearerToken.isBlank()) {
                logger.warn("[VelocityNavigator] Dashboard started on {}:{} without auth. Set a bearer token unless the port is firewalled.",
                        settings.bindHost(), settings.port());
            } else {
                logger.info("[VelocityNavigator] Dashboard started on {}:{} (bearer-token auth).",
                        settings.bindHost(), settings.port());
            }
        } catch (IOException | RuntimeException e) {
            if (server != null) {
                try {
                    server.stop(0);
                } catch (RuntimeException ignored) {
                }
                server = null;
            }
            logger.error("[VelocityNavigator] Failed to start dashboard on port {}: {}",
                    settings.port(), e.getMessage());
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(2);
            server = null;
            logger.info("[VelocityNavigator] Dashboard stopped.");
        }
    }

    private boolean isLoopback(String host) {
        if (host == null) {
            return false;
        }
        return "127.0.0.1".equals(host) || "0:0:0:0:0:0:0:1".equals(host)
                || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }

    private void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer realm=\"velocitynavigator\"");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        byte[] bytes = "Unauthorized. Use the dashboard login or an Authorization bearer header.".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(401, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        return isAuthorized(bearerToken, exchange);
    }

    static boolean isAuthorized(String bearerToken, HttpExchange exchange) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return true;
        }
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.startsWith("Bearer ")
                && bearerToken.equals(header.substring(7).trim());
    }

    private Map<String, Object> buildState() {
        Map<String, Object> state = new LinkedHashMap<>();
        Config config = plugin.config();
        if (config == null) {
            return state;
        }
        state.put("version", pluginVersion());
        state.put("routing_mode", config.routing().selectionMode().configValue());
        state.put("default_lobbies", lobbyNames(config.routing().defaultLobbies()));
        state.put("contextual_enabled", config.routing().contextual().enabled());
        state.put("health_checks_enabled", config.healthChecks().enabled());
        state.put("circuit_breaker_enabled", config.circuitBreaker().enabled());
        state.put("affinity_enabled", config.routing().affinity().enabled());

        List<Map<String, Object>> servers = new java.util.ArrayList<>();
        Set<String> tracked = trackedServers(config, plugin.dynamicLobbyNames());
        Map<String, Integer> cached = plugin.healthService() == null
                ? Map.of()
                : plugin.healthService().getCachedOnlineServers();
        Map<String, Long> latencies = plugin.healthService() == null
                ? Map.of()
                : plugin.healthService().getLatencies();
        Map<String, CircuitBreaker.State> breakerStates = plugin.circuitBreaker() == null
                ? Map.of()
                : plugin.getCircuitBreakerStatuses();
        Map<String, Boolean> drains = plugin.drainService().drainState();
        Map<String, Long> distribution = plugin.routingStats().getDistribution();

        for (String name : tracked) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("online", cached.containsKey(name.toLowerCase(Locale.ROOT)));
            entry.put("players", cached.getOrDefault(name.toLowerCase(Locale.ROOT), 0));
            entry.put("max_players", maxPlayersFor(config, name));
            entry.put("drained", drains.getOrDefault(name.toLowerCase(Locale.ROOT), false));
            CircuitBreaker.State cb = breakerStates.get(name.toLowerCase(Locale.ROOT));
            entry.put("circuit_breaker", cb == null ? "CLOSED" : cb.name());
            Long ping = latencies.get(name.toLowerCase(Locale.ROOT));
            entry.put("latency_ms", ping == null ? -1 : ping);
            entry.put("routed", distribution.getOrDefault(name, 0L));
            servers.add(entry);
        }
        state.put("servers", servers);
        state.put("joins", plugin.getPlayerJoins());
        state.put("leaves", plugin.getPlayerLeaves());
        state.put("drained_count", drains.size());
        state.put("cache_size", plugin.healthService() == null ? 0 : plugin.healthService().cacheSize());
        state.put("active_pings", plugin.healthService() == null ? 0 : plugin.healthService().activePingCount());
        state.put("affinity_entries", plugin.affinityService() == null ? 0 : plugin.affinityService().getAll().size());

        Map<String, Long> dist = new LinkedHashMap<>(distribution);
        state.put("distribution", dist);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_lobbies", tracked.size());
        summary.put("online_lobbies", servers.stream().filter(s -> Boolean.TRUE.equals(s.get("online"))).count());
        summary.put("drained_lobbies", servers.stream().filter(s -> Boolean.TRUE.equals(s.get("drained"))).count());
        state.put("summary", summary);

        return state;
    }

    private String pluginVersion() {
        return plugin.pluginVersion();
    }

    private List<String> lobbyNames(List<Config.LobbyEntry> entries) {
        return entries.stream().map(Config.LobbyEntry::server).toList();
    }

    private int maxPlayersFor(Config config, String serverName) {
        return plugin.lobbyEntry(serverName)
                .map(Config.LobbyEntry::maxPlayers)
                .orElse(Config.LobbyEntry.UNCAPPED);
    }

    static Set<String> trackedServers(Config config, Set<String> dynamicLobbies) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (Config.LobbyEntry entry : config.routing().defaultLobbies()) {
            names.add(entry.server());
        }
        if (config.lobbyFallback() != null
                && "fallback_server".equalsIgnoreCase(config.lobbyFallback().noServerStrategy())
                && !config.lobbyFallback().fallbackServer().isBlank()) {
            names.add(config.lobbyFallback().fallbackServer());
        }
        for (Config.GroupConfig g : config.routing().contextual().groups().values()) {
            for (Config.LobbyEntry entry : g.servers()) {
                names.add(entry.server());
            }
        }
        if (dynamicLobbies != null) {
            names.addAll(dynamicLobbies);
        }
        return names;
    }

    private final class DashboardHandler implements HttpHandler {
        private final Config.DashboardSettings settings;

        DashboardHandler(Config.DashboardSettings settings) {
            this.settings = settings;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            sendText(exchange, 200, renderHtml(settings), "text/html; charset=utf-8");
        }
    }

    private final class ApiHandler implements HttpHandler {
        private final Config.DashboardSettings settings;

        ApiHandler(Config.DashboardSettings settings) {
            this.settings = settings;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (!isAuthorized(exchange)) {
                sendUnauthorized(exchange);
                return;
            }
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String json = gson.toJson(buildState());
            sendText(exchange, 200, json, "application/json; charset=utf-8");
        }
    }

    private String renderHtml(Config.DashboardSettings settings) {
        int refresh = settings.refreshSeconds();
        String html = HTML_TEMPLATE;
        html = html.replace("__REFRESH_SEC__", String.valueOf(refresh));
        html = html.replace("__REFRESH_MS__", String.valueOf(refresh * 1000));
        return html;
    }

    private static final String HTML_TEMPLATE = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>VelocityNavigator Dashboard</title>
<style>
  :root {
    --bg: #0f1419;
    --panel: #1a2029;
    --panel-2: #232b37;
    --border: #2d3748;
    --text: #e2e8f0;
    --muted: #94a3b8;
    --accent: #38bdf8;
    --green: #22c55e;
    --yellow: #eab308;
    --red: #ef4444;
    --orange: #f97316;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: var(--bg);
    color: var(--text);
    line-height: 1.5;
  }
  [hidden] { display: none !important; }
  .login-overlay {
    position: fixed;
    inset: 0;
    z-index: 1000;
    display: grid;
    place-items: center;
    padding: 1rem;
    background: rgba(15, 20, 25, 0.96);
  }
  .login-card {
    width: min(420px, 100%);
    padding: 1.5rem;
    border: 1px solid var(--border);
    border-radius: 0.75rem;
    background: var(--panel);
    box-shadow: 0 24px 60px rgba(0, 0, 0, 0.4);
  }
  .login-card h2 { margin: 0 0 0.5rem; color: var(--accent); }
  .login-card p { margin: 0 0 1rem; color: var(--muted); }
  .login-card input {
    width: 100%;
    padding: 0.7rem 0.8rem;
    border: 1px solid var(--border);
    border-radius: 0.4rem;
    background: var(--panel-2);
    color: var(--text);
  }
  .login-card button {
    width: 100%;
    margin-top: 0.75rem;
    padding: 0.7rem 0.8rem;
    border: 0;
    border-radius: 0.4rem;
    background: var(--accent);
    color: #082f49;
    font-weight: 700;
    cursor: pointer;
  }
  .login-error { margin-top: 0.75rem !important; color: var(--red) !important; }
  header {
    padding: 1rem 1.5rem;
    background: linear-gradient(135deg, #1e3a5f 0%, #0f1419 100%);
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 0.75rem;
  }
  header h1 {
    margin: 0;
    font-size: 1.25rem;
    font-weight: 600;
    background: linear-gradient(90deg, #8EF7FF, #D9F7FF);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .badge {
    display: inline-block;
    padding: 0.15rem 0.5rem;
    border-radius: 9999px;
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    background: var(--yellow);
    color: #1a2029;
  }
  .meta {
    font-size: 0.85rem;
    color: var(--muted);
  }
  .meta span { color: var(--text); font-weight: 500; }
  main {
    padding: 1.5rem;
    max-width: 1400px;
    margin: 0 auto;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 1rem;
    margin-bottom: 1.5rem;
  }
  .kpi {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 0.5rem;
    padding: 1rem;
  }
  .kpi .label {
    font-size: 0.75rem;
    color: var(--muted);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 0.25rem;
  }
  .kpi .value {
    font-size: 1.75rem;
    font-weight: 700;
    line-height: 1;
  }
  .panel {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 0.5rem;
    padding: 1rem 1.25rem;
    margin-bottom: 1.5rem;
  }
  .panel h2 {
    margin: 0 0 0.75rem 0;
    font-size: 1rem;
    font-weight: 600;
    color: var(--accent);
    border-bottom: 1px solid var(--border);
    padding-bottom: 0.5rem;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.875rem;
  }
  th, td {
    text-align: left;
    padding: 0.5rem 0.5rem;
    border-bottom: 1px solid var(--border);
  }
  th {
    color: var(--muted);
    font-weight: 500;
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }
  .pill {
    display: inline-block;
    padding: 0.1rem 0.5rem;
    border-radius: 9999px;
    font-size: 0.75rem;
    font-weight: 600;
  }
  .pill.online { background: rgba(34, 197, 94, 0.2); color: var(--green); }
  .pill.offline { background: rgba(239, 68, 68, 0.2); color: var(--red); }
  .pill.drained { background: rgba(249, 115, 22, 0.2); color: var(--orange); }
  .pill.cb-closed { background: rgba(34, 197, 94, 0.15); color: var(--green); }
  .pill.cb-half { background: rgba(234, 179, 8, 0.2); color: var(--yellow); }
  .pill.cb-open { background: rgba(239, 68, 68, 0.2); color: var(--red); }
  .bar-row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 0.35rem;
    font-size: 0.8rem;
  }
  .bar-row .name { width: 130px; color: var(--text); }
  .bar-row .bar-bg {
    flex: 1;
    background: var(--panel-2);
    border-radius: 0.25rem;
    height: 1rem;
    overflow: hidden;
  }
  .bar-row .bar {
    height: 100%;
    background: linear-gradient(90deg, var(--accent), #8EF7FF);
    transition: width 0.3s;
  }
  .bar-row .count { width: 50px; text-align: right; color: var(--muted); }
  .two-col {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1.5rem;
  }
  @media (max-width: 800px) { .two-col { grid-template-columns: 1fr; } }
  .config-row { display: flex; justify-content: space-between; padding: 0.25rem 0; border-bottom: 1px dashed var(--border); font-size: 0.85rem; }
  .config-row:last-child { border-bottom: none; }
  .config-row .k { color: var(--muted); }
  .config-row .v { color: var(--text); font-family: ui-monospace, monospace; }
  footer {
    padding: 1rem 1.5rem;
    text-align: center;
    color: var(--muted);
    font-size: 0.8rem;
    border-top: 1px solid var(--border);
  }
  .empty { color: var(--muted); font-style: italic; padding: 0.5rem 0; }
  .latency-good { color: var(--green); }
  .latency-ok { color: var(--yellow); }
  .latency-bad { color: var(--red); }
  .latency-unknown { color: var(--muted); }
</style>
</head>
<body>
<div id="login-overlay" class="login-overlay" hidden>
  <form id="login-form" class="login-card">
    <h2>Dashboard login</h2>
    <p>Enter the bearer token configured in <code>navigator.toml</code>.</p>
    <input id="login-token" type="password" autocomplete="current-password" aria-label="Bearer token" required>
    <button type="submit">Open dashboard</button>
    <p id="login-error" class="login-error" hidden></p>
  </form>
</div>
<header>
  <div>
    <h1>VelocityNavigator Dashboard</h1>
    <span class="badge">Operations</span>
  </div>
  <div class="meta">
    Routing: <span id="meta-mode">-</span>
    &middot; Lobbies: <span id="meta-total">-</span> online
    &middot; Refresh: __REFRESH_SEC__s
    &middot; <span id="meta-time">-</span>
  </div>
</header>
<main>
  <div class="grid">
    <div class="kpi"><div class="label">Player joins since start</div><div class="value" id="kpi-joins">0</div></div>
    <div class="kpi"><div class="label">Player leaves since start</div><div class="value" id="kpi-leaves">0</div></div>
    <div class="kpi"><div class="label">Online lobbies</div><div class="value" id="kpi-online">0</div></div>
    <div class="kpi"><div class="label">Drained</div><div class="value" id="kpi-drained">0</div></div>
    <div class="kpi"><div class="label">Cache size</div><div class="value" id="kpi-cache">0</div></div>
    <div class="kpi"><div class="label">Affinity entries</div><div class="value" id="kpi-affinity">0</div></div>
  </div>

  <div class="panel">
    <h2>Lobby status</h2>
    <table>
      <thead>
        <tr>
          <th>Server</th>
          <th>Status</th>
          <th>Players</th>
          <th>Max</th>
          <th>Drain</th>
          <th>Circuit breaker</th>
          <th>Ping</th>
          <th>Routed</th>
        </tr>
      </thead>
      <tbody id="lobby-body">
        <tr><td colspan="8" class="empty">Loading...</td></tr>
      </tbody>
    </table>
  </div>

  <div class="two-col">
    <div class="panel">
      <h2>Routing distribution</h2>
      <div id="distribution"><div class="empty">No data yet.</div></div>
    </div>
    <div class="panel">
      <h2>Config summary</h2>
      <div id="config-summary"></div>
    </div>
  </div>
</main>
<footer>
  VelocityNavigator &middot; operations dashboard &middot; auto-refresh every __REFRESH_SEC__ seconds
</footer>
<script>
  let dashboardToken = '';
  let loginOpen = false;
  function requestHeaders() {
    const headers = { 'Accept': 'application/json' };
    if (dashboardToken) headers['Authorization'] = 'Bearer ' + dashboardToken;
    return headers;
  }
  function showLogin(message) {
    loginOpen = true;
    document.getElementById('login-overlay').hidden = false;
    const error = document.getElementById('login-error');
    error.textContent = message || '';
    error.hidden = !message;
    document.getElementById('login-token').focus();
  }
  function hideLogin() {
    loginOpen = false;
    document.getElementById('login-overlay').hidden = true;
    const error = document.getElementById('login-error');
    error.textContent = '';
    error.hidden = true;
  }
  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function(c) {
      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
    });
  }
  function latencyClass(ms) {
    if (ms < 0) return 'latency-unknown';
    if (ms < 100) return 'latency-good';
    if (ms < 300) return 'latency-ok';
    return 'latency-bad';
  }
  function formatMax(m) {
    return m < 0 ? '∞' : m;
  }
  function cbClass(state) {
    if (state === 'OPEN') return 'cb-open';
    if (state === 'HALF_OPEN') return 'cb-half';
    return 'cb-closed';
  }
  async function refresh() {
    if (loginOpen && !dashboardToken) return false;
    try {
      const attemptedToken = dashboardToken.length > 0;
      const res = await fetch('/api/state.json', { headers: requestHeaders() });
      if (!res.ok) {
        if (res.status === 401) {
          dashboardToken = '';
          showLogin(attemptedToken ? 'Invalid bearer token.' : '');
        }
        return false;
      }
      hideLogin();
      const s = await res.json();
      document.getElementById('meta-mode').textContent = s.routing_mode || '-';
      document.getElementById('meta-total').textContent = (s.summary && s.summary.online_lobbies != null) ? s.summary.online_lobbies : '-';
      document.getElementById('meta-time').textContent = new Date().toLocaleTimeString();
      document.getElementById('kpi-joins').textContent = s.joins != null ? s.joins : 0;
      document.getElementById('kpi-leaves').textContent = s.leaves != null ? s.leaves : 0;
      document.getElementById('kpi-online').textContent = (s.summary && s.summary.online_lobbies != null) ? s.summary.online_lobbies : 0;
      document.getElementById('kpi-drained').textContent = (s.summary && s.summary.drained_lobbies != null) ? s.summary.drained_lobbies : 0;
      document.getElementById('kpi-cache').textContent = s.cache_size != null ? s.cache_size : 0;
      document.getElementById('kpi-affinity').textContent = s.affinity_entries != null ? s.affinity_entries : 0;

      const tbody = document.getElementById('lobby-body');
      if (!s.servers || s.servers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="empty">No lobbies configured.</td></tr>';
      } else {
        tbody.innerHTML = s.servers.map(function(srv) {
          const status = srv.online ? '<span class="pill online">online</span>' : '<span class="pill offline">offline</span>';
          const drain = srv.drained ? '<span class="pill drained">drained</span>' : '<span class="muted">-</span>';
          const cb = '<span class="pill ' + cbClass(srv.circuit_breaker) + '">' + escapeHtml(srv.circuit_breaker) + '</span>';
          const lat = srv.latency_ms < 0 ? '<span class="latency-unknown">-</span>'
                    : '<span class="' + latencyClass(srv.latency_ms) + '">' + srv.latency_ms + 'ms</span>';
          return '<tr>'
            + '<td><code>' + escapeHtml(srv.name) + '</code></td>'
            + '<td>' + status + '</td>'
            + '<td>' + srv.players + '</td>'
            + '<td>' + formatMax(srv.max_players) + '</td>'
            + '<td>' + drain + '</td>'
            + '<td>' + cb + '</td>'
            + '<td>' + lat + '</td>'
            + '<td>' + srv.routed + '</td>'
            + '</tr>';
        }).join('');
      }

      const dist = document.getElementById('distribution');
      const entries = s.distribution ? Object.entries(s.distribution) : [];
      if (entries.length === 0) {
        dist.innerHTML = '<div class="empty">No routing data yet.</div>';
      } else {
        const max = Math.max.apply(null, entries.map(function(e) { return e[1]; }));
        dist.innerHTML = entries.sort(function(a,b) { return b[1] - a[1]; }).map(function(e) {
          const pct = max > 0 ? Math.round((e[1] / max) * 100) : 0;
          return '<div class="bar-row">'
            + '<span class="name">' + escapeHtml(e[0]) + '</span>'
            + '<div class="bar-bg"><div class="bar" style="width:' + pct + '%"></div></div>'
            + '<span class="count">' + e[1] + '</span>'
            + '</div>';
        }).join('');
      }

      const cs = document.getElementById('config-summary');
      const rows = [];
      rows.push(['Routing mode', s.routing_mode]);
      rows.push(['Default lobbies', (s.default_lobbies || []).join(', ') || '-']);
      rows.push(['Contextual routing', s.contextual_enabled ? 'enabled' : 'disabled']);
      rows.push(['Health checks', s.health_checks_enabled ? 'enabled' : 'disabled']);
      rows.push(['Circuit breaker', s.circuit_breaker_enabled ? 'enabled' : 'disabled']);
      rows.push(['Player affinity', s.affinity_enabled ? 'enabled' : 'disabled']);
      rows.push(['Active pings', s.active_pings != null ? s.active_pings : 0]);
      rows.push(['Plugin version', s.version]);
      cs.innerHTML = rows.map(function(r) {
        return '<div class="config-row"><span class="k">' + escapeHtml(r[0]) + '</span><span class="v">' + escapeHtml(r[1]) + '</span></div>';
      }).join('');
      return true;
    } catch (e) {
      console.error('refresh failed', e);
      if (loginOpen) showLogin('Dashboard request failed.');
      return false;
    }
  }
  document.getElementById('login-form').addEventListener('submit', async function(event) {
    event.preventDefault();
    const input = document.getElementById('login-token');
    const candidate = input.value;
    if (!candidate) {
      showLogin('Enter a bearer token.');
      return;
    }
    dashboardToken = candidate;
    if (await refresh()) input.value = '';
    else dashboardToken = '';
  });
  refresh();
  setInterval(refresh, __REFRESH_MS__);
</script>
</body>
</html>
""";
}
