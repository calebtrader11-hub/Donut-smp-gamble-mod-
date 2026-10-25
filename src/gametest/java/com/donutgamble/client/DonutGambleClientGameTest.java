package com.donutgamble.client;

import com.donutgamble.core.Auction;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Game;
import com.donutgamble.core.Outcome;
import com.donutgamble.core.Transaction;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.util.Locale;

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
			state.resolve(first, Outcome.PLAYER_WIN, null, null);
			client.setScreen(ScoreScreen.score(state, first, "WON"));
		});
		context.waitTicks(5);
		context.takeScreenshot("donutgamble-score");

		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			Bet next = state.ledger.pending(Game.PAPER).get(0);
			state.resolveTie(next);
			client.setScreen(ScoreScreen.score(state, next, "TIED"));
		});
		context.waitTicks(5);
		context.takeScreenshot("donutgamble-tie");

		runAuction(context);
		context.setScreen(() -> null);
	}

	/** Auction + finance on top of the gamble state above: routing, dedupe, corrections, refunds, exports, GUI. */
	private void runAuction(ClientGameTestContext context) {
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			state.config.auctionRefundLosers = true;
			int queued = state.ledger.pending().size();
			check(state.startAuction("Skeleton Spawner", 300, 250 * M), "auction starts");
			check(!state.startAuction("Another", 300, 0), "only one auction at a time");

			pay(client, "BeanznBread paid you $ 300M");
			pay(client, "BeanznBread paid you $ 300M");
			pay(client, "jimbo01 paid you $ 450M");
			pay(client, "RazorBroGaming paid you $ 1.2B");
			pay(client, "hayden260 paid you $ 75M");
			pay(client, "jimbo01 paid you $ 200M");
			Auction a = state.auctions.current;
			check(a.rankings().size() == 4, "4 bidders");
			check(a.total() == 2225 * M, "duplicate echo ignored, totals add up");
			check(a.leader().player().equals("RazorBroGaming"), "highest contributor leads");
			check(state.ledger.pending().size() == queued, "auction payments stay out of the gamble queue");
			state.correctAuction("hayden260", 25 * M);
			check(a.total() == 2250 * M, "manual correction");
			state.config.activeTab = QueueOverlay.Tab.AUCTION.name();
		});
		context.setScreen(() -> new OverlayPreviewScreen());
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-auction-live");

		for (ManagerScreen.Section section : new ManagerScreen.Section[]{
			ManagerScreen.Section.DASHBOARD, ManagerScreen.Section.AUCTION, ManagerScreen.Section.LEADERBOARD}) {
			context.runOnClient(client -> ManagerScreen.open(DonutGambleClient.state, section));
			context.waitTicks(3);
			context.takeScreenshot("donutgamble-manager-" + section.name().toLowerCase(Locale.ROOT));
		}

		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			Transaction hayden = state.finance.search("hayden260").stream()
				.filter(t -> t.source == Transaction.Source.AUCTION).findFirst().orElseThrow();
			state.setVoided(hayden, true);
			check(state.auctions.current.total() == 2175 * M, "voiding a payment pulls it out of the auction");

			long expensesBefore = state.finance.report(0).expenses();
			state.endAuction();
			Auction ended = state.auctions.history.get(state.auctions.history.size() - 1);
			check(ended.winner.equals("RazorBroGaming"), "winner");
			check(state.auctions.openRefunds().size() == 3, "losers are owed refunds");
			check(state.finance.report(0).expenses() == expensesBefore + 250 * M, "prize cost is an expense");

			long paidBefore = state.finance.report(0).paidOut();
			pay(client, "You paid jimbo01 $ 650M");
			check(state.auctions.openRefunds().size() == 2, "paying a refund clears it");
			check(state.finance.report(0).paidOut() == paidBefore + 650 * M, "payout recorded");

			pay(client, "Notch paid you $ 40M");
			check(state.ledger.pending().stream().anyMatch(b -> b.player.equals("Notch")), "after the auction, payments are bets again");

			state.exportTransactions(false);
			state.exportTransactions(true);
			state.exportAuctions(false);
			state.exportReport();
			try (var files = Files.list(FabricLoader.getInstance().getConfigDir().resolve("donutgamble/exports"))) {
				check(files.count() == 4, "4 export files written");
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}

			QueueOverlay overlay = DonutGambleClient.overlay;
			state.config.hudX = 6;
			state.config.hudY = 6;
			check(overlay.startDrag(8, 8), "grab the queue");
			overlay.drag(120, 60);
			overlay.endDrag();
			check(state.config.hudX == 118 && state.config.hudY == 58, "queue was dragged");
			state.config.hudX = 6;
			state.config.hudY = 6;
			state.config.activeTab = QueueOverlay.Tab.OWE.name();
		});
		context.setScreen(() -> new OverlayPreviewScreen());
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-owe-refunds");

		context.runOnClient(client -> ManagerScreen.open(DonutGambleClient.state, ManagerScreen.Section.PAYMENTS));
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-manager-payments");
		for (ManagerScreen.Section section : new ManagerScreen.Section[]{
			ManagerScreen.Section.FINANCE, ManagerScreen.Section.HISTORY, ManagerScreen.Section.SETTINGS}) {
			context.runOnClient(client -> ManagerScreen.open(DonutGambleClient.state, section));
			context.waitTicks(3);
			context.takeScreenshot("donutgamble-manager-" + section.name().toLowerCase(Locale.ROOT));
		}
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
