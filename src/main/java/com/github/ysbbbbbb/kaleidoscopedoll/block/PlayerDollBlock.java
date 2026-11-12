package com.github.ysbbbbbb.kaleidoscopedoll.block;

import com.github.ysbbbbbb.kaleidoscopedoll.block.entity.PlayerDollBlockEntity;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModDataComponent;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModSounds;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家玩偶方块：外观完全由客户端 BER 用玩家皮肤渲染（方块模型为空）。
 */
public class PlayerDollBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<PlayerDollBlock> CODEC = simpleCodec(PlayerDollBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape DOLL_SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);

    private static final double PARTICLE_OFFSET_RANGE = 0.25;
    private static final double PARTICLE_HEIGHT_OFFSET = 1.0;
    private static final double PARTICLE_HEIGHT_VARIANCE = 0.2;
    private static final float NOTE_COLOR_DIVISOR = 24.0F;
    private static final int MAX_NOTE_COLORS = 4;
    private static final float BASE_VOLUME = 1.0F;
    private static final float PITCH_VARIANCE = 0.5F;
    private static final float BASE_PITCH = 0.75F;

    public PlayerDollBlock(Properties properties) {
        super(properties
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.WOOL)
                .instabreak()
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DOLL_SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerDollBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            this.spawnNoteParticles(serverLevel, pos);
            this.playDollSound(serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }

    private void spawnNoteParticles(ServerLevel serverLevel, BlockPos pos) {
        var random = serverLevel.getRandom();
        double x = pos.getX() + 0.5 + (random.nextFloat() - 0.5) * PARTICLE_OFFSET_RANGE * 2;
        double y = pos.getY() + PARTICLE_HEIGHT_OFFSET + random.nextFloat() * PARTICLE_HEIGHT_VARIANCE;
        double z = pos.getZ() + 0.5 + (random.nextFloat() - 0.5) * PARTICLE_OFFSET_RANGE * 2;
        float noteColor = random.nextInt(MAX_NOTE_COLORS) / NOTE_COLOR_DIVISOR;
        serverLevel.sendParticles(ParticleTypes.NOTE, x, y, z, 0, noteColor, 0.0, 0.0, 1.0);
    }

    private void playDollSound(ServerLevel serverLevel, BlockPos pos) {
        float pitch = BASE_PITCH + serverLevel.getRandom().nextFloat() * PITCH_VARIANCE;
        serverLevel.playSound(null, pos, ModSounds.DUCK_TOY, SoundSource.BLOCKS, BASE_VOLUME, pitch);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack stack = new ItemStack(ModItems.PLAYER_DOLL);
        if (level.getBlockEntity(pos) instanceof PlayerDollBlockEntity doll && doll.getOwnerProfile() != null) {
            ResolvableProfile profile = doll.getOwnerProfile();
            stack.set(ModDataComponent.DOLL_OWNER, profile);
            stack.set(DataComponents.PROFILE, profile);
        }
        return stack;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            tickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
