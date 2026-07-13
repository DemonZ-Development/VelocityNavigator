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

public final class NavigatorAPIProvider {

    private static volatile NavigatorAPI instance;

    private NavigatorAPIProvider() {
    }

    public static NavigatorAPI get() {
        return instance;
    }

    static void set(NavigatorAPI api) {
        instance = api;
    }

    static void clear() {
        instance = null;
    }
}
