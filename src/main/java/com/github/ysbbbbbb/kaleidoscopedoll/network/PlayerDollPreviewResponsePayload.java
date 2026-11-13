package com.github.ysbbbbbb.kaleidoscopedoll.network;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端：返回预览档案。
 * <p>
 * {@code status} 区分「查不到」与「查询失败」：{@code OK} / {@code NOT_FOUND} /
 * {@code RATE_LIMITED}（Mojang 429 限流）/ {@code ERROR}。档案用 NBT 传送，避免流编解码限制。
 */
public record PlayerDollPreviewResponsePayload(CompoundTag profileTag, String name, boolean found, String status)
        implements CustomPacketPayload {
    public static final String STATUS_OK = "OK";
    public static final String STATUS_NOT_FOUND = "NOT_FOUND";
    public static final String STATUS_RATE_LIMITED = "RATE_LIMITED";
    public static final String STATUS_ERROR = "ERROR";

    public static final CustomPacketPayload.Type<PlayerDollPreviewResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "player_doll_preview_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDollPreviewResponsePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, PlayerDollPreviewResponsePayload::profileTag,
                    ByteBufCodecs.STRING_UTF8, PlayerDollPreviewResponsePayload::name,
                    ByteBufCodecs.BOOL, PlayerDollPreviewResponsePayload::found,
                    ByteBufCodecs.STRING_UTF8, PlayerDollPreviewResponsePayload::status,
                    PlayerDollPreviewResponsePayload::new);

    public static PlayerDollPreviewResponsePayload notFound(String name) {
        return new PlayerDollPreviewResponsePayload(new CompoundTag(), name, false, STATUS_NOT_FOUND);
    }

    public static PlayerDollPreviewResponsePayload ofStatus(String name, String status) {
        return new PlayerDollPreviewResponsePayload(new CompoundTag(), name, false, status);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
