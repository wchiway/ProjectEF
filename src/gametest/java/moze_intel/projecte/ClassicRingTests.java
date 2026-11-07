package moze_intel.projecte;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ClassicRingTests {
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;

    @GameTest
    public void zeroThrowsSnowAndFreezesLavaOnlyWithAura(GameTestHelper helper) {
        var player = player(helper);
        var level = helper.getLevel();
        var zero = ring("zero_ring");
        player.setItemInHand(HAND, zero);
        BlockPos water = player.blockPosition().east();
        BlockPos lava = player.blockPosition().west();
        level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(lava, Blocks.LAVA.defaultBlockState());

        ClassicRingItem.action(player, 0, HAND);
        zero.getItem().use(level, player, HAND);
        helper.assertTrue(level.getBlockState(water).is(Blocks.ICE), "Right click freezes water");
        helper.assertTrue(level.getBlockState(lava).is(Blocks.LAVA), "Right click leaves lava for the aura");
        clearCooldown(player, zero);
        ClassicRingItem.action(player, 5, HAND);
        helper.assertTrue(!level.getEntitiesOfClass(Snowball.class, player.getBoundingBox().inflate(3)).isEmpty(),
                "Attack launches a native snowball");

        ClassicRingItem.action(player, 4, HAND);
        player.tickCount = 20;
        ClassicRingItem.tickPlayer(player);
        helper.assertTrue(level.getBlockState(lava).is(Blocks.OBSIDIAN), "Active Zero turns lava sources into obsidian");
        assertModel(helper, zero, "on");
        helper.succeed();
    }

    @GameTest
    public void ignitionUsesRedstoneAndProtectsItsBearer(GameTestHelper helper) {
        var player = player(helper);
        var level = helper.getLevel();
        var ignition = ring("ignition_ring");
        player.setItemInHand(HAND, ignition);
        BlockPos origin = player.blockPosition();
        BlockPos obsidian = origin.relative(player.getDirection(), 3).below();
        level.setBlockAndUpdate(obsidian, Blocks.OBSIDIAN.defaultBlockState());
        ClassicRingItem.action(player, 0, HAND);
        ignition.getItem().use(level, player, HAND);
        helper.assertTrue(level.getBlockState(obsidian).is(Blocks.LAVA), "Charged fire wall melts obsidian");

        player.getInventory().setItem(5, new ItemStack(Items.REDSTONE, 2));
        ClassicRingItem.action(player, 4, HAND);
        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.setPos(player.position().add(0, 0, 2));
        player.tickCount = 20;
        ClassicRingItem.tickPlayer(player);
        helper.assertTrue(zombie.isOnFire() && player.getInventory().getItem(5).getCount() == 1,
                "Ignition aura lights nearby mobs and uses one redstone");
        player.tickCount = 40;
        ClassicRingItem.tickPlayer(player);
        helper.assertTrue(player.getInventory().getItem(5).getCount() == 1, "Redstone pays for the whole interval");
        float health = player.getHealth();
        player.hurtServer(level, level.damageSources().lava(), 4);
        helper.assertTrue(player.getHealth() == health, "Carried Ignition blocks lava damage");
        assertModel(helper, ignition, "on");
        helper.succeed();
    }

    @GameTest
    public void harvestPlantsFertilizesAndReaps(GameTestHelper helper) {
        var player = player(helper);
        var level = helper.getLevel();
        var harvest = ring("harvest_goddess_band");
        player.setItemInHand(HAND, harvest);
        BlockPos soil = player.blockPosition().east().below();
        level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState());
        level.removeBlock(soil.above(), false);
        player.getInventory().setItem(5, new ItemStack(Items.WHEAT_SEEDS));
        harvest.getItem().useOn(new UseOnContext(player, HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(soil.above()), Direction.UP, soil, false)));
        helper.assertTrue(level.getBlockState(soil.above()).is(Blocks.WHEAT)
                        && player.getInventory().getItem(5).isEmpty(),
                "Using the Harvest band on farmland plants an inventory seed");
        ((FarmlandBlock) Blocks.FARMLAND).fallOn(level, level.getBlockState(soil), soil, player, 2);
        helper.assertTrue(level.getBlockState(soil).is(Blocks.FARMLAND), "The band prevents farmland trampling");

        clearCooldown(player, harvest);
        player.getInventory().setItem(5, new ItemStack(Items.BONE_MEAL, 5));
        ClassicRingItem.action(player, 5, HAND);
        var wheat = (CropBlock) Blocks.WHEAT;
        helper.assertTrue(wheat.isMaxAge(level.getBlockState(soil.above()))
                        && player.getInventory().getItem(5).getCount() == 1,
                "Fertilizing matures crops and costs four bone meal");

        player.getInventory().setItem(6, new ItemStack(Items.CHARCOAL));
        ClassicRingItem.action(player, 4, HAND);
        player.tickCount = 20;
        ClassicRingItem.tickPlayer(player);
        helper.assertTrue(level.isEmptyBlock(soil.above()) && player.getInventory().getItem(6).isEmpty(),
                "Harvest aura reaps the mature crop and consumes charcoal");
        assertModel(helper, harvest, "on");
        helper.succeed();
    }

    @GameTest
    public void arcanaCyclesColorsAndGrantsFreeFlight(GameTestHelper helper) {
        var player = player(helper);
        var arcana = ring("arcana_ring");
        player.setItemInHand(HAND, arcana);
        SwiftwolfRingItem.tickPlayer(player);
        helper.assertTrue(player.getAbilities().mayfly, "Default Arcana allows flight with no EMC");
        player.getAbilities().flying = true;
        for (String mode : List.of("ignition", "wind", "harvest", "zero")) {
            ClassicRingItem.action(player, 3, HAND);
            assertModel(helper, arcana, mode + "_off");
            ClassicRingItem.action(player, 4, HAND);
            assertModel(helper, arcana, mode + "_on");
            SwiftwolfRingItem.tickPlayer(player);
            helper.assertTrue(player.getAbilities().flying, "Arcana keeps flight in every mode");
        }
        ClassicRingItem.action(player, 3, HAND);
        ClassicRingItem.action(player, 3, HAND);
        ClassicRingItem.action(player, 4, HAND);
        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.setPos(player.position().add(2, 0, 0));
        player.tickCount = 5;
        ClassicRingItem.tickPlayer(player);
        helper.assertTrue(zombie.getDeltaMovement().x > 0, "Yellow Arcana repels mobs");
        player.setItemInHand(HAND, ItemStack.EMPTY);
        SwiftwolfRingItem.tickPlayer(player);
        helper.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying, "Stowing Arcana revokes flight");
        helper.succeed();
    }

    @GameTest
    public void arcanaFireAndGaleProjectilesReachTheirTargets(GameTestHelper helper) {
        var player = player(helper);
        var level = helper.getLevel();
        var arcana = ring("arcana_ring");
        player.setItemInHand(HAND, arcana);
        player.setXRot(0);
        player.setYRot(0);
        BlockPos target = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(3)));
        level.setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(target, Blocks.SAND.defaultBlockState());
        ClassicRingItem.action(player, 3, HAND);
        arcana.getItem().use(level, player, HAND);
        for (int tick = 0; tick < 4; tick++) RingProjectiles.tick(level.getServer());
        helper.assertTrue(level.getBlockState(target).is(Blocks.GLASS), "Red Arcana fireball turns sand into glass");

        ClassicRingItem.action(player, 3, HAND);
        clearCooldown(player, arcana);
        player.getInventory().setItem(5, new ItemStack(Items.GLOWSTONE_DUST, 3));
        arcana.getItem().use(level, player, HAND);
        for (int tick = 0; tick < 4; tick++) RingProjectiles.tick(level.getServer());
        helper.assertTrue(player.getInventory().getItem(5).getCount() == 1
                        && !level.getEntities(EntityTypes.LIGHTNING_BOLT, player.getBoundingBox().inflate(6),
                        bolt -> true).isEmpty(),
                "Yellow Arcana strikes with lightning for two glowstone dust");
        helper.succeed();
    }

    @GameTest
    public void classicRingRecipesReturnCatalysts(GameTestHelper helper) {
        var zero = ring("zero_ring");
        var harvest = ring("harvest_goddess_band");
        zero.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Snow"));
        var matter = ring("red_matter");
        var arcanaInput = CraftingInput.of(3, 3, List.of(zero, ring("ignition_ring"), harvest,
                ring("swiftwolf_rending_gale"), matter, matter, matter, matter, matter));
        var recipe = recipe(helper, "arcana_ring");
        var level = helper.getLevel();
        helper.assertTrue(recipe.matches(arcanaInput, level), "Classic layout matches the Arcana recipe");
        var remainder = recipe.getRemainingItems(arcanaInput);
        helper.assertTrue(ItemStack.isSameItemSameComponents(zero, remainder.get(0))
                        && ItemStack.isSameItemSameComponents(harvest, remainder.get(2))
                        && remainder.get(1).isEmpty() && remainder.get(3).isEmpty(),
                "Arcana crafting preserves both catalyst rings and their data");

        var arcana = recipe.assemble(arcanaInput);
        for (var catalyst : List.of(arcana, zero)) {
            var input = CraftingInput.of(2, 1, List.of(catalyst, new ItemStack(Items.WATER_BUCKET)));
            var ice = recipe(helper, "conversions/water_to_ice");
            helper.assertTrue(ice.matches(input, level) && ice.assemble(input).is(Items.ICE)
                            && ItemStack.isSameItemSameComponents(ice.getRemainingItems(input).getFirst(), catalyst)
                            && ice.getRemainingItems(input).get(1).is(Items.BUCKET),
                    "Ice conversion returns the ring and the bucket");
        }
        for (var catalyst : List.of(arcana, harvest)) {
            var input = CraftingInput.of(2, 1, List.of(catalyst, new ItemStack(Items.DIRT)));
            var grass = recipe(helper, "conversions/dirt_to_grass");
            helper.assertTrue(grass.matches(input, level) && grass.assemble(input).is(Items.GRASS_BLOCK)
                            && ItemStack.isSameItemSameComponents(grass.getRemainingItems(input).getFirst(), catalyst),
                    "Grass conversion returns the ring");
        }
        helper.succeed();
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String id) {
        return (CraftingRecipe) helper.getLevel().getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, PECore.id(id))).orElseThrow().value();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setUUID(UUID.randomUUID());
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        return player;
    }

    private static ItemStack ring(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(PECore.id(name)));
    }

    private static void clearCooldown(ServerPlayer player, ItemStack ring) {
        var cooldowns = player.getCooldowns();
        cooldowns.removeCooldown(cooldowns.getCooldownGroup(ring));
    }

    private static void assertModel(GameTestHelper helper, ItemStack ring, String model) {
        helper.assertTrue(ring.get(DataComponents.CUSTOM_MODEL_DATA).strings().equals(List.of(model)),
                "Ring uses model state " + model);
    }
}
