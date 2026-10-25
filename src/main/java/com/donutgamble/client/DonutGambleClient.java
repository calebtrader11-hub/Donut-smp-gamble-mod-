package com.donutgamble.client;

import com.donutgamble.core.Outcome;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
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
	static KeyBinding OPEN_PANEL;
	private static KeyBinding TOGGLE_HUD;
	private static KeyBinding NEWEST_WIN;
	private static KeyBinding NEWEST_LOSE;
	static GambleState state;

	@Override
	public void onInitializeClient() {
		state = new GambleState();

		OPEN_PANEL = register("open_panel", GLFW.GLFW_KEY_INSERT);
		TOGGLE_HUD = register("toggle_hud", GLFW.GLFW_KEY_H);
		NEWEST_WIN = register("newest_win", GLFW.GLFW_KEY_RIGHT_BRACKET);
		NEWEST_LOSE = register("newest_lose", GLFW.GLFW_KEY_LEFT_BRACKET);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_PANEL.wasPressed()) {
				client.setScreen(new GambleScreen(state));
			}
			while (TOGGLE_HUD.wasPressed()) {
				state.toggleHud();
			}
			while (NEWEST_WIN.wasPressed()) {
				state.resolveNewest(Outcome.PLAYER_WIN);
			}
			while (NEWEST_LOSE.wasPressed()) {
				state.resolveNewest(Outcome.PLAYER_LOSE);
			}
		});

		// Server (system) messages only: player chat can't create fake payments.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				state.onServerMessage(message.getString());
			}
		});

		HudRenderer hud = new HudRenderer(state);
		HudElementRegistry.addLast(Identifier.of(MOD_ID, "profit_hud"), hud::render);
	}

	private static KeyBinding register(String name, int key) {
		return KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key." + MOD_ID + "." + name, InputUtil.Type.KEYSYM, key, CATEGORY));
	}
}
