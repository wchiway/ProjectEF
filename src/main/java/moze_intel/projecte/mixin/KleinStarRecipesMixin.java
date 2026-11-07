package moze_intel.projecte.mixin;

import moze_intel.projecte.KleinStarItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ShapedRecipe.class, ShapelessRecipe.class})
abstract class KleinStarRecipesMixin {
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("RETURN"), cancellable = true)
    private void projecte$requireFullStars(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && input.items().stream().anyMatch(stack -> stack.getItem() instanceof KleinStarItem)
                && !KleinStarItem.hasRequiredCharge(input, ((CraftingRecipe) (Object) this).assemble(input))) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private void projecte$combineStoredCharge(CraftingInput input, CallbackInfoReturnable<ItemStack> callback) {
        KleinStarItem.combine(input, callback.getReturnValue());
    }
}
