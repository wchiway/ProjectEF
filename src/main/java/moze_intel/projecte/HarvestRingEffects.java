package moze_intel.projecte;

import java.util.HashSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

final class HarvestRingEffects {
    enum Work { GROW, HARVEST, FERTILIZE }

    private enum Planting {
        WHEAT(1), CANE(1), CACTUS(5), SAPLING(4);

        final int spacing;

        Planting(int spacing) {
            this.spacing = spacing;
        }
    }

    private HarvestRingEffects() { }

    static void tend(ServerLevel level, Player player, ItemStack ring, int radius, Work work) {
        tend(level, player, player.blockPosition(), radius, 2, work,
                pos -> ClassicRingItem.canChange(level, player, ring, pos));
    }

    static void tendFromPedestal(ServerLevel level, Player actor, ItemStack ring, BlockPos center) {
        int radius = 4;
        tend(level, actor, center, radius, radius, Work.HARVEST,
                pos -> Math.abs(pos.getY() - center.getY()) <= radius
                        && ClassicRingItem.canChange(level, actor, ring, pos));
    }

    private static void tend(ServerLevel level, Player player, BlockPos center, int radius, int height, Work work,
                             Predicate<BlockPos> canChange) {
        var spreadThisPulse = new HashSet<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -height, -radius), center.offset(radius, height, radius))) {
            if (!canChange.test(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop) {
                if (work == Work.HARVEST && crop.isMaxAge(state)) {
                    level.destroyBlock(pos, true, player);
                } else if (work == Work.FERTILIZE) {
                    level.setBlockAndUpdate(pos, crop.getStateForAge(crop.getMaxAge()));
                } else if (work != Work.GROW || level.getRandom().nextInt(4) == 0) {
                    grow(level, pos, state, crop);
                }
            } else if (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)) {
                tendColumn(level, player, pos, state, work, canChange);
            } else if (isSmallPlant(state) && !spreadThisPulse.contains(pos)) {
                if (work == Work.HARVEST) level.destroyBlock(pos, true, player);
                else if (work == Work.FERTILIZE || level.getRandom().nextInt(64) == 0) {
                    BlockPos target = pos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()));
                    if (canChange.test(target) && level.isEmptyBlock(target)
                            && state.canSurvive(level, target) && level.setBlockAndUpdate(target, state)) {
                        spreadThisPulse.add(target);
                    }
                }
            } else if (state.is(Blocks.FARMLAND) && state.getValue(FarmlandBlock.MOISTURE) < 7) {
                level.setBlockAndUpdate(pos, state.setValue(FarmlandBlock.MOISTURE, 7));
            } else if (work == Work.FERTILIZE && state.is(BlockTags.SAPLINGS)
                    && state.getBlock() instanceof BonemealableBlock sapling && level.getRandom().nextInt(3) == 0) {
                grow(level, pos, state, sapling);
            }
        }
    }

    private static void grow(ServerLevel level, BlockPos pos, BlockState state, BonemealableBlock plant) {
        if (plant.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION)) {
            plant.performBonemeal(level, level.getRandom(), pos, state, BonemealSource.INTERACTION);
        }
    }

    private static void tendColumn(ServerLevel level, Player player, BlockPos pos, BlockState state, Work work,
                                   Predicate<BlockPos> canChange) {
        var plant = state.getBlock();
        if (level.getBlockState(pos.below()).is(plant)) return;
        if (work == Work.GROW && level.getRandom().nextInt(8) != 0) return;
        BlockPos top = pos;
        while (!top.equals(pos.above(2)) && level.getBlockState(top.above()).is(plant)) top = top.above();
        if (top.equals(pos.above(2))) {
            if (work != Work.HARVEST) return;
            // Break from the top down. Sugar cane keeps its base for the next harvest.
            int bottom = state.is(Blocks.SUGAR_CANE) ? 1 : 0;
            for (int height = 2; height >= bottom; height--) {
                BlockPos target = pos.above(height);
                if (!canChange.test(target)) return;
                level.destroyBlock(target, true, player);
            }
        } else {
            BlockPos next = top.above();
            if (canChange.test(next) && level.isEmptyBlock(next)
                    && plant.defaultBlockState().canSurvive(level, next)) {
                level.setBlockAndUpdate(next, plant.defaultBlockState());
            }
        }
    }

    private static boolean isSmallPlant(BlockState state) {
        return state.is(BlockTags.SMALL_FLOWERS) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS);
    }

    static boolean plant(ServerLevel level, Player player, ItemStack ring, BlockPos soil, boolean arcana) {
        if (!ClassicRingItem.canChange(level, player, ring, soil)) return false;
        Planting type = plantingAt(level, soil);
        if (type == null) return false;
        int radius = arcana ? 1 : 2;
        boolean planted = false;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos base = soil.offset(x * type.spacing, 0, z * type.spacing);
                BlockPos target = base.above();
                if (ClassicRingItem.canChange(level, player, ring, base) && ClassicRingItem.canChange(level, player, ring, target)
                        && level.isEmptyBlock(target) && plantingAt(level, base) == type) {
                    planted |= plantOne(level, player, target, type);
                }
            }
        }
        return planted;
    }

    private static boolean plantOne(ServerLevel level, Player player, BlockPos target, Planting type) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack seed = inventory.getItem(slot);
            BlockState plant = switch (type) {
                case WHEAT -> seed.is(Items.WHEAT_SEEDS) ? Blocks.WHEAT.defaultBlockState() : null;
                case CANE -> seed.is(Items.SUGAR_CANE) ? Blocks.SUGAR_CANE.defaultBlockState() : null;
                case CACTUS -> seed.is(Items.CACTUS) ? Blocks.CACTUS.defaultBlockState() : null;
                case SAPLING -> seed.is(ItemTags.SAPLINGS) && seed.getItem() instanceof BlockItem sapling
                        ? sapling.getBlock().defaultBlockState() : null;
            };
            if (plant == null || !plant.canSurvive(level, target)) continue;
            if (type == Planting.SAPLING && hasNeighborSapling(level, target)) continue;
            if (level.setBlockAndUpdate(target, plant)) {
                seed.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    private static Planting plantingAt(ServerLevel level, BlockPos soil) {
        BlockState ground = level.getBlockState(soil);
        if (ground.is(Blocks.FARMLAND)) return Planting.WHEAT;
        if (ground.is(Blocks.SAND) || ground.is(Blocks.RED_SAND)) return Planting.CACTUS;
        if (ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK)) {
            return nearWater(level, soil) ? Planting.CANE : Planting.SAPLING;
        }
        return null;
    }

    private static boolean nearWater(ServerLevel level, BlockPos soil) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = soil.relative(direction);
            if (level.isLoaded(neighbor) && level.getFluidState(neighbor).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private static boolean hasNeighborSapling(ServerLevel level, BlockPos pos) {
        for (BlockPos neighbor : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
            if (level.getBlockState(neighbor).is(BlockTags.SAPLINGS)) return true;
        }
        return false;
    }
}
