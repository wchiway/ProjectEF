package moze_intel.projecte;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CondenserMenu extends AbstractContainerMenu {
    public static final int WIDTH = 255;
    public static final int HEIGHT = 240;
    public static final int GRID_COLUMNS = 13;
    public static final int GRID_ROWS = 7;

    static final int GRID_SIZE = GRID_COLUMNS * GRID_ROWS;
    private static final int TARGET = 0;
    private static final int STAR = 1;
    private static final int ACCOUNT = 2;
    private static final int GRID_START = 3;
    private static final int PLAYER_START = GRID_START + GRID_SIZE;
    private final Container container;
    private final EmcMenuData values;

    public CondenserMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(GRID_START + GRID_SIZE), new EmcMenuData(2));
    }

    CondenserMenu(int id, Inventory inventory, EmcMachineBlockEntity machine) {
        this(id, inventory, machine, new EmcMenuData(machine::storedEmc, machine::condenserTargetEmc));
    }

    private CondenserMenu(int id, Inventory inventory, Container container, EmcMenuData values) {
        super(PERegistries.CONDENSER_MENU, id);
        this.container = container;
        this.values = values;
        addDataSlots(values);
        addSlot(new MachineSlot(container, EmcMachineBlockEntity.TARGET, 12, 7));
        addSlot(new MachineSlot(container, EmcMachineBlockEntity.STAR, 227, 7));
        addSlot(new MachineSlot(container, EmcMachineBlockEntity.INPUT, 207, 7));
        for (int i = 0; i < GRID_SIZE; i++) {
            addSlot(new MachineSlot(container, EmcMachineBlockEntity.OUTPUT + i,
                    12 + i % GRID_COLUMNS * 18, 26 + i / GRID_COLUMNS * 18));
        }
        addStandardInventorySlots(inventory, 48, 156);
    }

    public long storedEmc() { return values.value(0); }

    public long targetEmc() { return values.value(1); }

    public Slot targetSlot() { return slots.get(TARGET); }

    public Slot starSlot() { return slots.get(STAR); }

    public Slot accountSlot() { return slots.get(ACCOUNT); }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < PLAYER_START) {
            moved = moveItemStackTo(stack, PLAYER_START, slots.size(), true);
        } else if (stack.getItem() instanceof KleinStarItem && moveItemStackTo(stack, STAR, STAR + 1, false)) {
            moved = true;
        } else {
            moved = moveItemStackTo(stack, GRID_START, PLAYER_START, false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    private static final class MachineSlot extends Slot {
        MachineSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (getContainerSlot() == EmcMachineBlockEntity.STAR && !(stack.getItem() instanceof KleinStarItem)) {
                return false;
            }
            return container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return getContainerSlot() == EmcMachineBlockEntity.TARGET ? 1 : super.getMaxStackSize();
        }
    }
}
