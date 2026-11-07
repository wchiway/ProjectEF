package moze_intel.projecte;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class LootBallItem extends Item {
    LootBallItem(Properties properties) {
        super(properties);
    }

    static void drop(ServerLevel level, Vec3 position, List<ItemStack> drops) {
        List<ItemStack> packed = new ArrayList<>();
        for (ItemStack drop : drops) {
            for (ItemStack stack : packed) {
                if (!ItemStack.isSameItemSameComponents(stack, drop)) continue;
                int moved = Math.min(drop.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(moved);
                drop.shrink(moved);
                if (drop.isEmpty()) break;
            }
            if (!drop.isEmpty()) packed.add(drop);
        }
        Item item = BuiltInRegistries.ITEM.getValue(PECore.id("loot_ball"));
        for (int start = 0; start < packed.size(); start += ItemContainerContents.MAX_SIZE) {
            ItemStack ball = new ItemStack(item);
            ball.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(
                    packed.subList(start, Math.min(start + ItemContainerContents.MAX_SIZE, packed.size()))));
            var entity = new ItemEntity(level, position.x, position.y, position.z, ball);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack ball = player.getItemInHand(hand);
        var contents = ball.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (contents.size() == 0 || player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            ball.shrink(1);
            contents.nonEmptyItemCopyStream().forEach(item ->
                    player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY));
        }
        return InteractionResult.SUCCESS;
    }
}
