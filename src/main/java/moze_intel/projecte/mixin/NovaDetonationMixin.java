package moze_intel.projecte.mixin;

import moze_intel.projecte.NovaChargeBlock;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimedTnt.class)
abstract class NovaDetonationMixin {
    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void projecte$detonateNova(CallbackInfo callback) {
        if (NovaChargeBlock.detonate((PrimedTnt) (Object) this)) callback.cancel();
    }
}
