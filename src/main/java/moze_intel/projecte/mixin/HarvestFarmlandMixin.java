package moze_intel.projecte.mixin;

import moze_intel.projecte.ClassicRingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmlandBlock.class)
abstract class HarvestFarmlandMixin {
    @Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
    private void projecte$preventTrampling(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (entity instanceof Player player && ClassicRingItem.protectsFarmland(player)) ci.cancel();
    }
}
