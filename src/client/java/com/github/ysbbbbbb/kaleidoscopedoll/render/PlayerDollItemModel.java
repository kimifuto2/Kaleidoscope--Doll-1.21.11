package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** 玩家玩偶物品模型：block 空间几何（0..16），和普通方块玩偶同一坐标空间。 */
public class PlayerDollItemModel extends GeoModel<PlayerDollItem> {
    private static final Identifier STEVE = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "item/player_doll_steve");
    private static final Identifier ALEX = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "item/player_doll_alex");
    private static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath(
            KaleidoscopeDoll.MOD_ID, "textures/block/doll/player_doll.png");

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        PlayerSkin skin = skin(state);
        return skin != null && skin.model() == PlayerModelType.SLIM ? ALEX : STEVE;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        PlayerSkin skin = skin(state);
        return skin == null ? WHITE_TEXTURE : skin.body().texturePath();
    }

    @Override
    public Identifier getAnimationResource(PlayerDollItem animatable) {
        return null;
    }

    private static PlayerSkin skin(GeoRenderState state) {
        if (state == null) {
            return null;
        }
        ResolvableProfile profile = state.getGeckolibData(PlayerDollDataTickets.PROFILE);
        return profile == null ? null : PlayerDollSkins.get(profile);
    }
}
