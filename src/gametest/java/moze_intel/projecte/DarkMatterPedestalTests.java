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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class DarkMatterPedestalTests {
    @GameTest(maxTicks = 40)
    public void pedestalHarvestsOnlyWhenActive(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = placePedestal(helper);
        var player = player(helper, pos);
        var pedestal = (DarkMatterPedestalBlockEntity) level.getBlockEntity(pos);
        BlockPos ripe = pos.east(4);
        BlockPos young = pos.east(3);
        BlockPos outside = pos.east(5);
        for (BlockPos crop : List.of(ripe, young, outside)) {
            level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(crop.equals(young) ? 0 : 7));
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        use(player, pos);
        helper.assertTrue(pedestal.item().isEmpty(), "A pedestal accepts only a supported power item");

        ItemStack ring = namedBand();
        player.setItemInHand(InteractionHand.MAIN_HAND, ring.copy());
        use(player, pos);
        helper.assertTrue(player.getMainHandItem().isEmpty()
                        && ItemStack.isSameItemSameComponents(pedestal.item(), ring)
                        && !level.getBlockState(pos).getValue(DarkMatterPedestalBlock.ACTIVE),
                "Right-click mounts one ring and keeps the pedestal inactive");

        helper.runAfterDelay(12, () -> {
            helper.assertTrue(level.getBlockState(ripe).is(Blocks.WHEAT), "An inactive pedestal leaves crops alone");
            use(player, pos);
            helper.assertTrue(level.getBlockState(pos).getValue(DarkMatterPedestalBlock.ACTIVE),
                    "Empty-hand right-click activates the pedestal");
            helper.runAfterDelay(11, () -> {
                helper.assertTrue(level.isEmptyBlock(ripe)
                                && level.getBlockState(outside).is(Blocks.WHEAT),
                        "The active pedestal harvests mature crops within four blocks of its center");
                helper.assertTrue(level.getBlockState(young).is(Blocks.WHEAT)
                                && level.getBlockState(young).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7) > 0,
                        "The pedestal also grows immature crops");
                helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(ripe).inflate(0.75),
                        drop -> drop.getItem().is(Items.WHEAT)).isEmpty(), "Harvested crops drop normally");

                player.setShiftKeyDown(true);
                use(player, pos);
                helper.assertTrue(pedestal.item().isEmpty()
                                && !level.getBlockState(pos).getValue(DarkMatterPedestalBlock.ACTIVE)
                                && player.getInventory().contains(stack -> ItemStack.isSameItemSameComponents(stack, ring)),
                        "Sneak-right-click returns the original band and shuts the pedestal off");
                helper.succeed();
            });
        });
    }

    @GameTest
    public void pedestalPersistsItsBandAndDropsItOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = placePedestal(helper);
        var player = player(helper, pos);
        ItemStack ring = namedBand();
        player.setItemInHand(InteractionHand.MAIN_HAND, ring.copy());
        use(player, pos);
        use(player, pos);

        var original = (DarkMatterPedestalBlockEntity) level.getBlockEntity(pos);
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        original.saveWithFullMetadata(out);
        var reloaded = BlockEntity.loadStatic(pos, level.getBlockState(pos), out.buildResult(), level.registryAccess());
        helper.assertTrue(reloaded instanceof DarkMatterPedestalBlockEntity pedestal
                        && ItemStack.isSameItemSameComponents(pedestal.item(), ring)
                        && pedestal.getBlockState().getValue(DarkMatterPedestalBlock.ACTIVE),
                "Saving and loading restores the mounted band's data and active state");

        level.destroyBlock(pos, true, player);
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        helper.assertTrue(drops.stream().filter(drop -> ItemStack.isSameItemSameComponents(drop.getItem(), ring))
                        .mapToInt(drop -> drop.getItem().getCount()).sum() == 1,
                "Breaking a loaded pedestal drops exactly one intact band");
        helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(item("dm_pedestal").getItem())),
                "Breaking the block drops the pedestal too");
        helper.succeed();
    }

    @GameTest
    public void pedestalUsesTheClassicRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var holder = level.getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, PECore.id("dm_pedestal"))).orElseThrow();
        var recipe = (CraftingRecipe) holder.value();
        var red = item("red_matter");
        var dark = item("dark_matter_block");
        var input = CraftingInput.of(3, 3, List.of(red, dark, red, red, dark, red, dark, dark, dark));
        helper.assertTrue(recipe.matches(input, level) && recipe.assemble(input).is(item("dm_pedestal").getItem()),
                "Five Dark Matter Blocks and four Red Matter craft the pedestal");
        helper.succeed();
    }

    private static BlockPos placePedestal(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var block = BuiltInRegistries.BLOCK.getValue(PECore.id("dm_pedestal"));
        helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        return pos;
    }

    private static ServerPlayer player(GameTestHelper helper, BlockPos pos) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setUUID(UUID.randomUUID());
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        player.setPos(Vec3.atBottomCenterOf(pos.north(2)));
        return player;
    }

    private static void use(ServerPlayer player, BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static ItemStack namedBand() {
        var ring = item("harvest_goddess_band");
        ring.set(DataComponents.CUSTOM_NAME, Component.literal("Farm Band"));
        return ring;
    }

    private static ItemStack item(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(PECore.id(path)));
    }
}
