package com.donutgamble.client;

import com.donutgamble.core.Bet;
import com.donutgamble.core.Game;
import com.donutgamble.core.Outcome;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;

/** Drives a real client: fake /pay messages, resolve some bets, screenshot the HUD and the panel. */
public class DonutGambleClientGameTest implements FabricClientGameTest {
	private static final long M = 1_000_000L;

	@Override
	public void runTest(ClientGameTestContext context) {
		// No world: software-rendered CI can't load one in time. The title screen is enough to
		// exercise chat detection, the ledger and both GUIs.
		context.waitForScreen(TitleScreen.class);
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			state.config.elytraPrice = 180 * M;
			state.newSession();

			pay(client, "Max paid you $400M.");
			state.resolve(newest(state), Outcome.PLAYER_LOSE, 19, 19);
			pay(client, "Bob paid you $20M.");
			state.resolve(newest(state), Outcome.PLAYER_LOSE, 5, 5);
			pay(client, "Kira paid you $120M.");
			state.setGame(newest(state), Game.ELY);
			state.resolve(newest(state), Outcome.PLAYER_WIN, null, null);
			pay(client, "Alex_ paid you $45M.");
			state.resolve(newest(state), Outcome.PLAYER_WIN, 9, 8);
			pay(client, "Steve paid you $250M.");
			state.setGame(newest(state), Game.BLACKJACK);
			state.resolve(newest(state), Outcome.PLAYER_LOSE, 18, 20);

			pay(client, "[VIP] Bob: Notch paid you $500M");
			pay(client, "You received $250M from Steve");
			state.setGame(newest(state), Game.BLACKJACK);
			pay(client, "Alex_ paid you $45M.");
			pay(client, "xNova paid you $120M.");
			state.setGame(newest(state), Game.ELY);

			check(state.ledger.pending().size() == 3, "3 pending bets (spoofed chat ignored)");
			check(state.ledger.owed().size() == 2, "2 owed payouts");
			check(state.ledger.sessionStats().net() == 400 * M + 20 * M - 60 * M - 45 * M + 250 * M, "session net");
			GambleScreen.draftScore(state.ledger.pending().get(2).id, "18", "20");
		});

		context.setScreen(() -> new HudPreviewScreen(DonutGambleClient.state));
		context.waitTicks(5);
		context.takeScreenshot("donutgamble-hud");

		context.setScreen(() -> new GambleScreen(DonutGambleClient.state));
		context.waitTicks(5);
		context.takeScreenshot("donutgamble-panel");
		context.setScreen(() -> null);
	}

	/** Draws the in-game HUD over the title screen panorama. */
	private static final class HudPreviewScreen extends Screen {
		private final HudRenderer hud;

		HudPreviewScreen(GambleState state) {
			super(Text.literal("HUD preview"));
			hud = new HudRenderer(state);
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			super.render(ctx, mouseX, mouseY, delta);
			hud.render(ctx, null);
		}
	}

	private static void pay(MinecraftClient client, String message) {
		client.getMessageHandler().onGameMessage(Text.literal(message), false);
	}

	private static Bet newest(GambleState state) {
		return state.ledger.pending().get(0);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Check failed: " + what);
		}
	}
}
