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

/** Drives a real client: fake /pay messages, call some bets, screenshot the queue and the score box. */
public class DonutGambleClientGameTest implements FabricClientGameTest {
	private static final long M = 1_000_000L;

	@Override
	public void runTest(ClientGameTestContext context) {
		// No world: software-rendered CI can't load one in time. The title screen is enough to
		// exercise chat detection, the ledger and the GUIs.
		context.waitForScreen(TitleScreen.class);
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			state.config.elytraPrice = 180 * M;
			state.newSession();

			pay(client, "Max paid you $ 400M");
			state.resolveTie(last(state));
			pay(client, "Kira paid you $120M.");
			state.setGame(last(state), Game.ELY);
			state.resolve(last(state), Outcome.PLAYER_WIN, null, null);
			pay(client, "Alex_ paid you $45M.");
			state.resolve(last(state), Outcome.PLAYER_WIN, 9, 8);

			pay(client, "[VIP] Bob: Notch paid you $500M");
			pay(client, "RazorBroGaming paid you $ 50M");
			pay(client, "You received $250M from hayden260");
			pay(client, "xNova paid you $120M.");
			state.setGame(last(state), Game.ELY);

			check(state.ledger.pending().size() == 3, "3 queued bets (spoofed chat ignored)");
			check(state.ledger.pending().get(0).player.equals("RazorBroGaming"), "queue is oldest first");
			check(state.ledger.owed().size() == 2, "2 owed payouts");
			pay(client, "You paid Alex_ $ 1M");
			check(state.ledger.owed().size() == 2, "paying the wrong amount doesn't clear a payout");

			// A busy stream: lots of different players paying at once.
			String[] names = {"jimbo01", "BeanznBread", ".BedrockKid", "Sk8r_Boi", "x_X_Gamer_X_x", "Tobias", "Lunaa", "zz9", "Pepperoni_Pete", "AAA"};
			for (int i = 0; i < names.length; i++) {
				pay(client, names[i] + " paid you $ " + (10 + i * 7) + "M");
				state.setGame(last(state), Game.values()[i % 3]);
			}
			// A few finished blackjack hands so every tab has numbers.
			for (Bet b : state.ledger.pending(Game.BLACKJACK).subList(0, 2)) {
				state.resolve(b, Outcome.PLAYER_LOSE, 17, 20);
			}
			check(state.ledger.pending().size() == 11, "every player's payment is queued");
			check(state.ledger.pending(Game.PAPER).size() + state.ledger.pending(Game.BLACKJACK).size() + state.ledger.pending(Game.ELY).size() == 11, "tabs split the queue");
		});

		context.setScreen(() -> new OverlayPreviewScreen());
		context.runOnClient(client -> DonutGambleClient.state.setTheme(Colors.Theme.AQUA));
		for (QueueOverlay.Tab tab : QueueOverlay.Tab.values()) {
			context.runOnClient(client -> DonutGambleClient.state.config.activeTab = tab.name());
			context.waitTicks(3);
			context.takeScreenshot("donutgamble-tab-" + tab.name().toLowerCase(java.util.Locale.ROOT));
		}

		context.runOnClient(client -> client.setScreen(ScoreScreen.elytraPrice(DonutGambleClient.state, DonutGambleClient.state.ledger.pending().get(2))));
		context.waitTicks(5);
		context.takeScreenshot("donutgamble-elytra");

		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			Bet first = state.ledger.pending().get(0);
			state.resolve(first, Outcome.PLAYER_LOSE, null, null);
			client.setScreen(ScoreScreen.score(state, first, "LOST"));
		});
		context.waitTicks(5);
		context.takeScreenshot("donutgamble-score");
		context.setScreen(() -> null);
	}

	/** Draws the in-game queue over the title screen panorama. */
	private static final class OverlayPreviewScreen extends Screen {
		OverlayPreviewScreen() {
			super(Text.literal("Queue preview"));
		}

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			super.render(ctx, mouseX, mouseY, delta);
			DonutGambleClient.overlay.render(ctx, null);
		}
	}

	private static void pay(MinecraftClient client, String message) {
		client.getMessageHandler().onGameMessage(Text.literal(message), false);
	}

	private static Bet last(GambleState state) {
		var queue = state.ledger.pending();
		return queue.get(queue.size() - 1);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Check failed: " + what);
		}
	}
}
