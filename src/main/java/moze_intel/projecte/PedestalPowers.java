package moze_intel.projecte;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PedestalPowers {
    private static final Set<String> ITEMS = Set.of("harvest_goddess_band", "black_hole_band", "void_ring",
            "watch_of_flowing_time", "evertide_amulet", "volcanite_amulet", "repair_talisman", "ignition_ring",
            "zero_ring", "swiftwolf_rending_gale", "archangel_smite", "body_stone", "soul_stone", "life_stone");

    private PedestalPowers() { }

    private static String power(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(PECore.MODID) ? id.getPath() : "";
    }

    static boolean supports(ItemStack stack) {
        return ITEMS.contains(power(stack));
    }

    static void apply(ServerLevel level, BlockPos pos, Player actor, ItemStack item) {
        AABB bounds = new AABB(pos).inflate(4);
        switch (power(item)) {
            case "harvest_goddess_band" -> HarvestRingEffects.tendFromPedestal(level, actor, item, pos);
            case "black_hole_band", "void_ring" -> collectDrops(level, pos, bounds);
            case "watch_of_flowing_time" -> {
                UtilityPowerItem.accelerate(level, pos, 4, actor);
                for (BlockPos target : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 4, 4))) {
                    if (ClassicRingItem.canChange(level, actor, item, target)
                            && level.getBlockEntity(target) instanceof EmcMachineBlockEntity machine) {
                        EmcMachineBlockEntity.tick(level, target, machine.getBlockState(), machine);
                    }
                }
            }
            case "evertide_amulet", "volcanite_amulet" -> {
                if (!level.canHaveWeather()) return;
                boolean raining = item.getItem() instanceof EvertideAmuletItem;
                var weather = level.getWeatherData();
                if (raining) {
                    weather.setRainTime(Math.max(weather.getRainTime(), 600));
                    weather.setRaining(true);
                } else {
                    weather.setRaining(false);
                    weather.setThundering(false);
                    weather.setClearWeatherTime(600);
                }
            }
            case "zero_ring" -> {
                ClassicRingItem.freeze(level, actor, item, pos, 4, 4, true);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds)) entity.clearFire();
            }
            case "ignition_ring" -> {
                for (LivingEntity enemy : enemies(level, bounds)) {
                    if (ClassicRingItem.canChange(level, actor, item, enemy.blockPosition())) enemy.igniteForSeconds(4);
                }
            }
            case "swiftwolf_rending_gale", "archangel_smite" -> {
                if (Math.floorMod(level.getGameTime() - Math.floorMod(pos.hashCode(), 10), 40) != 0) return;
                for (LivingEntity enemy : enemies(level, bounds)) {
                    if (!ClassicRingItem.canChange(level, actor, item, enemy.blockPosition())) continue;
                    if (item.getItem() instanceof SwiftwolfRingItem) {
                        var lightning = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                        if (lightning != null) {
                            lightning.snapTo(enemy.position());
                            level.addFreshEntity(lightning);
                        }
                    } else {
                        Vec3 origin = Vec3.atBottomCenterOf(pos.above());
                        Arrow arrow = new Arrow(level, origin.x, origin.y, origin.z, new ItemStack(Items.ARROW), ItemStack.EMPTY);
                        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                        Vec3 aim = enemy.getEyePosition().subtract(origin);
                        arrow.shoot(aim.x, aim.y + aim.horizontalDistance() * .025, aim.z, 2, 0);
                        level.addFreshEntity(arrow);
                    }
                    break;
                }
            }
            case "repair_talisman" -> {
                if (Math.floorMod(level.getGameTime() - Math.floorMod(pos.hashCode(), 10), 20) != 0) return;
                for (Player player : level.getEntitiesOfClass(Player.class, bounds, p -> p.isAlive() && !p.isSpectator())) {
                    UtilityPowerItem.repair(player.getInventory());
                }
            }
            case "body_stone", "soul_stone", "life_stone" -> {
                if (!(item.getItem() instanceof UtilityPowerItem stone)) return;
                for (Player player : level.getEntitiesOfClass(Player.class, bounds, p -> p.isAlive() && !p.isSpectator())) {
                    UtilityPowerItem.heal(player, stone.power);
                }
            }
            default -> { }
        }
    }

    private static java.util.List<LivingEntity> enemies(ServerLevel level, AABB bounds) {
        return level.getEntitiesOfClass(LivingEntity.class, bounds, mob -> mob instanceof Enemy && mob.isAlive());
    }

    private static void collectDrops(ServerLevel level, BlockPos pos, AABB bounds) {
        Vec3 center = Vec3.atCenterOf(pos.above());
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bounds,
                drop -> drop.isAlive() && !drop.hasPickUpDelay())) {
            ItemStack remaining = item.getItem().copy();
            for (Direction side : Direction.values()) {
                var adjacent = pos.relative(side);
                if (level.isLoaded(adjacent) && level.getBlockEntity(adjacent) instanceof Container inventory) {
                    remaining = HopperBlockEntity.addItem(null, inventory, remaining, side.getOpposite());
                    if (remaining.isEmpty()) break;
                }
            }
            if (remaining.isEmpty()) {
                item.discard();
            } else {
                item.setItem(remaining);
                Vec3 pull = center.subtract(item.position());
                if (pull.lengthSqr() > .25) item.setDeltaMovement(pull.normalize().scale(.2));
            }
        }
    }
}
