package moze_intel.projecte.integration;

import moze_intel.projecte.api.item_handlers.IItemHandler;
import moze_intel.projecte.integration.avaritia.AvaritiaIntegration;
import moze_intel.projecte.integration.trinkets.TrinketsIntegration;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public class IntegrationHelper {

	public static final String TRINKETS_MODID = "trinkets";
	public static final String EMI_MODID = "emi";
	public static final String JEI_MODID = "jei";
	public static final String REI_MODID = "roughlyenoughitems";

	private static final boolean TRINKETS_LOADED = FabricLoader.getInstance().isModLoaded(TRINKETS_MODID);

	/**
	 * Called during common mod init to hook up any loaded optional integrations.
	 * <p>
	 * The trinkets accessories are registered per-item via {@link #registerCuriosCapability(Item)} during item
	 * registration. Avaritia's additional recipes are supplied by an automatically enabled data pack.
	 */
	public static void init() {
		if (FabricLoader.getInstance().isModLoaded(AvaritiaIntegration.MODID)) {
			AvaritiaIntegration.registerRecipePack();
		}
	}

	/**
	 * Gets the accessory (trinkets) inventory of the given player, or null if the trinkets mod is not loaded.
	 */
	@Nullable
	public static IItemHandler getCuriosInventory(Player player) {
		if (TRINKETS_LOADED) {
			return TrinketsIntegration.getInventory(player);
		}
		return null;
	}

	/**
	 * Registers the accessory (trinkets) capability for the given item when the trinkets mod is loaded.
	 */
	public static void registerCuriosCapability(Item item) {
		if (TRINKETS_LOADED) {
			TrinketsIntegration.registerTrinket(item);
		}
	}
}
