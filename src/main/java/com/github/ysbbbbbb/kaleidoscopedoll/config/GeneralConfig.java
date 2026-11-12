package com.github.ysbbbbbb.kaleidoscopedoll.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量配置：不再依赖 ForgeConfigAPIPort，改为直接读写 config/kaleidoscope_doll-common.json。
 * 选项名与旧版 TOML 配置保持一致，方便玩家迁移。
 */
public final class GeneralConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("kaleidoscope_doll-common.json");

    private static final Map<String, Value<?>> ALL = new LinkedHashMap<>();

    public static final IntValue YELLOW_DOLL_GIFT_BOX_WEIGHT = new IntValue("YellowDollGiftBoxWeight", 10, 0, 100);
    public static final IntValue GREEN_DOLL_GIFT_BOX_WEIGHT = new IntValue("GreenDollGiftBoxWeight", 10, 0, 100);
    public static final IntValue PURPLE_DOLL_GIFT_BOX_WEIGHT = new IntValue("PurpleDollGiftBoxWeight", 80, 0, 100);

    public static final BooleanValue DOLL_CAN_BE_THROWN = new BooleanValue("DollCanBeThrown", true);
    public static final BooleanValue DOLL_AFFECTED_BY_WATER = new BooleanValue("DollAffectedByWater", true);
    public static final BooleanValue DOLL_AFFECTED_BY_GRAVITY = new BooleanValue("DollAffectedByGravity", true);
    public static final BooleanValue DOLL_THROW_PARTICLE_EFFECT = new BooleanValue("DollThrowParticleEffect", true);
    public static final BooleanValue DOLL_CAN_KNOCKBACK_ENTITIES = new BooleanValue("DollCanKnockbackEntities", true);
    public static final DoubleValue DOLL_KNOCKBACK_FORCE = new DoubleValue("DollKnockbackForce", 1.0, 0.0, 10.0);
    public static final BooleanValue DOLL_CAN_MOUNT_ENTITIES = new BooleanValue("DollCanMountEntities", true);

    public static final BooleanValue ENABLE_PHANTOM_DOLL_SPAWN = new BooleanValue("EnablePhantomDollSpawn", true);
    public static final DoubleValue PHANTOM_DOLL_SPAWN_CHANCE = new DoubleValue("PhantomDollSpawnChance", 0.03, 0.0, 1.0);

    private GeneralConfig() {
    }

    public static void load() {
        JsonObject root = new JsonObject();
        if (Files.isRegularFile(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                if (!json.isBlank()) {
                    root = JsonParser.parseString(json).getAsJsonObject();
                }
            } catch (Exception e) {
                System.err.println("[kaleidoscope_doll] Failed to read config, using defaults: " + e);
            }
        }

        for (Value<?> value : ALL.values()) {
            value.read(root);
        }
        save(root);
    }

    private static void save(JsonObject root) {
        for (Value<?> value : ALL.values()) {
            value.write(root);
        }
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[kaleidoscope_doll] Failed to save config: " + e);
        }
    }

    private abstract static class Value<T> {
        protected final String key;
        protected final T defaultValue;
        protected T value;

        private Value(String key, T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            ALL.put(key, this);
        }

        public T get() {
            return this.value == null ? this.defaultValue : this.value;
        }

        protected abstract void read(JsonObject root);

        protected abstract void write(JsonObject root);
    }

    public static final class BooleanValue extends Value<Boolean> {
        private BooleanValue(String key, boolean defaultValue) {
            super(key, defaultValue);
        }

        @Override
        protected void read(JsonObject root) {
            if (root.has(this.key) && root.get(this.key).isJsonPrimitive()) {
                this.value = root.get(this.key).getAsBoolean();
            }
        }

        @Override
        protected void write(JsonObject root) {
            root.addProperty(this.key, get());
        }
    }

    public static final class IntValue extends Value<Integer> {
        private final int min;
        private final int max;

        private IntValue(String key, int defaultValue, int min, int max) {
            super(key, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        protected void read(JsonObject root) {
            if (root.has(this.key) && root.get(this.key).isJsonPrimitive()) {
                try {
                    this.value = Math.max(this.min, Math.min(this.max, root.get(this.key).getAsInt()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        @Override
        protected void write(JsonObject root) {
            root.addProperty(this.key, get());
        }
    }

    public static final class DoubleValue extends Value<Double> {
        private final double min;
        private final double max;

        private DoubleValue(String key, double defaultValue, double min, double max) {
            super(key, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        protected void read(JsonObject root) {
            if (root.has(this.key) && root.get(this.key).isJsonPrimitive()) {
                try {
                    this.value = Math.max(this.min, Math.min(this.max, root.get(this.key).getAsDouble()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        @Override
        protected void write(JsonObject root) {
            root.addProperty(this.key, get());
        }
    }
}
