package moze_intel.projecte;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GemArmorEffects {
    private GemArmorEffects() { }

    static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof Player player
                        && (source.is(DamageTypeTags.IS_FIRE) && wears(player, EquipmentSlot.CHEST, "gem_chestplate")
                        || source.is(DamageTypeTags.IS_FALL) && hasFlight(player))));
    }

    public static boolean hasFlight(Player player) {
        return wears(player, EquipmentSlot.FEET, "gem_boots");
    }

    public static boolean hasHelmet(Player player) {
        return wears(player, EquipmentSlot.HEAD, "gem_helmet");
    }

    private static boolean wears(Player player, EquipmentSlot slot, String id) {
        return player.getItemBySlot(slot).is(BuiltInRegistries.ITEM.getValue(PECore.id(id)));
    }

    static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || !player.isAlive() || player.isSpectator()) return;
        if (hasHelmet(player)) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
        if (hasFlight(player)) player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, true, false));
        if (wears(player, EquipmentSlot.CHEST, "gem_chestplate")) player.clearFire();
        if (wears(player, EquipmentSlot.LEGS, "gem_leggings")) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40, 0, true, false));
        }
    }

    public static boolean supportsControl(Player player, int command) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return false;
        boolean helmet = hasHelmet(player);
        boolean chest = wears(player, EquipmentSlot.CHEST, "gem_chestplate");
        return command == 0 && (helmet || chest) || command == 2 && helmet || command == 3 && chest;
    }

    static void action(ServerPlayer player, int command) {
        if (!supportsControl(player, command) || !ClassicRingItem.canUse(player)
                || player.containerMenu != player.inventoryMenu) return;
        ItemStack armor = player.getItemBySlot(command == 3 || command == 0 && !hasHelmet(player)
                ? EquipmentSlot.CHEST : EquipmentSlot.HEAD);
        boolean armed = armor.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBooleanOr("GemOffense", false);
        if (command == 0) {
            CustomData.update(DataComponents.CUSTOM_DATA, armor, tag -> tag.putBoolean("GemOffense", !armed));
            if (hasHelmet(player) && wears(player, EquipmentSlot.CHEST, "gem_chestplate")) {
                CustomData.update(DataComponents.CUSTOM_DATA, player.getItemBySlot(EquipmentSlot.CHEST),
                        tag -> tag.putBoolean("GemOffense", !armed));
            }
            PowerFuel.notice(player, armed ? "Gem powers disarmed" : "Gem powers armed");
            return;
        }
        if (!armed || player.getCooldowns().isOnCooldown(armor)) return;
        var level = (ServerLevel) player.level();
        if (command == 2) {
            Vec3 eye = player.getEyePosition();
            var hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(48)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK
                    || !ClassicRingItem.canChange(level, player, armor, hit.getBlockPos())
                    || !PowerFuel.spend(player, 128)) return;
            var bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(hit.getLocation());
                bolt.setCause(player);
                level.addFreshEntity(bolt);
            }
        } else {
            if (!PowerFuel.spend(player, 128)) return;
            for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5),
                    entity -> entity instanceof Enemy && entity.isAlive())) {
                enemy.hurtServer(level, level.damageSources().playerAttack(player), 16);
                Vec3 push = enemy.position().subtract(player.position()).normalize();
                enemy.push(push.x, .3, push.z);
            }
        }
        player.getCooldowns().addCooldown(armor, 40);
    }
}
