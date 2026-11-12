package com.github.ysbbbbbb.kaleidoscopedoll.server;

import com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 服务端解析「正版玩家名 → 完整 GameProfile」。
 *
 * <p>阶段 2：区分成功/不完整/不存在/失败四种状态；不再用离线 UUID 冒充正版玩家；
 * 只缓存完整档案，失败不缓存，失败原因回传给 GUI。</p>
 */
public final class PlayerProfileResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("KaleidoscopeDoll/ProfileResolver");
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Map<String, GameProfile> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "KaleidoscopeDoll-ProfileResolver");
        thread.setDaemon(true);
        return thread;
    });

    public enum Status {
        /** 完整档案（含 textures 属性）。 */
        SUCCESS_FULL,
        /** 只拿到 UUID/name，没有 textures，客户端可以尝试按 UUID 解析皮肤。 */
        SUCCESS_UUID_ONLY,
        /** 玩家名不存在。 */
        NOT_FOUND,
        /** 网络/服务异常。 */
        FAILED
    }

    public record Result(Status status, GameProfile profile, String detail) {
        public boolean hasProfile() {
            return this.profile != null && this.profile.id() != null;
        }
    }

    private PlayerProfileResolver() {
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public static CompletableFuture<Result> resolve(MinecraftServer server, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        GameProfile cached = CACHE.get(key);
        if (cached != null) {
            DollDebug.log("SERVER", "resolver.cacheHit", cached, "requestedName=" + name);
            return CompletableFuture.completedFuture(new Result(Status.SUCCESS_FULL, cached, "cache"));
        }
        return CompletableFuture.supplyAsync(() -> {
            if (server == null) {
                return new Result(Status.FAILED, null, "server-null");
            }
            GameProfile byName;
            try {
                byName = server.services().profileResolver().fetchByName(name).orElse(null);
            } catch (Exception exception) {
                DollDebug.msg("SERVER", "resolver.fetchByName.exception",
                        "requestedName=" + name + " error=" + exception.getClass().getSimpleName()
                                + " message=" + exception.getMessage());
                return new Result(Status.FAILED, null, "fetchByName:" + exception.getClass().getSimpleName());
            }
            if (byName == null || byName.id() == null) {
                DollDebug.msg("SERVER", "resolver.notFound", "requestedName=" + name);
                return new Result(Status.NOT_FOUND, null, "name-not-found");
            }
            DollDebug.log("SERVER", "resolver.fetchByName", byName, "requestedName=" + name);
            GameProfile full = byName;
            try {
                var result = server.services().sessionService().fetchProfile(byName.id(), true);
                if (result != null && result.profile() != null) {
                    full = result.profile();
                }
            } catch (Exception exception) {
                DollDebug.msg("SERVER", "resolver.fetchProfile.exception",
                        "requestedName=" + name + " error=" + exception.getClass().getSimpleName()
                                + " message=" + exception.getMessage());
            }
            boolean hasTextures = full.properties().containsKey("textures");
            if (hasTextures) {
                CACHE.put(key, full);
                DollDebug.log("SERVER", "resolver.final", full,
                        "requestedName=" + name + " status=SUCCESS_FULL cached=true");
                return new Result(Status.SUCCESS_FULL, full, "textures");
            }
            DollDebug.log("SERVER", "resolver.final", full,
                    "requestedName=" + name + " status=SUCCESS_UUID_ONLY cached=false");
            return new Result(Status.SUCCESS_UUID_ONLY, full, "no-textures");
        }, EXECUTOR);
    }
}
