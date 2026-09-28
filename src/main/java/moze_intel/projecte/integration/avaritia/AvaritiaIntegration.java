package moze_intel.projecte.integration.avaritia;

import moze_intel.projecte.PECore;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * AvaritiaNeo Fabric gates its matter singularities behind Item Alchemy. Reuse its item registration,
 * without enabling Item Alchemy's EMC parsers or pretending that Item Alchemy itself is installed.
 * This integration targets the item registration used by AvaritiaNeo Fabric 1.1.7 for Minecraft 1.21.1.
 */
public final class AvaritiaIntegration {

	public static final String MODID = "avaritia";

	private AvaritiaIntegration() {
	}

	public static void registerRecipePack() {
		if (!ResourceManagerHelper.registerBuiltinResourcePack(PECore.rl("avaritia"), PECore.MOD_CONTAINER,
				ResourcePackActivationType.ALWAYS_ENABLED)) {
			throw new IllegalStateException("Missing ProjectEF Avaritia compatibility data pack");
		}
	}

	/** Called after Avaritia's own compatibility initialization, while item registration is still open. */
	public static void registerMatterSingularities() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded(MODID) || loader.isModLoaded("itemalchemy")) {
			return;
		}
		try {
			// Only this class is safe without Item Alchemy. AvaritiaEMC also links to its recipe parsers.
			Class<?> items = Class.forName("net.byAqua3.avaritia.compat.itemalchemy.loader.AvaritiaEMCItems");
			items.getMethod("registerItems").invoke(null);
			PECore.LOGGER.info("Enabled Avaritia dark matter and red matter singularities for ProjectEF");
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not register AvaritiaNeo matter singularities for ProjectEF", e);
		}
	}
}
