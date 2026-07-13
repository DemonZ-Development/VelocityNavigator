/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisProtocolTest {
    @Test
    void writesUtf8RespCommandsWithByteAccurateLengths() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedOutputStream output = new BufferedOutputStream(bytes);
        RedisSyncService.command(output, "PUBLISH", "vn:state", "Привет");
        assertEquals("*3\r\n$7\r\nPUBLISH\r\n$8\r\nvn:state\r\n$12\r\nПривет\r\n", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void readsSimpleIntegerBulkAndSubscriptionResponses() throws Exception {
        assertEquals("OK", read("+OK\r\n"));
        assertEquals(3L, read(":3\r\n"));
        assertEquals("hello", read("$5\r\nhello\r\n"));
        assertEquals(List.of("message", "vn:state", "{}"), read("*3\r\n$7\r\nmessage\r\n$8\r\nvn:state\r\n$2\r\n{}\r\n"));
    }

    @Test
    void surfacesRedisErrorResponses() {
        assertThrows(IOException.class, () -> read("-NOAUTH Authentication required\r\n"));
    }

    @Test
    void rejectsOversizedResponseLines() {
        assertRejected("+" + "A".repeat(131_072) + "\r\n", "line limit");
    }

    @Test
    void rejectsOversizedBulkAndArrayLengthsBeforeAllocation() {
        assertRejected("$4194305\r\n", "bulk length limit");
        assertRejected("*16385\r\n", "array length limit");
    }

    @Test
    void rejectsExcessiveNesting() {
        StringBuilder response = new StringBuilder();
        for (int index = 0; index < 65; index++) response.append("*1\r\n");
        response.append("+OK\r\n");
        assertRejected(response.toString(), "nesting depth limit");
    }

    @Test
    void rejectsFramesAboveTheAggregateByteBudget() throws Exception {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        response.write("*9\r\n".getBytes(StandardCharsets.UTF_8));
        byte[] value = new byte[1_048_576];
        Arrays.fill(value, (byte) 'A');
        for (int index = 0; index < 9; index++) {
            response.write("$1048576\r\n".getBytes(StandardCharsets.UTF_8));
            response.write(value);
            response.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        assertRejected(response.toByteArray(), "frame byte limit");
    }

    @Test
    void rejectsMalformedLengthsAndBulkTerminators() {
        assertRejected("$-2\r\n", "Invalid Redis bulk length");
        assertRejected("*-2\r\n", "Invalid Redis array length");
        assertRejected("$999999999999999999999\r\n", "Invalid Redis bulk length");
        assertRejected("$1\r\nAxx", "Invalid Redis bulk terminator");
    }

    @Test
    void acceptsResponseLinesAtTheLimit() throws Exception {
        String value = "A".repeat(65_536);
        assertEquals(value, read("+" + value + "\r\n"));
    }

    private static void assertRejected(String response, String messagePart) {
        assertRejected(response.getBytes(StandardCharsets.UTF_8), messagePart);
    }

    private static void assertRejected(byte[] response, String messagePart) {
        IOException error = assertThrows(IOException.class, () -> read(response));
        assertTrue(error.getMessage() != null && error.getMessage().contains(messagePart), error.getMessage());
    }

    private static Object read(String response) throws IOException {
        return read(response.getBytes(StandardCharsets.UTF_8));
    }

    private static Object read(byte[] response) throws IOException {
        return RedisSyncService.read(new BufferedInputStream(new ByteArrayInputStream(response)));
    }
}
