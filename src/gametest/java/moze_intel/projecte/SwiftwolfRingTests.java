package moze_intel.projecte;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

public final class SwiftwolfRingTests {
    @GameTest
    public void ringCyclesModesAndRunsOnlyWithEmc(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setUUID(UUID.randomUUID());
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        var level = helper.getLevel();
        var ring = new ItemStack(BuiltInRegistries.ITEM.getValue(PECore.id("swiftwolf_rending_gale")));
        player.setItemInHand(InteractionHand.MAIN_HAND, ring);
        var item = ring.getItem();
        item.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ring.get(DataComponents.CUSTOM_MODEL_DATA).strings().equals(List.of("flight")),
                "Right click selects the flight model");
        SwiftwolfRingItem.tickPlayer(player);
        helper.assertTrue(!player.getAbilities().mayfly, "An unfunded ring grants no flight");

        PETransmutationState.deposit(level.getServer(), player.getUUID(), 64, 2);
        SwiftwolfRingItem.tickPlayer(player);
        helper.assertTrue(player.getAbilities().mayfly, "The funded hotbar ring grants flight");
        player.getAbilities().flying = true;
        SwiftwolfRingItem.tickPlayer(player);
        helper.assertTrue(PETransmutationState.balance(player) == 64, "Starting flight debits 64 EMC");

        item.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ring.get(DataComponents.CUSTOM_MODEL_DATA).strings().equals(List.of("repel")),
                "Second click selects the repel model");
        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        player.setPos(zombie.getX() - 1, zombie.getY(), zombie.getZ());
        player.tickCount = 5;
        SwiftwolfRingItem.tickPlayer(player);
        helper.assertTrue(zombie.getDeltaMovement().x > 0 && PETransmutationState.balance(player) == 0,
                "The repel field pushes nearby mobs and consumes EMC");
        helper.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying,
                "Switching to repel removes flight");
        item.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ring.get(DataComponents.CUSTOM_MODEL_DATA).strings().equals(List.of("both")),
                "Third click selects the combined model");
        item.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!ring.has(DataComponents.CUSTOM_MODEL_DATA), "Fourth click restores the off model");
        SwiftwolfRingItem.tickPlayer(player);
        helper.succeed();
    }
}
