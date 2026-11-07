package moze_intel.projecte;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class DarkMatterPedestalBlock extends BaseEntityBlock {
    static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 2, 13),
            Block.box(6, 2, 6, 10, 9, 10), Block.box(5, 9, 5, 11, 10, 11));

    DarkMatterPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DarkMatterPedestalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || !state.getValue(ACTIVE) ? null
                : createTickerHelper(type, PERegistries.DARK_MATTER_PEDESTAL_ENTITY, DarkMatterPedestalBlockEntity::tick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!ClassicRingItem.canUse(player) || !DarkMatterPedestalBlockEntity.supports(stack)
                || !(level.getBlockEntity(pos) instanceof DarkMatterPedestalBlockEntity pedestal)
                || !pedestal.item().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            if (!ClassicRingItem.canChange(serverLevel, player, stack, pos)) return InteractionResult.FAIL;
            pedestal.insert(stack.consumeAndReturn(1, player), player.getUUID());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!ClassicRingItem.canUse(player) || !player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty()
                || !(level.getBlockEntity(pos) instanceof DarkMatterPedestalBlockEntity pedestal)
                || pedestal.item().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            if (!ClassicRingItem.canChange(serverLevel, player, ItemStack.EMPTY, pos)) return InteractionResult.FAIL;
            if (player.isShiftKeyDown()) {
                ItemStack item = pedestal.removeItem();
                level.setBlockAndUpdate(pos, state.setValue(ACTIVE, false));
                player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
            } else {
                level.setBlockAndUpdate(pos, state.cycle(ACTIVE));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
