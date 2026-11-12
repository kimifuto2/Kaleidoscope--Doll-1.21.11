package com.github.ysbbbbbb.kaleidoscopedoll.network;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端在电脑界面输入 @+玩家ID 点选后发给服务端，请求生成该玩家的玩偶。
 */
public record PlayerDollCraftPayload(String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayerDollCraftPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "craft_player_doll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDollCraftPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PlayerDollCraftPayload::name, PlayerDollCraftPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
