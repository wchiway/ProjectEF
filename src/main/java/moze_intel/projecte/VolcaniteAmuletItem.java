package moze_intel.projecte;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;

public final class VolcaniteAmuletItem extends Item {
    VolcaniteAmuletItem(Properties properties) {
        super(properties);
    }

    public static boolean isCarried(Player player) {
        if (player.getOffhandItem().getItem() instanceof VolcaniteAmuletItem) return true;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof VolcaniteAmuletItem) return true;
        }
        return false;
    }

    static void action(ServerPlayer player, int command, InteractionHand hand) {
        if (!ClassicRingItem.canUse(player) || player.containerMenu != player.inventoryMenu) return;
        ItemStack stack = player.getItemInHand(hand);
        if (command == 0 || command == 1) {
            int charge = Math.clamp(charge(stack) + (command == 0 ? 1 : -1), 0, 4);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("VolcaniteCharge", charge));
            PowerFuel.notice(player, "Volcanite charge " + charge + "/4");
        } else if (command == 2 && !player.getCooldowns().isOnCooldown(stack)
                && PowerFuel.consume(player, Items.REDSTONE, 2)) {
            RingProjectiles.launch((ServerLevel) player.level(), player, stack, false, false);
            player.getCooldowns().addCooldown(stack, 10);
        }
    }

    private static int charge(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr("VolcaniteCharge", 0), 0, 4);
    }

    static boolean placeLava(ServerLevel level, Player player, ItemStack stack, BlockPos pos, boolean pay) {
        if (!ClassicRingItem.canChange(level, player, stack, pos)) return false;
        var state = level.getBlockState(pos);
        if (!state.canBeReplaced() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || pay && !PowerFuel.consume(player, Items.REDSTONE, 2)) return false;
        return level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !ClassicRingItem.canUse(player)) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        Direction face = context.getClickedFace();
        BlockPos origin = context.getClickedPos().relative(face);
        int charge = charge(stack);
        if (!placeLava(level, player, stack, origin, true)) return InteractionResult.FAIL;
        for (int step = 1; step <= charge; step++) {
            BlockPos target = player.getXRot() < -35 ? origin.above(step) : origin.relative(player.getDirection(), step);
            if (!placeLava(level, player, stack, target, true)) break;
        }
        player.getCooldowns().addCooldown(stack, 8);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal("Volcanite charge " + charge(stack) + "/4"));
        lines.accept(Component.literal("Right-click to place lava. Fire key launches a lava bolt. Costs redstone."));
        lines.accept(Component.literal("Hotbar or offhand grants lava walking and fire protection."));
    }
}
