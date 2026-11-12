package com.github.ysbbbbbb.kaleidoscopedoll.debug;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.item.component.ResolvableProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家玩偶数据链路诊断日志（阶段 1：只记录，不改业务逻辑）。
 *
 * <p>日志前缀固定为 {@code KaleidoscopeDoll/Diag}，字段：side/stage/name/uuid/profileNull/
 * profileType/propertyKeys/hasTextures。不会打印完整 textures Base64。</p>
 */
public final class DollDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger("KaleidoscopeDoll/Diag");
    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

    private DollDebug() {
    }

    public static boolean enabled() {
        return !Boolean.getBoolean("kaleidoscope_doll.diag.off");
    }

    public static void log(String side, String stage, Object profile, String extra) {
        if (!enabled()) {
            return;
        }
        String name = "?";
        String uuid = "?";
        boolean isNull = profile == null;
        String type = "none";
        Set<String> keys = Set.of();
        try {
            if (profile instanceof GameProfile gameProfile) {
                name = String.valueOf(gameProfile.name());
                uuid = String.valueOf(gameProfile.id());
                keys = gameProfile.properties().keySet();
                type = "GameProfile";
            } else if (profile instanceof ResolvableProfile resolvable) {
                name = resolvable.name().orElse("?");
                GameProfile partial = resolvable.partialProfile();
                if (partial != null) {
                    uuid = String.valueOf(partial.id());
                    keys = partial.properties().keySet();
                }
                type = "ResolvableProfile";
            }
        } catch (Throwable throwable) {
            type = "error:" + throwable.getClass().getSimpleName();
        }
        LOGGER.info("[{}] stage={} name={} uuid={} profileNull={} profileType={} propertyKeys={} hasTextures={} {}",
                side, stage, name, uuid, isNull, type, keys, keys.contains("textures"),
                extra == null ? "" : extra);
    }

    /** 渲染热路径用：同一个 key 在 ms 毫秒内只打一次。 */
    public static void logThrottled(String key, long ms, String side, String stage, Object profile, String extra) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST.get(key);
        if (last != null && now - last < ms) {
            return;
        }
        LAST.put(key, now);
        log(side, stage, profile, extra);
    }

    public static void msg(String side, String stage, String extra) {
        if (!enabled()) {
            return;
        }
        LOGGER.info("[{}] stage={} {}", side, stage, extra == null ? "" : extra);
    }
}
