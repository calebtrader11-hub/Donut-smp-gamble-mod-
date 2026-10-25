package com.donutgamble.client;

import com.donutgamble.core.Outcome;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DonutGambleClient implements ClientModInitializer {
	public static final String MOD_ID = "donutgamble";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
	static KeyBinding OPEN_QUEUE;
	private static KeyBinding TOGGLE_HUD;
	private static KeyBinding FIRST_WIN;
	private static KeyBinding FIRST_LOSE;
	static GambleState state;
	static QueueOverlay overlay;

	@Override
	public void onInitializeClient() {
		state = new GambleState();
		overlay = new QueueOverlay(state);

		OPEN_QUEUE = register("open_queue", GLFW.GLFW_KEY_INSERT);
		TOGGLE_HUD = register("toggle_hud", GLFW.GLFW_KEY_H);
		FIRST_WIN = register("first_win", GLFW.GLFW_KEY_RIGHT_BRACKET);
		FIRST_LOSE = register("first_lose", GLFW.GLFW_KEY_LEFT_BRACKET);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_QUEUE.wasPressed()) {
				client.setScreen(new QueueScreen(overlay));
			}
			while (TOGGLE_HUD.wasPressed()) {
				state.toggleHud();
			}
			while (FIRST_WIN.wasPressed()) {
				state.resolveFirst(Outcome.PLAYER_WIN);
			}
			while (FIRST_LOSE.wasPressed()) {
				state.resolveFirst(Outcome.PLAYER_LOSE);
			}
		});

		// Server (system) messages only: player chat can't create fake payments.
		ClientReceiveMessageEvents.GAME.register((message, overlayMessage) -> {
			if (!overlayMessage) {
				state.onServerMessage(message.getString());
			}
		});

		HudElementRegistry.addLast(Identifier.of(MOD_ID, "queue"), overlay::render);

		// The letters can also be clicked while chat is open.
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof ChatScreen) {
				ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !overlay.click(click.x(), click.y(), click.button()));
			}
		});
	}

	private static KeyBinding register(String name, int key) {
		return KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key." + MOD_ID + "." + name, InputUtil.Type.KEYSYM, key, CATEGORY));
	}
}
