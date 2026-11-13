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
    /**
     * 无档案时的兜底贴图（64×64 纯白玩偶）。
     * <p>
     * 注意这里是 <b>完整贴图路径</b>（带 {@code textures/} 前缀与 {@code .png} 后缀），不是模型空间 id：
     * GeckoLib 的 {@code GeoModel#getTextureResource} 最终会交给 {@code RenderTypes.entityCutoutNoCull(texture)}，
     * 该 identifier 由 TextureManager 直接按 {@code assets/<ns>/<path>} 读取。
     * 可对照 GeckoLib {@code DefaultedGeoModel#buildFormattedTexturePath} 拼出的就是 {@code textures/<subtype>/<name>.png}，
     * 以及原版 {@code EndCrystalRenderer} 用字面量 {@code "textures/entity/end_crystal/end_crystal.png"} 调同一个 RenderType。
     * 若改成 {@code kaleidoscope_doll:block/doll/player_doll}（模型空间写法），会解析到不存在的
     * {@code assets/kaleidoscope_doll/block/doll/player_doll} → 紫黑缺失贴图。
     * 模型 id（{@link #getModelResource}）才是模型空间写法，两者规则不同。
     */
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
