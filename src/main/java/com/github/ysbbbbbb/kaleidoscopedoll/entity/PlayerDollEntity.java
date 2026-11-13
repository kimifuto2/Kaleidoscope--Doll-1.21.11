package com.github.ysbbbbbb.kaleidoscopedoll.entity;

import com.github.ysbbbbbb.kaleidoscopedoll.item.PlayerDollEntityItem;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * 实体版玩家玩偶：外形与玩家玩偶方块完全一致（复用 geckolib/models/block/player_doll_steve|alex 的坐姿模型），
 * 但作为实体可以放置、被玩家打掉、并带回主人档案。
 * <p>
 * 档案同步：1.21.11 没有 COMPOUND_TAG 的 {@code EntityDataSerializer}，因此用一个
 * {@link EntityDataSerializers#STRING} 承载 <b>完整档案的 JSON</b>（含 textures 属性，客户端才能直接解析出皮肤）；
 * 若传入的是旧的 {@code uuid|name} 形式也会尝试解析。NBT 侧照旧用 {@link ResolvableProfile#CODEC} 存在 {@code Owner}。
 */
public class PlayerDollEntity extends DollEntity implements GeoAnimatable {
    /**
     * 同步给客户端的档案字符串（完整档案 JSON；为空表示无档案）
     */
    private static final EntityDataAccessor<String> DATA_OWNER =
            SynchedEntityData.defineId(PlayerDollEntity.class, EntityDataSerializers.STRING);

    private static final String TAG_OWNER = "Owner";

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /**
     * 服务端权威档案（客户端不用它，客户端从 DATA_OWNER 解析）
     */
    @Nullable
    private ResolvableProfile ownerProfile;

    // 客户端解析缓存：避免每帧 getTextureResource 都做一次 JSON 解析
    private String clientRawCache = "";
    @Nullable
    private ResolvableProfile clientProfileCache;

    public PlayerDollEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    public ResolvableProfile getOwnerProfile() {
        if (this.level().isClientSide()) {
            String raw = this.entityData.get(DATA_OWNER);
            if (!raw.equals(this.clientRawCache)) {
                this.clientRawCache = raw;
                this.clientProfileCache = decodeProfile(raw);
            }
            return this.clientProfileCache;
        }
        return this.ownerProfile;
    }

    public void setOwnerProfile(@Nullable ResolvableProfile profile) {
        this.ownerProfile = profile;
        this.entityData.set(DATA_OWNER, encodeProfile(profile));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, "");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read(TAG_OWNER, ResolvableProfile.CODEC).ifPresent(this::setOwnerProfile);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.ownerProfile != null) {
            output.store(TAG_OWNER, ResolvableProfile.CODEC, this.ownerProfile);
        }
    }

    /**
     * 只有玩家直接攻击才会被打掉（与普通实体玩偶一致），掉落带回档案的玩家实体玩偶物品。
     */
    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        if (this.isInvulnerableToBase(source) || this.isRemoved()) {
            return false;
        }
        if (!(source.getDirectEntity() instanceof net.minecraft.world.entity.player.Player)) {
            return false;
        }
        this.kill(serverLevel);
        this.markHurt();
        this.dropDollItem(serverLevel, source.getEntity());
        return true;
    }

    private void dropDollItem(ServerLevel serverLevel, @Nullable Entity source) {
        if (!serverLevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
            return;
        }
        this.playSound(SoundEvents.WOOL_BREAK, 1.0F, 1.0F);
        if (source instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().instabuild) {
            return;
        }
        this.spawnAtLocation(serverLevel, PlayerDollEntityItem.createWithProfile(this.ownerProfile));
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return PlayerDollEntityItem.createWithProfile(this.getOwnerProfile());
    }

    /**
     * 骑乘时朝向 = 载具朝向（玩偶面向坐骑移动/转向的方向）。
     * <p>
     * 上游渲染是在 {@code vehicle != null} 时直接用载具 yaw 覆盖自身 yaw；这里改成在 tick 阶段
     * 把实体自身 yaw 同步成载具 yaw，渲染端无需再做额外旋转（与 GeckoLib 的旋转顺序无关）。
     * <p>
     * 不能只写在服务端：客户端也会跑 rideTick，两边都写才能消除同步延迟导致的抖动。
     */
    @Override
    public void rideTick() {
        // 保留 DollEntity 的幻翼 pitch 逻辑
        super.rideTick();

        Entity vehicle = this.getVehicle();
        if (vehicle == null) {
            return;
        }
        float vehicleYaw = vehicle.getYRot();
        this.setYRot(vehicleYaw);
        this.yRotO = vehicleYaw;
        this.setYHeadRot(vehicleYaw);
    }

    // ------------------------------------------------------------------
    // GeckoLib
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // ------------------------------------------------------------------
    // 档案字符串编解码
    // ------------------------------------------------------------------

    public static String encodeProfile(@Nullable ResolvableProfile profile) {
        if (profile == null) {
            return "";
        }
        return ResolvableProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile)
                .result()
                .map(JsonElement::toString)
                .orElse("");
    }

    @Nullable
    public static ResolvableProfile decodeProfile(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (raw.charAt(0) == '{') {
            try {
                JsonElement json = JsonParser.parseString(raw);
                return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
            } catch (Exception ignored) {
                return null;
            }
        }
        // 兼容 uuid|name 的简洁形式（无 textures 属性，只能退回默认皮肤）
        int split = raw.indexOf('|');
        if (split <= 0 || split >= raw.length() - 1) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(raw.substring(0, split));
            String name = raw.substring(split + 1);
            return ResolvableProfile.createResolved(new GameProfile(uuid, name));
        } catch (Exception ignored) {
            return null;
        }
    }
}
