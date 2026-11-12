package com.github.ysbbbbbb.kaleidoscopedoll.init;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.inventory.ComputerMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

public class ModMenuTypes {
    public static final ExtendedScreenHandlerType<ComputerMenu, Unit> COMPUTER =
            new ExtendedScreenHandlerType<>(
                    (containerId, inventory, data) -> new ComputerMenu(containerId, inventory),
                    StreamCodec.<RegistryFriendlyByteBuf, Unit>unit(Unit.INSTANCE));

    public static void registerMenuTypes() {
        Registry.register(BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "computer"), COMPUTER);
    }
}
