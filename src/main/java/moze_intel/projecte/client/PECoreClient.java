package moze_intel.projecte.client;

import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.container.DMFurnaceContainer;
import moze_intel.projecte.gameObjs.entity.EntitySWRGProjectile;
import moze_intel.projecte.gameObjs.gui.AbstractCollectorScreen;
import moze_intel.projecte.gameObjs.gui.AbstractCondenserScreen;
import moze_intel.projecte.gameObjs.gui.AlchBagScreen;
import moze_intel.projecte.gameObjs.gui.AlchChestScreen;
import moze_intel.projecte.gameObjs.gui.GUIDMFurnace;
import moze_intel.projecte.gameObjs.gui.GUIEternalDensity;
import moze_intel.projecte.gameObjs.gui.GUIMercurialEye;
import moze_intel.projecte.gameObjs.gui.GUIRMFurnace;
import moze_intel.projecte.gameObjs.gui.GUIRelay.GUIRelayMK1;
import moze_intel.projecte.gameObjs.gui.GUIRelay.GUIRelayMK2;
import moze_intel.projecte.gameObjs.gui.GUIRelay.GUIRelayMK3;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import moze_intel.projecte.gameObjs.registries.PEBlockEntityTypes;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEContainerTypes;
import moze_intel.projecte.gameObjs.registries.PEDataComponentTypes;
import moze_intel.projecte.gameObjs.registries.PEEntityTypes;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.events.ToolTipEvent;
import moze_intel.projecte.gameObjs.sound.MovingSoundSWRG;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.PacketHandler.ClientboundRegistration;
import moze_intel.projecte.network.ThreadCheckUpdate;
import moze_intel.projecte.network.commands.client.DumpMissingEmc;
import moze_intel.projecte.rendering.ChestRenderer;
import moze_intel.projecte.rendering.EntitySpriteRenderer;
import moze_intel.projecte.rendering.LayerYue;
import moze_intel.projecte.rendering.PedestalRenderer;
import moze_intel.projecte.rendering.TransmutationRenderingOverlay;
import moze_intel.projecte.utils.ClientKeyHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.HitResult;

public class PECoreClient implements ClientModInitializer {

	public static final ResourceLocation ACTIVE_OVERRIDE = PECore.rl("active");
	public static final ResourceLocation MODE_OVERRIDE = PECore.rl("mode");

