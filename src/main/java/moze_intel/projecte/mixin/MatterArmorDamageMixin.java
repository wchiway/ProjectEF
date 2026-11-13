package moze_intel.projecte.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import moze_intel.projecte.MatterArmorItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
abstract class MatterArmorDamageMixin {
    @WrapMethod(method = "getDamageAfterArmorAbsorb")
    private float projecte$applyMatterArmor(DamageSource source, float incoming, Operation<Float> vanilla) {
        float remaining = vanilla.call(source, incoming);
        return MatterArmorItem.absorb((LivingEntity) (Object) this, source, incoming, remaining);
    }
}
