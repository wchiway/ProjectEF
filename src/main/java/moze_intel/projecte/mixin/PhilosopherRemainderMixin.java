package moze_intel.projecte.mixin;
import net.minecraft.world.item.*;
import moze_intel.projecte.PhilosophersStoneItem;
import moze_intel.projecte.EvertideAmuletItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Item.class)
public abstract class PhilosopherRemainderMixin {
 @Inject(method="getCraftingRemainder",at=@At("HEAD"),cancellable=true)
 private void projecte$keepStone(CallbackInfoReturnable<ItemStackTemplate> ci){if((Object)this instanceof PhilosophersStoneItem || (Object)this instanceof EvertideAmuletItem)ci.setReturnValue(new ItemStackTemplate((Item)(Object)this));}
}
