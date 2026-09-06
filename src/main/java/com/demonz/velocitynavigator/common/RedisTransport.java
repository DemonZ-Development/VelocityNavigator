/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.common;

import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RedisTransport {

    public static final int MAX_RESP_LINE_BYTES = 65_536;
    public static final int MAX_RESP_BULK_BYTES = 4 * 1024 * 1024;
    public static final int MAX_RESP_ARRAY_LENGTH = 16_384;
    public static final int MAX_RESP_NESTING_DEPTH = 64;
    public static final long MAX_RESP_FRAME_BYTES = 8L * 1024L * 1024L;

    private RedisTransport() {
    }

    public record ConnectionSettings(
            String host,
            int port,
            String username,
            String password,
            boolean ssl,
            int connectTimeoutMs,
            int readTimeoutMs) {
        public ConnectionSettings {
            host = (host == null || host.isBlank()) ? "127.0.0.1" : host;
            port = Math.max(1, Math.min(65535, port));
            username = username == null ? "" : username;
            password = password == null ? "" : password;
            connectTimeoutMs = Math.max(250, Math.min(30000, connectTimeoutMs));
            readTimeoutMs = Math.max(1000, Math.min(120000, readTimeoutMs));
        }
    }

    public static Socket connect(ConnectionSettings settings, boolean subscription) throws IOException {
        SocketFactory factory = settings.ssl() ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();
        Socket socket = factory.createSocket();
        if (socket instanceof SSLSocket sslSocket) {
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(parameters);
        }
        socket.connect(new InetSocketAddress(settings.host(), settings.port()), settings.connectTimeoutMs());
        socket.setSoTimeout(subscription ? 0 : settings.readTimeoutMs());
        return socket;
    }

    public static void authenticate(ConnectionSettings settings,
                                    BufferedInputStream input,
                                    BufferedOutputStream output) throws IOException {
        if (settings.password().isBlank()) return;
        if (settings.username().isBlank()) writeCommand(output, "AUTH", settings.password());
        else writeCommand(output, "AUTH", settings.username(), settings.password());
        if (!"OK".equals(readResponse(input))) {
            throw new IOException("Redis authentication failed");
        }
    }

    public static void writeCommand(BufferedOutputStream output, String... values) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(values, "values");
        output.write(("*" + values.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String value : values) {
            byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        output.flush();
    }

    public static Object readResponse(BufferedInputStream input) throws IOException {
        return readResponse(input, 0, new RespBudget(MAX_RESP_FRAME_BYTES));
    }

    private static Object readResponse(BufferedInputStream input, int depth, RespBudget budget) throws IOException {
        if (depth > MAX_RESP_NESTING_DEPTH) {
            throw new IOException("Redis nesting depth limit exceeded");
        }
        int type = input.read();
        if (type < 0) throw new EOFException();
        budget.consume(1);
        String lineValue = readLine(input, budget);
        return switch (type) {
            case '+' -> lineValue;
            case '-' -> throw new IOException(lineValue);
            case ':' -> parseNumber(lineValue);
            case '$' -> readBulk(input, parseLength(lineValue, "bulk"), budget);
            case '*' -> readArray(input, parseLength(lineValue, "array"), depth, budget);
            default -> throw new IOException("Unknown Redis response type");
        };
    }

    private static List<Object> readArray(BufferedInputStream input, int length, int depth, RespBudget budget) throws IOException {
        if (length < -1) throw new IOException("Invalid Redis array length");
        if (length > MAX_RESP_ARRAY_LENGTH) throw new IOException("Redis array length limit exceeded");
        if (length < 0) return new ArrayList<>();
        List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) values.add(readResponse(input, depth + 1, budget));
        return values;
    }

    private static String readBulk(BufferedInputStream input, int length, RespBudget budget) throws IOException {
        if (length < -1) throw new IOException("Invalid Redis bulk length");
        if (length > MAX_RESP_BULK_BYTES) throw new IOException("Redis bulk length limit exceeded");
        if (length < 0) return "";
        budget.consume((long) length + 2L);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        int carriageReturn = input.read();
        int lineFeed = input.read();
        if (carriageReturn != '\r' || lineFeed != '\n') {
            throw new IOException("Invalid Redis bulk terminator");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readLine(BufferedInputStream input, RespBudget budget) throws IOException {
        ByteArrayOutputStream accumulator = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int current = input.read();
            if (current < 0) throw new EOFException();
            budget.consume(1);
            if (previous == '\r' && current == '\n') break;
            if (previous >= 0) {
                if (accumulator.size() >= MAX_RESP_LINE_BYTES) {
                    throw new IOException("Redis response line limit exceeded");
                }
                accumulator.write(previous);
            }
            previous = current;
        }
        return accumulator.toString(StandardCharsets.UTF_8);
    }

    private static int parseLength(String value, String type) throws IOException {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
                throw new IOException("Invalid Redis " + type + " length");
            }
            return (int) parsed;
        } catch (NumberFormatException error) {
            throw new IOException("Invalid Redis " + type + " length", error);
        }
    }

    private static long parseNumber(String value) throws IOException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IOException("Invalid Redis integer response", error);
        }
    }

    private static final class RespBudget {
        private long remaining;

        private RespBudget(long remaining) {
            this.remaining = remaining;
        }

        private void consume(long bytes) throws IOException {
            if (bytes < 0 || bytes > remaining) {
                throw new IOException("Redis frame byte limit exceeded");
            }
            remaining -= bytes;
        }
    }
}
