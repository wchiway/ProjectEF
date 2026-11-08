package moze_intel.projecte;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ClassicExpansionTests {
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
        saved.putLong("emc", 49_876_543);
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
        helper.assertTrue(client.storedEmc() == 49_876_543 && client.targetEmc() == machine.condenserTargetEmc(),
                "The EMC bar receives the exact buffer and target price through 16-bit menu fields");
        helper.succeed();
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
