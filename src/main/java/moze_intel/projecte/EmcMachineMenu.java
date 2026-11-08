package moze_intel.projecte;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class EmcMachineMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 188;
    private static final int INPUT_SLOT = 0;
    private static final int STAR_SLOT = 1;
    private static final int PLAYER_START = 5;
    private final Container machine;
    private final EmcMenuData data;
    private final boolean collector;

    EmcMachineMenu(int id, Inventory inventory, boolean collector) {
        this(id, inventory, new SimpleContainer(5), collector, new EmcMenuData(4));
    }

    EmcMachineMenu(int id, Inventory inventory, EmcMachineBlockEntity machine, boolean collector) {
        this(id, inventory, machine, collector, new EmcMenuData(
                machine::storedEmc, machine::capacity, machine::lightLevel, machine::pendingBurnEmc));
    }

    private EmcMachineMenu(int id, Inventory inventory, Container machine, boolean collector, EmcMenuData data) {
        super(collector ? PERegistries.COLLECTOR_MENU : PERegistries.RELAY_MENU, id);
        this.machine = machine;
        this.collector = collector;
        this.data = data;
        addDataSlots(data);
        addSlot(new MachineSlot(machine, EmcMachineBlockEntity.INPUT, collector ? 16 : 48));
        addSlot(new MachineSlot(machine, EmcMachineBlockEntity.STAR, collector ? 80 : 112));
        if (collector) {
            addSlot(new MachineSlot(machine, EmcMachineBlockEntity.OUTPUT, 144));
        } else {
            addSlot(new LegacySlot(machine, EmcMachineBlockEntity.OUTPUT, 144));
        }
        // Older HopperMenu versions let players store items in all five machine slots.
        addSlot(new LegacySlot(machine, EmcMachineBlockEntity.TARGET, collector ? 48 : 16));
        addSlot(new LegacySlot(machine, 4, collector ? 112 : 80));
        addStandardInventorySlots(inventory, 8, 104);
    }

    public boolean isCollector() { return collector; }
    public long storedEmc() { return data.value(0); }
    public long capacity() { return data.value(1); }
    public int lightLevel() { return (int) data.value(2); }
    public long pendingBurnEmc() { return data.value(3); }

    @Override
    public boolean stillValid(Player player) {
        return machine.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < PLAYER_START) {
            moved = moveItemStackTo(stack, PLAYER_START, slots.size(), true);
        } else if (stack.getItem() instanceof KleinStarItem && moveItemStackTo(stack, STAR_SLOT, STAR_SLOT + 1, false)) {
            moved = true;
        } else {
            moved = moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    private static final class MachineSlot extends Slot {
        MachineSlot(Container container, int index, int x) {
            super(container, index, x, 63);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            int slot = getContainerSlot();
            return slot != EmcMachineBlockEntity.OUTPUT
                    && (slot != EmcMachineBlockEntity.STAR || stack.getItem() instanceof KleinStarItem)
                    && container.canPlaceItem(slot, stack);
        }
    }

    private static final class LegacySlot extends Slot {
        LegacySlot(Container container, int index, int x) {
            super(container, index, x, 63);
        }

        @Override
        public boolean isActive() {
            return hasItem();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
