package com.github.ysbbbbbb.kaleidoscopedoll.item;

import com.github.ysbbbbbb.kaleidoscopedoll.config.GeneralConfig;
import com.github.ysbbbbbb.kaleidoscopedoll.entity.PlayerDollEntity;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModDataComponent;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModEntities;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 玩家实体玩偶物品：放下后生成带玩家皮肤、坐姿的 {@link PlayerDollEntity}；
 * 档案存在 {@link ModDataComponent#DOLL_OWNER} 组件上，放置时写入实体。
 * <p>
 * 行为对照上游 {@code DollEntityItem}：Shift + 右键蓄力投掷、右键放置、右键可骑乘实体骑上去。
 * 图标是 GeckoLib 3D 玩偶（{@link GeoItem}），和玩家玩偶物品同一套渲染。
 */
public class PlayerDollEntityItem extends Item implements GeoItem {
    private static final int THROW_DURATION = 5;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    public final AtomicReference<Object> geoRenderProvider = new AtomicReference<>();

    public PlayerDollEntityItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createWithProfile(@Nullable ResolvableProfile profile) {
        ItemStack stack = new ItemStack(ModItems.PLAYER_DOLL_ENTITY);
        if (profile != null) {
            stack.set(ModDataComponent.DOLL_OWNER, profile);
        }
        return stack;
    }

    @Nullable
    public static ResolvableProfile getProfile(ItemStack stack) {
        return stack.get(ModDataComponent.DOLL_OWNER);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResolvableProfile profile = getProfile(stack);
        if (profile != null) {
            String name = profile.name().orElse("");
            if (!name.isEmpty()) {
                return Component.translatable("item.kaleidoscope_doll.player_doll_entity.owner", name);
            }
        }
        return super.getName(stack);
    }

    private boolean mayPlace(Player player, ItemStack stack, BlockPos pos, Direction face) {
        return !player.level().isOutsideBuildHeight(pos) && player.mayUseItemAt(pos, face, stack);
    }

    // ------------------------------------------------------------------
    // 使用 / 蓄力投掷（对照上游 DollEntityItem）
    // ------------------------------------------------------------------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // 蹲下 + 允许投掷 → 进入蓄力
        if (player.isShiftKeyDown() && GeneralConfig.DOLL_CAN_BE_THROWN.get()) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // 1.21.11 的 Item#releaseUsing 返回 boolean（1.21.1 是 void）
        int useTime = this.getUseDuration(stack, entity) - timeLeft;
        if (useTime >= THROW_DURATION && GeneralConfig.DOLL_CAN_BE_THROWN.get()) {
            int strength = useTime - THROW_DURATION;
            this.throwDollEntity(level, entity, stack, strength);
            return true;
        }
        return false;
    }

    private void throwDollEntity(Level level, LivingEntity thrower, ItemStack stack, int strength) {
        if (level.isClientSide()) {
            return;
        }
        PlayerDollEntity dollEntity = new PlayerDollEntity(ModEntities.PLAYER_DOLL, level);
        dollEntity.setOwnerProfile(getProfile(stack));
        dollEntity.setInThrowing(true);

        // 从眼睛位置丢出
        Vec3 eyePos = thrower.getEyePosition();
        dollEntity.setPos(eyePos.x, eyePos.y - 0.1, eyePos.z);

        // 沿视线方向给速度，蓄力越久越快
        Vec3 lookAngle = thrower.getLookAngle();
        double throwPower = Mth.clamp(strength * 0.1, 0.1, 3);
        dollEntity.setDeltaMovement(lookAngle.x * throwPower, lookAngle.y * throwPower, lookAngle.z * throwPower);
        dollEntity.setYRot(thrower.getYRot() - 180);

        level.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(dollEntity);

        if (!(thrower instanceof Player player) || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    // ------------------------------------------------------------------
    // 放置
    // ------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        if (player == null || !this.mayPlace(player, stack, clickedPos, clickedFace)) {
            return InteractionResult.FAIL;
        }

        // 蹲下时交给蓄力投掷，不放置
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        BlockPos spawnPos = clickedPos.relative(clickedFace);
        Vec3 spawnLocation = Vec3.atBottomCenterOf(spawnPos);

        PlayerDollEntity entity = new PlayerDollEntity(ModEntities.PLAYER_DOLL, level);
        entity.setOwnerProfile(getProfile(stack));
        entity.setPos(spawnLocation.x, spawnLocation.y, spawnLocation.z);
        entity.setYRot(player.getYRot() - 180);

        if (!entity.canSurvives()) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            entity.playSound(SoundEvents.WOOL_PLACE, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
            level.addFreshEntity(entity);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> tooltipAdder, TooltipFlag isAdvanced) {
        tooltipAdder.accept(Component.translatable("item.kaleidoscopedoll.player_doll_entity.place.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("item.kaleidoscopedoll.doll_entity_item.throw.tooltip")
                .withStyle(ChatFormatting.GRAY));
        if (getProfile(stack) == null) {
            tooltipAdder.accept(Component.translatable("item.kaleidoscopedoll.player_doll_entity.no_owner.tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ------------------------------------------------------------------
    // GeckoLib（3D 图标）
    // ------------------------------------------------------------------

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
