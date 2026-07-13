/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit;

import com.demonz.velocitynavigator.RedisRegistrationSigner;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendRedisRegistrationTest {
    @Test
    void authenticatesAndPublishesSignedRegistrationOverResp() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            var executor = Executors.newSingleThreadExecutor();
            var exchange = executor.submit(() -> receive(server));
            BackendRedisRegistration.Settings settings = new BackendRedisRegistration.Settings(
                    "127.0.0.1", server.getLocalPort(), "navigator", "password", false, "testvn",
                    2000, 2000, "shared-secret", "lobby-9", "10.0.0.9", 25569,
                    "default", 100, 2
            );
            BackendRedisRegistration.Result result = BackendRedisRegistration.publish(settings, false);
            Exchange received = exchange.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            assertTrue(result.success(), result.message());
            assertEquals(List.of("AUTH", "navigator", "password"), received.auth());
            assertEquals("PUBLISH", received.publish().get(0));
            assertEquals("testvn:servers:register", received.publish().get(1));
            JsonObject payload = JsonParser.parseString(received.publish().get(2)).getAsJsonObject();
            assertEquals("lobby-9", payload.get("name").getAsString());
            assertEquals("10.0.0.9", payload.get("host").getAsString());
            assertEquals(RedisRegistrationSigner.sign(payload, "shared-secret"), payload.get("signature").getAsString());
        }
    }

    @Test
    void createsSignedUnregisterPayload() {
        BackendRedisRegistration.Settings settings = new BackendRedisRegistration.Settings(
                "127.0.0.1", 6379, "", "", false, "vn", 2000, 2000,
                "secret", "lobby-1", "10.0.0.1", 25565, "default", -1, 1
        );
        JsonObject payload = BackendRedisRegistration.payload(settings, true);
        assertEquals("unregister", payload.get("action").getAsString());
        assertEquals(RedisRegistrationSigner.sign(payload, "secret"), payload.get("signature").getAsString());
    }

    @Test
    void rejectsOversizedRedisResponseLines() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            var executor = Executors.newSingleThreadExecutor();
            var exchange = executor.submit(() -> serveOversizedResponse(server));
            BackendRedisRegistration.Settings settings = new BackendRedisRegistration.Settings(
                    "127.0.0.1", server.getLocalPort(), "", "", false, "testvn",
                    2000, 2000, "shared-secret", "lobby-9", "10.0.0.9", 25569,
                    "default", 100, 2
            );
            BackendRedisRegistration.Result result = BackendRedisRegistration.publish(settings, false);
            exchange.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
            assertFalse(result.success());
            assertTrue(result.message().contains("line limit"), result.message());
        }
    }

    private static Exchange receive(ServerSocket server) throws Exception {
        try (Socket socket = server.accept()) {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            List<String> auth = readCommand(input);
            output.write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            List<String> publish = readCommand(input);
            output.write(":1\r\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            return new Exchange(auth, publish);
        }
    }

    private static Void serveOversizedResponse(ServerSocket server) throws Exception {
        try (Socket socket = server.accept()) {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            readCommand(input);
            try {
                output.write('+');
                output.write("A".repeat(131_072).getBytes(StandardCharsets.UTF_8));
                output.write("\r\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static List<String> readCommand(BufferedInputStream input) throws Exception {
        if (input.read() != '*') throw new IllegalStateException("Expected RESP array");
        int length = Integer.parseInt(line(input));
        List<String> values = new ArrayList<>();
        for (int index = 0; index < length; index++) {
            if (input.read() != '$') throw new IllegalStateException("Expected RESP bulk value");
            int bytes = Integer.parseInt(line(input));
            byte[] value = input.readNBytes(bytes);
            input.readNBytes(2);
            values.add(new String(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String line(BufferedInputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int current = input.read();
            if (current < 0) throw new EOFException();
            if (previous == '\r' && current == '\n') break;
            if (previous >= 0) output.write(previous);
            previous = current;
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private record Exchange(List<String> auth, List<String> publish) {
    }
}
