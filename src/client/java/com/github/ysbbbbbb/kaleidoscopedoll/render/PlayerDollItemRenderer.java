package com.github.ysbbbbbb.kaleidoscopedoll.render;

import com.github.ysbbbbbb.kaleidoscopedoll.init.ModDataComponent;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * 物品栏/手持的 3D 玩偶：block 空间模型 + 原版方块物品的 -0.5 格平移，
 * 使其位置/大小/旋转与普通方块玩偶一致。
 */
public class PlayerDollItemRenderer extends GeoItemRenderer<PlayerDollItem> {
    public PlayerDollItemRenderer() {
        super(new PlayerDollItemModel());
        this.withScale(1.0F);
    }

    @Override
    public void preRenderPass(RenderPassInfo<GeoRenderState> info, net.minecraft.client.renderer.SubmitNodeCollector collector) {
        super.preRenderPass(info, collector);
        var context = info.renderState().getGeckolibData(software.bernie.geckolib.constant.DataTickets.ITEM_RENDER_PERSPECTIVE);
        if (context == net.minecraft.world.item.ItemDisplayContext.GROUND
                || context == net.minecraft.world.item.ItemDisplayContext.FIXED) {
            info.poseStack().translate(0.0F, -0.5F, 0.0F);
        }
    }

    @Override
    public void addRenderData(PlayerDollItem animatable, GeoItemRenderer.RenderData renderData,
                              GeoRenderState renderState, float partialTick) {
        super.addRenderData(animatable, renderData, renderState, partialTick);
        ItemStack stack = renderData.itemStack();
        ResolvableProfile profile = stack.get(ModDataComponent.DOLL_OWNER);
        if (profile != null) {
            renderState.addGeckolibData(PlayerDollDataTickets.PROFILE, profile);
        }
    }
}
