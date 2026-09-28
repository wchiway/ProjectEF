package moze_intel.projecte.mixin.compat;

import moze_intel.projecte.integration.avaritia.AvaritiaIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional Fabric-only target; no Avaritia classes are linked when the mod is absent. */
@Pseudo
@Mixin(targets = "net.byAqua3.avaritia.loader.AvaritiaCompats", remap = false)
public abstract class AvaritiaCompatsMixin {

	@Inject(method = "registerCompats()V", at = @At("TAIL"))
	private static void projecte$registerMatterSingularities(CallbackInfo ci) {
		AvaritiaIntegration.registerMatterSingularities();
	}
}
