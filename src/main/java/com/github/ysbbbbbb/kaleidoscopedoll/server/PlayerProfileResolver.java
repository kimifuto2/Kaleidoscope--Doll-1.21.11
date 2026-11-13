package com.github.ysbbbbbb.kaleidoscopedoll.server;

import com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.MinecraftClientHttpException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
 *
 * <p>阶段 3（Mojang 429 限流修复）：
 * <ul>
 *   <li><b>同服在线玩家优先</b>：{@code PlayerList#getPlayerByName} 命中就直接用其 GameProfile，
 *       不消耗任何 Mojang 配额；</li>
 *   <li><b>负缓存</b>：NOT_FOUND 缓存 30s、FAILED/RATE_LIMITED 缓存 10s（按小写名），
 *       同一名字在 TTL 内不再打 API；成功档案仍长期缓存，失败不污染成功缓存；</li>
 *   <li><b>节流</b>：真实 API 调用之间至少间隔 {@link #MIN_API_INTERVAL_MS} 且串行化，
 *       避免输入过程中的连续请求把配额打满。</li>
 * </ul>
 */
public final class PlayerProfileResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("KaleidoscopeDoll/ProfileResolver");
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    /** 成功档案缓存（长期）。 */
    private static final Map<String, GameProfile> CACHE = new ConcurrentHashMap<>();
    /** 失败/不存在的负缓存（带过期时间）。 */
    private static final Map<String, CachedFailure> NEGATIVE_CACHE = new ConcurrentHashMap<>();

    private static final long NOT_FOUND_TTL_MS = 30_000L;
    private static final long FAILED_TTL_MS = 10_000L;
    private static final long MIN_API_INTERVAL_MS = 1_000L;

    /** 真实 API 调用串行化 + 节流用的锁（持锁期间睡眠以拉开间隔）。 */
    private static final Object API_LOCK = new Object();
    private static long lastApiCallMillis;

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
        FAILED,
        /** 被 Mojang 限流（HTTP 429）。 */
        RATE_LIMITED
    }

    public record Result(Status status, GameProfile profile, String detail) {
        public boolean hasProfile() {
            return this.profile != null && this.profile.id() != null;
        }
    }

    private record CachedFailure(Result result, long expiresAt) {
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

        CachedFailure failure = NEGATIVE_CACHE.get(key);
        if (failure != null) {
            if (failure.expiresAt() > System.currentTimeMillis()) {
                DollDebug.msg("SERVER", "resolver.negativeCacheHit",
                        "requestedName=" + name + " status=" + failure.result().status());
                return CompletableFuture.completedFuture(failure.result());
            }
            NEGATIVE_CACHE.remove(key);
        }

        // 同服在线玩家：直接用服务器上已有的 GameProfile（正版服带 textures），不消耗 Mojang 配额
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                GameProfile profile = online.getGameProfile();
                if (profile != null && profile.id() != null) {
                    CACHE.put(key, profile);
                    DollDebug.log("SERVER", "resolver.online", profile, "requestedName=" + name);
                    return CompletableFuture.completedFuture(new Result(Status.SUCCESS_FULL, profile, "online"));
                }
            }
        }

        return CompletableFuture.supplyAsync(() -> resolveFromApi(server, name, key), EXECUTOR);
    }

    private static Result resolveFromApi(MinecraftServer server, String name, String key) {
        if (server == null) {
            return rememberFailure(key, new Result(Status.FAILED, null, "server-null"), FAILED_TTL_MS);
        }
        // 串行 + 节流：同一时刻只有一个真实 API 调用，且两次调用至少间隔 MIN_API_INTERVAL_MS
        synchronized (API_LOCK) {
            GameProfile byName;
            try {
                throttle();
                byName = server.services().profileResolver().fetchByName(name).orElse(null);
            } catch (Exception exception) {
                boolean rateLimited = isRateLimited(exception);
                DollDebug.msg("SERVER", "resolver.fetchByName.exception",
                        "requestedName=" + name + " rateLimited=" + rateLimited
                                + " error=" + exception.getClass().getSimpleName()
                                + " message=" + exception.getMessage());
                Status status = rateLimited ? Status.RATE_LIMITED : Status.FAILED;
                return rememberFailure(key, new Result(status, null,
                        "fetchByName:" + exception.getClass().getSimpleName()), FAILED_TTL_MS);
            }

            if (byName == null || byName.id() == null) {
                DollDebug.msg("SERVER", "resolver.notFound", "requestedName=" + name);
                return rememberFailure(key, new Result(Status.NOT_FOUND, null, "name-not-found"), NOT_FOUND_TTL_MS);
            }
            DollDebug.log("SERVER", "resolver.fetchByName", byName, "requestedName=" + name);

            GameProfile full = byName;
            try {
                throttle();
                var result = server.services().sessionService().fetchProfile(byName.id(), true);
                if (result != null && result.profile() != null) {
                    full = result.profile();
                }
            } catch (Exception exception) {
                // 只拿到 UUID 也能用（客户端按 UUID 解析皮肤），这里降级而不是整体失败
                DollDebug.msg("SERVER", "resolver.fetchProfile.exception",
                        "requestedName=" + name + " rateLimited=" + isRateLimited(exception)
                                + " error=" + exception.getClass().getSimpleName()
                                + " message=" + exception.getMessage());
            }

            boolean hasTextures = full.properties().containsKey("textures");
            if (hasTextures) {
                CACHE.put(key, full);
                NEGATIVE_CACHE.remove(key);
                DollDebug.log("SERVER", "resolver.final", full,
                        "requestedName=" + name + " status=SUCCESS_FULL cached=true");
                return new Result(Status.SUCCESS_FULL, full, "textures");
            }
            DollDebug.log("SERVER", "resolver.final", full,
                    "requestedName=" + name + " status=SUCCESS_UUID_ONLY cached=false");
            return new Result(Status.SUCCESS_UUID_ONLY, full, "no-textures");
        }
    }

    /**
     * 记录负缓存；失败/不存在/限流都只在 TTL 内生效，不写入成功缓存。
     */
    private static Result rememberFailure(String key, Result result, long ttlMillis) {
        NEGATIVE_CACHE.put(key, new CachedFailure(result, System.currentTimeMillis() + ttlMillis));
        return result;
    }

    /**
     * 与上次真实 API 调用至少间隔 {@link #MIN_API_INTERVAL_MS}（在 {@link #API_LOCK} 内调用，等于串行 + 间隔）。
     */
    private static void throttle() {
        long now = System.currentTimeMillis();
        long wait = MIN_API_INTERVAL_MS - (now - lastApiCallMillis);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        lastApiCallMillis = System.currentTimeMillis();
    }

    /**
     * 是否被 Mojang 限流：authlib 的 HTTP 异常带状态码，429 即限流；兜底再看异常链里的 "429" 文本。
     */
    private static boolean isRateLimited(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof MinecraftClientHttpException http && http.getStatus() == 429) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("429")) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
