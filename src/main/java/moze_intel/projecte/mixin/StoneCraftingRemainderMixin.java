package moze_intel.projecte.mixin;

import moze_intel.projecte.PhilosophersStoneItem;
import moze_intel.projecte.EvertideAmuletItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A reusable catalyst must also keep its charge, fuel credit and custom name. */
@Mixin(CraftingRecipe.class)
public interface StoneCraftingRemainderMixin {
 @Inject(method="defaultCraftingReminder",at=@At("RETURN"))
 private static void projecte$keepStoneData(CraftingInput input,CallbackInfoReturnable<NonNullList<ItemStack>> ci){
  for(int i=0;i<input.size();i++)if(input.getItem(i).getItem() instanceof PhilosophersStoneItem || input.getItem(i).getItem() instanceof EvertideAmuletItem)ci.getReturnValue().set(i,input.getItem(i).copyWithCount(1));
 }
}
