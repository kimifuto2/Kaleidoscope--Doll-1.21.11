package com.github.ysbbbbbb.kaleidoscopedoll.network;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端：返回预览档案（found=false 时表示找不到玩家）。档案用 NBT 传送，避免流编解码限制。 */
public record PlayerDollPreviewResponsePayload(CompoundTag profileTag, String name, boolean found)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayerDollPreviewResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "player_doll_preview_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDollPreviewResponsePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, PlayerDollPreviewResponsePayload::profileTag,
                    ByteBufCodecs.STRING_UTF8, PlayerDollPreviewResponsePayload::name,
                    ByteBufCodecs.BOOL, PlayerDollPreviewResponsePayload::found,
                    PlayerDollPreviewResponsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
