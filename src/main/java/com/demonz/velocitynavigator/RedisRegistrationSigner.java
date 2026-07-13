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

import com.google.gson.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

public final class RedisRegistrationSigner {
    private RedisRegistrationSigner() {
    }

    public static String sign(JsonObject payload, String secret) {
        String canonical = String.join("|",
                value(payload, "type"), value(payload, "node"), value(payload, "timestamp"),
                value(payload, "name"), value(payload, "host"), value(payload, "port"),
                value(payload, "group"), value(payload, "max_players"), value(payload, "weight"), value(payload, "action"));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String value(JsonObject payload, String key) {
        return payload.has(key) && !payload.get(key).isJsonNull() ? payload.get(key).getAsString() : "";
    }
}
