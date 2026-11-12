package com.github.ysbbbbbb.kaleidoscopedoll.network;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：把制作状态/提示显示在电脑界面内部。
 */
public record ComputerStatusPayload(String key, String arg) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ComputerStatusPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "computer_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ComputerStatusPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ComputerStatusPayload::key,
                    ByteBufCodecs.STRING_UTF8, ComputerStatusPayload::arg, ComputerStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
