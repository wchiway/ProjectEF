package moze_intel.projecte.client;

import moze_intel.projecte.PEPackets;
import moze_intel.projecte.PERegistries;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;

public class PEClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        StoneControls.register();
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(PERegistries.DARK_MATTER_PEDESTAL_ENTITY,
                DarkMatterPedestalRenderer::new);
        net.minecraft.client.gui.screens.MenuScreens.register(PERegistries.ALCHEMICAL_CHEST_MENU, AlchemicalChestScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(PERegistries.TRANSMUTATION_MENU, TransmutationScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(PEPackets.Snapshot.TYPE, (packet, context) ->
                context.client().execute(() ->
                        { if (context.client().gui.screen() instanceof TransmutationScreen screen) screen.update(packet); }));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || !PERegistries.isTransmutationTable(
                    level.getBlockState(hit.getBlockPos()).getBlock())) return InteractionResult.PASS;
            ClientPlayNetworking.send(new PEPackets.Action(false, hit.getBlockPos().asLong(), "open", 0, "", 1));
            return InteractionResult.SUCCESS;
        });
        ItemEvents.USE.register((level, player, hand) -> {
            if (!level.isClientSide() || !PERegistries.isTransmutationTablet(player.getItemInHand(hand).getItem())) return null;
            ClientPlayNetworking.send(new PEPackets.Action(true, 0, "open", 0, "", 1));
            return InteractionResult.SUCCESS;
        });
    }
}
