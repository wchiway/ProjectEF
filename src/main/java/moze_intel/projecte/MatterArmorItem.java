package moze_intel.projecte;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

public final class MatterArmorItem extends Item {
    private final boolean redMatter;

    MatterArmorItem(Properties properties, boolean redMatter) {
        super(properties);
        this.redMatter = redMatter;
    }

    // ProjectE's armor bonus adds to vanilla absorption, capped by the pieces being worn.
    public static float absorb(LivingEntity wearer, DamageSource source, float incoming, float remaining) {
        if (remaining <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return remaining;

        float fraction = 0;
        float limit = 0;
        for (EquipmentSlot slot : EquipmentSlotGroup.ARMOR) {
            if (!(wearer.getItemBySlot(slot).getItem() instanceof MatterArmorItem piece)) continue;
            float weight = switch (slot) {
                case HEAD, FEET -> 0.2F;
                case CHEST, LEGS -> 0.3F;
                default -> 0;
            };
            if (weight == 0) continue;

            float pieceLimit = piece.absorptionLimit(slot, source, weight);
            if (pieceLimit <= 0) continue;
            fraction += (piece.redMatter ? 0.9F : 0.8F) * weight;
            limit += pieceLimit;
        }
        return Math.max(0, remaining - Math.min(incoming * fraction, limit));
    }

    private float absorptionLimit(EquipmentSlot slot, DamageSource source, float weight) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return (redMatter ? 500 : 350) * weight;
        if ((slot == EquipmentSlot.FEET && source.is(DamageTypeTags.IS_FALL))
                || (slot == EquipmentSlot.HEAD && source.is(DamageTypeTags.IS_DROWNING))) {
            return redMatter ? 10 : 5;
        }
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return 0;
        boolean headOrFeet = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.FEET;
        int cap = redMatter ? (headOrFeet ? 250 : 350) : (headOrFeet ? 100 : 150);
        return cap * weight;
    }
}
