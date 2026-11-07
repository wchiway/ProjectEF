package moze_intel.projecte.mixin;

import moze_intel.projecte.NovaChargeBlock;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TntBlock.class)
abstract class NovaChargeMixin {
    @ModifyVariable(method = "prime(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("STORE"))
    private static PrimedTnt projecte$rememberNova(PrimedTnt charge, Level level, BlockPos pos,
                                                    net.minecraft.world.entity.LivingEntity owner,
                                                    net.minecraft.world.item.ItemStack igniter) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof NovaChargeBlock) charge.setBlockState(state);
        return charge;
    }

}
