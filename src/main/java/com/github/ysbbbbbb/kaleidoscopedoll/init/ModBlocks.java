package com.github.ysbbbbbb.kaleidoscopedoll.init;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.block.ComputerBlock;
import com.github.ysbbbbbb.kaleidoscopedoll.block.PlayerDollBlock;
import com.github.ysbbbbbb.kaleidoscopedoll.block.DollGiftBoxBlock;
import com.github.ysbbbbbb.kaleidoscopedoll.block.DollMachineBlock;
import com.github.ysbbbbbb.kaleidoscopedoll.event.ModRegisterEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
    public static final ResourceKey<Block> DOLL_MACHINE_ID = id("doll_machine");
    public static final ResourceKey<Block> PURPLE_DOLL_GIFT_BOX_ID = id("purple_doll_gift_box");
    public static final ResourceKey<Block> GREEN_DOLL_GIFT_BOX_ID = id("green_doll_gift_box");
    public static final ResourceKey<Block> YELLOW_DOLL_GIFT_BOX_ID = id("yellow_doll_gift_box");
    public static final ResourceKey<Block> COMPUTER_ID = id("computer");
    public static final ResourceKey<Block> PLAYER_DOLL_ID = id("player_doll");

    public static final Block DOLL_MACHINE = new DollMachineBlock(BlockBehaviour.Properties.of().setId(DOLL_MACHINE_ID));
    public static final Block PURPLE_DOLL_GIFT_BOX = new DollGiftBoxBlock(BlockBehaviour.Properties.of().setId(PURPLE_DOLL_GIFT_BOX_ID));
    public static final Block GREEN_DOLL_GIFT_BOX = new DollGiftBoxBlock(BlockBehaviour.Properties.of().setId(GREEN_DOLL_GIFT_BOX_ID));
    public static final Block YELLOW_DOLL_GIFT_BOX = new DollGiftBoxBlock(BlockBehaviour.Properties.of().setId(YELLOW_DOLL_GIFT_BOX_ID));
    public static final Block COMPUTER = new ComputerBlock(BlockBehaviour.Properties.of().setId(COMPUTER_ID));
    public static final Block PLAYER_DOLL = new PlayerDollBlock(BlockBehaviour.Properties.of().setId(PLAYER_DOLL_ID));

    public static void registerBlocks() {
        Registry.register(BuiltInRegistries.BLOCK, DOLL_MACHINE_ID, DOLL_MACHINE);
        Registry.register(BuiltInRegistries.BLOCK, PURPLE_DOLL_GIFT_BOX_ID, PURPLE_DOLL_GIFT_BOX);
        Registry.register(BuiltInRegistries.BLOCK, GREEN_DOLL_GIFT_BOX_ID, GREEN_DOLL_GIFT_BOX);
        Registry.register(BuiltInRegistries.BLOCK, YELLOW_DOLL_GIFT_BOX_ID, YELLOW_DOLL_GIFT_BOX);
        Registry.register(BuiltInRegistries.BLOCK, COMPUTER_ID, COMPUTER);
        Registry.register(BuiltInRegistries.BLOCK, PLAYER_DOLL_ID, PLAYER_DOLL);
        ModRegisterEvent.registerBlocks();
    }

    private static ResourceKey<Block> id(String path) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, path));
    }
}
