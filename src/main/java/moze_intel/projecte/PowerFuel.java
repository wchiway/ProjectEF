package moze_intel.projecte;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class PowerFuel {
    private PowerFuel() { }

    static boolean spend(ServerPlayer player, long emc) {
        if (player.getAbilities().instabuild || KleinStarItem.pay(player, emc)
                || PETransmutationState.spendEmc(player, emc) || consumeSolidFuel(player, emc)) return true;
        notice(player, "Needs " + emc + " EMC");
        return false;
    }

    private static boolean consumeSolidFuel(ServerPlayer player, long cost) {
        // Large building costs are paid with stored EMC, keeping item fuel usage predictable.
        if (cost > 1_024) return false;
        var inventory = player.getInventory();
        var prices = PEEmcOverrides.forPlayer(player);
        long remaining = cost;
        int[] consume = new int[inventory.getContainerSize()];
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            var stack = inventory.getItem(slot);
            Item fuel = stack.getItem();
            var id = BuiltInRegistries.ITEM.getKey(fuel);
            if (!(fuel == Items.CHARCOAL || fuel == Items.COAL || fuel == Items.REDSTONE || fuel == Items.GUNPOWDER
                    || fuel == Items.GLOWSTONE_DUST || fuel == Items.GLOWSTONE
                    || id.equals(PECore.id("alchemical_coal")) || id.equals(PECore.id("mobius_fuel"))
                    || id.equals(PECore.id("aeternalis_fuel")))) continue;
            long value = prices.value(stack);
            if (value <= 0) continue;
            consume[slot] = (int) Math.min(stack.getCount(), 1 + (remaining - 1) / value);
            remaining -= value * consume[slot];
        }
        if (remaining > 0) return false;
        for (int slot = 0; slot < consume.length; slot++) {
            if (consume[slot] > 0) inventory.getItem(slot).shrink(consume[slot]);
        }
        inventory.setChanged();
        return true;
    }

    static boolean consume(Player player, Item fuel, int count) {
        if (player.getAbilities().instabuild) return true;
        var inventory = player.getInventory();
        int available = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.is(fuel)) available += stack.getCount();
        }
        if (available < count) return false;
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.is(fuel)) continue;
            int moved = Math.min(count, stack.getCount());
            stack.shrink(moved);
            count -= moved;
        }
        inventory.setChanged();
        return true;
    }

    static void notice(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text), true);
    }
}
