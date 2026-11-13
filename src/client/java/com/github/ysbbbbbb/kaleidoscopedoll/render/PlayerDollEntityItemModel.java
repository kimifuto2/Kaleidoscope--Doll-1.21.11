package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollEntityItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * 玩家实体玩偶物品的模型：和玩家玩偶物品一致（item 空间几何），皮肤从 {@code doll_owner} 组件解析。
 */
public class PlayerDollEntityItemModel extends GeoModel<PlayerDollEntityItem> {
    private static final Identifier STEVE = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "item/player_doll_steve");
    private static final Identifier ALEX = Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "item/player_doll_alex");

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        PlayerSkin skin = skin(state);
        return skin.model() == PlayerModelType.SLIM ? ALEX : STEVE;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        // 完整贴图路径（PlayerSkin#body().texturePath() 给的就是 textures/...png）
        return skin(state).body().texturePath();
    }

    @Override
    public Identifier getAnimationResource(PlayerDollEntityItem animatable) {
        return null;
    }

    private static PlayerSkin skin(GeoRenderState state) {
        ResolvableProfile profile = state == null ? null : state.getGeckolibData(PlayerDollDataTickets.PROFILE);
        return PlayerDollSkins.get(profile);
    }
}
