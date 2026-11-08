package moze_intel.projecte.mixin;

import moze_intel.projecte.PhilosophersStoneItem;
import moze_intel.projecte.EvertideAmuletItem;
import moze_intel.projecte.VolcaniteAmuletItem;
import moze_intel.projecte.ClassicRingItem;
import moze_intel.projecte.PECore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
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

 @Inject(method="getRemainingItems",at=@At("RETURN"))
 private void projecte$returnRecipeCatalysts(CraftingInput input,CallbackInfoReturnable<NonNullList<ItemStack>> ci) {
  if (input.items().stream().noneMatch(stack -> stack.getItem() instanceof ClassicRingItem
          || stack.getItem() instanceof VolcaniteAmuletItem)) return;
  ItemStack output = ((CraftingRecipe) this).assemble(input);
  var outputId = BuiltInRegistries.ITEM.getKey(output.getItem());
  boolean arcana = outputId.equals(PECore.id("arcana_ring"));
  boolean usesLava = output.is(Items.LAVA_BUCKET) || outputId.equals(PECore.id("iron_band"));
  for (int i = 0; i < input.size(); i++) {
   ItemStack stack = input.getItem(i);
   if (stack.getItem() instanceof VolcaniteAmuletItem && usesLava) {
    ci.getReturnValue().set(i, stack.copyWithCount(1));
   }
   if (!(stack.getItem() instanceof ClassicRingItem)) continue;
   var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
   boolean zero = id.equals(PECore.id("zero_ring"));
   boolean harvest = id.equals(PECore.id("harvest_goddess_band"));
   boolean catalyst = id.equals(PECore.id("arcana_ring"));
   if ((arcana && (zero || harvest)) || (output.is(Items.ICE) && (zero || catalyst))
           || (output.is(Items.GRASS_BLOCK) && (harvest || catalyst))) {
    ci.getReturnValue().set(i, stack.copyWithCount(1));
   }
  }
 }
}
