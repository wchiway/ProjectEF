package moze_intel.projecte;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class PETransmutationState extends SavedData {
    private static final Map<Identifier, Long> VALUES = new LinkedHashMap<>();
    static {
        try (InputStream stream = PETransmutationState.class.getClassLoader()
                .getResourceAsStream("data/projecte/emc_values.json")) {
            if (stream == null) {
                PECore.LOGGER.error("Missing ProjectEF EMC values; continuing with an empty table");
            } else {
                var entries = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                entries.entrySet().forEach(entry -> {
                    try {
                        long amount = entry.getValue().getAsLong();
                        if (amount > 0 && !PEEmcBlacklist.contains(entry.getKey())) price(entry.getKey(), amount);
                    } catch (RuntimeException exception) {
                        PECore.LOGGER.warn("Skipping invalid ProjectEF EMC entry '{}'", entry.getKey(), exception);
                    }
                });
            }
        } catch (java.io.IOException | RuntimeException exception) {
            PECore.LOGGER.error("Unable to load ProjectEF EMC values; continuing with the valid entries", exception);
        }
    }

    private record Account(long balance, List<String> learned, List<ItemStack> variants, List<String> favorites) {
        Account(long balance, List<String> learned) { this(balance, learned, List.of(), List.of()); }
        static final Codec<Account> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("balance").forGetter(Account::balance),
                Codec.STRING.listOf().fieldOf("learned").forGetter(Account::learned),
                ItemStack.CODEC.listOf().optionalFieldOf("variants", List.of()).forGetter(Account::variants),
                Codec.STRING.listOf().optionalFieldOf("favorites", List.of()).forGetter(Account::favorites)
        ).apply(instance, Account::new));
    }
    private static final Codec<PETransmutationState> CODEC = Codec.unboundedMap(Codec.STRING, Account.CODEC)
            .xmap(PETransmutationState::new, state -> state.accounts);
    public static final SavedDataType<PETransmutationState> TYPE = new SavedDataType<>(
            PECore.id("transmutation_accounts"), PETransmutationState::new, CODEC, null);
    private final Map<String, Account> accounts;
    private final Map<String, Integer> knowledgeRevisions = new java.util.HashMap<>();
    private PETransmutationState() { accounts = new LinkedHashMap<>(); }
    private PETransmutationState(Map<String, Account> accounts) { this.accounts = new LinkedHashMap<>(accounts); }

    private static void price(String id, long value) { VALUES.put(Identifier.parse(id), value); }
    public static long value(String id) {
        if (PEEmcBlacklist.contains(id)) return 0;
        try { return VALUES.getOrDefault(Identifier.parse(id), 0L); }
        catch (IllegalArgumentException exception) { return 0; }
    }
    public static long valueWithUpgrades(ItemStack stack, long base) {
        if (base <= 0) return 0;
        int modifier = 0;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (stack.is(Items.ENCHANTED_BOOK)) {
            enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        }
        for (Object2IntMap.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> entry : enchantments.entrySet()) {
            String id = entry.getKey().unwrapKey()
                    .map(key -> key.identifier().toString())
                    .orElse("");
            int levels = entry.getIntValue();
            modifier += (id.endsWith("binding_curse") || id.endsWith("vanishing_curse")) ? -levels : levels;
        }
        long adjusted = modifier == 0 ? base : Math.max(1L, base + (base * 5L * modifier) / 100L);
        // Fuel credit is real EMC: preserving a charged Stone must never duplicate it for free.
        long fuel = stack.getItem() instanceof PhilosophersStoneItem ? StoneMobTransmutation.fuelBuffer(stack) : 0;
        return adjusted > Long.MAX_VALUE - fuel ? Long.MAX_VALUE : adjusted + fuel;
    }
    /** Whether an item is eligible for EMC processing. */
    public static boolean plain(ItemStack stack) {
        return !stack.isEmpty();
    }
    /** Never learn copies of portable inventories: buying them would duplicate their contents. */
    private static boolean containsStoredItems(ItemStack stack) {
        var container = stack.get(DataComponents.CONTAINER);
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return (container != null && container.nonEmptyItems().iterator().hasNext())
                || (bundle != null && !bundle.isEmpty())
                || stack.has(DataComponents.BLOCK_ENTITY_DATA);
    }
    /** Filled/explorer maps may be sold for map EMC, but only blank maps are learnable and purchasable. */
    public static boolean sellOnlyMap(ItemStack stack) {
        return stack.is(Items.MAP) && stack.has(DataComponents.MAP_ID);
    }
    private static PETransmutationState state(ServerPlayer player) {
        return player.level().getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    private Account account(ServerPlayer player) {
        String id = player.getUUID().toString();
        Account current = accounts.getOrDefault(id, new Account(0, List.of()));
        Account normalized = normalizeDurability(current);
        if (normalized != current) {
            accounts.put(id, normalized);
            setDirty();
        }
        return normalized;
    }

    /** Upgrade old learned records without stripping data covered by disabled config switches. */
    private static Account normalizeDurability(Account current) {
        Set<String> known = new LinkedHashSet<>(current.learned());
        Set<ItemStack> variants = net.minecraft.world.item.ItemStackLinkedSet.createTypeAndComponentsSet();
        boolean changed = false;
        for (ItemStack saved : current.variants()) {
            if (saved.isEmpty()) {
                changed = true;
                continue;
            }
            ItemStack canonical = PEPreservationConfig.withoutDurability(saved);
            if (!ItemStack.matches(saved, canonical)) changed = true;
            if (ItemStack.isSameItemSameComponents(canonical, new ItemStack(canonical.getItem()))) {
                // A durability-only variant is the ordinary item, not a separate learned entry.
                known.add(BuiltInRegistries.ITEM.getKey(canonical.getItem()).toString());
                changed = true;
            } else if (!variants.add(canonical)) {
                changed = true;
            }
        }
        known.removeIf(PEEmcBlacklist::contains);
        List<String> learned = List.copyOf(known);
        if (!learned.equals(current.learned())) changed = true;
        return changed ? new Account(current.balance(), learned, List.copyOf(variants), remapFavorites(current, learned, List.copyOf(variants))) : current;
    }
    public static boolean deposit(MinecraftServer server, UUID owner, long unit, int quantity) {
        if (unit <= 0 || quantity <= 0 || unit > (Long.MAX_VALUE / quantity)) return false;
        PETransmutationState state = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        String id = owner.toString();
        Account current = state.accounts.getOrDefault(id, new Account(0, List.of()));
        if (current.balance() > Long.MAX_VALUE - unit * quantity) return false;
        state.accounts.put(id, new Account(current.balance() + unit * quantity, current.learned(), current.variants(), current.favorites()));
        state.setDirty();
        return true;
    }
    private void update(ServerPlayer player, Account account) {
        String owner = player.getUUID().toString();
        Account previous = accounts.get(owner);
        if (previous == null || !previous.learned().equals(account.learned()) || !sameVariants(previous.variants(), account.variants()))
            knowledgeRevisions.merge(owner, 1, Integer::sum);
        accounts.put(owner, account);
        setDirty();
    }
    public static boolean access(ServerPlayer player, boolean tablet, long position) {
        if (tablet) return PERegistries.isTransmutationTablet(player.getMainHandItem().getItem())
                || PERegistries.isTransmutationTablet(player.getOffhandItem().getItem());
        BlockPos pos = BlockPos.of(position);
        return player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64
                && player.level().isLoaded(pos)
                && PERegistries.isTransmutationTable(player.level().getBlockState(pos).getBlock());
    }
    public static void open(ServerPlayer player, boolean tablet, long position) {
        if (!access(player, tablet, position)) return;
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, owner) ->
                new TransmutationMenu(id, inventory, tablet, position), net.minecraft.network.chat.Component.literal(tablet ? "Transmutation Tablet" : "Transmutation Table")));
        sync(player, tablet, position);
    }
    public static void action(ServerPlayer player, PEPackets.Action request) {
        if (!access(player, request.tablet(), request.position())) return;
        PETransmutationState state = state(player);
        Account current = state.account(player);
        if (!(player.containerMenu instanceof TransmutationMenu menu) || menu.tablet != request.tablet() || menu.position != request.position()) return;
        if ("forget".equals(request.command())) {
            forgetKey(player, request.item(), request.count());
        } else if ("pin_carried".equals(request.command())) {
            String key = keyForStack(current, menu.getCarried());
            if (key != null && !current.favorites().contains(key) && current.favorites().size() < 64) {
                List<String> favorites = new java.util.ArrayList<>(current.favorites()); favorites.add(key);
                state.update(player, new Account(current.balance(), current.learned(), current.variants(), List.copyOf(favorites)));
            }
        } else if ("favorite".equals(request.command()) || "pin".equals(request.command())) {
            String key = request.item();
            boolean known = current.learned().contains(key);
            if (key.startsWith("variant:")) try { int i = Integer.parseInt(key.substring(8)); known = i >= 0 && i < current.variants().size(); } catch (NumberFormatException ignored) {}
            if (!known) return;
            List<String> favorites = new java.util.ArrayList<>(current.favorites());
            if ("pin".equals(request.command())) { if (!favorites.contains(key) && favorites.size() < 64) favorites.add(key); }
            else if (!favorites.remove(key) && favorites.size() < 64) favorites.add(key);
            state.update(player, new Account(current.balance(), current.learned(), current.variants(), List.copyOf(favorites)));
        } else if ("sell".equals(request.command())) {
            int slot = request.slot();
            if (slot < 0 || slot >= 36) return;
            sellStack(player, player.getInventory().getItem(slot), request.count());
        } else if ("buy".equals(request.command())) {
            ItemStack exemplar;
            if (request.item().startsWith("variant:")) {
                if (!PEPreservationConfig.enabled()) return;
                int index;
                try { index = Integer.parseInt(request.item().substring(8)); }
                catch (NumberFormatException exception) { return; }
                if (index < 0 || index >= current.variants().size()) return;
                exemplar = PEPreservationConfig.exemplar(current.variants().get(index));
                if (containsStoredItems(exemplar) || sellOnlyMap(exemplar)) return;
            } else {
                if (!current.learned().contains(request.item())) return;
                Item item;
                try { item = BuiltInRegistries.ITEM.getValue(Identifier.parse(request.item())); }
                catch (IllegalArgumentException exception) { return; }
                if (item == Items.AIR) return;
                exemplar = new ItemStack(item);
            }
            if (PEEmcBlacklist.contains(exemplar)) return;
            long unit = PEEmcOverrides.forPlayer(player).value(exemplar);
            int quantity = Math.min(Math.max(1, Math.min(request.count(), 64)), exemplar.getMaxStackSize());
            if (unit <= 0) return;
            quantity = (int)Math.min(quantity, current.balance() / unit);
            if (quantity <= 0) return;
            ItemStack stack = exemplar.copyWithCount(quantity);
            state.update(player, new Account(current.balance() - unit * quantity, current.learned(), current.variants(), current.favorites()));
            if (!player.getInventory().add(stack)) {
                player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        player.level(), player.getX(), player.getY(), player.getZ(), stack));
            }
        }
        sync(player, request.tablet(), request.position());
    }
    private static boolean sameVariants(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }
    private static String keyForStack(Account account, ItemStack stack) {
        if (stack.isEmpty()) return null;
        ItemStack canonical = PEPreservationConfig.withoutDurability(stack);
        if (ItemStack.isSameItemSameComponents(canonical, new ItemStack(canonical.getItem()))) {
            String id = BuiltInRegistries.ITEM.getKey(canonical.getItem()).toString();
            return account.learned().contains(id) ? id : null;
        }
        for (int i = 0; i < account.variants().size(); i++)
            if (ItemStack.isSameItemSameComponents(PEPreservationConfig.withoutDurability(account.variants().get(i)), canonical)) return "variant:" + i;
        return null;
    }
    public static int knowledgeRevision(ServerPlayer player) {
        return state(player).knowledgeRevisions.getOrDefault(player.getUUID().toString(), 0);
    }
    public static boolean forgetStack(ServerPlayer player, ItemStack stack) {
        String key = keyForStack(state(player).account(player), stack);
        return key != null && forgetKey(player, key, knowledgeRevision(player));
    }
    public static boolean forgetKey(ServerPlayer player, String key, int revision) {
        PETransmutationState state = state(player); Account current = state.account(player);
        List<String> learned = new java.util.ArrayList<>(current.learned());
        List<ItemStack> variants = new java.util.ArrayList<>(current.variants());
        if (key.startsWith("variant:")) {
            if (revision != knowledgeRevision(player)) return false;
            int index; try { index = Integer.parseInt(key.substring(8)); } catch (NumberFormatException invalid) { return false; }
            if (index < 0 || index >= variants.size()) return false;
            variants.remove(index);
        } else if (!learned.remove(key)) return false;
        state.update(player, new Account(current.balance(), List.copyOf(learned), List.copyOf(variants), remapFavorites(current, learned, variants)));
        return true;
    }
    public static void sellStack(ServerPlayer player, ItemStack stack, int count) {
        PETransmutationState state = state(player);
        Account current = state.account(player);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (PEEmcBlacklist.contains(id)) return;
            long unit = PEEmcOverrides.forPlayer(player).value(stack);
            int quantity = Math.min(stack.getCount(), Math.max(1, Math.min(count, 64)));
            if (unit > 0 && quantity > 0 && plain(stack)
                    && unit <= (Long.MAX_VALUE - current.balance()) / quantity) {
                Set<String> known = new LinkedHashSet<>(current.learned());
                List<ItemStack> variants = new java.util.ArrayList<>(current.variants());
                ItemStack exemplar = PEPreservationConfig.exemplar(stack);
                // Broken machines often carry operational block data even when empty.
                // Learn an empty block, retaining separate item-level names/enchants,
                // rather than either copying its saved contents or silently skipping it.
                if (exemplar.getItem() instanceof net.minecraft.world.item.BlockItem)
                    exemplar.remove(DataComponents.BLOCK_ENTITY_DATA);
                if (!sellOnlyMap(stack) && !containsStoredItems(exemplar)) {
                    boolean distinct = PEPreservationConfig.enabled() && !ItemStack.isSameItemSameComponents(exemplar, new ItemStack(exemplar.getItem()));
                    if (distinct) {
                        // Do not grant the plain item merely by learning a modified version.
                        boolean alreadyKnown = variants.stream().anyMatch(saved -> ItemStack.isSameItemSameComponents(PEPreservationConfig.exemplar(saved), exemplar));
                        if (!alreadyKnown && variants.size() < 4096) variants.add(exemplar);
                    } else known.add(id);
                }
                state.update(player, new Account(current.balance() + unit * quantity, List.copyOf(known), List.copyOf(variants), current.favorites()));
                stack.shrink(quantity);
            }

    }
    private static List<String> remapFavorites(Account old, List<String> learned, List<ItemStack> variants) {
        Set<String> result = new LinkedHashSet<>();
        for (String key : old.favorites()) {
            if (!key.startsWith("variant:")) { if (learned.contains(key)) result.add(key); continue; }
            try {
                int index = Integer.parseInt(key.substring(8));
                if (index < 0 || index >= old.variants().size()) continue;
                ItemStack canonical = PEPreservationConfig.withoutDurability(old.variants().get(index));
                if (ItemStack.isSameItemSameComponents(canonical, new ItemStack(canonical.getItem()))) result.add(BuiltInRegistries.ITEM.getKey(canonical.getItem()).toString());
                else for (int i = 0; i < variants.size(); i++) if (ItemStack.isSameItemSameComponents(canonical, variants.get(i))) { result.add("variant:" + i); break; }
            } catch (NumberFormatException ignored) {}
        }
        return List.copyOf(result);
    }
    public static void sync(ServerPlayer player, boolean tablet, long position) {
        Account account = state(player).account(player);
        if (ServerPlayNetworking.canSend(player, PEPackets.Snapshot.TYPE))
            ServerPlayNetworking.send(player, new PEPackets.Snapshot(tablet, position, account.balance(), account.learned(),
                    PEEmcOverrides.forPlayer(player).snapshot(),
                    PEPreservationConfig.enabled() ? account.variants().stream().map(PEPreservationConfig::exemplar).toList() : List.of(), account.favorites(), knowledgeRevision(player)));
    }
}
