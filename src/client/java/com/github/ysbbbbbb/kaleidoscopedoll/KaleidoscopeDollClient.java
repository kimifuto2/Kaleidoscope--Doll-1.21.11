package com.github.ysbbbbbb.kaleidoscopedoll;

import com.github.ysbbbbbb.kaleidoscopedoll.client.gui.ComputerMenuScreen;
import com.github.ysbbbbbb.kaleidoscopedoll.event.ModRegisterEvent;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModBlocks;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModBlockEntities;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModEntities;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModMenuTypes;
import com.github.ysbbbbbb.kaleidoscopedoll.render.DollEntityRender;
import com.github.ysbbbbbb.kaleidoscopedoll.render.PlayerDollGeoModel;
import com.github.ysbbbbbb.kaleidoscopedoll.render.PlayerDollSkins;
import com.github.ysbbbbbb.kaleidoscopedoll.render.PlayerDollItemRenderer;
import com.github.ysbbbbbb.kaleidoscopedoll.render.PlayerDollTrinketRenderer;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollItem;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModItems;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import com.github.ysbbbbbb.kaleidoscopedoll.render.PlayerDollDataTickets;
import com.github.ysbbbbbb.kaleidoscopedoll.block.entity.PlayerDollBlockEntity;
import software.bernie.geckolib.event.block.CompileBlockRenderStateEvent;
import com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import com.github.ysbbbbbb.kaleidoscopedoll.network.ComputerStatusPayload;
import com.github.ysbbbbbb.kaleidoscopedoll.network.PlayerDollPreviewResponsePayload;
import com.github.ysbbbbbb.kaleidoscopedoll.client.gui.ComputerMenuScreen;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public class KaleidoscopeDollClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 1.21.11 起方块渲染层由代码注册（模型里的 render_type 字段已失效）
        BlockRenderLayerMap.putBlock(ModBlocks.DOLL_MACHINE, ChunkSectionLayer.CUTOUT);
        ModRegisterEvent.DOLL_BLOCKS.values().forEach(block -> BlockRenderLayerMap.putBlock(block, ChunkSectionLayer.CUTOUT));

        // 实体玩偶渲染器（render state 管线）
        // 玩家玩偶作为 Trinkets 头饰渲染（未装 Trinkets 时自动跳过）
        PlayerDollTrinketRenderer.register();

        // 物品栏 / 手持的 3D 玩偶（带玩家皮肤）
        if (ModItems.PLAYER_DOLL instanceof PlayerDollItem dollItem) {
            dollItem.geoRenderProvider.set(new GeoRenderProvider() {
                private final java.util.function.Supplier<GeoItemRenderer<PlayerDollItem>> renderer =
                        com.google.common.base.Suppliers.memoize(PlayerDollItemRenderer::new);

                @Override
                public GeoItemRenderer<?> getGeoItemRenderer() {
                    return this.renderer.get();
                }
            });
        }

        // 阶段 5：把玩偶档案写进渲染状态，GeoModel 不再依赖 BLOCKPOS 反查
        CompileBlockRenderStateEvent.EVENT.register(event -> {
            if (event.getAnimatable() instanceof PlayerDollBlockEntity doll) {
                event.addData(PlayerDollDataTickets.PROFILE, doll.getOwnerProfile());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ComputerStatusPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (Minecraft.getInstance().screen instanceof ComputerMenuScreen screen) {
                        screen.setStatus(payload.key(), payload.arg());
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(PlayerDollPreviewResponsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (Minecraft.getInstance().screen instanceof ComputerMenuScreen screen) {
                        var parsed = net.minecraft.world.item.component.ResolvableProfile.CODEC
                                .parse(net.minecraft.nbt.NbtOps.INSTANCE, payload.profileTag())
                                .result().orElse(null);
                        screen.setPreview(parsed, payload.name(), payload.found());
                    }
                }));



        // 电脑界面（MenuScreens.register 在 1.21.11 是 private，由 Fabric 的 transitive access widener 开放）
        MenuScreens.register(ModMenuTypes.COMPUTER, ComputerMenuScreen::new);
    }
}
