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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ClassicExpansionTests {
    @GameTest
    public void collectorsEmitGlowstoneLight(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 7, 1));
        int glowstoneLight = Blocks.GLOWSTONE.defaultBlockState().getLightEmission();
        var positions = List.of(origin, origin.east(2), origin.south(2));
        for (int tier = 1; tier <= positions.size(); tier++) {
            BlockPos pos = positions.get(tier - 1);
            placeMachine(helper, pos, "collector_mk" + tier);
            helper.assertTrue(level.getBlockState(pos).getLightEmission() == glowstoneLight,
                    "Collector Mk " + tier + " emits the same light level as glowstone");
        }
        helper.succeedWhen(() -> {
            for (BlockPos pos : positions) {
                helper.assertTrue(level.getBrightness(LightLayer.BLOCK, pos.above()) == glowstoneLight - 1,
                        "Collector light propagates into the air above it");
            }
        });
    }

    @GameTest(maxTicks = 50)
    public void collectorRelayCondenserChainMakesItems(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var collector = placeMachine(helper, pos, "collector_mk1");
        placeMachine(helper, pos.east(), "relay_mk1");
        var condenser = placeMachine(helper, pos.east(2), "energy_condenser");
        level.setBlockAndUpdate(pos.above(), Blocks.SEA_LANTERN.defaultBlockState());
        condenser.setItem(EmcMachineBlockEntity.TARGET, new ItemStack(Items.DIRT));

        helper.runAfterDelay(32, () -> {
            helper.assertTrue(condenser.getItem(EmcMachineBlockEntity.OUTPUT).is(Items.DIRT),
                    "Light generates EMC that crosses the relay and makes copies at the condenser");
            helper.assertTrue(condenser.getItem(EmcMachineBlockEntity.TARGET).getCount() == 1,
                    "The original target is never consumed");
            helper.assertTrue(collector.getItem(EmcMachineBlockEntity.INPUT).isEmpty(), "No fuel items were consumed");
            helper.succeed();
        });
    }

    @GameTest
    public void relaySavesAndReleasesOversizedFuel(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var relay = placeMachine(helper, pos, "relay_mk3");
        ItemStack helmet = item("gem_helmet");
        long value = PEEmcOverrides.forPlayer(player).value(helmet);
        helper.assertTrue(value > relay.capacity() && relay.canPlaceItem(EmcMachineBlockEntity.INPUT, helmet),
                "An oversized Gem Helmet is accepted as fuel");

        relay.setItem(EmcMachineBlockEntity.INPUT, helmet);
        EmcMachineBlockEntity.tick(level, pos, relay.getBlockState(), relay);
        helper.assertTrue(relay.getItem(EmcMachineBlockEntity.INPUT).isEmpty()
                        && relay.storedEmc() == relay.capacity() && relay.pendingBurnEmc() == value - relay.capacity(),
                "The relay burns the item once and queues all EMC above capacity");

        var save = relay.saveWithoutMetadata(level.registryAccess());
        relay.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), save));
        player.openMenu(relay);
        helper.assertTrue(((EmcMachineMenu) player.containerMenu).pendingBurnEmc() == value - relay.capacity(),
                "The saved queue appears in the relay menu after reload");
        player.closeContainer();

        ItemStack starStack = item("klein_star_ein");
        relay.setItem(EmcMachineBlockEntity.STAR, starStack);
        relay.setItem(EmcMachineBlockEntity.INPUT, new ItemStack(Items.DIRT));
        EmcMachineBlockEntity.tick(level, pos, relay.getBlockState(), relay);
        EmcMachineBlockEntity.tick(level, pos, relay.getBlockState(), relay);
        long charge = ((KleinStarItem) starStack.getItem()).stored(starStack);
        helper.assertTrue(charge > 0 && relay.pendingBurnEmc() < value - relay.capacity()
                        && relay.storedEmc() + relay.pendingBurnEmc() + charge == value
                        && relay.getItem(EmcMachineBlockEntity.INPUT).is(Items.DIRT),
                "The queue resumes when capacity frees up, conserving EMC and finishing before the next item");
        helper.succeed();
    }

    @GameTest
    public void oldCondenserSaveStillDepositsIntoOwnersAccount(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = placeMachine(helper, pos, "energy_condenser");
        var owner = player(helper);
        var legacy = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        legacy.putString("Owner", owner.getUUID().toString());
        legacy.store("Item", ItemStack.CODEC, new ItemStack(Items.DIRT, 4));
        machine.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), legacy.buildResult()));
        EmcMachineBlockEntity.tick(level, pos, machine.getBlockState(), machine);

        helper.assertTrue(machine.getItem(EmcMachineBlockEntity.INPUT).isEmpty() && PETransmutationState.balance(owner) == 4,
                "Existing owner and inventory data are migrated and the old deposit mode still works");
        helper.succeed();
    }

    @GameTest
    public void condenserMenuUsesSharedGridAndPreservesOldInventory(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = placeMachine(helper, pos, "energy_condenser");
        var player = player(helper);
        machine.bind(player.getUUID());
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        saved.putLong("emc", PEEmcOverrides.forPlayer(player).value(new ItemStack(Items.DIAMOND)) - 1);
        saved.store("slot_2", ItemStack.CODEC, new ItemStack(Items.DIAMOND));
        saved.store("slot_3", ItemStack.CODEC, new ItemStack(Items.DIAMOND, 3));
        machine.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));

        player.openMenu(machine);
        var menu = (CondenserMenu) player.containerMenu;
        helper.assertTrue(menu.slots.size() == 3 + 91 + 36 && menu.targetSlot().getItem().is(Items.DIAMOND)
                        && menu.getSlot(3).getItem().getCount() == 3,
                "The 91-slot condenser menu opens with the existing target and output in their expected slots");

        var last = menu.getSlot(3 + 90);
        last.set(new ItemStack(Items.DIRT, 2));
        EmcMachineBlockEntity.tick(level, pos, machine.getBlockState(), machine);
        helper.assertTrue(last.getItem().getCount() == 1 && menu.getSlot(3).getItem().getCount() == 4
                        && menu.targetSlot().getItem().getCount() == 1,
                "Fuel from the last grid slot makes another target item without burning existing copies");
        int fuelSlot = last.getContainerSlot();
        int productSlot = menu.getSlot(3).getContainerSlot();
        helper.assertTrue(!machine.canTakeItemThroughFace(fuelSlot, last.getItem(), net.minecraft.core.Direction.DOWN)
                        && machine.canTakeItemThroughFace(productSlot, menu.getSlot(3).getItem(), net.minecraft.core.Direction.DOWN),
                "Hoppers pull produced items and leave fuel while a target is set");

        menu.targetSlot().set(ItemStack.EMPTY);
        EmcMachineBlockEntity.tick(level, pos, machine.getBlockState(), machine);
        helper.assertTrue(last.getItem().getCount() == 1 && menu.getSlot(3).getItem().getCount() == 4,
                "Clearing the target leaves fuel and products untouched in the shared grid");
        player.closeContainer();
        helper.succeed();
    }

    @GameTest
    public void condenserMenuSyncsFullEmcValues(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = placeMachine(helper, pos, "energy_condenser");
        var player = player(helper);
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        saved.putLong("emc", 4_345_678_901L);
        saved.store("slot_2", ItemStack.CODEC, item("klein_star_omega"));
        machine.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));

        var server = new CondenserMenu(1, player.getInventory(), machine);
        var client = new CondenserMenu(1, player.getInventory());
        server.addSlotListener(new net.minecraft.world.inventory.ContainerListener() {
            @Override public void slotChanged(net.minecraft.world.inventory.AbstractContainerMenu menu, int index, ItemStack stack) { }

            @Override public void dataChanged(net.minecraft.world.inventory.AbstractContainerMenu menu, int index, int value) {
                client.setData(index, (short) value);
            }
        });
        server.broadcastChanges();
        helper.assertTrue(client.storedEmc() == 4_345_678_901L && client.targetEmc() == machine.condenserTargetEmc(),
                "The EMC bar receives the exact buffer and target price through 16-bit menu fields");
        helper.succeed();
    }

    @GameTest
    public void condenserMakesTargetsAboveItsOldCapacity(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = placeMachine(helper, pos, "energy_condenser");
        var player = player(helper);
        machine.bind(player.getUUID());
        ItemStack tome = item("tome");
        long cost = PEEmcOverrides.forPlayer(player).value(tome);
        helper.assertTrue(cost > 64_000_000, "The Tome costs more than the previous condenser limit");
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        saved.putLong("emc", cost - 1);
        saved.store("slot_2", ItemStack.CODEC, tome);
        saved.store("slot_0", ItemStack.CODEC, new ItemStack(Items.DIRT));
        machine.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));

        EmcMachineBlockEntity.tick(level, pos, machine.getBlockState(), machine);
        helper.assertTrue(machine.getItem(EmcMachineBlockEntity.OUTPUT).is(tome.getItem())
                        && machine.getItem(EmcMachineBlockEntity.TARGET).getCount() == 1
                        && machine.storedEmc() == 0,
                "Fuel completes the Tome's price, leaves the template intact and charges the full cost");
        helper.succeed();
    }

    @GameTest
    public void collectorsAndRelaysOpenMachineMenus(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var player = player(helper);
        var collector = placeMachine(helper, pos, "collector_mk1");
        var relay = placeMachine(helper, pos.east(), "relay_mk1");
        ItemStack fuel = new ItemStack(Items.CHARCOAL);
        ItemStack star = item("klein_star_ein");

        collector.setItem(EmcMachineBlockEntity.TARGET, new ItemStack(Items.DIRT));
        player.openMenu(collector);
        var menu = (EmcMachineMenu) player.containerMenu;
        helper.assertTrue(menu.isCollector() && menu.slots.size() == 41
                        && menu.capacity() == collector.capacity() && menu.lightLevel() == collector.lightLevel()
                        && menu.getSlot(0).mayPlace(fuel) && menu.getSlot(1).mayPlace(star)
                        && !menu.getSlot(2).mayPlace(fuel),
                "Collector opens with live data and filtered fuel, Star and output slots");
        menu.getSlot(5).set(star.copy());
        menu.quickMoveStack(player, 5);
        helper.assertTrue(collector.getItem(EmcMachineBlockEntity.STAR).is(star.getItem()) && !menu.getSlot(5).hasItem(),
                "Shift-click routes a Klein Star into the collector's charging slot");
        var legacy = menu.getSlot(3);
        helper.assertTrue(legacy.isActive() && !legacy.mayPlace(fuel) && legacy.getItem().is(Items.DIRT),
                "The old HopperMenu slot remains visible while it contains saved items");
        menu.quickMoveStack(player, 3);
        helper.assertTrue(!legacy.isActive() && player.getInventory().contains(new ItemStack(Items.DIRT)),
                "Players can reclaim items left in old slots");
        player.closeContainer();

        player.openMenu(relay);
        menu = (EmcMachineMenu) player.containerMenu;
        helper.assertTrue(!menu.isCollector() && menu.slots.size() == 41 && menu.capacity() == relay.capacity()
                        && menu.getSlot(0).mayPlace(fuel) && menu.getSlot(1).mayPlace(star),
                "Relay opens with burn input, charging slot and EMC capacity");
        player.closeContainer();
        helper.succeed();
    }

    @GameTest
    public void filledMapsSellWithoutLearning(GameTestHelper helper) {
        var player = player(helper);
        var map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, new MapId(42));
        long price = PEEmcOverrides.forPlayer(player).value(new ItemStack(Items.MAP));
        PETransmutationState.deposit(helper.getLevel().getServer(), player.getUUID(), 1, 1);
        PETransmutationState.sellStack(player, map, 1);
        helper.assertTrue(price > 0 && map.isEmpty() && PETransmutationState.balance(player) == price + 1
                        && PETransmutationState.knowledgeRevision(player) == 0,
                "A filled map sells at the blank-map price and does not teach a map");
        var blank = new ItemStack(Items.MAP);
        PETransmutationState.sellStack(player, blank, 1);
        helper.assertTrue(blank.isEmpty() && PETransmutationState.balance(player) == 2 * price + 1
                        && PETransmutationState.knowledgeRevision(player) == 1,
                "A blank map can still be learned");
        helper.succeed();
    }

    @GameTest
    public void lavaBucketCraftReturnsVolcaniteAmulet(GameTestHelper helper) {
        ItemStack amulet = item("volcanite_amulet");
        amulet.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("My Amulet"));
        var input = CraftingInput.of(3, 1, List.of(amulet, new ItemStack(Items.BUCKET), new ItemStack(Items.REDSTONE)));
        var recipe = recipe(helper, "conversions/redstone_to_lava");
        var remainder = recipe.getRemainingItems(input);
        helper.assertTrue(recipe.matches(input, helper.getLevel()) && recipe.assemble(input).is(Items.LAVA_BUCKET)
                        && ItemStack.matches(remainder.getFirst(), amulet) && remainder.get(1).isEmpty(),
                "Crafting a lava bucket consumes the bucket and preserves the named amulet");
        ItemStack omega = item("klein_star_omega");
        ((KleinStarItem) omega.getItem()).charge(omega, Long.MAX_VALUE);
        var armorInput = CraftingInput.of(2, 2, List.of(item("rm_chestplate"), amulet, item("body_stone"), omega));
        var armorRecipe = recipe(helper, "gem_chestplate");
        helper.assertTrue(armorRecipe.matches(armorInput, helper.getLevel())
                        && armorRecipe.getRemainingItems(armorInput).get(1).isEmpty(),
                "The Gem Chestplate still consumes its amulet ingredient");
        helper.succeed();
    }

    @GameTest
    public void maxChargeMatterMiningHonorsLootBallPreference(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(helper);
        BlockPos center = helper.absolutePos(new BlockPos(1, 7, 1));
        var commands = level.getServer().getCommands();
        helper.assertTrue(!MiningDropPreferences.usesLootBalls(player),
                "A survival player starts with Loot Balls disabled without running a command");

        BlockPos edge = center.east(3);
        BlockPos outside = center.east(4);
        level.setBlockAndUpdate(edge, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(outside, Blocks.STONE.defaultBlockState());
        ItemStack pickaxe = item("rm_pick");
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        chargeMatterTool(level, player, pickaxe, 2);
        var hit = new BlockHitResult(Vec3.atCenterOf(center), Direction.UP, center, false);
        pickaxe.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(level.getBlockState(edge).is(Blocks.STONE), "Charge 2 stops at radius 2");
        chargeMatterTool(level, player, pickaxe, 1);
        player.getCooldowns().removeCooldown(PECore.id("rm_pick"));
        pickaxe.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(2));
        helper.assertTrue(level.isEmptyBlock(edge) && level.getBlockState(outside).is(Blocks.STONE)
                        && drops.size() == 1 && drops.getFirst().getItem().is(Items.COBBLESTONE),
                "Charge 3 mines out to radius 3 and drops regular items by default");

        drops.getFirst().discard();
        commands.performPrefixedCommand(player.createCommandSourceStack(), "projecte lootballs on");
        helper.assertTrue(MiningDropPreferences.usesLootBalls(player) && !MiningDropPreferences.usesLootBalls(player(helper)),
                "Enabling Loot Balls affects only the player who opted in");
        level.setBlockAndUpdate(edge, Blocks.STONE.defaultBlockState());
        ItemStack morningStar = item("rm_morning_star");
        player.setItemInHand(InteractionHand.MAIN_HAND, morningStar);
        chargeMatterTool(level, player, morningStar, 3);
        morningStar.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(2));
        helper.assertTrue(level.isEmptyBlock(edge) && drops.size() == 1
                        && drops.getFirst().getItem().is(item("loot_ball").getItem()),
                "The Morning Star reaches radius 3 and packs drops when the preference is on");
        commands.performPrefixedCommand(player.createCommandSourceStack(), "projecte lootballs off");
        helper.assertTrue(!MiningDropPreferences.usesLootBalls(player), "A player can clear their opt-in");
        helper.succeed();
    }

    private static void chargeMatterTool(net.minecraft.server.level.ServerLevel level, ServerPlayer player,
                                         ItemStack stack, int steps) {
        player.setShiftKeyDown(true);
        for (int i = 0; i < steps; i++) stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
    }

    @GameTest
    public void catalystAndLensesChargeOnlyWhenTheyMine(GameTestHelper helper) {
        var player = player(helper);
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 7, 1));
        PETransmutationState.deposit(level.getServer(), player.getUUID(), 192, 1);

        var hit = new BlockHitResult(Vec3.atCenterOf(center), Direction.UP, center, false);
        long expectedBalance = 192;
        for (String name : List.of("destruction_catalyst", "catalytic_lens", "hyperkinetic_lens")) {
            ItemStack tool = item(name);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            level.setBlockAndUpdate(center, Blocks.OBSIDIAN.defaultBlockState());
            tool.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(level.getBlockState(center).is(Blocks.OBSIDIAN)
                            && PETransmutationState.balance(player) == expectedBalance
                            && !player.getCooldowns().isOnCooldown(tool),
                    "A failed mining attempt spends no EMC and adds no cooldown");

            level.setBlockAndUpdate(center, Blocks.STONE.defaultBlockState());
            tool.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            expectedBalance -= 64;
            helper.assertTrue(level.isEmptyBlock(center) && PETransmutationState.balance(player) == expectedBalance,
                    "Breaking a block spends the normal EMC cost");
        }
        helper.succeed();
    }

    @GameTest
    public void mercurialEyeExtendsAndTransmutesAtTheExpectedCost(GameTestHelper helper) {
        var player = player(helper);
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 2));
        ItemStack eye = item("mercurial_eye");
        ItemStack sample = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, eye);
        player.setItemInHand(InteractionHand.OFF_HAND, sample);
        var prices = PEEmcOverrides.forPlayer(player);
        long stone = prices.value(sample);
        long obsidian = prices.value(new ItemStack(Items.OBSIDIAN));
        level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(pos.above().east(), Blocks.CHEST.defaultBlockState());
        PETransmutationState.deposit(level.getServer(), player.getUUID(), 8 * stone, 1);
        MercurialEyeItem.action(player, 4, InteractionHand.MAIN_HAND);
        MercurialEyeItem.action(player, 0, InteractionHand.MAIN_HAND);
        useEye(player, eye, pos, Direction.UP);

        int placed = 0;
        for (BlockPos plane : BlockPos.spiralAround(pos.above(), 1, Direction.EAST, Direction.SOUTH)) {
            if (level.getBlockState(plane).is(Blocks.STONE)) placed++;
        }
        helper.assertTrue(placed == 8 && level.getBlockState(pos.above().east()).is(Blocks.CHEST)
                        && sample.getCount() == 1 && PETransmutationState.balance(player) == 0,
                "Extension places a 3×3 plane, skips the chest and charges for eight blocks");

        MercurialEyeItem.action(player, 4, InteractionHand.MAIN_HAND);
        player.getCooldowns().removeCooldown(PECore.id("mercurial_eye"));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OBSIDIAN));
        PETransmutationState.deposit(level.getServer(), player.getUUID(), obsidian - stone, 1);
        useEye(player, eye, pos.above(), Direction.UP);
        helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.OBSIDIAN)
                        && level.getBlockState(pos.above().west()).is(Blocks.STONE)
                        && PETransmutationState.balance(player) == 0,
                "Transmutation starts at the clicked block and stops when the price difference cannot be paid");
        helper.succeed();
    }

    @GameTest
    public void mercurialEyeCreatesSingleBlocksAndPillars(GameTestHelper helper) {
        var player = player(helper);
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 2));
        ItemStack eye = item("mercurial_eye");
        ItemStack sample = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, eye);
        player.setItemInHand(InteractionHand.OFF_HAND, sample);
        long stone = PEEmcOverrides.forPlayer(player).value(sample);
        PETransmutationState.deposit(level.getServer(), player.getUUID(), 27 * stone, 1);
        level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());

        useEye(player, eye, pos, Direction.UP);
        helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.STONE)
                        && level.isEmptyBlock(pos.above().east()) && PETransmutationState.balance(player) == 26 * stone,
                "An Eye with default components keeps single-block placement");

        player.getCooldowns().removeCooldown(PECore.id("mercurial_eye"));
        for (int i = 0; i < 3; i++) MercurialEyeItem.action(player, 4, InteractionHand.MAIN_HAND);
        useEye(player, eye, pos, Direction.UP);
        for (BlockPos pillar : BlockPos.betweenClosed(pos.offset(-1, 1, -1), pos.offset(1, 3, 1))) {
            helper.assertTrue(level.getBlockState(pillar).is(Blocks.STONE), "Pillar fills a 3×3×3 volume");
        }
        helper.assertTrue(level.isEmptyBlock(pos.above(4)) && PETransmutationState.balance(player) == 0
                        && sample.getCount() == 1,
                "Pillar leaves the pre-existing block and sample intact and charges only for the gaps");
        helper.succeed();
    }

    private static void useEye(ServerPlayer player, ItemStack eye, BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        eye.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    @GameTest
    public void kleinStarsTransferChargeWithoutCopyingIt(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(helper);
        ItemStack stack = item("klein_star_ein");
        var star = (KleinStarItem) stack.getItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        PETransmutationState.deposit(level.getServer(), player.getUUID(), 400, 1);
        player.setShiftKeyDown(true);
        star.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(star.stored(stack) == 400 && PETransmutationState.balance(player) == 0,
                "Charging the star moves EMC out of the account");
        helper.assertTrue(PEEmcOverrides.forPlayer(player).value(stack) == 0
                        && star.stored(PEPreservationConfig.exemplar(stack)) == 0,
                "The shop cannot sell or learn stored charge");
        var recipe = recipe(helper, "klein_star_zwei");
        var upgraded = recipe.assemble(CraftingInput.of(2, 2, List.of(stack, stack, stack, stack)));
        helper.assertTrue(((KleinStarItem) upgraded.getItem()).stored(upgraded) == 1_600,
                "Upgrading four stars transfers their combined charge");
        player.setShiftKeyDown(false);
        star.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(star.stored(stack) == 0 && PETransmutationState.balance(player) == 400,
                "Depositing returns the stored charge once");
        helper.succeed();
    }

    @GameTest
    public void transmutationTableDrainsStarAndKeepsItInInput(GameTestHelper helper) {
        var player = player(helper);
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var table = BuiltInRegistries.BLOCK.getValue(PECore.id("transmutation_table"));
        level.setBlockAndUpdate(pos, table.defaultBlockState());
        PETransmutationState.open(player, false, pos.asLong());
        var menu = (TransmutationMenu) player.containerMenu;

        ItemStack stack = item("klein_star_ein");
        var name = net.minecraft.network.chat.Component.literal("My Klein Star");
        stack.set(DataComponents.CUSTOM_NAME, name);
        var star = (KleinStarItem) stack.getItem();
        star.charge(stack, 25_000);
        menu.getSlot(0).set(stack);
        menu.broadcastChanges();
        menu.broadcastChanges();

        ItemStack returned = menu.getSlot(0).getItem();
        helper.assertTrue(returned.is(star) && returned.getCount() == 1 && star.stored(returned) == 0
                        && name.equals(returned.get(DataComponents.CUSTOM_NAME)),
                "The deposit slot leaves the same named star with no charge across menu ticks");
        helper.assertTrue(PETransmutationState.balance(player) == 25_000
                        && PETransmutationState.knowledgeRevision(player) == 0,
                "The table deposits only the charge, once, without learning the shell");

        ItemStack expected = returned.copy();
        player.closeContainer();
        helper.assertTrue(player.getInventory().contains(starItem -> ItemStack.isSameItemSameComponents(starItem, expected)),
                "Closing the menu returns the discharged star to the player");
        helper.succeed();
    }

    @GameTest
    public void transmutationTabletDischargesInventoryStars(GameTestHelper helper) {
        var player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, item("transmutation_tablet"));
        PETransmutationState.open(player, true, 0);
        var menu = (TransmutationMenu) player.containerMenu;
        ItemStack starStack = item("klein_star_zwei");
        var star = (KleinStarItem) starStack.getItem();
        star.charge(starStack, 600);
        player.getInventory().setItem(9, starStack);
        PETransmutationState.action(player, new PEPackets.Action(true, 0, "sell", 9, "", 64));
        helper.assertTrue(star.stored(starStack) == 0 && player.getInventory().getItem(9).is(star)
                        && PETransmutationState.balance(player) == 600,
                "The inventory sweep drains charge without moving or selling the star");

        star.charge(starStack, 700);
        menu.quickMoveStack(player, 2);
        helper.assertTrue(star.stored(starStack) == 0 && player.getInventory().getItem(9).is(star)
                        && PETransmutationState.balance(player) == 1_300,
                "Vanilla shift-click also drains a charged star without selling it");
        player.closeContainer();
        helper.succeed();
    }

    @GameTest
    public void gemBootsRequireFullStar(GameTestHelper helper) {
        var recipe = recipe(helper, "gem_boots");
        var omega = item("klein_star_omega");
        var swiftwolf = item("swiftwolf_rending_gale");
        var input = CraftingInput.of(2, 2, List.of(item("rm_boots"), swiftwolf, swiftwolf, omega));
        helper.assertTrue(!recipe.matches(input, helper.getLevel()), "An empty Omega does not satisfy the recipe");
        ((KleinStarItem) omega.getItem()).charge(omega, 51_200_000);
        var charged = CraftingInput.of(2, 2, List.of(item("rm_boots"), swiftwolf, swiftwolf, omega));
        helper.assertTrue(recipe.matches(charged, helper.getLevel()), "A full Omega satisfies the recipe");
        helper.succeed();
    }

    @GameTest
    public void alchemyBagsShareStorageByPlayerAndColor(GameTestHelper helper) {
        var player = player(helper);
        var level = helper.getLevel();
        ItemStack bag = item("blue_alchemical_bag");
        player.setItemInHand(InteractionHand.MAIN_HAND, bag);
        bag.getItem().use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu instanceof AlchemicalChestMenu, "Using the bag opens portable storage");
        player.containerMenu.getSlot(0).set(new ItemStack(Items.GOLD_INGOT, 7));
        player.closeContainer();

        ItemStack secondBag = item("blue_alchemical_bag");
        player.setItemInHand(InteractionHand.MAIN_HAND, secondBag);
        secondBag.getItem().use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu.getSlot(0).getItem().getCount() == 7,
                "The same color opens the same 104-slot storage");
        player.closeContainer();

        ItemStack otherColor = item("red_alchemical_bag");
        player.setItemInHand(InteractionHand.MAIN_HAND, otherColor);
        otherColor.getItem().use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.containerMenu.getSlot(0).getItem().isEmpty(), "Other colors open separate storage");
        player.closeContainer();
        helper.succeed();
    }

    @GameTest
    public void novaPacksItsLootAndOpeningItReturnsTheItems(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var player = player(helper);
        var nova = BuiltInRegistries.BLOCK.getValue(PECore.id("nova_catalyst"));
        level.setBlockAndUpdate(pos, nova.defaultBlockState());
        level.setBlockAndUpdate(pos.east(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(TntBlock.prime(level, pos, player, new ItemStack(Items.FLINT_AND_STEEL)), "Nova can be ignited");
        level.removeBlock(pos, false);
        var charges = level.getEntitiesOfClass(PrimedTnt.class, new AABB(pos).inflate(2));
        helper.assertTrue(charges.size() == 1 && charges.getFirst().getBlockState().is(nova),
                "The primed entity remembers which Nova block lit it");
        charges.getFirst().setFuse(1);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(level.isEmptyBlock(pos.east()), "Nova detonates and mines nearby stone");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                    drop -> drop.getItem().is(item("loot_ball").getItem()));
            helper.assertTrue(!drops.isEmpty(), "The blast produced a Loot Ball");
            ItemStack packed = drops.getFirst().getItem().copy();
            helper.assertTrue(packed.get(DataComponents.CONTAINER).nonEmptyItemCopyStream()
                    .anyMatch(stack -> stack.is(Items.COBBLESTONE)), "Mined drops are inside the ball");
            player.setItemInHand(InteractionHand.MAIN_HAND, packed);
            packed.getItem().use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getInventory().contains(new ItemStack(Items.COBBLESTONE))
                    && !player.getMainHandItem().is(item("loot_ball").getItem()), "Opening the ball consumes it and returns the drops");
            helper.succeed();
        });
    }

    private static EmcMachineBlockEntity placeMachine(GameTestHelper helper, BlockPos pos, String id) {
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.getValue(PECore.id(id)).defaultBlockState());
        return (EmcMachineBlockEntity) level.getBlockEntity(pos);
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String id) {
        return (CraftingRecipe) helper.getLevel().getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, PECore.id(id))).orElseThrow().value();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setUUID(UUID.randomUUID());
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 0))));
        return player;
    }

    private static ItemStack item(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(PECore.id(path)));
    }
}
