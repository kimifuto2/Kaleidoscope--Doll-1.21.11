package com.github.ysbbbbbb.kaleidoscopedoll.init;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.item.DollEntityItem;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollEntityItem;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollItem;
import com.github.ysbbbbbb.kaleidoscopedoll.item.ModBlockItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final ResourceKey<Item> DOLL_MACHINE_ID = id("doll_machine");
    public static final ResourceKey<Item> PURPLE_DOLL_GIFT_BOX_ID = id("purple_doll_gift_box");
    public static final ResourceKey<Item> GREEN_DOLL_GIFT_BOX_ID = id("green_doll_gift_box");
    public static final ResourceKey<Item> YELLOW_DOLL_GIFT_BOX_ID = id("yellow_doll_gift_box");
    public static final ResourceKey<Item> DOLL_ICON_ID = id("doll_icon");
    public static final ResourceKey<Item> DOLL_ENTITY_ITEM_ID = id("doll_entity_item");
    public static final ResourceKey<Item> COMPUTER_ID = id("computer");
    public static final ResourceKey<Item> PLAYER_DOLL_ID = id("player_doll");

    public static final Item DOLL_MACHINE = new ModBlockItem(ModBlocks.DOLL_MACHINE,
            new Item.Properties().setId(DOLL_MACHINE_ID).useBlockDescriptionPrefix(), "tooltip.kaleidoscope_doll.doll_machine");
    public static final Item PURPLE_DOLL_GIFT_BOX = new ModBlockItem(ModBlocks.PURPLE_DOLL_GIFT_BOX,
            new Item.Properties().setId(PURPLE_DOLL_GIFT_BOX_ID).useBlockDescriptionPrefix(), "tooltip.kaleidoscope_doll.doll_gift_box");
    public static final Item GREEN_DOLL_GIFT_BOX = new ModBlockItem(ModBlocks.GREEN_DOLL_GIFT_BOX,
            new Item.Properties().setId(GREEN_DOLL_GIFT_BOX_ID).useBlockDescriptionPrefix(), "tooltip.kaleidoscope_doll.doll_gift_box");
    public static final Item YELLOW_DOLL_GIFT_BOX = new ModBlockItem(ModBlocks.YELLOW_DOLL_GIFT_BOX,
            new Item.Properties().setId(YELLOW_DOLL_GIFT_BOX_ID).useBlockDescriptionPrefix(), "tooltip.kaleidoscope_doll.doll_gift_box");
    public static final Item DOLL_ICON = new Item(new Item.Properties().setId(DOLL_ICON_ID));
    public static final Item DOLL_ENTITY_ITEM = new DollEntityItem(
            new Item.Properties().setId(DOLL_ENTITY_ITEM_ID).useItemDescriptionPrefix());
    public static final Item COMPUTER = new ModBlockItem(ModBlocks.COMPUTER,
            new Item.Properties().setId(COMPUTER_ID).useBlockDescriptionPrefix(), "tooltip.kaleidoscope_doll.computer");
    public static final Item PLAYER_DOLL = new PlayerDollItem(ModBlocks.PLAYER_DOLL,
            new Item.Properties().setId(PLAYER_DOLL_ID).stacksTo(16).useBlockDescriptionPrefix().equippable(net.minecraft.world.entity.EquipmentSlot.HEAD));

    public static final ResourceKey<Item> PLAYER_DOLL_ENTITY_ID = id("player_doll_entity");
    // 堆叠上限用默认值（64）：同档案的物品栈靠 ResolvableProfile 的值相等合并，不同档案不合并
    public static final Item PLAYER_DOLL_ENTITY = new PlayerDollEntityItem(
            new Item.Properties().setId(PLAYER_DOLL_ENTITY_ID).useItemDescriptionPrefix());

    public static void registerItems() {
        Registry.register(BuiltInRegistries.ITEM, DOLL_MACHINE_ID, DOLL_MACHINE);
        Registry.register(BuiltInRegistries.ITEM, PURPLE_DOLL_GIFT_BOX_ID, PURPLE_DOLL_GIFT_BOX);
        Registry.register(BuiltInRegistries.ITEM, GREEN_DOLL_GIFT_BOX_ID, GREEN_DOLL_GIFT_BOX);
        Registry.register(BuiltInRegistries.ITEM, YELLOW_DOLL_GIFT_BOX_ID, YELLOW_DOLL_GIFT_BOX);
        Registry.register(BuiltInRegistries.ITEM, DOLL_ICON_ID, DOLL_ICON);
        Registry.register(BuiltInRegistries.ITEM, DOLL_ENTITY_ITEM_ID, DOLL_ENTITY_ITEM);
        Registry.register(BuiltInRegistries.ITEM, COMPUTER_ID, COMPUTER);
        Registry.register(BuiltInRegistries.ITEM, PLAYER_DOLL_ID, PLAYER_DOLL);
        Registry.register(BuiltInRegistries.ITEM, PLAYER_DOLL_ENTITY_ID, PLAYER_DOLL_ENTITY);
    }

    private static ResourceKey<Item> id(String path) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, path));
    }
}
