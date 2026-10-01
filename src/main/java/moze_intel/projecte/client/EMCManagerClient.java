package moze_intel.projecte.client;

import com.mojang.blaze3d.platform.InputConstants;
import moze_intel.projecte.gameObjs.gui.EMCManagerScreen;
import moze_intel.projecte.network.packets.to_client.EMCManagerResponsePKT;
import moze_intel.projecte.network.packets.to_server.EMCManagerRequestPKT;
import moze_intel.projecte.utils.text.PELang;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class EMCManagerClient {

	private static int requestSequence;

	private EMCManagerClient() {
	}

	public static void register() {
		KeyMapping open = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.projecte.emc_manager", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F8, PELang.PROJECTE.getTranslationKey()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) {
				if (client.player != null && client.screen == null) {
					open(null);
				}
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof PauseScreen && client.player != null && supported()) {
				Screens.getButtons(screen).add(Button.builder(Component.translatable("gui.projecte.emc_manager.title"), button -> open(screen))
						.bounds(width - 114, 6, 108, 20).build());
			}
		});
	}

	public static boolean supported() {
		return ClientPlayNetworking.canSend(EMCManagerRequestPKT.TYPE);
	}

	private static void open(@Nullable Screen parent) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		if (!supported()) {
			client.player.displayClientMessage(Component.translatable("gui.projecte.emc_manager.unsupported"), false);
			return;
		}
		client.setScreen(new EMCManagerScreen(parent));
	}

	public static int nextRequestId() {
		return ++requestSequence;
	}

	public static void receive(EMCManagerResponsePKT response) {
		if (Minecraft.getInstance().screen instanceof EMCManagerScreen screen) {
			screen.receive(response);
		}
	}
}
