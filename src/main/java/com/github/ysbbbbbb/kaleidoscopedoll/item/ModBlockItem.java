package com.github.ysbbbbbb.kaleidoscopedoll.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * 1.21.11 起 Block 不再提供 appendHoverText 钩子，方块物品的 tooltip 需要由 BlockItem 自己提供。
 */
public class ModBlockItem extends BlockItem {
    private final String tooltipKey;

    public ModBlockItem(Block block, Properties properties, String tooltipKey) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable(this.tooltipKey).withStyle(ChatFormatting.DARK_GRAY));
    }
}
