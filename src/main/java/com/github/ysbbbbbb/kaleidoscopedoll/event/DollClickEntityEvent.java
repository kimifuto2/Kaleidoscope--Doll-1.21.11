package com.github.ysbbbbbb.kaleidoscopedoll.event;

import com.github.ysbbbbbb.kaleidoscopedoll.config.GeneralConfig;
import com.github.ysbbbbbb.kaleidoscopedoll.entity.DollEntity;
import com.github.ysbbbbbb.kaleidoscopedoll.entity.PlayerDollEntity;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModEntities;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopedoll.item.DollEntityItem;
import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollEntityItem;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 手持实体玩偶右键可骑乘实体：把玩偶放到目标实体身上（骑乘层数上限 5）。
 * 行为对照上游 {@code DollClickEntityEvent}，同时支持普通实体玩偶与玩家实体玩偶。
 */
public class DollClickEntityEvent {
    private static final int MAX_RIDING_LAYERS = 5;

    public static void register() {
        UseEntityCallback.EVENT.register(DollClickEntityEvent::onUseEntity);
    }

    private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
        ItemStack handItem = player.getItemInHand(hand);

        if (!GeneralConfig.DOLL_CAN_MOUNT_ENTITIES.get()) {
            return InteractionResult.PASS;
        }

        DollEntity dollEntity = createDollForMount(level, handItem);
        if (dollEntity == null) {
            return InteractionResult.PASS;
        }

        // 检查骑乘层数
        if (getRidingLayers(entity) >= MAX_RIDING_LAYERS) {
            return InteractionResult.PASS;
        }

        dollEntity.setPos(entity.getX(), entity.getY() + 1.5, entity.getZ());
        dollEntity.setYRot(player.getYRot() - 180);

        if (dollEntity.startRiding(entity)) {
            if (!level.isClientSide()) {
                level.addFreshEntity(dollEntity);
            }
            entity.playSound(SoundEvents.WOOL_PLACE, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                handItem.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Nullable
    private static DollEntity createDollForMount(Level level, ItemStack handItem) {
        if (handItem.is(ModItems.DOLL_ENTITY_ITEM)) {
            return DollEntityItem.getDollEntity(level, handItem);
        }
        if (handItem.is(ModItems.PLAYER_DOLL_ENTITY)) {
            PlayerDollEntity playerDoll = new PlayerDollEntity(ModEntities.PLAYER_DOLL, level);
            playerDoll.setOwnerProfile(PlayerDollEntityItem.getProfile(handItem));
            return playerDoll;
        }
        return null;
    }

    private static int getRidingLayers(Entity entity) {
        int layers = 1;
        Entity current = entity;

        while (current.getVehicle() != null) {
            current = current.getVehicle();
            layers++;
        }

        return layers;
    }
}
