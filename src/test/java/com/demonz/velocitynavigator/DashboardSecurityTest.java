/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardSecurityTest {
    @Test
    void rejectsQueryTokensAndAcceptsAuthorizationHeaders() throws Exception {
        Exchange query = new Exchange(URI.create("/api/state.json?token=validation-secret"));
        assertFalse(DashboardServer.isAuthorized("validation-secret", query));

        Exchange header = new Exchange(URI.create("/api/state.json"));
        header.getRequestHeaders().set("Authorization", "Bearer validation-secret");
        assertTrue(DashboardServer.isAuthorized("validation-secret", header));

        Exchange wrong = new Exchange(URI.create("/api/state.json"));
        wrong.getRequestHeaders().set("Authorization", "Bearer wrong-secret");
        assertFalse(DashboardServer.isAuthorized("validation-secret", wrong));

        Exchange mixedQuery = new Exchange(URI.create("/api/state.json?mode=full&token=validation-secret"));
        assertFalse(DashboardServer.isAuthorized("validation-secret", mixedQuery));

        Exchange prefixed = new Exchange(URI.create("/api/state.json"));
        prefixed.getRequestHeaders().set("Authorization", "Bearer validation-secret-extra");
        assertFalse(DashboardServer.isAuthorized("validation-secret", prefixed));
        assertTrue(DashboardServer.isAuthorized("", query));
    }

    @Test
    void dashboardLoginKeepsTheTokenOutOfUrlsAndBrowserStorage() throws Exception {
        Field templateField = DashboardServer.class.getDeclaredField("HTML_TEMPLATE");
        templateField.setAccessible(true);
        String template = (String) templateField.get(null);
        assertTrue(template.contains("id=\"login-form\""));
        assertTrue(template.contains("Authorization"));
        assertFalse(template.contains("?token"));
        assertFalse(template.contains("location.search"));
        assertFalse(template.contains("localStorage"));
        assertFalse(template.contains("sessionStorage"));
        assertTrue(template.contains("Player joins since start"));
        assertTrue(template.contains("Player leaves since start"));
        assertFalse(template.contains(">Beta<"));
    }

    private static final class Exchange extends HttpExchange {
        private final URI uri;
        private final Headers request = new Headers();
        private final Headers response = new Headers();

        private Exchange(URI uri) {
            this.uri = uri;
        }

        @Override
        public Headers getRequestHeaders() {
            return request;
        }

        @Override
        public Headers getResponseHeaders() {
            return response;
        }

        @Override
        public URI getRequestURI() {
            return uri;
        }

        @Override
        public String getRequestMethod() {
            return "GET";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getResponseBody() {
            return new ByteArrayOutputStream();
        }

        @Override
        public void sendResponseHeaders(int code, long length) {
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public int getResponseCode() {
            return -1;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 9226);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream input, OutputStream output) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
