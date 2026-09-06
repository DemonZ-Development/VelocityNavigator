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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class FoliaSchedulerCompat {

    private static final boolean IS_FOLIA;

    private static MethodHandle ENTITY_GET_SCHEDULER;
    private static MethodHandle ENTITY_SCHEDULER_RUN;
    private static MethodHandle ENTITY_SCHEDULER_RUN_DELAYED;

    private static MethodHandle SERVER_GET_GLOBAL_REGION_SCHEDULER;
    private static MethodHandle GLOBAL_REGION_SCHEDULER_RUN;
    private static MethodHandle GLOBAL_REGION_SCHEDULER_RUN_DELAYED;
    private static MethodHandle GLOBAL_REGION_SCHEDULER_RUN_AT_FIXED_RATE;
    private static MethodHandle SERVER_GET_REGION_SCHEDULER;
    private static MethodHandle REGION_SCHEDULER_EXECUTE;

    static {
        boolean isFolia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
        }
        IS_FOLIA = isFolia;

        if (IS_FOLIA) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                Class<?> entitySchedulerClass = Class.forName(
                        "io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                Class<?> scheduledTaskClass = Class.forName(
                        "io.papermc.paper.threadedregions.scheduler.ScheduledTask");
                Class<?> globalRegionSchedulerClass = Class.forName(
                        "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Class<?> regionSchedulerClass = Class.forName(
                        "io.papermc.paper.threadedregions.scheduler.RegionScheduler");

                ENTITY_GET_SCHEDULER = lookup.findVirtual(
                        Entity.class, "getScheduler",
                        MethodType.methodType(entitySchedulerClass));

                ENTITY_SCHEDULER_RUN = lookup.findVirtual(
                        entitySchedulerClass, "run",
                        MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, Runnable.class));

                ENTITY_SCHEDULER_RUN_DELAYED = lookup.findVirtual(
                        entitySchedulerClass, "runDelayed",
                        MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, Runnable.class, long.class));

                SERVER_GET_GLOBAL_REGION_SCHEDULER = lookup.findVirtual(
                        org.bukkit.Server.class, "getGlobalRegionScheduler",
                        MethodType.methodType(globalRegionSchedulerClass));

                GLOBAL_REGION_SCHEDULER_RUN = lookup.findVirtual(
                        globalRegionSchedulerClass, "run",
                        MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class));

                GLOBAL_REGION_SCHEDULER_RUN_DELAYED = lookup.findVirtual(
                        globalRegionSchedulerClass, "runDelayed",
                        MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, long.class));

                GLOBAL_REGION_SCHEDULER_RUN_AT_FIXED_RATE = lookup.findVirtual(
                        globalRegionSchedulerClass, "runAtFixedRate",
                        MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, long.class, long.class));

                SERVER_GET_REGION_SCHEDULER = lookup.findVirtual(
                        org.bukkit.Server.class, "getRegionScheduler",
                        MethodType.methodType(regionSchedulerClass));

                REGION_SCHEDULER_EXECUTE = lookup.findVirtual(
                        regionSchedulerClass, "execute",
                        MethodType.methodType(void.class, Plugin.class, Location.class, Runnable.class));

            } catch (Throwable t) {
                Bukkit.getLogger().log(Level.SEVERE, "Failed to initialize Folia scheduler method handles", t);
            }
        }
    }

    private FoliaSchedulerCompat() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static void runTask(Plugin plugin, Entity entity, Runnable task) {
        if (!canSchedule(plugin)) {
            return;
        }
        if (IS_FOLIA && ENTITY_GET_SCHEDULER != null && ENTITY_SCHEDULER_RUN != null) {
            try {
                Object entityScheduler = ENTITY_GET_SCHEDULER.invoke(entity);
                Consumer<?> consumer = scheduledTask -> task.run();
                ENTITY_SCHEDULER_RUN.invoke(entityScheduler, plugin, consumer, (Runnable) null);
                return;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule task on entity region", t);
            }
        }

        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runTask(Plugin plugin, Location location, Runnable task) {
        if (!canSchedule(plugin)) {
            return;
        }
        if (IS_FOLIA && SERVER_GET_REGION_SCHEDULER != null && REGION_SCHEDULER_EXECUTE != null) {
            try {
                Object regionScheduler = SERVER_GET_REGION_SCHEDULER.invoke(Bukkit.getServer());
                REGION_SCHEDULER_EXECUTE.invoke(regionScheduler, plugin, location, task);
                return;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule task on location region", t);
            }
        }

        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (!canSchedule(plugin)) {
            return;
        }
        if (IS_FOLIA && ENTITY_GET_SCHEDULER != null && ENTITY_SCHEDULER_RUN_DELAYED != null) {
            try {
                Object entityScheduler = ENTITY_GET_SCHEDULER.invoke(entity);
                Consumer<?> consumer = scheduledTask -> task.run();
                ENTITY_SCHEDULER_RUN_DELAYED.invoke(entityScheduler, plugin, consumer, (Runnable) null, delayTicks);
                return;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule delayed task on entity region", t);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static void runGlobalTask(Plugin plugin, Runnable task) {
        if (!canSchedule(plugin)) {
            return;
        }
        if (IS_FOLIA && SERVER_GET_GLOBAL_REGION_SCHEDULER != null && GLOBAL_REGION_SCHEDULER_RUN != null) {
            try {
                Object globalScheduler = SERVER_GET_GLOBAL_REGION_SCHEDULER.invoke(Bukkit.getServer());
                Consumer<?> consumer = scheduledTask -> task.run();
                GLOBAL_REGION_SCHEDULER_RUN.invoke(globalScheduler, plugin, consumer);
                return;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule global task", t);
            }
        }

        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runGlobalTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!canSchedule(plugin)) {
            return;
        }
        if (IS_FOLIA && SERVER_GET_GLOBAL_REGION_SCHEDULER != null && GLOBAL_REGION_SCHEDULER_RUN_DELAYED != null) {
            try {
                Object globalScheduler = SERVER_GET_GLOBAL_REGION_SCHEDULER.invoke(Bukkit.getServer());
                Consumer<?> consumer = scheduledTask -> task.run();
                GLOBAL_REGION_SCHEDULER_RUN_DELAYED.invoke(globalScheduler, plugin, consumer, delayTicks);
                return;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule delayed global task", t);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static Object runGlobalTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!canSchedule(plugin)) {
            return null;
        }
        long safeDelay = Math.max(1L, delayTicks);
        long safePeriod = Math.max(1L, periodTicks);
        if (IS_FOLIA && SERVER_GET_GLOBAL_REGION_SCHEDULER != null && GLOBAL_REGION_SCHEDULER_RUN_AT_FIXED_RATE != null) {
            try {
                Object globalScheduler = SERVER_GET_GLOBAL_REGION_SCHEDULER.invoke(Bukkit.getServer());
                Consumer<?> consumer = scheduledTask -> task.run();
                return GLOBAL_REGION_SCHEDULER_RUN_AT_FIXED_RATE.invoke(
                        globalScheduler, plugin, consumer, safeDelay, safePeriod);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to schedule repeating global task", t);
            }
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, safeDelay, safePeriod);
    }

    private static boolean canSchedule(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }

    public static void cancelTask(Plugin plugin, Object task) {
        if (task == null) return;
        if (task instanceof org.bukkit.scheduler.BukkitTask bukkitTask) {
            bukkitTask.cancel();
            return;
        }
        try {
            java.lang.reflect.Method cancel = task.getClass().getMethod("cancel");
            cancel.invoke(task);
        } catch (Throwable ignored) {
        }
    }
}
