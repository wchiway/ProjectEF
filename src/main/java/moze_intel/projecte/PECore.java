package moze_intel.projecte;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.ProjectERegistries;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.nss.AbstractNSSTag;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.events.PlayerEvents;
import moze_intel.projecte.gameObjs.blocks.ProjectETNT;
import moze_intel.projecte.gameObjs.items.PhilosophersStone;
import moze_intel.projecte.gameObjs.items.rings.Arcana.ArcanaMode;
import moze_intel.projecte.gameObjs.registries.PEArmorMaterials;
import moze_intel.projecte.gameObjs.registries.PEAttachmentTypes;
import moze_intel.projecte.gameObjs.registries.PEBlockEntityTypes;
import moze_intel.projecte.gameObjs.registries.PEBlockTypes;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEContainerTypes;
import moze_intel.projecte.gameObjs.registries.PECreativeTabs;
import moze_intel.projecte.gameObjs.registries.PEDataComponentTypes;
import moze_intel.projecte.gameObjs.registries.PEEntityTypes;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.gameObjs.registries.PENormalizedSimpleStacks;
import moze_intel.projecte.gameObjs.registries.PERecipeConditions;
import moze_intel.projecte.gameObjs.registries.PERecipeSerializers;
import moze_intel.projecte.gameObjs.registries.PESoundEvents;
import moze_intel.projecte.impl.TransmutationOffline;
import moze_intel.projecte.impl.capability.AlchBagImpl;
import moze_intel.projecte.impl.capability.KnowledgeImpl;
import moze_intel.projecte.integration.IntegrationHelper;
import moze_intel.projecte.network.PENetwork;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.ThreadCheckUpdate;
import moze_intel.projecte.network.commands.EMCCMD;
import moze_intel.projecte.network.commands.KnowledgeCMD;
import moze_intel.projecte.network.commands.RemoveEmcCMD;
import moze_intel.projecte.network.commands.ResetEmcCMD;
import moze_intel.projecte.network.commands.SetEmcCMD;
import moze_intel.projecte.network.commands.ShowBagCMD;
import moze_intel.projecte.network.packets.to_client.SyncEmcPKT;
import moze_intel.projecte.network.packets.to_client.SyncFuelMapperPKT;
import moze_intel.projecte.network.packets.to_client.SyncWorldTransmutations;
import moze_intel.projecte.utils.WorldHelper;
import moze_intel.projecte.world_transmutation.WorldTransmutationManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.core.dispenser.ShearsDispenseItemBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class PECore implements ModInitializer {

	public static final String MODID = ProjectEAPI.PROJECTE_MODID;
	public static final String MODNAME = "ProjectEF";

	/**
	 * Legacy configuration directory retained for compatibility with existing installations.
	 */
	public static final String CONFIG_DIR_NAME = "ProjectE";
	public static final GameProfile FAKEPLAYER_GAMEPROFILE = new GameProfile(UUID.fromString("590e39c7-9fb6-471b-a4c2-c0e539b2423d"), "[" + MODNAME + "]");
	public static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * @deprecated No longer populated. The remote UUID list was removed; use
	 * {@link moze_intel.projecte.config.CommonConfig#highAlchemistUUIDs} instead.
	 */
	@Deprecated
	public static final List<String> uuids = List.of();

	public static ModContainer MOD_CONTAINER;

	public static void debugLog(String msg, Object... args) {
		if (FabricLoader.getInstance().isDevelopmentEnvironment() || ProjectEConfig.common.debugLogging.get()) {
			LOGGER.info(msg, args);
		} else {
			LOGGER.debug(msg, args);
		}
	}

	public static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	private static PECore instance;
	@Nullable
	private static MinecraftServer currentServer;

	/**
	 * The running server instance, replacing the static server lookup NeoForge used to provide. Set while a server (integrated or dedicated) is starting until it
	 * has stopped.
	 */
	@Nullable
	public static MinecraftServer getServer() {
		return currentServer;
	}

	@Nullable
	private EmcUpdateData emcUpdateResourceManager;
	private PacketHandler packetHandler;

	public static PacketHandler packetHandler() {
		return instance.packetHandler;
	}

	@Override
	public void onInitialize() {
		instance = this;
		MOD_CONTAINER = FabricLoader.getInstance().getModContainer(MODID)
				.orElseThrow(() -> new IllegalStateException("Mod container for " + MODID + " not present"));

		//Register our config files
		ProjectEConfig.register();

		//Perform the actual game object registration, in dependency order
		PEAttachmentTypes.init();
		PESoundEvents.SOUND_EVENTS.register();
		PEDataComponentTypes.DATA_COMPONENT_TYPES.register();
		PEArmorMaterials.ARMOR_MATERIALS.register();
		PEBlockTypes.BLOCK_TYPES.register();
		PEBlocks.BLOCKS.register();
		PEItems.ITEMS.register();
		PEBlockEntityTypes.BLOCK_ENTITY_TYPES.register();
		PEContainerTypes.CONTAINER_TYPES.register();
		PEEntityTypes.ENTITY_TYPES.register();
		PECreativeTabs.CREATIVE_TABS.register();
		PENormalizedSimpleStacks.NSS_SERIALIZERS.register();
		PERecipeConditions.init();
		PERecipeSerializers.RECIPE_SERIALIZERS.register();

		registerCapabilities();

		this.packetHandler = new PacketHandler();

		//Reload listeners: track when server data (recipes and friends) change so that we recalculate EMC values
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public net.minecraft.resources.ResourceLocation getFabricId() {
				return rl("emc_update_tracker");
			}

			@Override
			public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
				emcUpdateResourceManager = new EmcUpdateData(resourceManager);
			}
		});
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(WorldTransmutationManager.INSTANCE);

		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(this::dataPackSync);
		ServerLifecycleEvents.SERVER_STARTING.register(this::serverStarting);
		ServerLifecycleEvents.SERVER_STOPPED.register(this::serverQuit);
		CommandRegistrationCallback.EVENT.register(this::registerCommands);
		PlayerEvents.register();
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (player.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof PhilosophersStone stone) {
				return stone.useOn(new UseOnContext(player, hand, hitResult));
			}
			return InteractionResult.PASS;
		});

		IntegrationHelper.init();

		commonSetup();
	}

	private void registerCapabilities() {
		PECapabilities.ALCH_BAG_CAPABILITY.registerForType((player, context) -> new AlchBagImpl(player), EntityType.PLAYER);
		PECapabilities.KNOWLEDGE_CAPABILITY.registerForType((player, context) -> new KnowledgeImpl(player), EntityType.PLAYER);
	}

	private void commonSetup() {
		registerFurnaceFuels();
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			new ThreadCheckUpdate().start();
		}
		EMCMappingHandler.loadMappers();

		//Match the NeoForge version, which only overrides getFlammability (= vanilla burnOdds) with 100
		//and leaves getFireSpreadSpeed (= vanilla igniteOdds) at its default of 0 for these blocks.
		//burnOdds is what FireBlock#checkBurnOut rolls against, so fire next to a Nova block still ignites it.
		FlammableBlockRegistry.getDefaultInstance().add(PEBlocks.NOVA_CATALYST.getBlock(), 0, 100);
		FlammableBlockRegistry.getDefaultInstance().add(PEBlocks.NOVA_CATACLYSM.getBlock(), 0, 100);

		//Dispenser Behavior
		registerDispenseBehavior(new ShearsDispenseItemBehavior(), PEItems.DARK_MATTER_SHEARS, PEItems.RED_MATTER_SHEARS, PEItems.RED_MATTER_KATAR);
		DispenserBlock.registerBehavior(PEBlocks.NOVA_CATALYST, PEBlocks.NOVA_CATALYST.getBlock().createDispenseItemBehavior());
		DispenserBlock.registerBehavior(PEBlocks.NOVA_CATACLYSM, PEBlocks.NOVA_CATACLYSM.getBlock().createDispenseItemBehavior());
		registerDispenseBehavior(new OptionalDispenseItemBehavior() {
			@NotNull
			@Override
			protected ItemStack execute(@NotNull BlockSource source, @NotNull ItemStack stack) {
				//[VanillaCopy] Based off the flint and steel dispense behavior
				if (!canDispenseIgnite(stack)) {
					//Only allow using the arcana ring to ignite things when on ignition mode
					setSuccess(false);
					return super.execute(source, stack);
				}
				Level level = source.level();
				setSuccess(true);
				Direction direction = source.state().getValue(DispenserBlock.FACING);
				BlockPos pos = source.pos().relative(direction);
				BlockState state = level.getBlockState(pos);
				if (BaseFireBlock.canBePlacedAt(level, pos, direction)) {
					level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
					level.gameEvent(null, GameEvent.BLOCK_PLACE, pos);
				} else if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
					//Light campfires, candles, and candle cakes like vanilla flint and steel does
					level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
					level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
				} else if (state.getBlock() instanceof TntBlock) {
					//Ignite the tnt, spawning the custom ProjectEF entity when it is a Nova block
					if (state.getBlock() instanceof ProjectETNT petnt) {
						petnt.createAndAddEntity(level, pos, null);
					} else {
						TntBlock.explode(level, pos);
					}
					level.removeBlock(pos, false);
				} else {
					setSuccess(false);
				}
				return stack;
			}
		}, PEItems.IGNITION_RING, PEItems.ARCANA_RING);
		DispenserBlock.registerBehavior(PEItems.EVERTIDE_AMULET, new DefaultDispenseItemBehavior() {
			@NotNull
			@Override
			public ItemStack execute(@NotNull BlockSource source, @NotNull ItemStack stack) {
				//Based off of vanilla's bucket dispense behaviors
				// Note: We only do evertide, not volcanite, as placing lava requires EMC
				Level level = source.level();
				Direction direction = source.state().getValue(DispenserBlock.FACING);
				BlockPos pos = source.pos().relative(direction);
				Storage<FluidVariant> fluidStorage = FluidStorage.SIDED.find(level, pos, direction.getOpposite());
				if (fluidStorage != null) {
					try (Transaction transaction = Transaction.openOuter()) {
						fluidStorage.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, transaction);
						transaction.commit();
					}
					return stack;
				}
				WorldHelper.placeFluid(null, level, pos, Fluids.WATER, !ProjectEConfig.server.items.opEvertide.get());
				level.playSound(null, pos.getX(), pos.getY(), pos.getZ(), PESoundEvents.WATER_MAGIC.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
				return stack;
			}
		});
	}

	private static void registerFurnaceFuels() {
		//Keep the burn times from the former NeoForge furnace fuel data map.
		int alchemicalCoal = 1_600 * 4;
		int mobiusFuel = alchemicalCoal * 4;
		int aeternalisFuel = mobiusFuel * 4;
		FuelRegistry fuels = FuelRegistry.INSTANCE;
		fuels.add(PEItems.ALCHEMICAL_COAL.get(), alchemicalCoal);
		fuels.add(PEBlocks.ALCHEMICAL_COAL.asItem(), alchemicalCoal * 9);
		fuels.add(PEItems.MOBIUS_FUEL.get(), mobiusFuel);
		fuels.add(PEBlocks.MOBIUS_FUEL.asItem(), mobiusFuel * 9);
		fuels.add(PEItems.AETERNALIS_FUEL.get(), aeternalisFuel);
		fuels.add(PEBlocks.AETERNALIS_FUEL.asItem(), aeternalisFuel * 9);
	}

	private static boolean canDispenseIgnite(ItemStack stack) {
		if (stack.is(PEItems.IGNITION_RING.get())) {
			return true;
		}
		return stack.is(PEItems.ARCANA_RING.get()) && stack.getOrDefault(PEDataComponentTypes.ARCANA_MODE.get(), ArcanaMode.ZERO) == ArcanaMode.IGNITION;
	}

	private static void registerDispenseBehavior(DispenseItemBehavior behavior, ItemLike... items) {
		for (ItemLike item : items) {
			DispenserBlock.registerBehavior(item, behavior);
		}
	}

	private void dataPackSync(ServerPlayer player, boolean joined) {
		if (emcUpdateResourceManager != null) {
			MinecraftServer server = player.getServer();
			if (server != null) {
				long start = System.currentTimeMillis();
				//Clear the cached created tags
				AbstractNSSTag.clearCreatedTags();
				CustomEMCParser.init(server.registryAccess());
				try {
					EMCMappingHandler.map(server.getRecipeManager(), server.registryAccess(), emcUpdateResourceManager.resourceManager());
					PECore.LOGGER.info("Registered {} EMC values. (took {} ms)", EMCMappingHandler.getEmcMapSize(), System.currentTimeMillis() - start);
				} catch (Throwable t) {
					PECore.LOGGER.error("Error calculating EMC values", t);
				}
				emcUpdateResourceManager = null;
			}
		}
		//The host of an integrated server shares the EMC, fuel and world transmutation data with the server, so don't sync it to them.
		// Memory connections still encode packets, so syncing would replace the shared EMC map with a copy decoded against the client's registries,
		// which the server then fails to encode for any LAN player that joins (dynamic registry holders such as armor trims don't match)
		if (!player.connection.connection.isMemoryConnection()) {
			PENetwork.sendToPlayer(player, SyncEmcPKT.serializeEmcData(player.registryAccess()), FuelMapper.getSyncPacket());
			PENetwork.sendToPlayer(player, WorldTransmutationManager.getSyncPacket());
		}
	}

	private void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, CommandBuildContext context,
			Commands.CommandSelection environment) {
		dispatcher.register(Commands.literal("projecte")
				.requires(PEPermissions.COMMAND)
				.then(RemoveEmcCMD.register(context))
				.then(ResetEmcCMD.register(context))
				.then(SetEmcCMD.register(context))
				.then(ShowBagCMD.register(context))
				.then(EMCCMD.register(context))
				.then(KnowledgeCMD.register(context))
		);
	}

	private void serverStarting(MinecraftServer server) {
		currentServer = server;
	}

	private void serverQuit(MinecraftServer server) {
		//Ensure we save any changes to the custom emc file
		CustomEMCParser.flush(server.registryAccess());
		TransmutationOffline.cleanAll();
		EMCMappingHandler.clearEmcMap();
		currentServer = null;
	}

	private record EmcUpdateData(ResourceManager resourceManager) {
	}
}
