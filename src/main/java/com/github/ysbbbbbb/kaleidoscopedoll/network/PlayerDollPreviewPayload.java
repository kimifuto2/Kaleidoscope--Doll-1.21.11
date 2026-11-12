package com.github.ysbbbbbb.kaleidoscopedoll.network;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 客户端：输入 @+玩家名 时请求预览档案。 */
public record PlayerDollPreviewPayload(String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayerDollPreviewPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "player_doll_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDollPreviewPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PlayerDollPreviewPayload::name, PlayerDollPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
