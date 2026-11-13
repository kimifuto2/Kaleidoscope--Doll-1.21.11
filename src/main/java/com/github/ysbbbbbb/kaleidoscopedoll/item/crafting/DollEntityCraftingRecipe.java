package com.github.ysbbbbbb.kaleidoscopedoll.item.crafting;

import com.github.ysbbbbbb.kaleidoscopedoll.init.ModDataComponent;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModRecipes;
import com.github.ysbbbbbb.kaleidoscopedoll.item.DollEntityItem;
import com.github.ysbbbbbb.kaleidoscopedoll.item.DollItem;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollEntityItem;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 玩偶 + 粘液球 → 实体玩偶。
 * <ul>
 *     <li>普通 {@link DollItem} + 粘液球 → 方块实体玩偶（原有行为，保持不变）</li>
 *     <li>{@link PlayerDollItem} + 粘液球 → 玩家实体玩偶，并把 {@code doll_owner} 带到产物</li>
 * </ul>
 */
public class DollEntityCraftingRecipe extends CustomRecipe {
    public DollEntityCraftingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean hasDollItem = false;
        boolean hasPlayerDollItem = false;
        boolean hasSlimeBall = false;
        int itemCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                itemCount++;
                if (stack.getItem() instanceof PlayerDollItem) {
                    hasPlayerDollItem = true;
                } else if (stack.getItem() instanceof DollItem) {
                    hasDollItem = true;
                } else if (stack.is(Items.SLIME_BALL)) {
                    hasSlimeBall = true;
                } else {
                    return false;
                }
            }
        }

        if (itemCount != 2 || !hasSlimeBall) {
            return false;
        }
        // 只允许「一个玩偶 + 一个粘液球」，两种玩偶互斥
        return hasDollItem ^ hasPlayerDollItem;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof PlayerDollItem) {
                ResolvableProfile profile = stack.get(ModDataComponent.DOLL_OWNER);
                return PlayerDollEntityItem.createWithProfile(profile);
            }
        }

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof DollItem dollItem) {
                return DollEntityItem.createItemWithBlockState(dollItem.getBlock().defaultBlockState());
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipes.DOLL_ENTITY_CRAFTING;
    }
}
