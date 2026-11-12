package com.github.ysbbbbbb.kaleidoscopedoll.init;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {
    public static SoundEvent DUCK_TOY = SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "block.duck_toy"), 16);

    public static void registerSounds() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "block.duck_toy"), DUCK_TOY);
    }
}
