package moze_intel.projecte.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.items.rings.ArchangelSmite;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.network.packets.IPEPacket;
import moze_intel.projecte.network.packets.to_client.EMCManagerResponsePKT;
import moze_intel.projecte.network.packets.to_client.NovaExplosionSyncPKT;
import moze_intel.projecte.network.packets.to_client.SyncEmcPKT;
import moze_intel.projecte.network.packets.to_client.SyncFuelMapperPKT;
import moze_intel.projecte.network.packets.to_client.SyncWorldTransmutations;
import moze_intel.projecte.network.packets.to_client.alch_bag.SyncAllBagDataPKT;
import moze_intel.projecte.network.packets.to_client.alch_bag.SyncBagsDataPKT;
import moze_intel.projecte.network.packets.to_client.container.SyncOffhandPkt;
import moze_intel.projecte.network.packets.to_client.container.UpdateCondenserLockPKT;
import moze_intel.projecte.network.packets.to_client.container.UpdateWindowLongPKT;
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncChangePKT;
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncEmcPKT;
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncInputsAndLocksPKT;
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncPKT;
import moze_intel.projecte.network.packets.to_server.EMCManagerRequestPKT;
import moze_intel.projecte.network.packets.to_server.KeyPressPKT;
import moze_intel.projecte.network.packets.to_server.SearchUpdatePKT;
import moze_intel.projecte.network.packets.to_server.UpdateGemModePKT;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Heavily based off of Mekanism's packet handler
 */
public final class PacketHandler {

	private static final List<ClientboundRegistration<?>> CLIENTBOUND_REGISTRATIONS = new ArrayList<>();

	//Client to server instanced packets
	private SimplePacketPayLoad activateArchangel;

	//Server to client instanced packets
	private SimplePacketPayLoad clearKnowledge;
	private SimplePacketPayLoad updateTransmutationTargets;

	private SimplePacketPayLoad resetCooldown;

	public PacketHandler() {
		registerClientToServer(new PacketRegistrar(true));
		registerServerToClient(new PacketRegistrar(false));
	}

	/**
	 * Exposes all clientbound packet types and their handlers so that the client mod initializer can register the actual receivers.
	 */
	public static List<ClientboundRegistration<?>> getClientboundRegistrations() {
		return CLIENTBOUND_REGISTRATIONS;
	}

	private void registerClientToServer(PacketRegistrar registrar) {
		registrar.play(EMCManagerRequestPKT.TYPE, EMCManagerRequestPKT.STREAM_CODEC);
		registrar.play(KeyPressPKT.TYPE, KeyPressPKT.STREAM_CODEC);
		activateArchangel = registrar.playInstanced(PECore.rl("activate_archangel"), (ignored, context) -> {
			Player player = context.player();
			ItemStack main = player.getMainHandItem();
			if (!main.isEmpty() && main.is(PEItems.ARCHANGEL_SMITE.get())) {
				ArchangelSmite.fireVolley(main, player);
			}
		});
		registrar.play(SearchUpdatePKT.TYPE, SearchUpdatePKT.STREAM_CODEC);
		registrar.play(UpdateGemModePKT.TYPE, UpdateGemModePKT.STREAM_CODEC);
	}

