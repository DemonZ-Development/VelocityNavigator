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
package com.demonz.velocitynavigator.bukkit;

import com.demonz.velocitynavigator.common.RedisRegistrationSigner;
import com.demonz.velocitynavigator.common.RedisTransport;

import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public final class BackendRedisRegistration {

    private BackendRedisRegistration() {
    }

    public static Result publish(Settings settings, boolean unregister) {
        JsonObject payload = payload(settings, unregister);
        try (Socket socket = RedisTransport.connect(settings.toConnectionSettings(), false)) {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            RedisTransport.authenticate(settings.toConnectionSettings(), input, output);
            RedisTransport.writeCommand(output, "PUBLISH",
                    settings.channelPrefix() + ":servers:register", payload.toString());
            if (!(RedisTransport.readResponse(input) instanceof Long)) {
                return new Result(false, "Redis returned an invalid PUBLISH response");
            }
            return new Result(true, unregister ? "unregistered" : "registered");
        } catch (IOException | RuntimeException error) {
            return new Result(false, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    static JsonObject payload(Settings settings, boolean unregister) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "register");
        payload.addProperty("node", "backend:" + settings.serverName());
        payload.addProperty("timestamp", System.currentTimeMillis());
        payload.addProperty("name", settings.serverName());
        payload.addProperty("host", settings.advertisedHost());
        payload.addProperty("port", settings.advertisedPort());
        payload.addProperty("group", settings.group());
        payload.addProperty("max_players", settings.maxPlayers());
        payload.addProperty("weight", settings.weight());
        payload.addProperty("action", unregister ? "unregister" : "");
        payload.addProperty("signature", RedisRegistrationSigner.sign(payload, settings.registrationSecret()));
        return payload;
    }

    public record Settings(String host, int port, String username, String password, boolean ssl,
                           String channelPrefix, int connectTimeoutMs, int readTimeoutMs,
                           String registrationSecret, String serverName, String advertisedHost,
                           int advertisedPort, String group, int maxPlayers, int weight) {
        public Settings {
            if (host == null || host.isBlank()) host = "127.0.0.1";
            port = Math.max(1, Math.min(65535, port));
            username = username == null ? "" : username;
            password = password == null ? "" : password;
            channelPrefix = channelPrefix == null || channelPrefix.isBlank() ? "vn" : channelPrefix;
            connectTimeoutMs = Math.max(250, Math.min(30000, connectTimeoutMs));
            readTimeoutMs = Math.max(1000, Math.min(120000, readTimeoutMs));
            if (registrationSecret == null || registrationSecret.isBlank()) {
                throw new IllegalArgumentException("redis.registration_secret is required");
            }
            if (serverName == null || !serverName.matches("[A-Za-z0-9_.-]{1,64}")) {
                throw new IllegalArgumentException("redis.server_name is invalid");
            }
            if (advertisedHost == null || advertisedHost.isBlank()) {
                throw new IllegalArgumentException("redis.advertised_host is required");
            }
            advertisedPort = Math.max(1, Math.min(65535, advertisedPort));
            group = group == null || group.isBlank() ? "default" : group;
            if (maxPlayers < -1) maxPlayers = -1;
            if (weight < 1) weight = 1;
        }

        public RedisTransport.ConnectionSettings toConnectionSettings() {
            return new RedisTransport.ConnectionSettings(host, port, username, password, ssl,
                    connectTimeoutMs, readTimeoutMs);
        }
    }

    public record Result(boolean success, String message) {
    }
}
