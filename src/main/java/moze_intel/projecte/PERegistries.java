package moze_intel.projecte;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class PERegistries {

    public static net.minecraft.world.inventory.MenuType<TransmutationMenu> TRANSMUTATION_MENU;
    public static net.minecraft.world.inventory.MenuType<AlchemicalChestMenu> ALCHEMICAL_CHEST_MENU;
    public static BlockEntityType<AlchemicalChestBlockEntity> ALCHEMICAL_CHEST_ENTITY;
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static Block transmutationTable;
    private static Item transmutationTablet;
    private static Block energyCondenser;
    private static Block darkMatterFurnace;
    private static Block redMatterFurnace;
    public static BlockEntityType<EnergyCondenserBlockEntity> ENERGY_CONDENSER_ENTITY;
    public static BlockEntityType<MatterFurnaceBlockEntity> MATTER_FURNACE_ENTITY;

    public static BlockEntityType<DarkMatterPedestalBlockEntity> DARK_MATTER_PEDESTAL_ENTITY;

    private PERegistries() {
    }

    public static void register() {
        TRANSMUTATION_MENU = Registry.register(BuiltInRegistries.MENU, PECore.id("transmutation"), new net.minecraft.world.inventory.MenuType<>(TransmutationMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
        ALCHEMICAL_CHEST_MENU = Registry.register(BuiltInRegistries.MENU, PECore.id("alchemical_chest"), new net.minecraft.world.inventory.MenuType<>(AlchemicalChestMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
        registerItems();
        registerBlocks();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, PECore.id("projectef"), net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.literal("ProjectEF")).icon(() -> new net.minecraft.world.item.ItemStack(transmutationTablet))
                .displayItems((parameters, output) -> { ITEMS.values().forEach(output::accept); BLOCKS.values().forEach(output::accept); }).build());
    }

    private static void registerItems() {
        simpleItem("philosophers_stone", noStack());
        simpleItem("repair_talisman", noStack());
        simpleItem("low_covalence_dust");
        simpleItem("medium_covalence_dust");
        simpleItem("high_covalence_dust");

        for (String color : new String[] {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            simpleItem(color + "_alchemical_bag", noStack());
        }

        for (String tier : new String[] {"ein", "zwei", "drei", "vier", "sphere", "omega"}) {
            simpleItem("klein_star_" + tier, noStack());
        }

        for (String name : new String[] {"alchemical_coal", "mobius_fuel", "aeternalis_fuel", "dark_matter", "red_matter", "iron_band"}) {
            simpleItem(name);
        }

        for (String name : new String[] {
                "dm_pick", "dm_axe", "dm_shovel", "dm_sword", "dm_hoe", "dm_shears", "dm_hammer",
                "rm_pick", "rm_axe", "rm_shovel", "rm_sword", "rm_hoe", "rm_shears", "rm_hammer", "rm_katar", "rm_morning_star",
                "dm_helmet", "dm_chestplate", "dm_leggings", "dm_boots",
                "rm_helmet", "rm_chestplate", "rm_leggings", "rm_boots",
                "gem_helmet", "gem_chestplate", "gem_leggings", "gem_boots",
                "black_hole_band", "archangel_smite", "harvest_goddess_band", "ignition_ring", "zero_ring", "swiftwolf_rending_gale",
                "watch_of_flowing_time", "evertide_amulet", "volcanite_amulet", "gem_of_eternal_density", "mercurial_eye", "void_ring",
                "arcana_ring", "body_stone", "soul_stone", "mind_stone", "life_stone",
                "divining_rod_1", "divining_rod_2", "divining_rod_3", "destruction_catalyst", "hyperkinetic_lens", "catalytic_lens",
                "tome"
        }) {
            simpleItem(name, noStackFireResistant());
        }
        transmutationTablet = simpleItem("transmutation_tablet", noStackFireResistant());
    }

    private static void registerBlocks() {
        var chestKey=ResourceKey.create(Registries.BLOCK,PECore.id("alchemical_chest"));
        Block chest=new AlchemicalChestBlock(BlockBehaviour.Properties.of().setId(chestKey).strength(2.5f).sound(net.minecraft.world.level.block.SoundType.WOOD));
        Registry.register(BuiltInRegistries.BLOCK,chestKey,chest);BLOCKS.put("alchemical_chest",chest);
        var chestItemKey=ResourceKey.create(Registries.ITEM,PECore.id("alchemical_chest"));
        Registry.register(BuiltInRegistries.ITEM,chestItemKey,new BlockItem(chest,new Item.Properties().setId(chestItemKey).useBlockDescriptionPrefix()));
        ALCHEMICAL_CHEST_ENTITY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,PECore.id("alchemical_chest"),new BlockEntityType<>(AlchemicalChestBlockEntity::new,java.util.Set.of(chest)));
        block("alchemical_coal_block", fuelBlock(MapColor.COLOR_RED));
        block("mobius_fuel_block", fuelBlock(MapColor.COLOR_RED));
        block("aeternalis_fuel_block", fuelBlock(MapColor.COLOR_LIGHT_GRAY));
        block("dark_matter_block", matterBlock(MapColor.COLOR_BLACK), fireResistant());
        block("red_matter_block", matterBlock(MapColor.COLOR_RED), fireResistant());
        var pedestalKey = ResourceKey.create(Registries.BLOCK, PECore.id("dm_pedestal"));
        Block pedestal = new DarkMatterPedestalBlock(BlockBehaviour.Properties.of().setId(pedestalKey)
                .mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).noOcclusion()
                .pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE)
                .lightLevel(state -> state.getValue(DarkMatterPedestalBlock.ACTIVE) ? 7 : 0));
        Registry.register(BuiltInRegistries.BLOCK, pedestalKey, pedestal);
        BLOCKS.put("dm_pedestal", pedestal);
        var pedestalItemKey = ResourceKey.create(Registries.ITEM, PECore.id("dm_pedestal"));
        Registry.register(BuiltInRegistries.ITEM, pedestalItemKey,
                new BlockItem(pedestal, new Item.Properties().setId(pedestalItemKey).useBlockDescriptionPrefix().fireResistant()));
        DARK_MATTER_PEDESTAL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PECore.id("dm_pedestal"),
                new BlockEntityType<>(DarkMatterPedestalBlockEntity::new, java.util.Set.of(pedestal)));
        darkMatterFurnace = matterFurnace("dm_furnace", MapColor.COLOR_BLACK);
        redMatterFurnace = matterFurnace("rm_furnace", MapColor.COLOR_RED);
        // The table should always drop itself when broken; requiring a particular
        // tool would make it appear to vanish when harvested by hand or with the
        // wrong tier of pickaxe.
        transmutationTable = transmutationTable("transmutation_table", table(10.0F, 30.0F));
        energyCondenser = energyCondenser("energy_condenser", table(3.0F, 6.0F));
        ENERGY_CONDENSER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, PECore.id("energy_condenser")),
                new BlockEntityType<>(EnergyCondenserBlockEntity::new, java.util.Set.of(energyCondenser)));
        MATTER_FURNACE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, PECore.id("matter_furnace")),
                new BlockEntityType<>((pos, state) -> new MatterFurnaceBlockEntity(pos, state, state.getBlock() == redMatterFurnace),
                        java.util.Set.of(darkMatterFurnace, redMatterFurnace)));
    }

    public static boolean isTransmutationTable(Block block) {
        return block == transmutationTable;
    }

    public static boolean isTransmutationTablet(Item item) {
        return item == transmutationTablet;
    }

    public static boolean isBlackHoleBand(Item item) {
        return item == ITEMS.get("black_hole_band");
    }

    private static Block energyCondenser(String name, BlockBehaviour.Properties properties) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, PECore.id(name));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, PECore.id(name));
        Block block = new EnergyCondenserBlock(properties.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        energyCondenser = block;
        BLOCKS.put(name, block);
        return block;
    }

    private static Block matterFurnace(String name, MapColor color) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, PECore.id(name));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, PECore.id(name));
        Block block = new MatterFurnaceBlock(name.equals("rm_furnace"), BlockBehaviour.Properties.of().mapColor(color)
                .requiresCorrectToolForDrops().strength(1_000_000.0F, 3_000_000.0F)
                .lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 14 : 0)
                .setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().fireResistant()));
        BLOCKS.put(name, block);
        return block;
    }

    private static Item simpleItem(String name) {
        return simpleItem(name, Function.identity());
    }

    private static Item simpleItem(String name, Function<Item.Properties, Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PECore.id(name));
        Item.Properties settings = properties.apply(new Item.Properties().setId(key));
        Item item;
        if (name.equals("philosophers_stone")) item = new PhilosophersStoneItem(settings);
        else if (name.equals("evertide_amulet")) item = new EvertideAmuletItem(settings);
        else if (name.equals("swiftwolf_rending_gale")) item = new SwiftwolfRingItem(settings);
        else if (name.equals("arcana_ring")) item = new ClassicRingItem(settings, null);
        else if (name.equals("zero_ring")) item = new ClassicRingItem(settings, ClassicRingItem.Power.ZERO);
        else if (name.equals("ignition_ring")) item = new ClassicRingItem(settings, ClassicRingItem.Power.IGNITION);
        else if (name.equals("harvest_goddess_band")) item = new ClassicRingItem(settings, ClassicRingItem.Power.HARVEST);
        else if (name.matches("[dr]m_(pick|axe|shovel|sword|hoe|shears|hammer|katar|morning_star)")) item = new MatterToolItem(settings, name.substring(3), name.startsWith("rm_"));
        else if (name.matches("(dm|rm|gem)_(helmet|chestplate|leggings|boots)")) {
            var type = switch(name.substring(name.indexOf('_') + 1)) { case "helmet" -> net.minecraft.world.item.equipment.ArmorType.HELMET; case "chestplate" -> net.minecraft.world.item.equipment.ArmorType.CHESTPLATE; case "leggings" -> net.minecraft.world.item.equipment.ArmorType.LEGGINGS; default -> net.minecraft.world.item.equipment.ArmorType.BOOTS; };
            var base = net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE;
            var material = new net.minecraft.world.item.equipment.ArmorMaterial(1000, java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.HELMET, 3, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 8, net.minecraft.world.item.equipment.ArmorType.LEGGINGS, 6, net.minecraft.world.item.equipment.ArmorType.BOOTS, 3), 10, base.equipSound(), name.startsWith("dm_") ? 3 : 5, .1f, base.repairIngredient(), ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, PECore.id(name.startsWith("dm_") ? "dark_matter" : name.startsWith("rm_") ? "red_matter" : "gem_armor")));
            item = new Item(settings.humanoidArmor(material, type).component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE));
        } else item = new Item(settings);
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ITEMS.put(name, item);
        return item;
    }

    private static Block block(String name, BlockBehaviour.Properties properties) {
        return block(name, properties, Function.identity());
    }

    private static Block block(String name, BlockBehaviour.Properties properties, Function<Item.Properties, Item.Properties> itemProperties) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, PECore.id(name));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, PECore.id(name));
        Block block = new Block(properties.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties.apply(new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
        BLOCKS.put(name, block);
        return block;
    }

    private static Block transmutationTable(String name, BlockBehaviour.Properties properties) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, PECore.id(name));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, PECore.id(name));
        Block block = new TransmutationTableBlock(properties.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        BLOCKS.put(name, block);
        return block;
    }

    private static BlockBehaviour.Properties fuelBlock(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.5F, 1.5F);
    }

    private static BlockBehaviour.Properties stone(float hardness, float resistance) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(hardness, resistance);
    }

    private static BlockBehaviour.Properties table(float hardness, float resistance) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                .requiresCorrectToolForDrops().strength(0.5F, 1.5F);
    }

    private static BlockBehaviour.Properties matterBlock(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(1_000_000.0F, 3_000_000.0F).lightLevel(state -> 14);
    }

    private static Function<Item.Properties, Item.Properties> noStack() {
        return properties -> properties.stacksTo(1);
    }

    private static Function<Item.Properties, Item.Properties> fireResistant() {
        return Item.Properties::fireResistant;
    }

    private static Function<Item.Properties, Item.Properties> noStackFireResistant() {
        return properties -> properties.stacksTo(1).fireResistant();
    }
}
