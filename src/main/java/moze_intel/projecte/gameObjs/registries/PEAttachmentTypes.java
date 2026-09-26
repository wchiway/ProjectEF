package moze_intel.projecte.gameObjs.registries;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.item_handlers.IItemHandler;
import moze_intel.projecte.api.item_handlers.IItemHandlerModifiable;
import moze_intel.projecte.impl.capability.AlchBagImpl.AlchemicalBagAttachment;
import moze_intel.projecte.impl.capability.KnowledgeImpl.KnowledgeAttachment;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.item.ItemStack;

public class PEAttachmentTypes {

	private PEAttachmentTypes() {
	}

	public static final AttachmentType<AlchemicalBagAttachment> ALCHEMICAL_BAGS = AttachmentRegistry.<AlchemicalBagAttachment>builder()
			.initializer(AlchemicalBagAttachment::new)
			.persistent(AlchemicalBagAttachment.CODEC)
			.copyOnDeath()
			.buildAndRegister(PECore.rl("alchemical_bags"));

	public static final AttachmentType<KnowledgeAttachment> KNOWLEDGE = AttachmentRegistry.<KnowledgeAttachment>builder()
			.initializer(KnowledgeAttachment::new)
			.persistent(KnowledgeAttachment.CODEC)
			.copyOnDeath()
			.buildAndRegister(PECore.rl("knowledge"));

	public static final AttachmentType<Boolean> GEM_ARMOR_STATE = AttachmentRegistry.<Boolean>builder()
			.initializer(() -> false)
			.persistent(Codec.BOOL)
			.copyOnDeath()
			.buildAndRegister(PECore.rl("gem_armor_state"));

	/**
	 * Only attached while ProjectEF is the one that granted the player flight. Persistent as vanilla also persists {@link net.minecraft.world.entity.player.Abilities#mayfly}.
	 */
	public static final AttachmentType<Boolean> GRANTED_FLIGHT = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL)
			.buildAndRegister(PECore.rl("granted_flight"));

	/**
	 * Ensures the attachment types are registered. Attachment registration happens in the static initializers above, this method just provides an explicit trigger
	 * point during mod construction.
	 */
	public static void init() {
	}

	public static <HANDLER extends IItemHandlerModifiable> HANDLER copyHandler(IItemHandler handler, Int2ObjectFunction<HANDLER> handlerCreator) {
		int slots = handler.getSlots();
		HANDLER handlerCopy = handlerCreator.get(slots);
		for (int i = 0; i < slots; i++) {
			ItemStack stack = handler.getStackInSlot(i);
			if (!stack.isEmpty()) {
				handlerCopy.setStackInSlot(i, stack.copy());
			}
		}
		return handlerCopy;
	}
}
