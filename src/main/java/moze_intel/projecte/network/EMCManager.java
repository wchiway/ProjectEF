package moze_intel.projecte.network;

import java.util.Map;
import java.util.WeakHashMap;
import moze_intel.projecte.PECore;
import moze_intel.projecte.PEPermissions;
import moze_intel.projecte.api.nss.AbstractNSSTag;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.config.MappingConfig;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.emc.mappers.CustomEMCMapper;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.network.packets.to_client.EMCManagerResponsePKT;
import moze_intel.projecte.network.packets.to_client.EMCManagerResponsePKT.Status;
import moze_intel.projecte.network.packets.to_client.SyncEmcPKT;
import moze_intel.projecte.network.packets.to_server.EMCManagerRequestPKT;
import moze_intel.projecte.network.packets.to_server.EMCManagerRequestPKT.Action;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Server-thread-only operations for the EMC editor; no commands are executed on the player's behalf. */
public final class EMCManager {

	private static final CustomEMCMapper CUSTOM_MAPPER = new CustomEMCMapper();
	private static final Map<MinecraftServer, Long> LAST_REMAP = new WeakHashMap<>();
	private static final Map<ServerPlayer, Long> LAST_EDIT = new WeakHashMap<>();

	private EMCManager() {
	}

	public static void handle(ServerPlayer player, EMCManagerRequestPKT request) {
		int permissions = permissions(player);
		if (permissions == 0 || !allowed(request.action(), permissions)) {
			reply(player, request, permissions, Status.NO_PERMISSION);
			return;
		}
		if (CustomEMCParser.currentEntries == null) {
			reply(player, request, permissions, Status.NOT_READY);
			return;
		}
		if (request.action() == Action.APPLY) {
			apply(player, request, permissions);
			return;
		}
		Item item = BuiltInRegistries.ITEM.getOptional(request.item()).orElse(Items.AIR);
		if (item == Items.AIR) {
			reply(player, request, permissions, Status.INVALID_ITEM);
			return;
		}
		if (request.action() == Action.QUERY) {
			reply(player, request, permissions, Status.READY);
			return;
		}
		if (request.action() == Action.SET && request.value() <= 0) {
			reply(player, request, permissions, Status.INVALID_VALUE);
			return;
		}
		NSSItem target = NSSItem.createItem(request.item());
		if (CustomEMCParser.currentEntries.entries().getLong(target) != request.expectedValue()) {
			reply(player, request, permissions, Status.CONFLICT);
			return;
		}
		long now = System.nanoTime();
		Long lastEdit = LAST_EDIT.get(player);
		if (lastEdit != null && now - lastEdit < 200_000_000L) {
			reply(player, request, permissions, Status.COOLDOWN);
			return;
		}
		LAST_EDIT.put(player, now);
		long value = switch (request.action()) {
			case SET -> request.value();
			case REMOVE -> 0;
			case RESET -> -1;
			default -> throw new IllegalStateException("Not an edit: " + request.action());
		};
		boolean saved = CustomEMCParser.saveChange(player.registryAccess(), target, value);
		if (saved) {
			PECore.LOGGER.info("{} changed custom EMC for {} from {} to {}", player.getGameProfile().getName(), request.item(), request.expectedValue(), value);
		}
		reply(player, request, permissions, saved ? Status.SAVED : Status.SAVE_FAILED);
	}

	private static int permissions(ServerPlayer player) {
		CommandSourceStack source = player.createCommandSourceStack();
		if (!PEPermissions.COMMAND.test(source)) {
			return 0;
		}
		int permissions = 0;
		if (PEPermissions.COMMAND_SET_EMC.test(source)) {
			permissions |= EMCManagerResponsePKT.CAN_SET;
		}
		if (PEPermissions.COMMAND_REMOVE_EMC.test(source)) {
			permissions |= EMCManagerResponsePKT.CAN_REMOVE;
		}
		if (PEPermissions.COMMAND_RESET_EMC.test(source)) {
			permissions |= EMCManagerResponsePKT.CAN_RESET;
		}
		return permissions;
	}

	private static boolean allowed(Action action, int permissions) {
		return switch (action) {
			case QUERY -> permissions != 0;
			case SET -> (permissions & EMCManagerResponsePKT.CAN_SET) != 0;
			case REMOVE -> (permissions & EMCManagerResponsePKT.CAN_REMOVE) != 0;
			case RESET -> (permissions & EMCManagerResponsePKT.CAN_RESET) != 0;
			case APPLY -> permissions == EMCManagerResponsePKT.ALL_PERMISSIONS;
		};
	}

	private static void apply(ServerPlayer player, EMCManagerRequestPKT request, int permissions) {
		MinecraftServer server = player.server;
		long now = System.nanoTime();
		Long lastRemap = LAST_REMAP.get(server);
		if (lastRemap != null && now - lastRemap < 5_000_000_000L) {
			reply(player, request, permissions, Status.COOLDOWN);
			return;
		}
		if (!MappingConfig.isEnabled(CUSTOM_MAPPER)) {
			reply(player, request, permissions, Status.MAPPER_DISABLED);
			return;
		}
		if (!CustomEMCParser.flushChanges(server.registryAccess())) {
			reply(player, request, permissions, Status.SAVE_FAILED);
			return;
		}
		LAST_REMAP.put(server, now);
		SyncEmcPKT previous = EMCMappingHandler.createPacketData();
		try {
			AbstractNSSTag.clearCreatedTags();
			EMCMappingHandler.map(server.getRecipeManager(), server.registryAccess(), server.getResourceManager(), true);
		} catch (RuntimeException e) {
			EMCMappingHandler.updateEmcValues(previous.data());
			FuelMapper.loadMap();
			PECore.LOGGER.error("Failed to apply EMC manager changes", e);
			reply(player, request, permissions, Status.REMAP_FAILED);
			return;
		}
		SyncEmcPKT emc = SyncEmcPKT.serializeEmcData(server.registryAccess());
		for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
			//The integrated host shares the server map; decoding it against client registries corrupts LAN synchronization.
			if (!recipient.connection.connection.isMemoryConnection()) {
				PENetwork.sendToPlayer(recipient, emc, FuelMapper.getSyncPacket());
			}
			if (recipient.containerMenu instanceof TransmutationContainer) {
				PECore.packetHandler().updateTransmutationTargets(recipient);
			}
		}
		PECore.LOGGER.info("{} applied custom EMC changes ({} values)", player.getGameProfile().getName(), EMCMappingHandler.getEmcMapSize());
		reply(player, request, permissions, Status.APPLIED);
	}

	private static void reply(ServerPlayer player, EMCManagerRequestPKT request, int permissions, Status status) {
		long current = 0;
		long custom = -1;
		Item item = BuiltInRegistries.ITEM.getOptional(request.item()).orElse(Items.AIR);
		if (permissions != 0 && item != Items.AIR && CustomEMCParser.currentEntries != null) {
			current = IEMCProxy.INSTANCE.getValue(item);
			custom = CustomEMCParser.currentEntries.entries().getLong(NSSItem.createItem(request.item()));
		}
		PENetwork.sendToPlayer(player, new EMCManagerResponsePKT(request.requestId(), request.item(), current, custom,
				permissions, CustomEMCParser.needsRemap(), status));
	}
}
