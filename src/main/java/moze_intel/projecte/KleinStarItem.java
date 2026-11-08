package moze_intel.projecte;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public final class KleinStarItem extends Item {
    private static final String STORED = "KleinStarEmc";
    private final long capacity;

    KleinStarItem(Properties properties, long capacity) {
        super(properties);
        this.capacity = capacity;
    }

    long stored(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getLongOr(STORED, 0), 0, capacity);
    }

    long charge(ItemStack stack, long offered) {
        long stored = stored(stack);
        long accepted = Math.min(offered, capacity - stored);
        if (accepted > 0) setStored(stack, stored + accepted);
        return accepted;
    }

    long drain(ItemStack stack, long requested) {
        long stored = stored(stack);
        long amount = Math.min(stored, requested);
        if (amount > 0) setStored(stack, stored - amount);
        return amount;
    }

    private void setStored(ItemStack stack, long amount) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            if (amount == 0) data.remove(STORED);
            else data.putLong(STORED, amount);
        });
    }

    static boolean canPay(ServerPlayer player, long amount) {
        return findCharged(player, amount) != ItemStack.EMPTY;
    }

    static boolean pay(ServerPlayer player, long amount) {
        ItemStack stack = findCharged(player, amount);
        if (!(stack.getItem() instanceof KleinStarItem star)) return false;
        star.drain(stack, amount);
        player.getInventory().setChanged();
        return true;
    }

    private static ItemStack findCharged(ServerPlayer player, long amount) {
        var inventory = player.getInventory();
        for (int slot = 0; slot <= inventory.getContainerSize(); slot++) {
            ItemStack stack = slot == inventory.getContainerSize() ? player.getOffhandItem() : inventory.getItem(slot);
            if (stack.getItem() instanceof KleinStarItem star && star.stored(stack) >= amount) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    static ItemStack emptyCopy(ItemStack stack) {
        if (!(stack.getItem() instanceof KleinStarItem star) || star.stored(stack) == 0) return stack;
        ItemStack copy = stack.copy();
        star.setStored(copy, 0);
        return copy;
    }

    public static boolean hasRequiredCharge(net.minecraft.world.item.crafting.CraftingInput input, ItemStack result) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem());
        boolean full = id.getNamespace().equals(PECore.MODID)
                && (id.getPath().equals("tome") || id.getPath().startsWith("gem_"));
        if (!full) return true;
        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof KleinStarItem star && star.stored(stack) < star.capacity) return false;
        }
        return true;
    }

    public static void combine(net.minecraft.world.item.crafting.CraftingInput input, ItemStack result) {
        if (!(result.getItem() instanceof KleinStarItem larger)) return;
        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof KleinStarItem star && star.capacity < larger.capacity) {
                larger.charge(result, star.stored(stack));
            }
        }
    }

    boolean discharge(ServerPlayer player, ItemStack stack) {
        if (!PETransmutationState.deposit(player.level().getServer(), player.getUUID(), stored(stack), 1)) return false;
        setStored(stack, 0);
        return true;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server && !player.isSpectator()) {
            ItemStack stack = player.getItemInHand(hand);
            long stored = stored(stack);
            if (player.isShiftKeyDown()) {
                long amount = Math.min(PETransmutationState.balance(server), capacity - stored);
                if (PETransmutationState.spendEmc(server, amount)) setStored(stack, stored + amount);
            } else {
                discharge(server, stack);
            }
            server.sendSystemMessage(Component.literal(stored(stack) + " / " + capacity + " EMC"), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal(stored(stack) + " / " + capacity + " EMC"));
        lines.accept(Component.literal("Sneak-use to charge. Use or place in the transmutation input to discharge."));
    }
}
