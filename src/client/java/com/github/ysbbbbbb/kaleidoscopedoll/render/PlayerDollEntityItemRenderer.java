package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollEntityItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.component.ResolvableProfile;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * 玩家实体玩偶物品的 3D 图标/手持渲染：和 {@link PlayerDollItemRenderer} 一致，
 * 皮肤从物品的 {@code kaleidoscope_doll:doll_owner} 组件解析。
 */
public class PlayerDollEntityItemRenderer extends GeoItemRenderer<PlayerDollEntityItem> {
    public PlayerDollEntityItemRenderer() {
        super(new PlayerDollEntityItemModel());
        this.withScale(1.0F);
    }

    @Override
    public void preRenderPass(RenderPassInfo<GeoRenderState> info, SubmitNodeCollector collector) {
        super.preRenderPass(info, collector);
        var context = info.renderState().getGeckolibData(software.bernie.geckolib.constant.DataTickets.ITEM_RENDER_PERSPECTIVE);
        if (context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED) {
            info.poseStack().translate(0.0F, -0.5F, 0.0F);
        }
    }

    @Override
    public void addRenderData(PlayerDollEntityItem animatable, GeoItemRenderer.RenderData renderData,
                              GeoRenderState renderState, float partialTick) {
        super.addRenderData(animatable, renderData, renderState, partialTick);
        ResolvableProfile profile = PlayerDollEntityItem.getProfile(renderData.itemStack());
        if (profile != null) {
            renderState.addGeckolibData(PlayerDollDataTickets.PROFILE, profile);
        }
    }
}
