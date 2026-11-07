package moze_intel.projecte;

import java.util.EnumSet;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class ClassicRingItem extends Item {
    private static final String CHARGE = "ClassicRingCharge";
    private static final int FUEL_TICKS = 200;

    enum Power {
        ZERO("zero", "Zero"), IGNITION("ignition", "Ignition"),
        WIND("wind", "Swiftwolf"), HARVEST("harvest", "Harvest");

        final String model;
        final String label;

        Power(String model, String label) {
            this.model = model;
            this.label = label;
        }
    }

    private final Power singlePower;
    private final WeakHashMap<ServerPlayer, Long> paidUntil = new WeakHashMap<>();

    ClassicRingItem(Properties properties, Power singlePower) {
        super(properties);
        this.singlePower = singlePower;
    }

    private boolean isArcana() {
        return singlePower == null;
    }

    private static String model(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY)
                .strings().stream().findFirst().orElse("");
    }

    private Power power(ItemStack stack) {
        if (!isArcana()) return singlePower;
        String model = model(stack);
        for (Power power : Power.values()) {
            if (model.equals(power.model + "_off") || model.equals(power.model + "_on")) return power;
        }
        return Power.ZERO;
    }

    private boolean active(ItemStack stack) {
        return isArcana() ? model(stack).equals(power(stack).model + "_on") : model(stack).equals("on");
    }

    private void setMode(ServerPlayer player, ItemStack stack, Power power, boolean active) {
        if (isArcana() || active) {
            String model = isArcana() ? power.model + (active ? "_on" : "_off") : "on";
            stack.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(), List.of(), List.of(model), List.of()));
        } else {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
        player.sendSystemMessage(Component.literal(power.label + (active ? " active" : " inactive")), true);
    }

    private int maxCharge(Power power) {
        return power == Power.IGNITION && !isArcana() ? 10 : 4;
    }

    private int charge(ItemStack stack, Power power) {
        return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr(CHARGE, 0), 0, maxCharge(power));
    }

    public boolean supportsControl(int command) {
        return command >= 0 && (command < 3 || command == 4 || command == 3 && isArcana()
                || command == 5 && !isArcana());
    }

    static void action(ServerPlayer player, int command, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ClassicRingItem ring) || !ring.supportsControl(command)
                || !canUse(player) || player.containerMenu != player.inventoryMenu) return;
        Power power = ring.power(stack);
        switch (command) {
            case 0, 1 -> {
                int max = ring.maxCharge(power);
                int next = Math.clamp(ring.charge(stack, power) + (command == 0 ? 1 : -1), 0, max);
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                    if (next == 0) tag.remove(CHARGE);
                    else tag.putInt(CHARGE, next);
                });
                player.sendSystemMessage(Component.literal(power.label + " charge " + next + "/" + max), true);
            }
            case 2 -> ring.release(player, stack, power);
            case 3 -> {
                Power next = Power.values()[(power.ordinal() + 1) % Power.values().length];
                ring.setMode(player, stack, next, false);
            }
            case 4 -> ring.setMode(player, stack, power, !ring.active(stack));
            case 5 -> ring.cast(player, stack, power);
            default -> { }
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!canUse(player)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        Power power = power(stack);
        if (power == Power.HARVEST || !isArcana() && power == Power.IGNITION && charge(stack, power) == 0) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (isArcana()) cast(serverPlayer, stack, power);
            else release(serverPlayer, stack, power);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null || !canUse(player) || power(stack) != Power.HARVEST
                || context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        if (context.getLevel() instanceof ServerLevel level && !player.getCooldowns().isOnCooldown(stack)) {
            if (!HarvestRingEffects.plant(level, player, stack, context.getClickedPos(), isArcana())) {
                return InteractionResult.PASS;
            }
            player.getCooldowns().addCooldown(stack, 5);
        }
        return InteractionResult.SUCCESS;
    }

    private void cast(ServerPlayer player, ItemStack stack, Power power) {
        if (player.getCooldowns().isOnCooldown(stack)) return;
        if (power == Power.HARVEST && !pay(player, ingredient -> ingredient.is(Items.BONE_MEAL), 4)) return;
        if (power == Power.WIND && !pay(player, ingredient -> ingredient.is(Items.GLOWSTONE_DUST), 2)) return;
        var level = (ServerLevel) player.level();
        switch (power) {
            case ZERO -> RingProjectiles.snowball(level, player);
            case IGNITION -> RingProjectiles.launch(level, player, stack, false, isArcana());
            case WIND -> RingProjectiles.launch(level, player, stack, true, false);
            case HARVEST -> HarvestRingEffects.tend(level, player, stack, 4, HarvestRingEffects.Work.FERTILIZE);
        }
        player.getCooldowns().addCooldown(stack, 10);
    }

    private void release(ServerPlayer player, ItemStack stack, Power power) {
        if (player.getCooldowns().isOnCooldown(stack)) return;
        ServerLevel level = (ServerLevel) player.level();
        switch (power) {
            case ZERO -> freeze(level, player, stack, 1 + charge(stack, power), 1, false);
            case IGNITION -> {
                if (charge(stack, power) == 0) return;
                fireWall(level, player, stack);
            }
            case WIND, HARVEST -> {
                cast(player, stack, power);
                return;
            }
        }
        player.getCooldowns().addCooldown(stack, 10);
    }

    private void fireWall(ServerLevel level, ServerPlayer player, ItemStack stack) {
        BlockPos origin = player.blockPosition();
        Direction forward = player.getDirection();
        Direction sideways = forward.getClockWise();
        if (isArcana()) {
            for (int distance = -30; distance <= 30; distance++) {
                for (int depth = 1; depth <= 3; depth++) {
                    lightSurface(level, player, stack, origin.relative(sideways, distance).relative(forward, depth));
                }
            }
        } else {
            int length = 3 * charge(stack, Power.IGNITION);
            for (int distance = 1; distance <= length; distance++) {
                lightSurface(level, player, stack, origin.relative(forward, distance));
            }
        }
    }

    static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RingProjectiles.tick(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) tickPlayer(player);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damage, amount) ->
                !(entity instanceof ServerPlayer player && damage.is(DamageTypeTags.IS_FIRE)
                        && anyCarried(player, ring -> ring.isArcana() || ring.singlePower == Power.IGNITION)));
    }

    static boolean hasArcana(Player player) {
        return anyCarried(player, ClassicRingItem::isArcana);
    }

    public static boolean protectsFarmland(Player player) {
        return anyCarried(player, ring -> ring.singlePower == Power.HARVEST);
    }

    private static boolean anyCarried(Player player, Predicate<ClassicRingItem> test) {
        for (int slot = 0; slot < 10; slot++) {
            ItemStack stack = carried(player, slot);
            if (stack.getItem() instanceof ClassicRingItem ring && test.test(ring)) return true;
        }
        return false;
    }

    private static ItemStack carried(Player player, int slot) {
        return slot == 9 ? player.getOffhandItem() : player.getInventory().getItem(slot);
    }

    static void tickPlayer(ServerPlayer player) {
        if (!canUse(player) || player.tickCount % 5 != 0) return;
        var level = (ServerLevel) player.level();
        boolean pulse = player.tickCount % 20 == 0;
        boolean protectFromFire = false;
        ItemStack extinguishingRing = ItemStack.EMPTY;
        boolean grow = false;
        var applied = EnumSet.noneOf(Power.class);
        ItemStack growthRing = ItemStack.EMPTY;
        for (int slot = 0; slot < 10; slot++) {
            ItemStack stack = carried(player, slot);
            if (!(stack.getItem() instanceof ClassicRingItem ring)) continue;
            Power power = ring.power(stack);
            protectFromFire |= ring.isArcana() || power == Power.IGNITION;
            if (!ring.isArcana() && power == Power.IGNITION) extinguishingRing = stack;
            if (power == Power.HARVEST) {
                grow = true;
                growthRing = stack;
            }
            if (!ring.active(stack) || !pulse && power != Power.WIND || applied.contains(power)) continue;
            if (!ring.fundAura(player, power)) {
                ring.setMode(player, stack, power, false);
                continue;
            }
            applied.add(power);
            switch (power) {
                case ZERO -> freeze(level, player, stack, 4, ring.isArcana() ? 4 : 1, true);
                case IGNITION -> igniteAura(level, player, stack, ring.isArcana() ? 4 : 1);
                case WIND -> SwiftwolfRingItem.repel(player);
                case HARVEST -> HarvestRingEffects.tend(level, player, stack, 4, HarvestRingEffects.Work.HARVEST);
            }
        }
        if (protectFromFire) player.clearFire();
        if (pulse && !extinguishingRing.isEmpty() && !applied.contains(Power.IGNITION)) {
            extinguish(level, player, extinguishingRing);
        }
        if (pulse && grow && !applied.contains(Power.HARVEST)) {
            HarvestRingEffects.tend(level, player, growthRing, 4, HarvestRingEffects.Work.GROW);
        }
    }

    private boolean fundAura(ServerPlayer player, Power power) {
        if (isArcana() || power == Power.ZERO || player.getAbilities().instabuild) return true;
        long time = player.level().getGameTime();
        if (time < paidUntil.getOrDefault(player, 0L)) return true;
        boolean paid = pay(player, ingredient -> ingredient.is(Items.REDSTONE) || power == Power.HARVEST
                && (ingredient.is(Items.COAL) || ingredient.is(Items.CHARCOAL)), 1);
        if (paid) paidUntil.put(player, time + FUEL_TICKS);
        return paid;
    }

    private static boolean pay(Player player, Predicate<ItemStack> match, int amount) {
        if (player.getAbilities().instabuild) return true;
        var inventory = player.getInventory();
        int available = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (match.test(stack)) available += stack.getCount();
        }
        if (available < amount) return false;
        for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!match.test(stack)) continue;
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
        inventory.setChanged();
        return true;
    }

    private static void freeze(ServerLevel level, Player player, ItemStack stack, int radius, int height, boolean lava) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -height, -radius),
                center.offset(radius, height, radius))) {
            if (!canChange(level, player, stack, pos)) continue;
            var state = level.getBlockState(pos);
            if (state.is(Blocks.WATER)) {
                level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
            } else if (lava && state.is(Blocks.LAVA) && state.getFluidState().isSource()) {
                level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
            } else if (state.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
                level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState());
            }
        }
    }

    private static void igniteAura(ServerLevel level, ServerPlayer player, ItemStack stack, int radius) {
        BlockPos center = player.blockPosition();
        int height = radius == 4 ? radius : 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -height, -radius),
                center.offset(radius, height, radius))) {
            if (height == 0) lightSurface(level, player, stack, pos);
            else light(level, player, stack, pos);
        }
        for (var target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3),
                entity -> entity != player && entity.isAlive() && player.distanceToSqr(entity) <= 9
                        && (!(entity instanceof Player other) || player.canHarmPlayer(other)))) {
            if (canChange(level, player, stack, target.blockPosition())) target.igniteForSeconds(5);
        }
    }

    private static void extinguish(ServerLevel level, ServerPlayer player, ItemStack stack) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (canChange(level, player, stack, pos) && level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                level.removeBlock(pos, false);
            }
        }
    }

    private static void lightSurface(ServerLevel level, Player player, ItemStack stack, BlockPos pos) {
        if (!canChange(level, player, stack, pos)) return;
        if (level.getBlockState(pos.below()).is(Blocks.OBSIDIAN)) {
            light(level, player, stack, pos.below());
        } else if (!light(level, player, stack, pos)) {
            light(level, player, stack, pos.above());
        }
    }

    static boolean light(ServerLevel level, Player player, ItemStack stack, BlockPos pos) {
        if (!canChange(level, player, stack, pos)) return false;
        var old = level.getBlockState(pos);
        if (old.is(Blocks.OBSIDIAN)) return level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
        if (!old.isAir()) return false;
        var fire = BaseFireBlock.getState(level, pos);
        return fire.canSurvive(level, pos) && level.setBlock(pos, fire, Block.UPDATE_ALL);
    }

    static boolean canChange(ServerLevel level, Player player, ItemStack stack, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.isLoaded(pos)
                && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos)
                && player.mayUseItemAt(pos, Direction.UP, stack);
    }

    static boolean canUse(Player player) {
        return player.isAlive() && !player.isSpectator() && player.mayBuild();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        Power power = power(stack);
        lines.accept(Component.literal(power.label + (active(stack) ? " active" : " inactive")
                + " | Charge " + charge(stack, power) + "/" + maxCharge(power)));
        String controls = switch (power) {
            case ZERO -> isArcana() ? "Right-click: throw snowball. Fire key: freeze area."
                    : "Left-click: throw snowball. Right-click: freeze area.";
            case IGNITION -> isArcana() ? "Right-click: throw fireball. Fire key: fire wall."
                    : "Left-click: throw fireball. Right-click while charged: fire wall.";
            case WIND -> "Right-click: lightning (2 glowstone dust). Active: repel.";
            case HARVEST -> isArcana() ? "Right-click: plant 3x3. Fire key: fertilize (4 bone meal)."
                    : "Right-click: plant a field. Left-click: fertilize (4 bone meal).";
        };
        lines.accept(Component.literal(controls));
        lines.accept(Component.literal(isArcana() ? "Crafting key: cycle powers. Mode key: toggle aura."
                : "Mode key: toggle aura. Charge key: increase power."));
        if (isArcana()) lines.accept(Component.literal("Hotbar or offhand: flight and fire protection."));
        else if (power == Power.IGNITION) lines.accept(Component.literal("Active: uses redstone. Hotbar: fire protection."));
        else if (power == Power.HARVEST) lines.accept(Component.literal("Active: harvests with redstone, coal or charcoal."));
    }
}