	@Override
	public void onInitializeClient() {
		ThreadCheckUpdate.registerClient();
		registerClientPacketReceivers();
		registerScreens();
		registerKeybindings();
		EMCManagerClient.register();
		registerOverlays();
		registerRenderers();
		addLayers();
		registerItemProperties();
		DumpMissingEmc.registerClientCommand();

		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof EntitySWRGProjectile projectile && Minecraft.getInstance().mouseHandler.isMouseGrabbed()) {
				Minecraft.getInstance().getSoundManager().play(new MovingSoundSWRG(projectile, level.getRandom()));
			}
		});

		ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) ->
				ToolTipEvent.onTooltip(lines, stack, Minecraft.getInstance().player, Screen.hasShiftDown()));

		//Left clicking into empty air with the archangel smite fires a volley, handled server side through the existing packet
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			if (clickCount > 0 && player.getMainHandItem().is(PEItems.ARCHANGEL_SMITE.get())
				&& (client.hitResult == null || client.hitResult.getType() == HitResult.Type.MISS)) {
				PECore.packetHandler().activateArchangel();
			}
			return false;
		});
	}

	private void registerClientPacketReceivers() {
		for (ClientboundRegistration<?> registration : PacketHandler.getClientboundRegistrations()) {
			registerClientReceiver(registration);
		}
	}

	private <MSG extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void registerClientReceiver(ClientboundRegistration<MSG> registration) {
		ClientPlayNetworking.registerGlobalReceiver(registration.type(), (payload, context) -> registration.handler().handle(payload, context::player));
	}

	private void registerScreens() {
		MenuScreens.register(PEContainerTypes.RM_FURNACE_CONTAINER.get(), GUIRMFurnace::new);
		//noinspection RedundantTypeArguments (necessary for it to actually compile)
		MenuScreens.<DMFurnaceContainer, GUIDMFurnace<DMFurnaceContainer>>register(PEContainerTypes.DM_FURNACE_CONTAINER.get(), GUIDMFurnace::new);
		MenuScreens.register(PEContainerTypes.CONDENSER_CONTAINER.get(), AbstractCondenserScreen.MK1::new);
		MenuScreens.register(PEContainerTypes.CONDENSER_MK2_CONTAINER.get(), AbstractCondenserScreen.MK2::new);
		MenuScreens.register(PEContainerTypes.ALCH_CHEST_CONTAINER.get(), AlchChestScreen::new);
		MenuScreens.register(PEContainerTypes.ALCH_BAG_CONTAINER.get(), AlchBagScreen::new);
		MenuScreens.register(PEContainerTypes.ETERNAL_DENSITY_CONTAINER.get(), GUIEternalDensity::new);
		MenuScreens.register(PEContainerTypes.TRANSMUTATION_CONTAINER.get(), GUITransmutation::new);
		MenuScreens.register(PEContainerTypes.RELAY_MK1_CONTAINER.get(), GUIRelayMK1::new);
		MenuScreens.register(PEContainerTypes.RELAY_MK2_CONTAINER.get(), GUIRelayMK2::new);
		MenuScreens.register(PEContainerTypes.RELAY_MK3_CONTAINER.get(), GUIRelayMK3::new);
		MenuScreens.register(PEContainerTypes.COLLECTOR_MK1_CONTAINER.get(), AbstractCollectorScreen.MK1::new);
		MenuScreens.register(PEContainerTypes.COLLECTOR_MK2_CONTAINER.get(), AbstractCollectorScreen.MK2::new);
		MenuScreens.register(PEContainerTypes.COLLECTOR_MK3_CONTAINER.get(), AbstractCollectorScreen.MK3::new);
		MenuScreens.register(PEContainerTypes.MERCURIAL_EYE_CONTAINER.get(), GUIMercurialEye::new);
	}

	private void registerItemProperties() {
		//Property Overrides
		addPropertyOverrides(ACTIVE_OVERRIDE, (stack, level, entity, seed) -> stack.getOrDefault(PEDataComponentTypes.ACTIVE.get(), false) ? 1F : 0F,
				PEItems.GEM_OF_ETERNAL_DENSITY, PEItems.VOID_RING, PEItems.ARCANA_RING, PEItems.ARCHANGEL_SMITE, PEItems.BLACK_HOLE_BAND, PEItems.BODY_STONE,
				PEItems.HARVEST_GODDESS_BAND, PEItems.IGNITION_RING, PEItems.LIFE_STONE, PEItems.MIND_STONE, PEItems.SOUL_STONE, PEItems.WATCH_OF_FLOWING_TIME,
				PEItems.ZERO_RING);
		addPropertyOverrides(MODE_OVERRIDE, (stack, level, entity, seed) ->
				stack.getOrDefault(PEDataComponentTypes.ARCANA_MODE.get(), PEItems.ARCANA_RING.asItem().getDefaultMode()).ordinal(), PEItems.ARCANA_RING);
		addPropertyOverrides(MODE_OVERRIDE, (stack, level, entity, seed) ->
				stack.getOrDefault(PEDataComponentTypes.SWRG_MODE.get(), PEItems.ARCANA_RING.asItem().getDefaultMode()).ordinal(), PEItems.SWIFTWOLF_RENDING_GALE);
	}

	private void registerKeybindings() {
		ClientKeyHelper.registerKeyBindings();
	}

	private void registerOverlays() {
		TransmutationRenderingOverlay overlay = new TransmutationRenderingOverlay();
		WorldRenderEvents.BLOCK_OUTLINE.register(overlay::onBlockOutline);
		HudRenderCallback.EVENT.register(overlay::onHudRender);
	}

	private void registerRenderers() {
		//Block Entity renderers
		BlockEntityRenderers.register(PEBlockEntityTypes.ALCHEMICAL_CHEST.get(), context -> new ChestRenderer(context, PECore.rl("textures/block/alchemical_chest.png"), PEBlocks.ALCHEMICAL_CHEST));
		BlockEntityRenderers.register(PEBlockEntityTypes.CONDENSER.get(), context -> new ChestRenderer(context, PECore.rl("textures/block/condenser_mk1.png"), PEBlocks.CONDENSER));
		BlockEntityRenderers.register(PEBlockEntityTypes.CONDENSER_MK2.get(), context -> new ChestRenderer(context, PECore.rl("textures/block/condenser_mk2.png"), PEBlocks.CONDENSER_MK2));
		BlockEntityRenderers.register(PEBlockEntityTypes.DARK_MATTER_PEDESTAL.get(), PedestalRenderer::new);

		//Entity renderers
		EntityRendererRegistry.register(PEEntityTypes.WATER_PROJECTILE.get(), context -> new EntitySpriteRenderer<>(context, PECore.rl("textures/entity/water_orb.png")));
		EntityRendererRegistry.register(PEEntityTypes.LAVA_PROJECTILE.get(), context -> new EntitySpriteRenderer<>(context, PECore.rl("textures/entity/lava_orb.png")));
		EntityRendererRegistry.register(PEEntityTypes.MOB_RANDOMIZER.get(), context -> new EntitySpriteRenderer<>(context, PECore.rl("textures/entity/randomizer.png")));
		EntityRendererRegistry.register(PEEntityTypes.LENS_PROJECTILE.get(), context -> new EntitySpriteRenderer<>(context, PECore.rl("textures/entity/lens_explosive.png")));
		EntityRendererRegistry.register(PEEntityTypes.FIRE_PROJECTILE.get(), context -> new EntitySpriteRenderer<>(context, PECore.rl("textures/entity/fireball.png")));
		EntityRendererRegistry.register(PEEntityTypes.SWRG_PROJECTILE.get(), context -> new EntitySpriteRenderer<>(context, PECore.rl("textures/entity/lightning.png")));
		EntityRendererRegistry.register(PEEntityTypes.NOVA_CATALYST_PRIMED.get(), TntRenderer::new);
		EntityRendererRegistry.register(PEEntityTypes.NOVA_CATACLYSM_PRIMED.get(), TntRenderer::new);
		EntityRendererRegistry.register(PEEntityTypes.HOMING_ARROW.get(), TippableArrowRenderer::new);
	}

	private void addLayers() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, registrationHelper, context) -> {
			if (renderer instanceof PlayerRenderer playerRenderer) {
				registrationHelper.register(new LayerYue(playerRenderer));
			}
		});
	}

	@SuppressWarnings("deprecation")
	private static void addPropertyOverrides(ResourceLocation override, ClampedItemPropertyFunction propertyGetter, ItemLike... itemProviders) {
		for (ItemLike itemProvider : itemProviders) {
			ItemProperties.register(itemProvider.asItem(), override, propertyGetter);
		}
	}
}
