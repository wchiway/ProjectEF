package moze_intel.projecte;

import com.mojang.serialization.Codec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

final class AlchemicalBagItem extends Item {
    AlchemicalBagItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server && !player.isSpectator()) {
            String key = player.getUUID() + "/" + BuiltInRegistries.ITEM.getKey(this);
            var data = level.getServer().overworld().getDataStorage().computeIfAbsent(Bags.TYPE);
            var container = data.bags.computeIfAbsent(key, ignored -> data.new Bag());
            server.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new AlchemicalChestMenu(id, inventory, container),
                    player.getItemInHand(hand).getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }

    static void repairCarried(ServerPlayer player) {
        var inventory = player.getInventory();
        var used = new java.util.HashSet<Item>();
        Bags data = null;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            Item item = inventory.getItem(slot).getItem();
            if (!(item instanceof AlchemicalBagItem) || !used.add(item)) continue;
            if (data == null) data = player.level().getServer().overworld().getDataStorage().computeIfAbsent(Bags.TYPE);
            var bag = data.bags.get(player.getUUID() + "/" + BuiltInRegistries.ITEM.getKey(item));
            if (bag != null && UtilityPowerItem.hasTalisman(bag)) UtilityPowerItem.repair(bag);
        }
    }

    private static final class Bags extends SavedData {
        private static final Codec<Bags> CODEC = Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf(0, 104))
                .xmap(Bags::new, Bags::save);
        private static final SavedDataType<Bags> TYPE =
                new SavedDataType<>(PECore.id("alchemical_bags"), Bags::new, CODEC, null);
        private final Map<String, Bag> bags = new LinkedHashMap<>();

        private Bags() { }

        private Bags(Map<String, List<ItemStack>> saved) {
            saved.forEach((key, items) -> {
                Bag bag = new Bag();
                for (int index = 0; index < items.size(); index++) bag.items.set(index, items.get(index));
                bags.put(key, bag);
            });
        }

        private Map<String, List<ItemStack>> save() {
            Map<String, List<ItemStack>> contents = new LinkedHashMap<>();
            bags.forEach((key, bag) -> {
                if (!bag.isEmpty()) contents.put(key, List.copyOf(bag.items));
            });
            return contents;
        }

        private final class Bag extends SimpleContainer {
            Bag() { super(104); }

            @Override
            public void setChanged() {
                super.setChanged();
                setDirty();
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return !(stack.getItem() instanceof AlchemicalBagItem);
            }
        }
    }
}
