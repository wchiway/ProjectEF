package moze_intel.projecte;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class MiningPowerItem extends Item {
    enum Type { CATALYST, HYPERKINETIC, CATALYTIC }
    private final Type type;

    MiningPowerItem(Properties properties, Type type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !ClassicRingItem.canUse(player)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            if (player.isShiftKeyDown()) charge(server, context.getItemInHand());
            else mine(server, context.getItemInHand(), context.getClickedPos(), context.getClickedFace());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ClassicRingItem.canUse(player)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.isShiftKeyDown()) charge(server, stack);
            else if (type != Type.CATALYST) {
                Vec3 eye = player.getEyePosition();
                var hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(32)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() == HitResult.Type.BLOCK) mine(server, stack, hit.getBlockPos(), hit.getDirection());
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void charge(ServerPlayer player, ItemStack stack) {
        int next = (charge(stack) + 1) % 4;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("MiningCharge", next));
        PowerFuel.notice(player, "Mining charge " + (next + 1) + "/4");
    }

    private int charge(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr("MiningCharge", 0), 0, 3);
    }

    private void mine(ServerPlayer player, ItemStack stack, BlockPos center, Direction face) {
        if (player.getCooldowns().isOnCooldown(stack)) return;
        var level = (ServerLevel) player.level();
        int charge = charge(stack);
        int depth = type == Type.HYPERKINETIC ? 1 : (charge + 1) * (charge + 1);
        int width = type == Type.CATALYTIC && charge >= 3 ? 2 : 1;
        Direction x = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        Direction y = face.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        List<ItemStack> drops = new ArrayList<>();
        var result = PowerFuel.spendIf(player, 64L * (charge + 1), () -> {
            boolean mined = false;
            for (int d = 0; d < depth; d++) {
                BlockPos slice = center.relative(face.getOpposite(), d);
                for (int a = -width; a <= width; a++) {
                    for (int b = -width; b <= width; b++) {
                        BlockPos pos = slice.relative(x, a).relative(y, b);
                        if (ClassicRingItem.canChange(level, player, stack, pos) && collect(level, pos, player, drops)) {
                            mined = true;
                        }
                    }
                }
            }
            return mined;
        });
        if (result != PowerFuel.SpendResult.APPLIED) return;
        LootBallItem.dropMining(level, player, center.relative(face), drops);
        player.getCooldowns().addCooldown(stack, 10);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<net.minecraft.network.chat.Component> lines,
                                net.minecraft.world.item.TooltipFlag flag) {
        lines.accept(net.minecraft.network.chat.Component.literal("Charge " + (charge(stack) + 1) + "/4"));
        lines.accept(net.minecraft.network.chat.Component.literal("Sneak-use to cycle charge. Right-click to mine."));
    }

    static boolean collect(ServerLevel level, BlockPos pos, Entity actor, List<ItemStack> drops) {
        var state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || state.getDestroySpeed(level, pos) < 0 || state.getBlock().getExplosionResistance() >= 1_200) return false;
        List<ItemStack> loot = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS)
                ? Block.getDrops(state, level, pos, null, actor, new ItemStack(Items.NETHERITE_PICKAXE)) : List.of();
        if (!level.destroyBlock(pos, false, actor)) return false;
        drops.addAll(loot);
        return true;
    }
}
