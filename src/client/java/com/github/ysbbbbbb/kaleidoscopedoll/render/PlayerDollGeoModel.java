package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.block.entity.PlayerDollBlockEntity;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * 玩家玩偶的基岩模型：宽/细两套，贴图用玩家皮肤。
 * 档案由 CompileBlockRenderStateEvent 写入渲染状态（见 KaleidoscopeDollClient），不再依赖 BLOCKPOS 反查。
 */
public class PlayerDollGeoModel extends GeoModel<PlayerDollBlockEntity> {
    private static final Identifier STEVE = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "block/player_doll_steve");
    private static final Identifier ALEX = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "block/player_doll_alex");

    public PlayerDollGeoModel() {
        com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug.msg("CLIENT", "geoModel.constructed", "class=PlayerDollGeoModel");
    }

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        PlayerSkin skin = skin(state);
        return skin.model() == PlayerModelType.SLIM ? ALEX : STEVE;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return skin(state).body().texturePath();
    }

    @Override
    public Identifier getAnimationResource(PlayerDollBlockEntity animatable) {
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
