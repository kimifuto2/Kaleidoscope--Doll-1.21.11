package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * 客户端皮肤解析：优先交给原版 PlayerSkinRenderCache（完整档案、动态档案都试），
 * 解析失败/未就绪时才退回默认皮肤，并且不会永久锁死结果。
 */
public final class PlayerDollSkins {
    private static PlayerSkinRenderCache cache;

    private PlayerDollSkins() {
    }

    public static void init(PlayerSkinRenderCache skinRenderCache) {
        cache = skinRenderCache;
    }

    public static PlayerSkin get(ResolvableProfile profile) {
        if (profile == null || cache == null) {
            DollDebug.logThrottled("skins:none", 3000, "CLIENT", "skins.fallback", profile,
                    "reason=" + (profile == null ? "no-profile" : "cache-null"));
            return DefaultPlayerSkin.getDefaultSkin();
        }
        try {
            PlayerSkin skin = cache.getOrDefault(profile).playerSkin();
            if (skin != null) {
                DollDebug.logThrottled("skins:" + profile.name().orElse("?"), 3000,
                        "CLIENT", "skins.resolved", profile,
                        "texture=" + skin.body().texturePath() + " model=" + skin.model());
                return skin;
            }
        } catch (Throwable throwable) {
            DollDebug.msg("CLIENT", "skins.error", "error=" + throwable.getClass().getSimpleName()
                    + " message=" + throwable.getMessage());
        }
        DollDebug.logThrottled("skins:" + profile.name().orElse("?"), 3000, "CLIENT", "skins.fallback", profile,
                "reason=cache-miss");
        return DefaultPlayerSkin.getDefaultSkin();
    }
}
