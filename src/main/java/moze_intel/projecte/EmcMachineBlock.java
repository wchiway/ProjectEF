package moze_intel.projecte;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

final class EmcMachineBlock extends BaseEntityBlock {
    enum Kind { COLLECTOR, RELAY, CONDENSER }

    final Kind kind;
    final int tier;

    EmcMachineBlock(Properties properties, Kind kind, int tier) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EmcMachineBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof EmcMachineBlockEntity machine) {
            machine.bind(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server
                && level.getBlockEntity(pos) instanceof EmcMachineBlockEntity machine) {
            server.openMenu(machine);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type,
                kind == Kind.CONDENSER ? PERegistries.ENERGY_CONDENSER_ENTITY : PERegistries.EMC_MACHINE_ENTITY,
                EmcMachineBlockEntity::tick);
    }
}
