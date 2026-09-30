package moze_intel.projecte.emc.mappers;

import net.minecraft.world.item.crafting.RecipeManager;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongSortedMaps;
import java.util.Iterator;
import java.util.Map;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.mapper.EMCMapper;
import moze_intel.projecte.api.mapper.IEMCMapper;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.config.MappingConfig;
import moze_intel.projecte.config.PEConfigTranslations;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

@EMCMapper(priority = Integer.MIN_VALUE)
public class CustomEMCMapper implements IEMCMapper<NormalizedSimpleStack, Long> {
	private static final Map<String, Long> AVARITIA_EMC = Map.ofEntries(
			Map.entry("avaritia:compressed_crafting_table", 288L),
			Map.entry("avaritia:double_compressed_crafting_table", 2_592L),
			Map.entry("avaritia:diamond_lattice", 40_960L),
			Map.entry("avaritia:crystal_matrix_ingot", 442_368L),
			Map.entry("avaritia:extreme_crafting_table", 3_541_536L),
			Map.entry("avaritia:crystal_matrix_block", 3_981_312L),
			Map.entry("avaritia:record_fragment", 256L),
			Map.entry("avaritia:neutron_pile", 100L),
			Map.entry("avaritia:neutron_nugget", 900L),
			Map.entry("avaritia:neutronium_ingot", 8_100L),
			Map.entry("avaritia:neutron_ingot", 8_100L),
			Map.entry("avaritia:neutronium_block", 72_900L),
			Map.entry("avaritia:neutron_block", 72_900L),
			Map.entry("avaritia:neutron_collector", 3_166_720L),
			Map.entry("avaritia:compressor", 4_670_532L),
			Map.entry("avaritia:amethyst_singularity", 25_600L),
			Map.entry("avaritia:copper_singularity", 460_800L),
			Map.entry("avaritia:iron_singularity", 3_686_400L),
			Map.entry("avaritia:gold_singularity", 14_745_600L),
			Map.entry("avaritia:lapis_singularity", 12_441_600L),
			Map.entry("avaritia:redstone_singularity", 1_152_000L),
			Map.entry("avaritia:quartz_singularity", 1_228_800L),
			Map.entry("avaritia:diamond_singularity", 58_982_400L),
			Map.entry("avaritia:netherite_singularity", 103_219_200L),
			Map.entry("avaritia:emerald_singularity", 117_964_800L),
			Map.entry("avaritia:infinity_singularity", 210_201_600L),
			Map.entry("avaritia:eternal_singularity", 210_201_600L),
			Map.entry("avaritia:ultimate_stew", 548L),
			Map.entry("avaritia:cosmic_meatballs", 612L),
			Map.entry("avaritia:endest_pearl", 204_456L),
			Map.entry("avaritia:infinity_catalyst", 214_905_788L),
			Map.entry("avaritia:infinity_ingot", 2_368_581_748L),
			Map.entry("avaritia:infinity_block", 21_319_235_732L),
			Map.entry("avaritia:compressed_chest", 576L),
			Map.entry("avaritia:infinity_chest", 7_965_441_352L),
			Map.entry("avaritia:auto_crafting_table", 4_912_236L),
			Map.entry("avaritia:automatic_crafting_table", 4_912_236L)
	);

	@Override
	public void addMappings(IMappingCollector<NormalizedSimpleStack, Long> mapper, RecipeManager recipeManager,
			RegistryAccess registryAccess, ResourceManager resourceManager) {
		if (MappingConfig.enableAvaritiaEMC()) {
			for (Map.Entry<String, Long> entry : AVARITIA_EMC.entrySet()) {
				ResourceLocation id = ResourceLocation.parse(entry.getKey());
				BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
					PECore.debugLog("Adding built-in Avaritia EMC value for {}: {}", id, entry.getValue());
					mapper.setValueBefore(NSSItem.createItem(item), entry.getValue());
				});
			}
		}
		for (Iterator<Object2LongMap.Entry<NSSItem>> iterator = Object2LongSortedMaps.fastIterator(CustomEMCParser.currentEntries.entries()); iterator.hasNext(); ) {
			Object2LongMap.Entry<NSSItem> entry = iterator.next();
			NSSItem item = entry.getKey();
			long emc = entry.getLongValue();
			PECore.debugLog("Adding custom EMC value for {}: {}", item, emc);
			//Note: We set it for each of the values in the tag to make sure it is properly taken into account when calculating the individual EMC values
			item.forSelfAndEachElement(mapper, emc, IMappingCollector::setValueBefore);
		}
	}

	@Override
	public String getName() {
		return PEConfigTranslations.MAPPING_CUSTOM_EMC_MAPPER.title();
	}

	@Override
	public String getTranslationKey() {
		return PEConfigTranslations.MAPPING_CUSTOM_EMC_MAPPER.getTranslationKey();
	}

	@Override
	public String getDescription() {
		return PEConfigTranslations.MAPPING_CUSTOM_EMC_MAPPER.tooltip();
	}
}
