package moze_intel.projecte.network.packets.to_server;

import moze_intel.projecte.PECore;
import moze_intel.projecte.network.EMCManager;
import moze_intel.projecte.network.PEPacketContext;
import moze_intel.projecte.network.packets.IPEPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public record EMCManagerRequestPKT(int requestId, Action action, ResourceLocation item, long value, long expectedValue) implements IPEPacket {

	public enum Action {
		QUERY, SET, REMOVE, RESET, APPLY
	}

	public static final Type<EMCManagerRequestPKT> TYPE = new Type<>(PECore.rl("emc_manager_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EMCManagerRequestPKT> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public EMCManagerRequestPKT decode(RegistryFriendlyByteBuf buffer) {
			return new EMCManagerRequestPKT(buffer.readVarInt(), buffer.readEnum(Action.class), buffer.readResourceLocation(), buffer.readLong(), buffer.readLong());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, EMCManagerRequestPKT packet) {
			buffer.writeVarInt(packet.requestId);
			buffer.writeEnum(packet.action);
			buffer.writeResourceLocation(packet.item);
			buffer.writeLong(packet.value);
			buffer.writeLong(packet.expectedValue);
		}
	};

	@NotNull
	@Override
	public CustomPacketPayload.Type<EMCManagerRequestPKT> type() {
		return TYPE;
	}

	@Override
	public void handle(PEPacketContext context) {
		if (context.player() instanceof ServerPlayer player) {
			EMCManager.handle(player, this);
		}
	}
}
