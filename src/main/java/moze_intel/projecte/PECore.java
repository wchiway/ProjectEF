package moze_intel.projecte;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;

public class PECore implements ModInitializer {

    public static final String MODID = "projecte";
    public static final String MODNAME = "ProjectE";
    public static final GameProfile FAKEPLAYER_GAMEPROFILE = new GameProfile(UUID.fromString("590e39c7-9fb6-471b-a4c2-c0e539b2423d"), "[" + MODNAME + "]");
    public static final Logger LOGGER = LogUtils.getLogger();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public void onInitialize() {
        PEPreservationConfig.load();
        PERegistries.register();
        PEPackets.register();
        StoneMobTransmutation.register();
        SwiftwolfRingItem.registerEvents();
        EvertideAmuletItem.registerEvents();
        ClassicRingItem.registerEvents();
        GemArmorEffects.register();
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                        && VolcaniteAmuletItem.isCarried(player)));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
            PEPackets.StoneAction.TYPE, (packet, context) -> context.server().execute(() -> {
                var player = context.player();
                var hand = packet.offhand() ? net.minecraft.world.InteractionHand.OFF_HAND
                        : net.minecraft.world.InteractionHand.MAIN_HAND;
                if (player.getItemInHand(hand).getItem() instanceof EvertideAmuletItem) {
                    EvertideAmuletItem.action(player, packet.command(), hand);
                } else if (player.getItemInHand(hand).getItem() instanceof VolcaniteAmuletItem) {
                    VolcaniteAmuletItem.action(player, packet.command(), hand);
                } else if (player.getItemInHand(hand).getItem() instanceof MercurialEyeItem eye
                        && eye.supportsControl(packet.command())) {
                    MercurialEyeItem.action(player, packet.command(), hand);
                } else if (player.getItemInHand(hand).getItem() instanceof ClassicRingItem) {
                    ClassicRingItem.action(player, packet.command(), hand);
                } else if (GemArmorEffects.supportsControl(player, packet.command())) {
                    GemArmorEffects.action(player, packet.command());
                } else {
                    PhilosophersStoneItem.action(player, packet.command(), hand);
                }
            }));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UtilityPowerItem.tickPlayer(player);
                GemArmorEffects.tick(player);
                if (!hasBlackHoleBand(player)) continue;
                for (ItemEntity entity : player.level().getEntitiesOfClass(ItemEntity.class,
                        player.getBoundingBox().inflate(8.0D), item -> item.isAlive() && !item.hasPickUpDelay())) {
                    ItemStack remainder = entity.getItem().copy();
                    player.getInventory().add(remainder);
                    if (remainder.isEmpty()) entity.discard();
                    else entity.setItem(remainder);
                }
            }
        });
        PESetEmcCommand.register();
        MiningDropPreferences.registerCommand();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                PEPackets.Action.TYPE, (packet, context) ->
                        context.server().execute(() -> {
                            if ("open".equals(packet.command()))
                                PETransmutationState.open(context.player(), packet.tablet(), packet.position());
                            else PETransmutationState.action(context.player(), packet);
                        }));
        LOGGER.info("{} Fabric port shell initialized", MODNAME);
    }

    private static boolean hasBlackHoleBand(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (PERegistries.isBlackHoleBand(stack.getItem()) || UtilityPowerItem.isActiveVoidRing(stack)) return true;
        }
        ItemStack offhand = player.getOffhandItem();
        return PERegistries.isBlackHoleBand(offhand.getItem()) || UtilityPowerItem.isActiveVoidRing(offhand);
    }
}
