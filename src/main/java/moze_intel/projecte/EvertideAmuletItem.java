package moze_intel.projecte;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class EvertideAmuletItem extends Item {
    private static final int MAX_CHARGE = 4;
    private static final int SHOT_LIFETIME = 32;
    private static final List<WaterShot> SHOTS = new ArrayList<>();

    private static final class WaterShot {
        final ServerPlayer owner;
        final ItemStack amulet;
        final ServerLevel level;
        final Vec3 velocity;
        Vec3 position;
        int age;

        WaterShot(ServerPlayer owner, ItemStack amulet) {
            this.owner = owner;
            this.amulet = amulet;
            level = (ServerLevel) owner.level();
            velocity = owner.getLookAngle().scale(1.5);
            position = owner.getEyePosition();
        }
    }

    EvertideAmuletItem(Properties properties) {
        super(properties);
    }

    static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(EvertideAmuletItem::tickShots);
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                SHOTS.removeIf(shot -> shot.level.getServer() == server));
    }

    public static boolean isCarried(Player player) {
        if (player.getOffhandItem().getItem() instanceof EvertideAmuletItem) return true;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof EvertideAmuletItem) return true;
        }
        return false;
    }

    static int charge(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr("EvertideCharge", 0), 0, MAX_CHARGE);
    }

    static void action(ServerPlayer player, int command, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof EvertideAmuletItem) || !player.isAlive() || player.isSpectator()
                || !player.mayBuild() || player.containerMenu != player.inventoryMenu) return;
        switch (command) {
            case 0, 1 -> {
                int charge = Math.clamp(charge(stack) + (command == 0 ? 1 : -1), 0, MAX_CHARGE);
                var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (charge == 0) tag.remove("EvertideCharge");
                else tag.putInt("EvertideCharge", charge);
                if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
                else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                player.sendSystemMessage(Component.literal("Evertide charge: " + charge + "/" + MAX_CHARGE), true);
            }
            case 2 -> {
                if (player.getCooldowns().isOnCooldown(stack)) return;
                SHOTS.add(new WaterShot(player, stack));
                player.getCooldowns().addCooldown(stack, 10);
            }
            case 3 -> {
                if (player.getCooldowns().isOnCooldown(stack)) return;
                int radius = charge(stack);
                if (radius > 0) {
                    freeze((ServerLevel) player.level(), player, stack, radius);
                    player.getCooldowns().addCooldown(stack, 10);
                } else {
                    player.sendSystemMessage(Component.literal("Charge the Evertide Amulet before freezing water"), true);
                }
            }
            default -> { }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.mayBuild() || player.isSpectator()) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        return placeWater(level, player, context.getItemInHand(), context.getClickedPos(),
                context.getClickedFace()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    static boolean placeWater(ServerLevel level, Player player, ItemStack stack, BlockPos clicked, Direction face) {
        if (!canChange(level, player, stack, clicked, face)) return false;
        var state = level.getBlockState(clicked);
        if (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON)) {
            return level.setBlock(clicked, Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, 3), Block.UPDATE_ALL);
        }
        int radius = charge(stack);
        if (radius == 0 && putWater(level, player, stack, clicked, face)) return true;
        BlockPos origin = state.canBeReplaced(Fluids.WATER) && !state.hasBlockEntity()
                ? clicked : clicked.relative(face);
        if (radius == 0) return putWater(level, player, stack, origin, face);
        if (player.getXRot() < -35) {
            boolean placed = false;
            for (int height = 0; height <= radius * 2; height++) {
                BlockPos target = origin.above(height);
                if (!putWater(level, player, stack, target, face)) break;
                placed = true;
            }
            return placed;
        }
        boolean placed = false;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                placed |= putWater(level, player, stack, origin.offset(x, 0, z), face);
            }
        }
        return placed;
    }

    private static boolean putWater(ServerLevel level, Player player, ItemStack stack, BlockPos pos, Direction face) {
        if (!canChange(level, player, stack, pos, face)) return false;
        var state = level.getBlockState(pos);
        if (state.is(Blocks.LAVA)) return coolLava(level, player, stack, pos);
        if (state.is(Blocks.WATER) && state.getFluidState().isSource()) return true;
        if (state.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(player, level, pos, state, Fluids.WATER)) {
            return container.placeLiquid(level, pos, state, Fluids.WATER.defaultFluidState());
        }
        // Classic Evertide works in hot dimensions too. Place the source directly.
        if (!state.canBeReplaced(Fluids.WATER) || state.hasBlockEntity()) return false;
        return level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static boolean canChange(ServerLevel level, Player player, ItemStack stack, BlockPos pos, Direction face) {
        return !level.isOutsideBuildHeight(pos) && level.isLoaded(pos)
                && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos)
                && player.mayUseItemAt(pos, face, stack);
    }

    private static boolean coolLava(ServerLevel level, Player player, ItemStack stack, BlockPos pos) {
        if (!canChange(level, player, stack, pos, Direction.UP)) return false;
        var lava = level.getBlockState(pos);
        if (!lava.is(Blocks.LAVA)) return false;
        Block result = lava.getFluidState().isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE;
        return level.setBlock(pos, result.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void freeze(ServerLevel level, Player player, ItemStack stack, int radius) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (!canChange(level, player, stack, pos, Direction.UP)) continue;
            var water = level.getBlockState(pos);
            if (water.is(Blocks.WATER) && water.getFluidState().isSource()) {
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void tickShots(MinecraftServer server) {
        for (var iterator = SHOTS.iterator(); iterator.hasNext();) {
            WaterShot shot = iterator.next();
            if (shot.level.getServer() != server) continue;
            if (!advance(shot)) iterator.remove();
        }
    }

    private static boolean advance(WaterShot shot) {
        ServerPlayer owner = shot.owner;
        if (!owner.isAlive() || owner.hasDisconnected() || owner.isSpectator() || !owner.mayBuild()
                || owner.level() != shot.level
                || (owner.getMainHandItem() != shot.amulet && owner.getOffhandItem() != shot.amulet)) return false;
        Vec3 next = shot.position.add(shot.velocity);
        BlockPos nextPos = BlockPos.containing(next);
        if (!shot.level.isLoaded(nextPos)) return false;
        var hit = shot.level.clip(new ClipContext(shot.position, next, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner));
        boolean impact = hit.getType() == HitResult.Type.BLOCK;
        Vec3 end = impact ? hit.getLocation() : next;
        for (int step = 1; step <= 3; step++) {
            Vec3 point = shot.position.lerp(end, step / 3.0);
            shot.level.sendParticles(ParticleTypes.SPLASH, point.x, point.y, point.z, 2, .04, .04, .04, 0);
            BlockPos sample = BlockPos.containing(point);
            coolLava(shot.level, owner, shot.amulet, sample);
            for (Direction direction : Direction.values()) {
                coolLava(shot.level, owner, shot.amulet, sample.relative(direction));
            }
        }
        if (impact) {
            putWater(shot.level, owner, shot.amulet, hit.getBlockPos().relative(hit.getDirection()), hit.getDirection());
            return false;
        }
        shot.position = next;
        if (++shot.age < SHOT_LIFETIME) return true;
        if (shot.velocity.y > 0.75 && shot.level.canHaveWeather() && shot.level.canSeeSky(nextPos)) {
            var weather = shot.level.getWeatherData();
            weather.setRainTime(6000);
            weather.setRaining(true);
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal("Evertide charge: " + charge(stack) + "/" + MAX_CHARGE));
        lines.accept(Component.literal("Right-click: place water. Aim up while charged for a fountain."));
        lines.accept(Component.literal("Hotbar or offhand: breathe and walk on water. Sneak to dive."));
        lines.accept(Component.literal("Charge, fire, and freeze use the ProjectE keybinds."));
    }
}
