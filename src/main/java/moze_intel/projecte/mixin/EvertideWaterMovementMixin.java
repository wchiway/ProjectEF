package moze_intel.projecte.mixin;

import moze_intel.projecte.EvertideAmuletItem;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class EvertideWaterMovementMixin {
    @Unique
    private static final VoxelShape PROJECTE_WATER_SURFACE = Shapes.box(0, 0, 0, 1, 8.0 / 9.0, 1);

    @Inject(method = "getLiquidCollisionShape", at = @At("HEAD"), cancellable = true)
    private void projecte$waterSurface(CallbackInfoReturnable<VoxelShape> result) {
        if ((Object) this instanceof Player player && !player.isShiftKeyDown()
                && EvertideAmuletItem.isCarried(player)) {
            result.setReturnValue(PROJECTE_WATER_SURFACE);
        }
    }

    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
    private void projecte$walkOnWater(FluidState fluid, CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof Player player && fluid.is(FluidTags.WATER)
                && !player.isShiftKeyDown() && EvertideAmuletItem.isCarried(player)) {
            result.setReturnValue(true);
        }
    }

    @Inject(method = "decreaseAirSupply", at = @At("HEAD"), cancellable = true)
    private void projecte$breatheUnderwater(int air, CallbackInfoReturnable<Integer> result) {
        if ((Object) this instanceof Player player && player.isEyeInFluid(FluidTags.WATER)
                && EvertideAmuletItem.isCarried(player)) {
            result.setReturnValue(player.getMaxAirSupply());
        }
    }
}
