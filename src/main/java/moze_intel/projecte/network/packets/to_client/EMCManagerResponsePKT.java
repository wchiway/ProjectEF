package moze_intel.projecte.network.packets.to_client;

import java.util.Locale;
import moze_intel.projecte.PECore;
import moze_intel.projecte.client.EMCManagerClient;
import moze_intel.projecte.network.PEPacketContext;
import moze_intel.projecte.network.packets.IPEPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record EMCManagerResponsePKT(int requestId, ResourceLocation item, long currentValue, long customValue,
		int permissions, boolean pending, Status status) implements IPEPacket {

	public static final int CAN_SET = 1;
	public static final int CAN_REMOVE = 2;
	public static final int CAN_RESET = 4;
	public static final int ALL_PERMISSIONS = CAN_SET | CAN_REMOVE | CAN_RESET;

	public enum Status {
		READY, SAVED, APPLIED, NO_PERMISSION, INVALID_ITEM, INVALID_VALUE, CONFLICT, SAVE_FAILED, REMAP_FAILED, COOLDOWN, NOT_READY, MAPPER_DISABLED;

		public Component message() {
			return Component.translatable("gui.projecte.emc_manager.status." + name().toLowerCase(Locale.ROOT));
		}

		public boolean isError() {
			return ordinal() > APPLIED.ordinal();
		}
	}

	public static final Type<EMCManagerResponsePKT> TYPE = new Type<>(PECore.rl("emc_manager_response"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EMCManagerResponsePKT> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public EMCManagerResponsePKT decode(RegistryFriendlyByteBuf buffer) {
			return new EMCManagerResponsePKT(buffer.readVarInt(), buffer.readResourceLocation(), buffer.readLong(), buffer.readLong(),
					buffer.readVarInt(), buffer.readBoolean(), buffer.readEnum(Status.class));
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, EMCManagerResponsePKT packet) {
			buffer.writeVarInt(packet.requestId);
			buffer.writeResourceLocation(packet.item);
			buffer.writeLong(packet.currentValue);
			buffer.writeLong(packet.customValue);
			buffer.writeVarInt(packet.permissions);
			buffer.writeBoolean(packet.pending);
			buffer.writeEnum(packet.status);
		}
	};

	@NotNull
	@Override
	public CustomPacketPayload.Type<EMCManagerResponsePKT> type() {
		return TYPE;
	}

	@Override
	public void handle(PEPacketContext context) {
		ClientHandler.receive(this);
	}

	private static class ClientHandler {

		private static void receive(EMCManagerResponsePKT packet) {
			EMCManagerClient.receive(packet);
		}
	}
}
