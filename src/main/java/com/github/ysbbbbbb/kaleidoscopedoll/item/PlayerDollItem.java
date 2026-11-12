package com.github.ysbbbbbb.kaleidoscopedoll.item;

import com.github.ysbbbbbb.kaleidoscopedoll.KaleidoscopeDoll;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModDataComponent;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 玩家玩偶物品：名字由玩家档案决定；图标/手持/掉落物由 GeckoLib 渲染（带玩家皮肤）。
 */
public class PlayerDollItem extends BlockItem implements GeoItem {
    private static final Identifier ADVANCEMENT =
            Identifier.fromNamespaceAndPath(KaleidoscopeDoll.MOD_ID, "doll/player_doll_self");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    public final AtomicReference<Object> geoRenderProvider = new AtomicReference<>();

    public PlayerDollItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResolvableProfile profile = stack.get(ModDataComponent.DOLL_OWNER);
        if (profile != null) {
            String name = profile.name().orElse("");
            if (!name.isEmpty()) {
                return Component.translatable("item.kaleidoscope_doll.player_doll.owner", name);
            }
        }
        return super.getName(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if (!(entity instanceof ServerPlayer player) || level.getServer() == null) {
            return;
        }
        ResolvableProfile profile = stack.get(ModDataComponent.DOLL_OWNER);
        if (profile == null) {
            return;
        }
        var partial = profile.partialProfile();
        if (partial == null || partial.id() == null || !partial.id().equals(player.getUUID())) {
            return;
        }
        AdvancementHolder holder = level.getServer().getAdvancements().get(ADVANCEMENT);
        if (holder != null) {
            player.getAdvancements().award(holder, "impossible");
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void createGeoRenderer(Consumer consumer) {
        consumer.accept(this.geoRenderProvider.get());
    }

    @Override
    public Object getRenderProvider() {
        return this.geoRenderProvider.get();
    }
}
