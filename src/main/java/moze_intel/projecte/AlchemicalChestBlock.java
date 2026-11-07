package moze_intel.projecte;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public final class AlchemicalChestBlock extends Block implements EntityBlock {
 public AlchemicalChestBlock(Properties p){super(p);}
 public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new AlchemicalChestBlockEntity(pos,state);}
 @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
  if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof MenuProvider menu)player.openMenu(menu);
  return InteractionResult.SUCCESS;
 }
 @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
        Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
  return level.isClientSide() ? null : (world, pos, blockState, entity) -> {
   if (world.getGameTime() % 20 == Math.floorMod(pos.hashCode(), 20)
           && entity instanceof AlchemicalChestBlockEntity chest && UtilityPowerItem.hasTalisman(chest)) {
    UtilityPowerItem.repair(chest);
   }
  };
 }
}
