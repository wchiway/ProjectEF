package moze_intel.projecte.handlers;

import com.google.common.base.Predicates;
import java.util.function.Predicate;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.items.ItemPE;
import moze_intel.projecte.gameObjs.registries.PEAttachmentTypes;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.utils.PlayerHelper;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;

public class InternalAbilities {

	private static final AttributeModifier WATER_SPEED_BOOST = new AttributeModifier(PECore.rl("water_speed_boost"), 0.15, Operation.ADD_VALUE);
	private static final AttributeModifier LAVA_SPEED_BOOST = new AttributeModifier(PECore.rl("lava_speed_boost"), 0.15, Operation.ADD_VALUE);

	public static void tick(Player player) {
		Predicate<Player> applyWaterSpeed = Predicates.alwaysFalse();
		Predicate<Player> applyLavaSpeed = Predicates.alwaysFalse();
		WalkOnType waterWalkOnType = canWalkOnWater(player);
		WalkOnType lavaWalkOnType = canWalkOnLava(player);
		if (waterWalkOnType.canWalk() || lavaWalkOnType.canWalk()) {
			FluidState below = player.level().getFluidState(player.getOnPos());
			boolean water = waterWalkOnType.canWalk() && below.is(FluidTags.WATER);
			//Note: Technically we could probably only have lava be true if water is false, but given the
			// fact vanilla uses tags for logic and technically (although it probably would cause lots of
			// weirdness, the block we are standing on could be both water and lava, which would mean that
			// we would want to apply both speed boosts).
			boolean lava = lavaWalkOnType.canWalk() && below.is(FluidTags.LAVA);
			if ((water || lava) && player.getInBlockState().isAir()) {
				if (!player.isShiftKeyDown()) {
					player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
					player.resetFallDistance();
					player.setOnGround(true);
				}
				applyWaterSpeed = waterWalkOnType.applySpeed(water);
				applyLavaSpeed = lavaWalkOnType.applySpeed(lava);
			} else if (!player.level().isClientSide) {
				if (waterWalkOnType.canWalk() && player.isInWater()) {
					//Things that apply water walking also refresh air supply when in water
					player.setAirSupply(player.getMaxAirSupply());
				}
			}
		}
		if (!player.level().isClientSide) {
			updateAttribute(player, Attributes.MOVEMENT_SPEED, WATER_SPEED_BOOST, applyWaterSpeed);
			updateAttribute(player, Attributes.MOVEMENT_SPEED, LAVA_SPEED_BOOST, applyLavaSpeed);
			updateFlight(player);
		}
	}

	//Note: The attributes that currently use this cannot be converted to just being attributes on the items, as they can be disabled based on the player state
	private static void updateAttribute(Player player, Holder<Attribute> attribute, AttributeModifier modifier, Predicate<Player> applyAttribute) {
		AttributeInstance attributeInstance = player.getAttribute(attribute);
		if (attributeInstance != null) {
			boolean hasModifier = attributeInstance.hasModifier(modifier.id());
			if (applyAttribute.test(player)) {
				if (!hasModifier) {
					//Should have it, but doesn't have the modifier yet, add it
					attributeInstance.addTransientModifier(modifier);
				}
			} else if (hasModifier) {
				//Shouldn't have the modifier, remove it
				attributeInstance.removeModifier(modifier.id());
			}
		}
	}

	private static boolean shouldPlayerFly(Player player) {
		return PlayerHelper.checkHotbarCurios(player, (p, stack) ->
				stack.is(PEItems.ARCANA_RING.get()) || (stack.is(PEItems.SWIFTWOLF_RENDING_GALE.get()) && ItemPE.hasEmc(p, stack, 64, true)));
	}

	/**
	 * Replaces NeoForge CREATIVE_FLIGHT attribute management with direct mayfly control. As other mods (for example Avaritia's infinity armor) also directly grant
	 * mayfly, we keep track of whether we are the ones that granted it, and only ever revoke flight that we granted.
	 * Only sends an update packet when the ability actually changes.
	 */
	private static void updateFlight(Player player) {
		// Never interfere with vanilla creative/spectator flight
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		Abilities abilities = player.getAbilities();
		if (shouldPlayerFly(player)) {
			if (!abilities.mayfly) {
				abilities.mayfly = true;
				player.setAttached(PEAttachmentTypes.GRANTED_FLIGHT, true);
				player.onUpdateAbilities();
			}
		} else if (player.hasAttached(PEAttachmentTypes.GRANTED_FLIGHT)) {
			player.removeAttached(PEAttachmentTypes.GRANTED_FLIGHT);
			if (abilities.mayfly) {
				abilities.mayfly = false;
				abilities.flying = false;
				player.onUpdateAbilities();
			}
		}
	}

	private static WalkOnType canWalkOnWater(Player player) {
		if (PlayerHelper.checkHotbarCurios(player, (p, stack) -> stack.is(PEItems.EVERTIDE_AMULET.get()))) {
			return WalkOnType.ABLE_WITH_SPEED;
		}
		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		return !helmet.isEmpty() && helmet.is(PEItems.GEM_HELMET.get()) ? WalkOnType.ABLE : WalkOnType.UNABLE;
	}

	private static WalkOnType canWalkOnLava(Player player) {
		if (PlayerHelper.checkHotbarCurios(player, (p, stack) -> stack.is(PEItems.VOLCANITE_AMULET.get()))) {
			return WalkOnType.ABLE_WITH_SPEED;
		}
		ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
		return !chestplate.isEmpty() && chestplate.is(PEItems.GEM_CHESTPLATE.get()) ? WalkOnType.ABLE : WalkOnType.UNABLE;
	}

	private enum WalkOnType {
		ABLE,
		ABLE_WITH_SPEED,
		UNABLE;

		public boolean canWalk() {
			return this != UNABLE;
		}

		public Predicate<Player> applySpeed(boolean onType) {
			//Note: We do this instead of wrapping it to avoid the capturing lambdas
			if (onType && this == ABLE_WITH_SPEED) {
				return Predicates.alwaysTrue();
			}
			return Predicates.alwaysFalse();
		}
	}
}
