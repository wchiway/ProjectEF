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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class MercurialEyeItem extends Item {
    private static final int MAX_CHARGE = 4;

    private enum Mode {
        CREATION("Creation"), EXTENSION("Extension"), TRANSMUTATION("Transmutation"), PILLAR("Pillar");

        final String label;
        Mode(String label) { this.label = label; }
    }

    MercurialEyeItem(Properties properties) {
        super(properties);
    }

    public boolean supportsControl(int command) {
        return command == 0 || command == 1 || command == 4;
    }

    static void action(ServerPlayer player, int command, InteractionHand hand) {
        ItemStack eye = player.getItemInHand(hand);
        if (!(eye.getItem() instanceof MercurialEyeItem item) || !item.supportsControl(command)
                || !ClassicRingItem.canUse(player)) return;
        if (command == 4) {
            int next = (mode(eye).ordinal() + 1) % Mode.values().length;
            CustomData.update(DataComponents.CUSTOM_DATA, eye, tag -> tag.putInt("EyeMode", next));
        } else {
            int charge = Math.clamp(charge(eye) + (command == 0 ? 1 : -1), 0, MAX_CHARGE);
            CustomData.update(DataComponents.CUSTOM_DATA, eye, tag -> tag.putInt("EyeCharge", charge));
        }
        PowerFuel.notice(player, status(eye));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !ClassicRingItem.canUse(context.getPlayer())) return InteractionResult.PASS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack eye = context.getItemInHand();
        if (player.getCooldowns().isOnCooldown(eye)) return InteractionResult.FAIL;

        var level = (ServerLevel) player.level();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState source = level.getBlockState(clicked);
        Mode mode = mode(eye);
        var prices = PEEmcOverrides.forPlayer(player);
        var otherHand = context.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack sample = player.getItemInHand(otherHand);
        ItemStack material = new ItemStack(sample.isEmpty() && mode == Mode.EXTENSION ? source.getBlock() : sample.getItem());
        if (!(material.getItem() instanceof BlockItem block)) return InteractionResult.FAIL;
        long price = prices.value(material);
        if (price <= 0 || (mode != Mode.CREATION && isMultiblock(block.getBlock().defaultBlockState()))) {
            return InteractionResult.FAIL;
        }

        boolean transmute = mode == Mode.TRANSMUTATION;
        long sourcePrice = transmute ? prices.value(new ItemStack(source.getBlock())) : 0;
        if (transmute && (sourcePrice <= 0 || source.is(block.getBlock()) || source.hasBlockEntity()
                || !source.getFluidState().isEmpty() || isMultiblock(source))) return InteractionResult.FAIL;

        int radius = switch (mode) {
            case CREATION -> 0;
            case PILLAR -> 1;
            default -> charge(eye);
        };
        int depth = mode == Mode.PILLAR ? 3 * (charge(eye) + 1) : 1;
        Direction u = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        Direction v = face.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        BlockPos center = transmute ? clicked : clicked.relative(face);
        boolean changed = false;
        long cost = Math.max(0, price - sourcePrice);
        for (int layer = 0; layer < depth; layer++) {
            for (BlockPos cursor : BlockPos.spiralAround(center.relative(face, layer), radius, u, v)) {
                BlockPos pos = cursor.immutable();
                if (!ClassicRingItem.canChange(level, player, eye, pos)
                        || !player.mayUseItemAt(pos, face, eye)) continue;
                BlockState previous = level.getBlockState(pos);
                if (transmute ? previous != source || previous.getDestroySpeed(level, pos) < 0
                        : previous.hasBlockEntity() || !previous.canBeReplaced()) continue;

                var hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
                var placement = new BlockPlaceContext(level, player, context.getHand(), material.copy(), hit);
                var result = PowerFuel.spendIf(player, cost, () -> transmute
                        ? replace(level, player, block, placement, pos)
                        : place(block, placement, pos));
                if (result == PowerFuel.SpendResult.UNFUNDED) return finish(player, eye, changed);
                if (result != PowerFuel.SpendResult.APPLIED) continue;
                changed = true;
                if (sourcePrice > price && !player.getAbilities().instabuild) {
                    PETransmutationState.deposit(level.getServer(), player.getUUID(), sourcePrice - price, 1);
                }
            }
        }
        return finish(player, eye, changed);
    }

    private static boolean isMultiblock(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART);
    }

    private static boolean place(BlockItem block, BlockPlaceContext placement, BlockPos pos) {
        var adjusted = block.updatePlacementContext(placement);
        return adjusted != null && adjusted.getClickedPos().equals(pos) && block.place(placement).consumesAction();
    }

    private static boolean replace(ServerLevel level, ServerPlayer player, BlockItem block,
                                   BlockPlaceContext placement, BlockPos pos) {
        if (block.getBlock().defaultBlockState().hasBlockEntity()) return false;
        BlockState replacement = block.getBlock().getStateForPlacement(placement);
        return replacement != null && replacement.canSurvive(level, pos)
                && level.isUnobstructed(replacement, pos, CollisionContext.of(player))
                && level.setBlockAndUpdate(pos, replacement);
    }

    private static InteractionResult finish(ServerPlayer player, ItemStack eye, boolean changed) {
        if (!changed) return InteractionResult.FAIL;
        player.getCooldowns().addCooldown(eye, 10);
        return InteractionResult.SUCCESS;
    }

    private static Mode mode(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Mode.values()[Math.clamp(tag.getIntOr("EyeMode", 0), 0, Mode.values().length - 1)];
    }

    private static int charge(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Math.clamp(tag.getIntOr("EyeCharge", 0), 0, MAX_CHARGE);
    }

    private static String status(ItemStack stack) {
        return mode(stack).label + " · Charge " + (charge(stack) + 1) + " / " + (MAX_CHARGE + 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal(status(stack)));
        lines.accept(Component.literal("Sample a block in the other hand. Mode and charge use the ProjectE keybinds."));
    }
}
