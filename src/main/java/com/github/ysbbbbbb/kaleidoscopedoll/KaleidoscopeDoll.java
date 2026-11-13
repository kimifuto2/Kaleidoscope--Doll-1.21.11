package com.github.ysbbbbbb.kaleidoscopedoll;

import com.github.ysbbbbbb.kaleidoscopedoll.config.GeneralConfig;
import com.github.ysbbbbbb.kaleidoscopedoll.event.DollClickEntityEvent;
import com.github.ysbbbbbb.kaleidoscopedoll.event.PhantomSpawnEvent;
import com.github.ysbbbbbb.kaleidoscopedoll.init.*;
import com.github.ysbbbbbb.kaleidoscopedoll.inventory.ComputerMenu;
import com.github.ysbbbbbb.kaleidoscopedoll.network.ComputerDollClickPayload;
import com.github.ysbbbbbb.kaleidoscopedoll.network.PlayerDollCraftPayload;
import com.github.ysbbbbbb.kaleidoscopedoll.network.ComputerStatusPayload;
import com.github.ysbbbbbb.kaleidoscopedoll.network.PlayerDollPreviewPayload;
import com.github.ysbbbbbb.kaleidoscopedoll.network.PlayerDollPreviewResponsePayload;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.nbt.CompoundTag;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class KaleidoscopeDoll implements ModInitializer {
    public static final String MOD_ID = "kaleidoscope_doll";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        GeneralConfig.load();
        ModBlocks.registerBlocks();
        ModItems.registerItems();
        ModCreativeTabs.registerTabs();
        ModSounds.registerSounds();
        ModEntities.registerEntities();
        ModRecipes.registerRecipe();
        ModDataComponent.registerDataComponent();
        ModMenuTypes.registerMenuTypes();
        ModBlockEntities.registerBlockEntities();

        DollClickEntityEvent.register();
        PhantomSpawnEvent.register();

        // 电脑界面：羊毛换玩偶
        PayloadTypeRegistry.playC2S().register(ComputerDollClickPayload.TYPE, ComputerDollClickPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ComputerDollClickPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.containerMenu instanceof ComputerMenu computerMenu && !player.isSpectator()) {
                computerMenu.clickDollButton(payload.doll());
            }
        });

        PayloadTypeRegistry.playS2C().register(ComputerStatusPayload.TYPE, ComputerStatusPayload.STREAM_CODEC);

        PayloadTypeRegistry.playC2S().register(PlayerDollPreviewPayload.TYPE, PlayerDollPreviewPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlayerDollPreviewPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var server = player.level().getServer();
            if (server == null) {
                return;
            }
            // 服务端兜底校验：名字不合法时根本不发请求（客户端已先过滤，这里防止伪造包消耗配额）
            if (!com.github.ysbbbbbb.kaleidoscopedoll.server.PlayerProfileResolver.isValidName(payload.name())) {
                ServerPlayNetworking.send(player, PlayerDollPreviewResponsePayload.notFound(payload.name()));
                return;
            }
            com.github.ysbbbbbb.kaleidoscopedoll.server.PlayerProfileResolver.resolve(server, payload.name())
                    .whenComplete((result, throwable) -> server.execute(() -> {
                        if (throwable != null || result == null) {
                            ServerPlayNetworking.send(player, PlayerDollPreviewResponsePayload.ofStatus(
                                    payload.name(), PlayerDollPreviewResponsePayload.STATUS_ERROR));
                            return;
                        }
                        if (!result.hasProfile()) {
                            // 把「查不到」「被限流」「其它失败」分开回传，GUI 才能给出准确提示
                            String status = switch (result.status()) {
                                case NOT_FOUND -> PlayerDollPreviewResponsePayload.STATUS_NOT_FOUND;
                                case RATE_LIMITED -> PlayerDollPreviewResponsePayload.STATUS_RATE_LIMITED;
                                case FAILED -> PlayerDollPreviewResponsePayload.STATUS_ERROR;
                                default -> PlayerDollPreviewResponsePayload.STATUS_NOT_FOUND;
                            };
                            ServerPlayNetworking.send(player, PlayerDollPreviewResponsePayload.ofStatus(payload.name(), status));
                            return;
                        }
                        var encoded = ResolvableProfile.CODEC.encodeStart(
                                net.minecraft.nbt.NbtOps.INSTANCE,
                                ResolvableProfile.createResolved(result.profile()));
                        net.minecraft.nbt.Tag rawTag = encoded.result().orElseGet(CompoundTag::new);
                        CompoundTag tag = rawTag instanceof CompoundTag compoundTag ? compoundTag : new CompoundTag();
                        ServerPlayNetworking.send(player, new PlayerDollPreviewResponsePayload(
                                tag, payload.name(), true, PlayerDollPreviewResponsePayload.STATUS_OK));
                    }));
        });

        PayloadTypeRegistry.playS2C().register(PlayerDollPreviewResponsePayload.TYPE, PlayerDollPreviewResponsePayload.STREAM_CODEC);

        // 电脑界面：@+玩家ID 生成玩家皮肤玩偶
        PayloadTypeRegistry.playC2S().register(PlayerDollCraftPayload.TYPE, PlayerDollCraftPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlayerDollCraftPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.containerMenu instanceof ComputerMenu computerMenu && !player.isSpectator()) {
                computerMenu.craftPlayerDoll(player, payload.name());
            }
        });
    }
}
