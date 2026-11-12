package com.github.ysbbbbbb.kaleidoscopedoll.network;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端在电脑界面里点选玩偶时发给服务端的包。
 */
public record ComputerDollClickPayload(ItemStack doll) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ComputerDollClickPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "computer_doll_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ComputerDollClickPayload> STREAM_CODEC =
            StreamCodec.composite(ItemStack.STREAM_CODEC, ComputerDollClickPayload::doll, ComputerDollClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
