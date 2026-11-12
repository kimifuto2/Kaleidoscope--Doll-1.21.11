package com.github.ysbbbbbb.kaleidoscopedoll.inventory;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug;
import com.github.ysbbbbbb.kaleidoscopedoll.event.ModRegisterEvent;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModMenuTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.github.ysbbbbbb.kaleidoscopedoll.network.ComputerStatusPayload;
import org.jetbrains.annotations.Nullable;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModDataComponent;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopedoll.server.PlayerProfileResolver;

/**
 * 玩偶电脑：输入羊毛，在界面里选择玩偶，输出槽生成对应玩偶。
 * 取出输出槽会消耗 1 个羊毛。
 */
public class ComputerMenu extends AbstractContainerMenu {
    /**
     * 可投入的物品：任意羊毛（由数据包标签 #minecraft:wool 提供）
     */
    public static final TagKey<Item> COMPUTER_TOKENS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "computer_tokens"));

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    /** 制作一个玩偶所需 tick（3 秒） */
    public static final int CRAFT_TIME = 60;

    private final Player player;
    private int craftTime;
    private ItemStack pendingDoll = ItemStack.EMPTY;
    @Nullable
    private ResolvableProfile pendingProfile;
    private String pendingName = "";
    private final ContainerData craftData = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? craftTime : CRAFT_TIME;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                craftTime = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    private final Container input = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(COMPUTER_TOKENS);
        }
    };

    private final Container output = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };

    public ComputerMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.COMPUTER, containerId);
        this.player = playerInventory.player;
        this.addDataSlots(this.craftData);

        // 输入槽：只能放羊毛
        this.addSlot(new Slot(this.input, 0, 108, 98) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(COMPUTER_TOKENS);
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
            }
        });

        // 输出槽：只能取不能放
        this.addSlot(new Slot(this.output, 0, 151, 98) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
                // 羊毛在制作完成时已扣除
            }
        });

        // 玩家快捷栏
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 184));
        }
        // 玩家背包
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 126 + i * 18));
            }
        }
    }

    /**
     * 客户端点击玩偶按钮后由服务端调用：校验输入并生成输出。
     */
    public boolean clickDollButton(ItemStack doll) {
        if (doll.isEmpty() || !this.input.getItem(0).is(COMPUTER_TOKENS)) {
            this.sendStatus("message.kaleidoscope_doll.computer.no_wool", "");
            return false;
        }
        // 服务端再校验一次，防止客户端伪造：作者/赞助玩偶，或带主人档案的玩家玩偶
        boolean hasOwner = doll.get(ModDataComponent.DOLL_OWNER) != null;
        if (!isPlayerDoll(doll.getItem()) && !hasOwner && !doll.is(ModItems.PLAYER_DOLL)) {
            return false;
        }
        if (this.isCrafting()) {
            this.sendStatus("message.kaleidoscope_doll.computer.busy", "");
            return false;
        }
        ResolvableProfile profile = doll.get(ModDataComponent.DOLL_OWNER);
        if (profile != null) {
            this.pendingDoll = ItemStack.EMPTY;
            this.pendingProfile = profile;
            this.pendingName = profile.name().orElse("?");
        } else {
            this.pendingDoll = new ItemStack(doll.getItem());
            this.pendingProfile = null;
            this.pendingName = doll.getHoverName().getString();
        }
        this.craftTime = 1;
        this.sendStatus("message.kaleidoscope_doll.computer.crafting", "");
        this.broadcastChanges();
        return true;
    }

    private boolean isCrafting() {
        return this.craftTime > 0;
    }

    @Override
    public void broadcastChanges() {
        if (this.player.level() instanceof ServerLevel) {
            this.tickCrafting();
        }
        super.broadcastChanges();
    }

    private void tickCrafting() {
        if (!this.isCrafting()) {
            return;
        }
        if (!this.input.getItem(0).is(COMPUTER_TOKENS)) {
            this.resetCraft();
            this.sendStatus("message.kaleidoscope_doll.computer.no_wool", "");
            return;
        }
        if (this.craftTime < CRAFT_TIME) {
            this.craftTime++;
            return;
        }
        ItemStack outputStack;
        if (this.pendingProfile != null) {
            outputStack = new ItemStack(ModItems.PLAYER_DOLL);
            outputStack.set(ModDataComponent.DOLL_OWNER, this.pendingProfile);
            outputStack.set(DataComponents.PROFILE, this.pendingProfile);
        } else if (!this.pendingDoll.isEmpty()) {
            outputStack = new ItemStack(this.pendingDoll.getItem());
        } else {
            this.resetCraft();
            return;
        }
        this.output.setItem(0, outputStack);
        this.input.removeItem(0, 1);
        this.input.setChanged();
        this.resetCraft();
        this.sendStatus("message.kaleidoscope_doll.computer.crafted", "");
    }

    private void resetCraft() {
        this.craftTime = 0;
        this.pendingDoll = ItemStack.EMPTY;
        this.pendingProfile = null;
        this.pendingName = "";
    }

    private void sendStatus(String key, String arg) {
        if (this.player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new ComputerStatusPayload(key, arg));
        }
    }

    public int getCraftProgress() {
        return this.craftData.get(0);
    }

    public int getCraftTotal() {
        return this.craftData.get(1);
    }

    /**
     * 是否是「玩家玩偶」（作者/赞助玩偶），与上游的 PLAYER_DOLLS 标签一致。
     */
    public static boolean isPlayerDoll(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && ModRegisterEvent.SPECIAL_TOOLTIPS.containsKey(id);
    }

    /**
     * 服务端处理「@+玩家ID」请求：消耗输入槽里的羊毛，把输出槽换成该玩家的皮肤玩偶。
     */
    public boolean craftPlayerDoll(ServerPlayer player, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (!PlayerProfileResolver.isValidName(trimmed)) {
            this.sendStatus("message.kaleidoscope_doll.computer.invalid_name", "");
            return false;
        }
        if (!this.input.getItem(0).is(COMPUTER_TOKENS)) {
            this.sendStatus("message.kaleidoscope_doll.computer.no_wool", "");
            return false;
        }
        var server = player.level().getServer();
        PlayerProfileResolver.resolve(server, trimmed).whenComplete((result, throwable) -> {
            if (throwable != null || result == null) {
                player.sendSystemMessage(Component.translatable("message.kaleidoscope_doll.computer.resolve_failed"));
                return;
            }
            if (result.status() == PlayerProfileResolver.Status.NOT_FOUND) {
                player.sendSystemMessage(Component.translatable(
                        "message.kaleidoscope_doll.computer.player_not_found", trimmed));
                return;
            }
            if (!result.hasProfile()) {
                player.sendSystemMessage(Component.translatable("message.kaleidoscope_doll.computer.resolve_failed"));
                return;
            }
            if (server == null) {
                return;
            }
            server.execute(() -> {
                if (player.containerMenu != this) {
                    return;
                }
                if (!this.input.getItem(0).is(COMPUTER_TOKENS)) {
                    this.sendStatus("message.kaleidoscope_doll.computer.no_wool", "");
                    return;
                }
                if (this.isCrafting()) {
                    this.sendStatus("message.kaleidoscope_doll.computer.busy", "");
                    return;
                }
                this.pendingDoll = ItemStack.EMPTY;
                this.pendingProfile = ResolvableProfile.createResolved(result.profile());
                this.pendingName = String.valueOf(result.profile().name());
                this.craftTime = 1;
                this.sendStatus("message.kaleidoscope_doll.computer.crafting", "");
                this.broadcastChanges();
            });
        });
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotItem = slot.getItem();
            result = slotItem.copy();
            if (index < 2) {
                if (!this.moveItemStackTo(slotItem, 2, this.slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
                // 移动的是输出槽，成功转移后输入槽扣除一个
                if (index == 1) {
                    ItemStack inputStack = this.input.getItem(0);
                    if (!inputStack.isEmpty()) {
                        inputStack.shrink(1);
                        this.input.setChanged();
                    }
                }
            } else if (!this.moveItemStackTo(slotItem, 0, 2, true)) {
                return ItemStack.EMPTY;
            }

            if (slotItem.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        ItemStack remaining = this.input.getItem(0);
        if (!remaining.isEmpty()) {
            if (player instanceof ServerPlayer) {
                player.getInventory().placeItemBackInInventory(remaining);
            }
            this.input.setItem(0, ItemStack.EMPTY);
        }
    }
}
