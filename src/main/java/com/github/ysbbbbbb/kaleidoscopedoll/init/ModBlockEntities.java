package com.github.ysbbbbbb.kaleidoscopedoll.init;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.block.entity.PlayerDollBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {
    public static final BlockEntityType<PlayerDollBlockEntity> PLAYER_DOLL =
            FabricBlockEntityTypeBuilder.create(PlayerDollBlockEntity::new, ModBlocks.PLAYER_DOLL).build();

    public static void registerBlockEntities() {
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "player_doll"), PLAYER_DOLL);
    }
}
