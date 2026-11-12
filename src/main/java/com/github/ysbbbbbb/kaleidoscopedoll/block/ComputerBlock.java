package com.github.ysbbbbbb.kaleidoscopedoll.block;

import com.github.ysbbbbbb.kaleidoscopedoll.inventory.ComputerMenu;
import com.github.ysbbbbbb.kaleidoscopedoll.init.ModMenuTypes;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class ComputerBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<ComputerBlock> CODEC = simpleCodec(ComputerBlock::new);
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape NORTH_AABB = Shapes.or(
            Block.box(0, 0, 0, 16, 4, 15),
            Block.box(1, 4, 5, 15, 15, 13)
    );
    private static final VoxelShape SOUTH_AABB = Shapes.or(
            Block.box(0, 0, 1, 16, 4, 16),
            Block.box(1, 4, 3, 15, 15, 11)
    );
    private static final VoxelShape EAST_AABB = Shapes.or(
            Block.box(1, 0, 0, 16, 4, 16),
            Block.box(3, 4, 1, 11, 15, 15)
    );
    private static final VoxelShape WEST_AABB = Shapes.or(
            Block.box(0, 0, 0, 15, 4, 16),
            Block.box(5, 4, 1, 13, 15, 15)
    );

    public ComputerBlock(BlockBehaviour.Properties properties) {
        super(properties
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.METAL)
                .strength(1.25F, 4.2F)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                         Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.openMenu(this.getMenuProvider(state, level, pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.openMenu(this.getMenuProvider(state, level, pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState blockState, Level level, BlockPos blockPos) {
        return new ComputerMenuProvider();
    }

    @Override
    protected BlockState updateShape(BlockState currentState, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos currentPos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        if (currentState.getValue(WATERLOGGED)) {
            tickAccess.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(currentState, level, tickAccess, currentPos, direction, neighborPos, neighborState, random);
    }

    @Override
    public FluidState getFluidState(BlockState blockState) {
        return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        boolean isWaterlogged = fluidState.getType() == Fluids.WATER;

        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, isWaterlogged);
    }

    @Override
    public VoxelShape getShape(BlockState blockState, BlockGetter level, BlockPos blockPos, CollisionContext context) {
        Direction facing = blockState.getValue(FACING);
        return switch (facing) {
            case SOUTH -> SOUTH_AABB;
            case EAST -> EAST_AABB;
            case WEST -> WEST_AABB;
            default -> NORTH_AABB;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    /**
     * 打开电脑界面：使用 Fabric 的扩展菜单（无需额外数据），这样客户端能按扩展类型创建界面。
     */
    private static final class ComputerMenuProvider implements ExtendedScreenHandlerFactory<Unit> {
        @Override
        public Unit getScreenOpeningData(ServerPlayer player) {
            return Unit.INSTANCE;
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("block.kaleidoscope_doll.computer");
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
            return new ComputerMenu(containerId, inventory);
        }
    }
}
