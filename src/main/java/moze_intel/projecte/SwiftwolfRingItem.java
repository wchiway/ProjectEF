package moze_intel.projecte;

import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class SwiftwolfRingItem extends Item {
    private static final List<String> MODES = List.of("off", "flight", "repel", "both");
    private static final List<String> LABELS = List.of("Off", "Flight", "Repel", "Flight + Repel");
    private static final int FLIGHT = 1;
    private static final int REPEL = 2;
    private static final long COST = 64;
    private static final int PAID_TICKS = 120;
    private static final Map<ServerPlayer, ActiveRing> ACTIVE = new WeakHashMap<>();

    private static final class ActiveRing {
        boolean grantedFlight;
        int flightTicks;
        int repelTicks;
    }

    SwiftwolfRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack ring = player.getItemInHand(hand);
            int next = (mode(ring) + 1) % MODES.size();
            if (next == 0) ring.remove(DataComponents.CUSTOM_MODEL_DATA);
            else ring.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(), List.of(), List.of(MODES.get(next)), List.of()));
            serverPlayer.sendSystemMessage(Component.literal("Swiftwolf: " + LABELS.get(next)), true);
        }
        return InteractionResult.SUCCESS;
    }

    static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers()
                .forEach(SwiftwolfRingItem::tickPlayer));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damage, amount) ->
                !(entity instanceof ServerPlayer player && damage.is(DamageTypes.FALL) && carriedMode(player) >= 0));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> server.getPlayerList().getPlayers()
                .forEach(SwiftwolfRingItem::clear));
    }

    private static int mode(ItemStack ring) {
        CustomModelData data = ring.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.strings().isEmpty() ? 0 : Math.max(0, MODES.indexOf(data.strings().getFirst()));
    }

    // -1 means no ring. Multiple rings contribute modes without multiplying effects.
    private static int carriedMode(ServerPlayer player) {
        int modes = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof SwiftwolfRingItem) modes = Math.max(0, modes) | mode(stack);
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.getItem() instanceof SwiftwolfRingItem ? Math.max(0, modes) | mode(offhand) : modes;
    }

    static void tickPlayer(ServerPlayer player) {
        int modes = carriedMode(player);
        boolean freeFlight = ClassicRingItem.hasArcana(player) || GemArmorEffects.hasFlight(player);
        if ((!freeFlight && modes <= 0) || !player.isAlive() || player.isSpectator()) {
            clear(player);
            return;
        }
        ActiveRing state = ACTIVE.computeIfAbsent(player, ignored -> new ActiveRing());
        tickFlight(player, state, (modes & FLIGHT) != 0, freeFlight);
        if (modes > 0 && (modes & REPEL) != 0 && player.tickCount % 5 == 0) {
            if (state.repelTicks == 0 && pay(player)) state.repelTicks = PAID_TICKS;
            if (state.repelTicks > 0) {
                state.repelTicks -= 5;
                repel(player);
            }
        }
    }

    private static void tickFlight(ServerPlayer player, ActiveRing state, boolean enabled, boolean free) {
        if (player.getAbilities().instabuild) {
            state.grantedFlight = false;
            return;
        }
        boolean funded = free || enabled && (state.flightTicks > 0 || KleinStarItem.canPay(player, COST) || PETransmutationState.balance(player) >= COST);
        if (!funded) {
            revokeFlight(player, state);
            return;
        }
        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            state.grantedFlight = true;
            player.onUpdateAbilities();
        }
        if (!free && state.grantedFlight && player.getAbilities().flying) {
            if (state.flightTicks == 0 && pay(player)) state.flightTicks = PAID_TICKS;
            if (state.flightTicks > 0) state.flightTicks--;
            else revokeFlight(player, state);
        }
    }

    private static boolean pay(ServerPlayer player) {
        return player.getAbilities().instabuild || KleinStarItem.pay(player, COST) || PETransmutationState.spendEmc(player, COST);
    }

    static void repel(ServerPlayer player) {
        for (var entity : player.level().getEntities(player, player.getBoundingBox().inflate(5.0),
                target -> target.isAlive() && (target instanceof Mob
                        || target instanceof Projectile shot && shot.getOwner() != player))) {
            Vec3 away = entity.position().subtract(player.position());
            if (away.lengthSqr() > 25 || away.lengthSqr() < 1.0E-6) continue;
            Vec3 force = away.normalize().scale(0.35);
            entity.push(force.x, Math.max(0.08, force.y), force.z);
        }
    }

    private static void clear(ServerPlayer player) {
        ActiveRing state = ACTIVE.remove(player);
        if (state != null) revokeFlight(player, state);
    }

    private static void revokeFlight(ServerPlayer player, ActiveRing state) {
        if (!state.grantedFlight) return;
        state.grantedFlight = false;
        if (player.getAbilities().instabuild || player.isSpectator()) return;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }
}
