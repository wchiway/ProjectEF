package moze_intel.projecte;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class EvertideAmuletTests {
    @GameTest
    public void evertidePlacesWaterAndSupportsThePlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        var ring = new ItemStack(BuiltInRegistries.ITEM.getValue(PECore.id("evertide_amulet")));
        player.setItemInHand(InteractionHand.MAIN_HAND, ring);
        var ground = helper.absolutePos(new BlockPos(1, 2, 1));
        var surface = ground.above();
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(surface, Blocks.AIR.defaultBlockState());

        var hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, .5, 0), Direction.UP, ground, false);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        ring.getItem().useOn(context);
        helper.assertTrue(level.getFluidState(surface).isSourceOfType(Fluids.WATER),
                "Using the amulet on a block places a water source");
        player.setPos(surface.getX() + .5, surface.getY() + 1, surface.getZ() + .5);
        var water = level.getBlockState(surface);
        helper.assertTrue(!water.getCollisionShape(level, surface, CollisionContext.of(player)).isEmpty(),
                "The carried amulet gives the player a real water surface to stand on");
        player.setShiftKeyDown(true);
        helper.assertTrue(water.getCollisionShape(level, surface, CollisionContext.of(player)).isEmpty(),
                "Sneaking lets the player descend through the surface");
        player.setShiftKeyDown(false);
        player.setPos(surface.getX() + .5, surface.getY(), surface.getZ() + .5);
        level.setBlockAndUpdate(surface.above(), Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(surface.above(2), Blocks.WATER.defaultBlockState());
        player.setAirSupply(40);
        player.baseTick();
        player.baseTick();
        helper.assertTrue(player.getAirSupply() == player.getMaxAirSupply(),
                "The carried amulet replenishes air underwater");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(!player.canStandOnFluid(Fluids.WATER.defaultFluidState()),
                "Removing the amulet restores normal fluid movement");
        player.baseTick();
        helper.assertTrue(player.getAirSupply() < player.getMaxAirSupply(),
                "Removing the amulet restores normal air consumption");

        player.setItemInHand(InteractionHand.MAIN_HAND, ring);
        EvertideAmuletItem.action(player, 0, InteractionHand.MAIN_HAND);
        EvertideAmuletItem.action(player, 3, InteractionHand.MAIN_HAND);
        helper.assertTrue(level.getBlockState(surface).is(Blocks.ICE),
                "The charged freeze action turns nearby source water into ice");
        helper.succeed();
    }
}
