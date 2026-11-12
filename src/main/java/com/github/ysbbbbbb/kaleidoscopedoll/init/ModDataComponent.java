package com.github.ysbbbbbb.kaleidoscopedoll.init;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

public class ModDataComponent {
    public static final DataComponentType<CompoundTag> TAG_DOLL_ENTITY = DataComponentType.<CompoundTag>builder()
            .persistent(CompoundTag.CODEC)
            .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
            .build();

    public static final DataComponentType<ResolvableProfile> DOLL_OWNER = DataComponentType.<ResolvableProfile>builder()
            .persistent(ResolvableProfile.CODEC)
            .networkSynchronized(ResolvableProfile.STREAM_CODEC)
            .build();

    public static void registerDataComponent() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "doll_entity"), TAG_DOLL_ENTITY);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "doll_owner"), DOLL_OWNER);
    }
}
