/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

public final class UiBridgeProtocol {
    private static final int MAGIC = 0x564E4431;
    private static final int MAX_BYTES = 30000;
    public enum Type { CAPABILITIES, CLIENT_CONTEXT, OPEN, ACTION }
    public record Capabilities(boolean dialogs) {}
    public record ClientContext(int protocol, boolean bedrock, String language) {
        public ClientContext(int protocol, boolean bedrock) {
            this(protocol, bedrock, "");
        }
    }
    public record Button(String action, String label, String tooltip, String material) {}
    public record Input(String key, String label, int maxLength) {}
    public record Open(String token, String title, String body, String closeLabel, JavaUiMode mode,
                       int protocol, boolean bedrock, boolean authentication, boolean register,
                       int lifetimeSeconds, List<Button> buttons, List<Input> inputs) {
        public Open {
            buttons = List.copyOf(buttons);
            inputs = List.copyOf(inputs);
        }
    }
    public record Action(String token, String action, Map<String, String> inputs) {
        public Action { inputs = Map.copyOf(inputs); }
    }

    private UiBridgeProtocol() {}

    public static boolean isPacket(byte[] payload) {
        return payload != null && payload.length >= 4 && ByteBuffer.wrap(payload).getInt() == MAGIC;
    }

    public static Type type(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) { return header(in); }
    }

    public static byte[] capabilities(boolean dialogs) throws IOException {
        return encode(Type.CAPABILITIES, out -> out.writeBoolean(dialogs));
    }

    public static Capabilities decodeCapabilities(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            requireType(in, Type.CAPABILITIES);
            Capabilities result = new Capabilities(in.readBoolean());
            end(in);
            return result;
        }
    }

    public static byte[] context(int protocol, boolean bedrock) throws IOException {
        return context(protocol, bedrock, "");
    }

    public static byte[] context(int protocol, boolean bedrock, String language) throws IOException {
        String lang = language == null ? "" : language.trim();
        return encode(Type.CLIENT_CONTEXT, out -> {
            out.writeInt(protocol);
            out.writeBoolean(bedrock);
            write(out, lang, 16);
        });
    }

    public static ClientContext decodeContext(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            requireType(in, Type.CLIENT_CONTEXT);
            int protocol = in.readInt();
            boolean bedrock = in.readBoolean();
            String language = in.available() > 0 ? read(in, 16) : "";
            end(in);
            return new ClientContext(protocol, bedrock, language);
        }
    }

    public static byte[] open(Open open) throws IOException {
        if (open.buttons().size() > 54 || open.inputs().size() > 4 || open.lifetimeSeconds() < 5
                || open.lifetimeSeconds() > 3600 || open.token().isBlank() || open.title().isBlank()) {
            throw new IllegalArgumentException("Invalid UI metadata");
        }
        return encode(Type.OPEN, out -> {
            write(out, open.token(), 64); write(out, open.title(), 256); write(out, open.body(), 4096);
            write(out, open.closeLabel(), 128); out.writeByte(open.mode().ordinal());
            out.writeInt(open.protocol()); out.writeBoolean(open.bedrock());
            out.writeBoolean(open.authentication()); out.writeBoolean(open.register());
            out.writeShort(open.lifetimeSeconds()); out.writeByte(open.buttons().size());
            for (Button button : open.buttons()) {
                write(out, button.action(), 256); write(out, button.label(), 512);
                write(out, button.tooltip(), 2048); write(out, button.material(), 64);
            }
            out.writeByte(open.inputs().size());
            for (Input field : open.inputs()) {
                write(out, field.key(), 32); write(out, field.label(), 256); out.writeShort(field.maxLength());
            }
        });
    }

    public static Open decodeOpen(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            requireType(in, Type.OPEN);
            String token = read(in, 64), title = read(in, 256), body = read(in, 4096), close = read(in, 128);
            int mode = in.readUnsignedByte();
            if (mode >= JavaUiMode.values().length || token.isBlank() || title.isBlank()) throw new IOException("Invalid UI mode or identity");
            int protocol = in.readInt(); boolean bedrock = in.readBoolean();
            boolean auth = in.readBoolean(), register = in.readBoolean(); int lifetime = in.readUnsignedShort();
            int count = in.readUnsignedByte();
            if (count > 54 || lifetime < 5 || lifetime > 3600) throw new IOException("Invalid UI bounds");
            List<Button> buttons = new ArrayList<>();
            HashSet<String> actions = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Button button = new Button(read(in, 256), read(in, 512), read(in, 2048), read(in, 64));
                if (button.label().isBlank() || (!button.action().isBlank() && !actions.add(button.action()))) throw new IOException("Invalid UI button");
                buttons.add(button);
            }
            int fields = in.readUnsignedByte();
            if (fields > 4 || (!auth && fields != 0)) throw new IOException("Invalid UI input count");
            List<Input> inputs = new ArrayList<>();
            HashSet<String> keys = new HashSet<>();
            for (int i = 0; i < fields; i++) {
                Input field = new Input(read(in, 32), read(in, 256), in.readUnsignedShort());
                if (field.key().isBlank() || !keys.add(field.key()) || field.maxLength() < 1 || field.maxLength() > 256) throw new IOException("Invalid UI input");
                inputs.add(field);
            }
            end(in);
            return new Open(token, title, body, close, JavaUiMode.values()[mode], protocol, bedrock, auth, register, lifetime, buttons, inputs);
        }
    }

    public static byte[] action(Action action) throws IOException {
        if (action.inputs().size() > 4) throw new IllegalArgumentException("Too many UI inputs");
        return encode(Type.ACTION, out -> {
            write(out, action.token(), 64); write(out, action.action(), 256); out.writeByte(action.inputs().size());
            for (Map.Entry<String, String> field : action.inputs().entrySet()) {
                write(out, field.getKey(), 32); write(out, field.getValue(), 256);
            }
        });
    }

    public static Action decodeAction(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            requireType(in, Type.ACTION);
            String token = read(in, 64), action = read(in, 256);
            int count = in.readUnsignedByte();
            if (count > 4 || token.isBlank() || action.isBlank()) throw new IOException("Invalid UI response");
            Map<String, String> inputs = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String key = read(in, 32), value = read(in, 256);
                if (key.isBlank() || inputs.putIfAbsent(key, value) != null) throw new IOException("Duplicate UI input");
            }
            end(in);
            return new Action(token, action, inputs);
        }
    }

    @FunctionalInterface
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static byte[] encode(Type type, Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeByte(type.ordinal()); writer.write(out);
        }
        if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("UI payload is too large");
        return bytes.toByteArray();
    }
    private static DataInputStream input(byte[] payload) throws IOException {
        if (payload == null || payload.length < 5 || payload.length > MAX_BYTES) throw new IOException("Invalid UI frame size");
        return new DataInputStream(new ByteArrayInputStream(payload));
    }
    private static Type header(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Invalid UI frame");
        int type = in.readUnsignedByte();
        if (type >= Type.values().length) throw new IOException("Unknown UI frame");
        return Type.values()[type];
    }
    private static void requireType(DataInputStream in, Type type) throws IOException {
        if (header(in) != type) throw new IOException("Unexpected UI frame");
    }
    private static void write(DataOutputStream out, String text, int max) throws IOException {
        if (text == null || text.length() > max) throw new IllegalArgumentException("UI text exceeds bounds");
        out.writeUTF(text);
    }
    private static String read(DataInputStream in, int max) throws IOException {
        String text = in.readUTF();
        if (text.length() > max) throw new IOException("UI text exceeds bounds");
        return text;
    }
    private static void end(DataInputStream in) throws IOException {
        if (in.available() != 0) throw new IOException("Trailing UI frame data");
    }
}