	private void registerServerToClient(PacketRegistrar registrar) {
		registrar.play(EMCManagerResponsePKT.TYPE, EMCManagerResponsePKT.STREAM_CODEC);
		resetCooldown = registrar.playInstanced(PECore.rl("reset_cooldown"), (ignored, context) -> context.player().resetAttackStrengthTicker());
		clearKnowledge = registrar.playInstanced(PECore.rl("clear_knowledge"), (ignored, context) -> {
			Player player = context.player();
			IKnowledgeProvider knowledge = PECapabilities.KNOWLEDGE_CAPABILITY.find(player);
			if (knowledge != null) {
				knowledge.clearKnowledge();
				if (player.containerMenu instanceof TransmutationContainer container) {
					container.transmutationInventory.updateClientTargets(false);
				}
			}
		});
		registrar.play(KnowledgeSyncPKT.TYPE, KnowledgeSyncPKT.STREAM_CODEC);
		registrar.play(KnowledgeSyncEmcPKT.TYPE, KnowledgeSyncEmcPKT.STREAM_CODEC);
		registrar.play(KnowledgeSyncInputsAndLocksPKT.TYPE, KnowledgeSyncInputsAndLocksPKT.STREAM_CODEC);
		registrar.play(KnowledgeSyncChangePKT.TYPE, KnowledgeSyncChangePKT.STREAM_CODEC);
		registrar.play(NovaExplosionSyncPKT.TYPE, NovaExplosionSyncPKT.STREAM_CODEC);
		registrar.play(SyncAllBagDataPKT.TYPE, SyncAllBagDataPKT.STREAM_CODEC);
		registrar.play(SyncBagsDataPKT.TYPE, SyncBagsDataPKT.STREAM_CODEC);
		registrar.play(SyncEmcPKT.TYPE, SyncEmcPKT.STREAM_CODEC);
		registrar.play(SyncOffhandPkt.TYPE, SyncOffhandPkt.STREAM_CODEC);
		registrar.play(SyncFuelMapperPKT.TYPE, SyncFuelMapperPKT.STREAM_CODEC);
		registrar.play(SyncWorldTransmutations.TYPE, SyncWorldTransmutations.STREAM_CODEC);
		registrar.play(UpdateCondenserLockPKT.TYPE, UpdateCondenserLockPKT.STREAM_CODEC);
		updateTransmutationTargets = registrar.playInstanced(PECore.rl("update_transmutation_targets"), (ignored, context) -> {
			if (context.player().containerMenu instanceof TransmutationContainer container) {
				container.transmutationInventory.updateClientTargets(false);
			}
		});
		registrar.play(UpdateWindowLongPKT.TYPE, UpdateWindowLongPKT.STREAM_CODEC);
	}

	public void clearKnowledge(ServerPlayer player) {
		PENetwork.sendToPlayer(player, clearKnowledge);
	}

	public void updateTransmutationTargets(ServerPlayer player) {
		PENetwork.sendToPlayer(player, updateTransmutationTargets);
	}

	public void resetCooldown(ServerPlayer player) {
		PENetwork.sendToPlayer(player, resetCooldown);
	}

	public void activateArchangel() {
		PENetwork.sendToServer(activateArchangel);
	}

	@FunctionalInterface
	public interface PEPayloadHandler<MSG extends CustomPacketPayload> {

		void handle(MSG payload, PEPacketContext context);
	}

	public record ClientboundRegistration<MSG extends CustomPacketPayload>(CustomPacketPayload.Type<MSG> type, PEPayloadHandler<MSG> handler) {
	}

	protected record SimplePacketPayLoad(CustomPacketPayload.Type<CustomPacketPayload> type) implements CustomPacketPayload {

		private SimplePacketPayLoad(ResourceLocation id) {
			this(new CustomPacketPayload.Type<>(id));
		}
	}

	protected record PacketRegistrar(boolean toServer) {

		public <MSG extends IPEPacket> void play(CustomPacketPayload.Type<MSG> type, StreamCodec<? super RegistryFriendlyByteBuf, MSG> reader) {
			if (toServer) {
				PayloadTypeRegistry.playC2S().register(type, reader);
				ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> payload.handle(context::player));
			} else {
				PayloadTypeRegistry.playS2C().register(type, reader);
				CLIENTBOUND_REGISTRATIONS.add(new ClientboundRegistration<>(type, IPEPacket::handle));
			}
		}

		public SimplePacketPayLoad playInstanced(ResourceLocation id, PEPayloadHandler<CustomPacketPayload> handler) {
			SimplePacketPayLoad payload = new SimplePacketPayLoad(id);
			if (toServer) {
				PayloadTypeRegistry.playC2S().register(payload.type(), StreamCodec.unit(payload));
				ServerPlayNetworking.registerGlobalReceiver(payload.type(), (received, context) -> handler.handle(received, context::player));
			} else {
				PayloadTypeRegistry.playS2C().register(payload.type(), StreamCodec.unit(payload));
				CLIENTBOUND_REGISTRATIONS.add(new ClientboundRegistration<>(payload.type(), handler));
			}
			return payload;
		}
	}
}
