/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MenuBridgeProtocol {

    public static final String CHANNEL = "velocitynavigator:menu";
    public static final int MAX_PAYLOAD_BYTES = 128 * 1024;
    private static final int MAGIC = 0x564E4D31;
    private static final int OPEN = 1;
    private static final int SELECT = 2;
    private static final int HELLO = 3;

    private MenuBridgeProtocol() {
    }

    public static byte[] encodeOpen(String token, int rows, String title, int page, int totalPages,
                                    int refreshSeconds, boolean fillEmpty, String fillerMaterial,
                                    List<MenuItem> items) throws IOException {
        if (items == null || items.isEmpty() || items.size() > 54) {
            throw new IllegalArgumentException("Inventory menu must contain between 1 and 54 items");
        }
        requireNonBlank(token, "token");
        requireNonBlank(title, "title");
        requireNonBlank(fillerMaterial, "filler material");
        int safeRows = Math.max(2, Math.min(6, rows));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(OPEN);
            writeBounded(out, token, 64);
            out.writeByte(safeRows);
            writeBounded(out, title, 128);
            out.writeShort(Math.max(0, page));
            out.writeShort(Math.max(1, totalPages));
            out.writeShort(Math.max(0, Math.min(300, refreshSeconds)));
            out.writeBoolean(fillEmpty);
            writeBounded(out, fillerMaterial, 64);
            out.writeByte(items.size());
            for (MenuItem item : items) {
                if (item.slot() < 0 || item.slot() >= safeRows * 9) {
                    throw new IllegalArgumentException("Inventory item slot is outside the menu");
                }
                requireNonBlank(item.target(), "target");
                requireNonBlank(item.material(), "material");
                requireNonBlank(item.name(), "name");
                out.writeByte(item.slot());
                writeBounded(out, item.target(), 96);
                writeBounded(out, item.material(), 64);
                writeBounded(out, item.name(), 256);
                List<String> lore = item.lore() == null ? List.of() : item.lore();
                if (lore.size() > 16) {
                    throw new IllegalArgumentException("Inventory item lore is limited to 16 lines");
                }
                out.writeByte(lore.size());
                for (String line : lore) {
                    writeBounded(out, line, 512);
                }
            }
        }
        if (bytes.size() > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Inventory menu payload is too large");
        }
        return bytes.toByteArray();
    }

    public static OpenMenu decodeOpen(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            requireHeader(in, OPEN);
            String token = readBounded(in, 64);
            int rows = in.readUnsignedByte();
            if (rows < 2 || rows > 6) {
                throw new IOException("Invalid inventory row count");
            }
            String title = readBounded(in, 128);
            if (token.isBlank() || title.isBlank()) {
                throw new IOException("Open-menu token and title are required");
            }
            int page = in.readUnsignedShort();
            int totalPages = in.readUnsignedShort();
            int refreshSeconds = in.readUnsignedShort();
            boolean fillEmpty = in.readBoolean();
            String fillerMaterial = readBounded(in, 64);
            int count = in.readUnsignedByte();
            if (count < 1 || count > rows * 9 || totalPages < 1 || page >= totalPages || refreshSeconds > 300) {
                throw new IOException("Invalid inventory metadata");
            }
            List<MenuItem> items = new ArrayList<>(count);
            boolean[] occupied = new boolean[rows * 9];
            for (int index = 0; index < count; index++) {
                int slot = in.readUnsignedByte();
                String target = readBounded(in, 96);
                String material = readBounded(in, 64);
                String name = readBounded(in, 256);
                if (slot >= occupied.length || occupied[slot] || target.isBlank() || material.isBlank() || name.isBlank()) {
                    throw new IOException("Invalid menu item");
                }
                occupied[slot] = true;
                int loreCount = in.readUnsignedByte();
                if (loreCount > 16) {
                    throw new IOException("Invalid lore line count");
                }
                List<String> lore = new ArrayList<>(loreCount);
                for (int line = 0; line < loreCount; line++) {
                    lore.add(readBounded(in, 512));
                }
                items.add(new MenuItem(slot, target, material, name, List.copyOf(lore)));
            }
            requireFullyRead(in);
            return new OpenMenu(token, rows, title, page, totalPages, refreshSeconds, fillEmpty,
                    fillerMaterial, List.copyOf(items));
        }
    }

    public static byte[] encodeSelection(String token, String target) throws IOException {
        requireNonBlank(token, "token");
        requireNonBlank(target, "target");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(SELECT);
            writeBounded(out, token, 64);
            writeBounded(out, target, 96);
        }
        return bytes.toByteArray();
    }

    public static Selection decodeSelection(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            requireHeader(in, SELECT);
            Selection selection = new Selection(readBounded(in, 64), readBounded(in, 96));
            if (selection.token().isBlank() || selection.target().isBlank()) {
                throw new IOException("Selection token and target are required");
            }
            requireFullyRead(in);
            return selection;
        }
    }

    public static byte[] encodeHello(String version) throws IOException {
        requireNonBlank(version, "version");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(HELLO);
            writeBounded(out, version, 32);
        }
        return bytes.toByteArray();
    }

    public static Hello decodeHello(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            requireHeader(in, HELLO);
            Hello hello = new Hello(readBounded(in, 32));
            requireFullyRead(in);
            return hello;
        }
    }

    public static PacketType packetType(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Invalid menu bridge packet");
            }
            return switch (in.readUnsignedByte()) {
                case OPEN -> PacketType.OPEN;
                case SELECT -> PacketType.SELECT;
                case HELLO -> PacketType.HELLO;
                default -> throw new IOException("Unknown menu bridge packet type");
            };
        }
    }

    private static DataInputStream input(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Invalid menu bridge payload size");
        }
        return new DataInputStream(new ByteArrayInputStream(payload));
    }

    private static void requireHeader(DataInputStream in, int action) throws IOException {
        if (in.readInt() != MAGIC || in.readUnsignedByte() != action) {
            throw new IOException("Invalid menu bridge packet");
        }
    }

    private static void writeBounded(DataOutputStream out, String value, int maxChars) throws IOException {
        String safe = value == null ? "" : value;
        if (safe.length() > maxChars) {
            throw new IllegalArgumentException("Menu bridge string length is outside the allowed range");
        }
        out.writeUTF(safe);
    }

    private static String readBounded(DataInputStream in, int maxChars) throws IOException {
        String value = in.readUTF();
        if (value.length() > maxChars) {
            throw new IOException("Invalid menu bridge string length");
        }
        return value;
    }

    private static void requireFullyRead(DataInputStream in) throws IOException {
        if (in.available() != 0) {
            throw new IOException("Unexpected trailing menu bridge data");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Menu bridge " + label + " is required");
        }
    }

    public enum PacketType { OPEN, SELECT, HELLO }

    public record MenuItem(int slot, String target, String material, String name, List<String> lore) {
    }

    public record OpenMenu(String token, int rows, String title, int page, int totalPages,
                           int refreshSeconds, boolean fillEmpty, String fillerMaterial, List<MenuItem> items) {
    }

    public record Selection(String token, String target) {
    }

    public record Hello(String version) {
    }
}
