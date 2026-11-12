package com.github.ysbbbbbb.kaleidoscopedoll.init;


import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.item.crafting.DollEntityCraftingRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class ModRecipes {
    public static RecipeSerializer<DollEntityCraftingRecipe> DOLL_ENTITY_CRAFTING;

    public static void registerRecipe() {
        DOLL_ENTITY_CRAFTING = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "doll_entity_crafting"),
                new CustomRecipe.Serializer<>(DollEntityCraftingRecipe::new));
    }
}
