package com.github.ysbbbbbb.kaleidoscopedoll.init;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.entity.DollEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    public static final ResourceKey<EntityType<?>> DOLL_ID = ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "doll"));

    public static final EntityType<DollEntity> DOLL =
            EntityType.Builder.<DollEntity>of(DollEntity::new, MobCategory.MISC)
                    .sized(0.75f, 0.75f)
                    .clientTrackingRange(10)
                    .build(DOLL_ID);


    public static void registerEntities() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, DOLL_ID, ModEntities.DOLL);
    }
}
