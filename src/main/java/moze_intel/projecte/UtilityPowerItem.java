package moze_intel.projecte;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

final class UtilityPowerItem extends Item {
    enum Power {
        BODY, SOUL, LIFE, MIND, REPAIR, WATCH, ARCHANGEL, DIVINING, DENSITY, VOID, TOME
    }

    final Power power;
    private final int tier;
    private static final List<String> DENSITY_TARGETS = List.of("minecraft:iron_ingot", "minecraft:gold_ingot",
            "minecraft:diamond", "projecte:dark_matter", "projecte:red_matter");

    UtilityPowerItem(Properties properties, Power power, int tier) {
        super(properties);
        this.power = power;
        this.tier = tier;
    }

    static void tickPlayer(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.tickCount % 20 != 0) return;
        var inventory = player.getInventory();
        AlchemicalBagItem.repairCarried(player);
        boolean repaired = false;
        for (int slot = 0; slot <= inventory.getContainerSize(); slot++) {
            ItemStack stack = slot == inventory.getContainerSize() ? player.getOffhandItem() : inventory.getItem(slot);
            if (!(stack.getItem() instanceof UtilityPowerItem item)) continue;
            if (item.power == Power.REPAIR && !repaired) {
                repair(inventory);
                repaired = true;
            }
            if (slot >= 9 && slot < inventory.getContainerSize() || !active(stack)) continue;
            switch (item.power) {
                case BODY, SOUL, LIFE -> {
                    if (needsHealing(player, item.power)
                            && (player.getAbilities().instabuild || KleinStarItem.pay(player, 16)
                            || PETransmutationState.spendEmc(player, 16))) {
                        heal(player, item.power);
                    }
                }
                case WATCH -> {
                    if (player.getAbilities().instabuild || KleinStarItem.pay(player, 32)
                            || PETransmutationState.spendEmc(player, 32)) {
                        var level = (ServerLevel) player.level();
                        accelerate(level, player.blockPosition(), 3, player);
                    }
                }
                case DENSITY, VOID -> condense(player, stack);
                default -> { }
            }
        }
    }

    static void repair(Container inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isDamaged()) {
                stack.setDamageValue(stack.getDamageValue() - 1);
                inventory.setChanged();
            }
        }
    }

    static boolean hasTalisman(Container inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).getItem() instanceof UtilityPowerItem item && item.power == Power.REPAIR) return true;
        }
        return false;
    }

    private static boolean needsHealing(Player player, Power power) {
        return power != Power.BODY && player.getHealth() < player.getMaxHealth()
                || power != Power.SOUL && player.getFoodData().needsFood();
    }

    static void heal(Player player, Power power) {
        if (power != Power.BODY) player.heal(1);
        if (power != Power.SOUL && player.getFoodData().needsFood()) player.getFoodData().eat(1, .1F);
    }

    static void accelerate(ServerLevel level, BlockPos center, int radius, Player actor) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
            if (!ClassicRingItem.canChange(level, actor, ItemStack.EMPTY, pos)) continue;
            var state = level.getBlockState(pos);
            if (state.isRandomlyTicking()) state.randomTick(level, pos, level.getRandom());
        }
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(radius),
                entity -> entity instanceof Enemy)) {
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 1, true, false));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ClassicRingItem.canUse(player)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        switch (power) {
            case BODY, SOUL, LIFE, WATCH -> {
                setFlag(stack, !active(stack));
                PowerFuel.notice(server, active(stack) ? "Active" : "Inactive");
            }
            case DENSITY, VOID -> configureDensity(server, stack, hand);
            case MIND -> transferXp(server, stack);
            case ARCHANGEL -> {
                if (PowerFuel.spend(server, 64)) volley((ServerLevel) level, server);
            }
            case TOME -> {
                if (PETransmutationState.learnAll(server)) stack.consume(1, server);
            }
            default -> { return InteractionResult.PASS; }
        }
        player.getCooldowns().addCooldown(stack, 5);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (power != Power.DIVINING) return InteractionResult.PASS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!ClassicRingItem.canUse(player)) return InteractionResult.FAIL;
        var level = (ServerLevel) player.level();
        ItemStack stack = context.getItemInHand();
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        long total = 0, greatest = 0;
        int count = 0;
        int depth = switch (tier) { case 1 -> 3; case 2 -> 16; default -> 64; };
        Direction face = context.getClickedFace();
        Direction x = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        Direction y = face.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        var prices = PEEmcOverrides.forPlayer(player);
        for (int distance = 0; distance < depth; distance++) {
            BlockPos plane = context.getClickedPos().relative(face.getOpposite(), distance);
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                BlockPos pos = plane.relative(x, a).relative(y, b);
                if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) continue;
                long value = prices.value(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
                if (value > 0) {
                    count++;
                    greatest = Math.max(greatest, value);
                    total += Math.min(Long.MAX_VALUE - total, value);
                }
            }
        }
        PowerFuel.notice(player, "Average EMC " + (count == 0 ? 0 : total / count) + " · Highest " + greatest);
        player.getCooldowns().addCooldown(stack, 10);
        return InteractionResult.SUCCESS;
    }

    private static void volley(ServerLevel level, LivingEntity player) {
        for (int offset : new int[]{-5, 0, 5}) {
            Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), ItemStack.EMPTY);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + offset, 0, 3, 0);
            level.addFreshEntity(arrow);
        }
    }

    private static void transferXp(ServerPlayer player, ItemStack stack) {
        int stored = Math.clamp(data(stack).getIntOr("StoredXP", 0), 0, 1_000_000);
        if (player.isShiftKeyDown()) {
            int level = player.experienceLevel;
            double base = level <= 16 ? level * (level + 6.0)
                    : level <= 31 ? 2.5 * level * level - 40.5 * level + 360
                    : 4.5 * level * level - 162.5 * level + 2220;
            int points = (int) Math.min(1_000_000 - stored,
                    base + Math.round(player.experienceProgress * player.getXpNeededForNextLevel()));
            player.giveExperiencePoints(-points);
            stored += points;
        } else {
            player.giveExperiencePoints(stored);
            stored = 0;
        }
        int remaining = stored;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("StoredXP", remaining));
        PowerFuel.notice(player, stored + " XP stored");
    }

    private static void configureDensity(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            int mode = (mode(stack) + 1) % DENSITY_TARGETS.size();
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("DensityMode", mode));
            PowerFuel.notice(player, "Target " + DENSITY_TARGETS.get(mode));
        } else {
            var other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!other.isEmpty() && ItemStack.isSameItemSameComponents(other, new ItemStack(other.getItem()))) {
                String input = BuiltInRegistries.ITEM.getKey(other.getItem()).toString();
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("DensityInput", input));
            }
            setFlag(stack, !active(stack));
            PowerFuel.notice(player, active(stack) ? "Condensing " + data(stack).getStringOr("DensityInput", "") : "Inactive");
        }
    }

    private static void condense(ServerPlayer player, ItemStack stack) {
        var inventory = player.getInventory();
        var prices = PEEmcOverrides.forPlayer(player);
        String source = data(stack).getStringOr("DensityInput", "");
        String target = DENSITY_TARGETS.get(mode(stack));
        long unit = prices.value(source), targetValue = prices.value(target);
        if (unit <= 0 || targetValue <= unit || unit > 64_000_000 || targetValue > 64_000_000) return;
        long balance = data(stack).getLongOr("DensityEMC", 0);
        for (int slot = 0; slot < inventory.getContainerSize() && balance < targetValue; slot++) {
            ItemStack input = inventory.getItem(slot);
            if (input == stack || !BuiltInRegistries.ITEM.getKey(input.getItem()).toString().equals(source)
                    || !ItemStack.isSameItemSameComponents(input, new ItemStack(input.getItem()))) continue;
            int count = (int) Math.min(input.getCount(), (targetValue - balance + unit - 1) / unit);
            input.shrink(count);
            balance += unit * count;
        }
        if (balance >= targetValue) {
            ItemStack result = new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(target)));
            if (inventory.add(result)) balance -= targetValue;
        }
        long left = balance;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong("DensityEMC", left));
        inventory.setChanged();
    }

    private static net.minecraft.nbt.CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    static boolean containsValue(ItemStack stack) {
        return stack.getItem() instanceof UtilityPowerItem item
                && (item.power == Power.MIND && data(stack).getIntOr("StoredXP", 0) > 0
                || (item.power == Power.DENSITY || item.power == Power.VOID) && data(stack).getLongOr("DensityEMC", 0) > 0);
    }

    static ItemStack stripStoredValue(ItemStack stack) {
        if (!containsValue(stack)) return stack;
        ItemStack copy = stack.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> {
            tag.remove("StoredXP");
            tag.remove("DensityEMC");
        });
        return copy;
    }

    static boolean isActiveVoidRing(ItemStack stack) {
        return stack.getItem() instanceof UtilityPowerItem item && item.power == Power.VOID && active(stack);
    }

    private static boolean active(ItemStack stack) {
        return data(stack).getBooleanOr("PowerActive", false);
    }

    private static void setFlag(ItemStack stack, boolean active) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("PowerActive", active));
        if (stack.getItem() instanceof UtilityPowerItem item && switch (item.power) {
            case BODY, SOUL, LIFE, WATCH -> true;
            default -> false;
        }) {
            if (active) stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                    List.of(), List.of(), List.of("on"), List.of()));
            else stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    private static int mode(ItemStack stack) {
        return Math.clamp(data(stack).getIntOr("DensityMode", 0), 0, DENSITY_TARGETS.size() - 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        switch (power) {
            case MIND -> lines.accept(Component.literal("Sneak-use to store XP. Use to withdraw. " + data(stack).getIntOr("StoredXP", 0) + " XP"));
            case DENSITY, VOID -> lines.accept(Component.literal("Use with input in the other hand. Sneak-use to cycle target. " + DENSITY_TARGETS.get(mode(stack))));
            case WATCH, BODY, SOUL, LIFE -> lines.accept(Component.literal(active(stack) ? "Active" : "Inactive"));
            default -> { }
        }
    }
}
