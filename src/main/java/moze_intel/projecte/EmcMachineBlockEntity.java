package moze_intel.projecte;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

final class EmcMachineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    static final int INPUT = 0, STAR = 1, TARGET = 2, OUTPUT = 3;
    private static final int[] INSERT = {INPUT};
    private static final int[] EXTRACT = {OUTPUT};
    private static final int[] COLLECT = {INPUT, OUTPUT};
    private static final int[] CONDENSER_GRID = java.util.stream.IntStream.range(OUTPUT, OUTPUT + CondenserMenu.GRID_SIZE).toArray();
    private static final int COLLECTOR_INTERVAL = 20;
    private static final int[] RATES = {4, 12, 40};
    private static final int[] TRANSFER = {64, 192, 640};
    private static final long[] COLLECTOR_CAPACITIES = {10_000, 30_000, 60_000};
    private static final long[] RELAY_CAPACITIES = {100_000, 1_000_000, 10_000_000};
    private final ItemStack[] items;
    private final EmcMachineBlock.Kind kind;
    private final int tier;
    private final long capacity;
    private UUID owner;
    private long emc;
    private long pendingBurnEmc;
    private int collectorBonusTicks;

    EmcMachineBlockEntity(BlockPos pos, BlockState state) {
        super(((EmcMachineBlock) state.getBlock()).kind == EmcMachineBlock.Kind.CONDENSER
                ? PERegistries.ENERGY_CONDENSER_ENTITY : PERegistries.EMC_MACHINE_ENTITY, pos, state);
        var block = (EmcMachineBlock) state.getBlock();
        kind = block.kind;
        items = new ItemStack[kind == EmcMachineBlock.Kind.CONDENSER ? OUTPUT + CondenserMenu.GRID_SIZE : 5];
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        tier = block.tier - 1;
        capacity = switch (kind) {
            case COLLECTOR -> COLLECTOR_CAPACITIES[tier];
            case RELAY -> RELAY_CAPACITIES[tier];
            case CONDENSER -> Long.MAX_VALUE;
        };
    }

    long storedEmc() { return emc; }

    long condenserTargetEmc() {
        ItemStack target = items[TARGET];
        return level instanceof ServerLevel server && !PETransmutationState.sellOnlyMap(target)
                ? price(server, new ItemStack(target.getItem())) : 0;
    }

    long capacity() { return capacity; }

    long pendingBurnEmc() { return pendingBurnEmc; }

    int lightLevel() {
        return level == null ? 0 : level.getMaxLocalRawBrightness(worldPosition.above());
    }

    void bind(UUID placer) {
        if (owner == null) {
            owner = placer;
            setChanged();
        }
    }

    static void tick(Level level, BlockPos pos, BlockState state, EmcMachineBlockEntity machine) {
        if (level instanceof ServerLevel server) {
            boolean generate = level.getGameTime() % COLLECTOR_INTERVAL == Math.floorMod(pos.hashCode(), COLLECTOR_INTERVAL);
            machine.processTick(server, generate);
        }
    }

    void tickFromWatch(ServerLevel level) {
        boolean generate = false;
        if (kind == EmcMachineBlock.Kind.COLLECTOR) {
            // Track bonus ticks independently so the pedestal's phase cannot skip or double generation.
            collectorBonusTicks = (collectorBonusTicks + 1) % COLLECTOR_INTERVAL;
            generate = collectorBonusTicks == 0;
            setChanged();
        }
        processTick(level, generate);
    }

    private void processTick(ServerLevel level, boolean generate) {
        switch (kind) {
            case CONDENSER -> condense(level);
            case RELAY -> burnRelayFuel(level);
            case COLLECTOR -> {
                if (generate) accept(RATES[tier] * lightLevel() / 15);
                upgradeFuel(level);
            }
        }

        ItemStack star = items[STAR];
        if (star.getItem() instanceof KleinStarItem klein && emc > 0) {
            spend(klein.charge(star, Math.min(emc, TRANSFER[tier])));
        }
        if (kind != EmcMachineBlock.Kind.CONDENSER) transfer(level, worldPosition);
    }

    private void condense(ServerLevel level) {
        ItemStack template = items[TARGET];
        ItemStack input = items[INPUT];
        if (template.isEmpty()) {
            // Existing condensers keep their account-deposit mode while the target slot is empty.
            long unit = price(level, input);
            if (owner != null && unit > 0 && PETransmutationState.deposit(level.getServer(), owner, unit, input.getCount())) {
                setItem(INPUT, ItemStack.EMPTY);
            }
            return;
        }
        ItemStack result = new ItemStack(template.getItem());
        long unit = condenserTargetEmc();
        if (unit <= 0) return;
        if (input.is(result.getItem()) || !burnFuel(level, input)) {
            for (int slot : CONDENSER_GRID) {
                ItemStack stack = items[slot];
                if (!stack.is(result.getItem()) && burnFuel(level, stack)) break;
            }
        }
        if (emc >= unit && addCondenserOutput(result)) spend(unit);
    }

    private void burnRelayFuel(ServerLevel level) {
        if (emc >= capacity) return;
        if (pendingBurnEmc > 0) {
            pendingBurnEmc -= accept(pendingBurnEmc);
            return;
        }

        burnFuel(level, items[INPUT]);
    }

    private boolean burnFuel(ServerLevel level, ItemStack input) {
        if (input.getItem() instanceof KleinStarItem star) {
            return accept(star.drain(input, Math.min(capacity - emc, TRANSFER[tier]))) > 0;
        }
        long value = price(level, input);
        if (value <= 0 || emc >= capacity) return false;
        if (kind == EmcMachineBlock.Kind.RELAY && value > capacity) {
            // Consume one item and retain its remaining EMC while the tier's buffer drains.
            pendingBurnEmc = value - accept(value);
        } else {
            if (value > capacity - emc) return false;
            accept(value);
        }
        input.shrink(1);
        return true;
    }

    private boolean addCondenserOutput(ItemStack result) {
        int free = -1;
        for (int slot : CONDENSER_GRID) {
            ItemStack stack = items[slot];
            if (stack.isEmpty()) {
                if (free == -1) free = slot;
            } else if (ItemStack.isSameItemSameComponents(stack, result) && stack.getCount() < stack.getMaxStackSize()) {
                stack.grow(1);
                setChanged();
                return true;
            }
        }
        if (free == -1) return false;
        setItem(free, result);
        return true;
    }

    private void upgradeFuel(ServerLevel level) {
        ItemStack input = items[INPUT];
        if (input.isEmpty()) return;
        Item upgraded = nextFuel(input);
        if (upgraded == null) return;
        ItemStack result = new ItemStack(upgraded);
        long current = price(level, input), next = price(level, result);
        if (current > 0 && next >= current && next - current <= emc && addOutput(result)) {
            input.shrink(1);
            spend(next - current);
            setChanged();
        }
    }

    private static Item nextFuel(ItemStack input) {
        var ids = List.of(Items.CHARCOAL, Items.REDSTONE, Items.COAL, Items.GUNPOWDER, Items.GLOWSTONE_DUST,
                Items.BLAZE_POWDER, Items.GLOWSTONE,
                BuiltInRegistries.ITEM.getValue(PECore.id("alchemical_coal")),
                BuiltInRegistries.ITEM.getValue(PECore.id("mobius_fuel")),
                BuiltInRegistries.ITEM.getValue(PECore.id("aeternalis_fuel")));
        int index = ids.indexOf(input.getItem());
        return index >= 0 && index + 1 < ids.size() ? ids.get(index + 1) : null;
    }

    private boolean addOutput(ItemStack result) {
        ItemStack output = items[OUTPUT];
        if (output.isEmpty()) {
            setItem(OUTPUT, result);
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(output, result) || output.getCount() >= output.getMaxStackSize()) return false;
        output.grow(1);
        setChanged();
        return true;
    }

    private static long price(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return PEEmcOverrides.forServer(level.getServer()).value(stack);
    }

    private long accept(long offered) {
        long amount = Math.min(offered, capacity - emc);
        if (amount > 0) {
            emc += amount;
            setChanged();
        }
        return amount;
    }

    private void spend(long amount) {
        if (amount > 0) {
            emc -= amount;
            setChanged();
        }
    }

    private void transfer(ServerLevel level, BlockPos pos) {
        long budget = Math.min(emc, TRANSFER[tier]);
        for (Direction direction : Direction.values()) {
            if (budget <= 0) break;
            BlockPos target = pos.relative(direction);
            if (!level.isLoaded(target) || !(level.getBlockEntity(target) instanceof EmcMachineBlockEntity machine)) continue;
            if (machine.kind == EmcMachineBlock.Kind.CONDENSER && !machine.items[TARGET].isEmpty()
                    || kind == EmcMachineBlock.Kind.COLLECTOR && machine.kind == EmcMachineBlock.Kind.RELAY) {
                long moved = machine.accept(budget);
                spend(moved);
                budget -= moved;
            }
        }
    }

    @Override public int getContainerSize() { return items.length; }
    @Override public boolean isEmpty() { return java.util.Arrays.stream(items).allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items[slot]; }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = items[slot].split(count);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack old = items[slot];
        items[slot] = ItemStack.EMPTY;
        return old;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        items[slot] = stack;
        setChanged();
    }
    @Override public void clearContent() {
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX()+.5, worldPosition.getY()+.5, worldPosition.getZ()+.5) < 64;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!(level instanceof ServerLevel server)) return false;
        if (kind == EmcMachineBlock.Kind.CONDENSER && slot >= OUTPUT && slot < items.length) {
            return stack.getItem() instanceof KleinStarItem || price(server, stack) > 0;
        }
        return switch (slot) {
            case INPUT -> kind == EmcMachineBlock.Kind.COLLECTOR ? nextFuel(stack) != null
                    : stack.getItem() instanceof KleinStarItem || price(server, stack) > 0;
            case STAR -> stack.getItem() instanceof KleinStarItem;
            case TARGET -> kind == EmcMachineBlock.Kind.CONDENSER && !PETransmutationState.sellOnlyMap(stack)
                    && price(server, new ItemStack(stack.getItem())) > 0
                    && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
            default -> false;
        };
    }
    @Override public int[] getSlotsForFace(Direction side) {
        if (kind == EmcMachineBlock.Kind.CONDENSER && (side == Direction.DOWN || !items[TARGET].isEmpty())) {
            return CONDENSER_GRID;
        }
        return side == Direction.DOWN ? EXTRACT : side == Direction.UP ? INSERT : COLLECT;
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (side == Direction.DOWN || !canPlaceItem(slot, stack)) return false;
        if (kind != EmcMachineBlock.Kind.CONDENSER || items[TARGET].isEmpty()) return slot == INPUT;
        return slot >= OUTPUT && !stack.is(items[TARGET].getItem());
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (kind != EmcMachineBlock.Kind.CONDENSER) return slot == OUTPUT;
        return slot >= OUTPUT && slot < items.length
                && (items[TARGET].isEmpty() || ItemStack.isSameItemSameComponents(stack, new ItemStack(items[TARGET].getItem())));
    }

    @Override public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return switch (kind) {
            case CONDENSER -> new CondenserMenu(id, inventory, this);
            case COLLECTOR -> new EmcMachineMenu(id, inventory, this, true);
            case RELAY -> new EmcMachineMenu(id, inventory, this, false);
        };
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) Containers.dropContents(server, pos, this);
        super.preRemoveSideEffects(pos, state);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
        output.putLong("emc", emc);
        if (pendingBurnEmc > 0) output.putLong("pending_burn_emc", pendingBurnEmc);
        if (collectorBonusTicks > 0) output.putInt("collector_bonus_ticks", collectorBonusTicks);
        for (int slot = 0; slot < items.length; slot++) {
            if (!items[slot].isEmpty()) output.store("slot_" + slot, ItemStack.CODEC, items[slot]);
        }
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", UUIDUtil.CODEC).orElseGet(() -> input.getString("Owner").map(value -> {
            try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
        }).orElse(null));
        emc = Math.clamp(input.getLongOr("emc", 0), 0, capacity);
        pendingBurnEmc = kind == EmcMachineBlock.Kind.RELAY
                ? Math.max(0, input.getLongOr("pending_burn_emc", 0)) : 0;
        collectorBonusTicks = kind == EmcMachineBlock.Kind.COLLECTOR
                ? Math.clamp(input.getIntOr("collector_bonus_ticks", 0), 0, COLLECTOR_INTERVAL - 1) : 0;
        for (int slot = 0; slot < items.length; slot++) {
            items[slot] = input.read("slot_" + slot, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        }
        if (items[INPUT].isEmpty()) items[INPUT] = input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
