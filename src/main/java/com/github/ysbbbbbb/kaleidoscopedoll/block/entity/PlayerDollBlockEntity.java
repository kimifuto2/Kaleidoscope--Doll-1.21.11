package com.github.ysbbbbbb.kaleidoscopedoll.block.entity;

import com.github.ysbbbbbb.kaleidoscopedoll.debug.DollDebug;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModBlockEntities;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModDataComponent;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * 玩家玩偶方块实体：全程保存 {@link ResolvableProfile}（阶段 3：统一档案类型）。
 */
public class PlayerDollBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private ResolvableProfile ownerProfile;

    public PlayerDollBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLAYER_DOLL, pos, state);
    }

    @Nullable
    public ResolvableProfile getOwnerProfile() {
        return this.ownerProfile;
    }

    public void setOwnerProfile(@Nullable ResolvableProfile profile) {
        this.ownerProfile = profile;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private String side() {
        return this.level != null && this.level.isClientSide() ? "CLIENT" : "SERVER";
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.ownerProfile != null) {
            output.store("Owner", ResolvableProfile.CODEC, this.ownerProfile);
        }
        DollDebug.log(this.side(), "be.saveAdditional.before", this.ownerProfile,
                "pos=" + this.worldPosition + " hasLevel=" + (this.level != null));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ownerProfile = input.read("Owner", ResolvableProfile.CODEC).orElse(null);
        if (this.ownerProfile == null) {
            // 兼容旧存档：只有名字 + UUID
            String name = input.getStringOr("OwnerName", "");
            long most = input.getLongOr("OwnerUuidMost", 0L);
            long least = input.getLongOr("OwnerUuidLeast", 0L);
            if (!name.isEmpty() && (most != 0L || least != 0L)) {
                this.ownerProfile = ResolvableProfile.createResolved(
                        new GameProfile(new UUID(most, least), name));
                DollDebug.log(this.side(), "be.loadAdditional.legacyFallback", this.ownerProfile,
                        "pos=" + this.worldPosition);
            }
        }
        DollDebug.log(this.side(), "be.loadAdditional.afterCodec", this.ownerProfile,
                "pos=" + this.worldPosition);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (this.ownerProfile != null) {
            builder.set(ModDataComponent.DOLL_OWNER, this.ownerProfile);
            builder.set(DataComponents.PROFILE, this.ownerProfile);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter input) {
        super.applyImplicitComponents(input);
        ResolvableProfile profile = input.get(ModDataComponent.DOLL_OWNER);
        if (profile == null) {
            profile = input.get(DataComponents.PROFILE);
        }
        DollDebug.log(this.side(), "be.applyComponents.input", profile, "pos=" + this.worldPosition);
        if (profile != null) {
            this.ownerProfile = profile;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        DollDebug.msg(this.side(), "be.updateTag",
                "pos=" + this.worldPosition + " hasOwner=" + tag.contains("Owner"));
        return tag;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
