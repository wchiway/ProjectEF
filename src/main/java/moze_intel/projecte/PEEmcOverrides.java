package moze_intel.projecte;

import com.mojang.serialization.Codec;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.item.ItemStack;

/** World-specific, operator-set EMC prices. A zero price disables an item. */
public final class PEEmcOverrides extends SavedData {
    private static final Codec<PEEmcOverrides> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
            .xmap(PEEmcOverrides::new, overrides -> overrides.prices);
    public static final SavedDataType<PEEmcOverrides> TYPE = new SavedDataType<>(
            PECore.id("emc_overrides"), PEEmcOverrides::new, CODEC, null);
    private final Map<String, Long> prices;

    private PEEmcOverrides() { prices = new LinkedHashMap<>(); }
    private PEEmcOverrides(Map<String, Long> prices) { this.prices = new LinkedHashMap<>(prices); }

    public static PEEmcOverrides forPlayer(ServerPlayer player) {
        return forServer(player.level().getServer());
    }
    public static PEEmcOverrides forServer(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    public long value(String item) {
        if (PEEmcBlacklist.contains(item)) return 0;
        return prices.getOrDefault(item, PETransmutationState.value(item));
    }
    public long value(ItemStack stack) {
        if (UtilityPowerItem.containsValue(stack)) return 0;
        if (stack.getItem() instanceof KleinStarItem star && star.stored(stack) > 0) return 0;
        String item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (PEEmcBlacklist.contains(item)) return 0;
        // Filled/explorer maps use the ordinary map item plus map data. They sell at
        // the normal map value, but PETransmutationState keeps them out of learned items.
        if (PETransmutationState.sellOnlyMap(stack)) item = "minecraft:map";
        return PETransmutationState.valueWithUpgrades(stack, prices.getOrDefault(item, PETransmutationState.value(item)));
    }
    public Map<String, Long> snapshot() { return Map.copyOf(prices); }
    public void set(String item, long amount) {
        if (PEEmcBlacklist.contains(item)) return;
        prices.put(item, amount);
        setDirty();
    }
}
