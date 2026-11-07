package moze_intel.projecte;

import com.mojang.serialization.Codec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;

/** Regression coverage for wear-independent learning and existing account upgrades. */
public final class DurabilityPreservationTests {
    private static Class<?> accountClass() throws Exception {
        return Class.forName("moze_intel.projecte.PETransmutationState$Account");
    }

    private static Object account(List<String> learned, List<ItemStack> variants) throws Exception {
        Constructor<?> constructor = accountClass().getDeclaredConstructor(long.class, List.class, List.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(123456L, learned, variants, List.of());
    }

    private static Object normalize(Object account) throws Exception {
        Method method = PETransmutationState.class.getDeclaredMethod("normalizeDurability", accountClass());
        method.setAccessible(true);
        return method.invoke(null, account);
    }

    private static Object field(Object account, String name) throws Exception {
        Method accessor = accountClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(account);
    }

    private static ItemStack sword(GameTestHelper helper, int damage, int sharpness) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(damage);
        sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), sharpness);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Glass Sword"));
        CompoundTag data = new CompoundTag();
        data.putString("cosmetic", "glass_sword");
        sword.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return sword;
    }

    @GameTest
    public void wearDoesNotDefineLearnedIdentity(GameTestHelper helper) {
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(100);
        ItemStack normal = PEPreservationConfig.exemplar(worn);
        helper.assertTrue(ItemStack.isSameItemSameComponents(normal, new ItemStack(Items.IRON_PICKAXE)),
                "wear-only tools use the ordinary learned item");
        helper.assertTrue(normal.getDamageValue() == 0 && worn.getDamageValue() == 100,
                "canonicalization removes saved wear without changing the sold stack");
        ItemStack one = sword(helper, 20, 3);
        ItemStack two = sword(helper, 800, 3);
        helper.assertTrue(ItemStack.isSameItemSameComponents(PEPreservationConfig.exemplar(one),
                PEPreservationConfig.exemplar(two)), "the same enchanted cosmetic item ignores wear");
        helper.assertTrue(PEPreservationConfig.exemplar(one).get(DataComponents.CUSTOM_NAME).equals(one.get(DataComponents.CUSTOM_NAME)),
                "custom names survive");
        helper.succeed();
    }

    @GameTest
    public void migratesOldDuplicatesAndKeepsBalance(GameTestHelper helper) throws Exception {
        ItemStack first = new ItemStack(Items.IRON_PICKAXE);
        first.setDamageValue(40);
        ItemStack second = first.copy();
        second.setDamageValue(90);
        Object old = account(List.of("minecraft:stone"), List.of(first, second,
                sword(helper, 30, 3), sword(helper, 300, 3), sword(helper, 60, 4)));
        Object migrated = normalize(old);
        helper.assertTrue(field(migrated, "balance").equals(123456L), "migration preserves EMC");
        helper.assertTrue(field(migrated, "learned").equals(List.of("minecraft:stone", "minecraft:iron_pickaxe")),
                "durability-only variants merge into ordinary knowledge");
        @SuppressWarnings("unchecked") List<ItemStack> variants = (List<ItemStack>) field(migrated, "variants");
        helper.assertTrue(variants.size() == 2 && variants.stream().allMatch(stack -> stack.getDamageValue() == 0),
                "wear duplicates collapse while distinct enchantments remain");
        helper.assertTrue(!((List<?>) field(migrated, "learned")).contains("minecraft:diamond_sword"),
                "enchanted versions do not unlock the ordinary sword");
        helper.assertTrue(normalize(migrated) == migrated, "migration is idempotent");
        helper.succeed();
    }

    @GameTest
    public void disabledSwitchesDoNotEraseStoredVariants(GameTestHelper helper) throws Exception {
        Field enabled = PEPreservationConfig.class.getDeclaredField("preserveItemData");
        enabled.setAccessible(true);
        boolean original = enabled.getBoolean(null);
        enabled.setBoolean(null, false);
        try {
            ItemStack saved = sword(helper, 500, 3);
            Object migrated = normalize(account(List.of(), List.of(saved)));
            @SuppressWarnings("unchecked") List<ItemStack> variants = (List<ItemStack>) field(migrated, "variants");
            helper.assertTrue(variants.size() == 1 && ItemStack.isSameItemSameComponents(variants.getFirst(),
                    PEPreservationConfig.withoutDurability(saved)), "only wear is removed from saved records while disabled");
        } finally {
            enabled.setBoolean(null, original);
        }
        helper.succeed();
    }

    @GameTest
    public void migratedKnowledgeSurvivesSerialization(GameTestHelper helper) throws Exception {
        Object migrated = normalize(account(List.of("minecraft:stone"), List.of(sword(helper, 150, 3), sword(helper, 600, 3))));
        Field codecField = accountClass().getDeclaredField("CODEC");
        codecField.setAccessible(true);
        @SuppressWarnings("unchecked") Codec<Object> codec = (Codec<Object>) codecField.get(null);
        var ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        Object reopened = codec.parse(ops, codec.encodeStart(ops, migrated).getOrThrow()).getOrThrow();
        @SuppressWarnings("unchecked") List<ItemStack> variants = (List<ItemStack>) field(reopened, "variants");
        helper.assertTrue(variants.size() == 1 && variants.getFirst().getDamageValue() == 0, "wear stays absent after reload");
        helper.assertTrue(ItemStack.isSameItemSameComponents(variants.getFirst(),
                PEPreservationConfig.withoutDurability(sword(helper, 150, 3))), "all other components survive reload");
        helper.assertTrue(field(reopened, "balance").equals(123456L), "EMC survives reload");
        helper.succeed();
    }

    @GameTest
    public void damagedVariantSaleAndPurchaseRoundTrip(GameTestHelper helper) throws Exception {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(PECore.id("transmutation_tablet"))));
        player.containerMenu = new TransmutationMenu(1, player.getInventory(), true, 0L);
        for (int damage : new int[] {50, 500}) {
            player.getInventory().setItem(1, sword(helper, damage, 3));
            PETransmutationState.action(player, new PEPackets.Action(true, 0L, "sell", 1, "", 1));
        }
        var state = player.level().getServer().overworld().getDataStorage().computeIfAbsent(PETransmutationState.TYPE);
        Method getAccount = PETransmutationState.class.getDeclaredMethod("account", ServerPlayer.class);
        getAccount.setAccessible(true);
        Object learned = getAccount.invoke(state, player);
        helper.assertTrue(((List<?>) field(learned, "variants")).size() == 1, "selling different wear learns one variant");
        PETransmutationState.action(player, new PEPackets.Action(true, 0L, "buy", 0, "variant:0", 1));
        ItemStack bought = ItemStack.EMPTY;
        for (int i = 1; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.DIAMOND_SWORD)) bought = player.getInventory().getItem(i);
        }
        helper.assertTrue(!bought.isEmpty() && bought.getDamageValue() == 0, "purchase does not recreate wear");
        helper.assertTrue(ItemStack.isSameItemSameComponents(bought,
                PEPreservationConfig.withoutDurability(sword(helper, 50, 3))), "purchase keeps enchantments and cosmetic data");
        helper.succeed();
    }
}
