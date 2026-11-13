package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.entity.PlayerDollEntity;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * 玩家实体玩偶的基岩模型：复用方块版玩家的坐姿模型（block 空间 0..16），
 * 贴图用玩家皮肤（GeckoLib 贴图 id 是完整贴图路径 {@code ns:textures/...png}）。
 */
public class PlayerDollEntityModel extends GeoModel<PlayerDollEntity> {
    private static final Identifier STEVE = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "block/player_doll_steve");
    private static final Identifier ALEX = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "block/player_doll_alex");

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        PlayerSkin skin = skin(state);
        return skin.model() == PlayerModelType.SLIM ? ALEX : STEVE;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        // 必须是完整贴图路径（PlayerSkin#body().texturePath() 给的就是 textures/...png）
        return skin(state).body().texturePath();
    }

    @Override
    public Identifier getAnimationResource(PlayerDollEntity animatable) {
        return null;
    }

    private static PlayerSkin skin(GeoRenderState state) {
        if (state == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }
        ResolvableProfile profile = state.getGeckolibData(PlayerDollDataTickets.PROFILE);
        return PlayerDollSkins.get(profile);
    }
}
