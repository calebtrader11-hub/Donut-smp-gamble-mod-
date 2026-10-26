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

import com.donutgamble.client.ui.Paint;
import net.minecraft.client.input.KeyInput;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Drives a real client: fake /pay messages, calls bets and runs an auction, checking results and screenshotting every screen. */
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
			state.config.elyShotPrice = 40 * M;
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

		// Chat-style queue (the default).
		context.setScreen(() -> new OverlayPreviewScreen());
		context.runOnClient(client -> DonutGambleClient.state.setTheme(Colors.Theme.AQUA));
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-queue-chat");
		// The tabbed box is optional; show it for its screenshots.
		context.runOnClient(client -> {
			var hudSlots = DonutGambleClient.state.config.hud;
			hudSlots.slot("queue.chat", 2, 6, true).visible = false;
			hudSlots.slot("queue", 6, 6, true).visible = true;
		});
		for (QueueHud.Tab tab : QueueHud.Tab.values()) {
			context.runOnClient(client -> DonutGambleClient.state.config.activeTab = tab.name());
			context.waitTicks(3);
			context.takeScreenshot("donutgamble-tab-" + tab.name().toLowerCase(Locale.ROOT));
		}
		context.runOnClient(client -> {
			DonutGambleClient.state.config.activeTab = QueueHud.Tab.PAPER.name();
			// Back to the default layout: chat-style queue, no tabbed box.
			var hudSlots = DonutGambleClient.state.config.hud;
			hudSlots.slot("queue", 6, 6, true).visible = false;
			hudSlots.slot("queue.chat", 2, 6, true).visible = true;
		});

		// W on Paper: recorded, "Awaiting manual payment" toast, score picker with 1-9.
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			Bet first = state.ledger.pending(Game.PAPER).get(0);
			state.callWin(first);
			check(first.isPending(), "W only opens the result window; nothing recorded yet");
			check(client.currentScreen instanceof ScoreScreen, "W opens the result window");
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-score-paper-empty");
		context.runOnClient(client -> {
			// Host 8, viewer 9, typed like the streamer would: the viewer's higher number wins.
			client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(org.lwjgl.glfw.GLFW.GLFW_KEY_8, 0, 0));
			client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(org.lwjgl.glfw.GLFW.GLFW_KEY_9, 0, 0));
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-score-paper");
		context.runOnClient(client -> {
			client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
			Bet b = DonutGambleClient.state.ledger.bets.stream().filter(x -> x.player.equals("RazorBroGaming")).findFirst().orElseThrow();
			check(b.scoreThem == 9 && b.scoreMe == 8, "typed numbers saved (viewer 9, host 8)");
			check(b.outcome == Outcome.PLAYER_WIN, "the numbers decided: viewer higher wins");
			check(DonutGambleClient.state.activeToasts().stream().anyMatch(t -> t.text().startsWith("Awaiting manual payment of $100m")), "payment toast");
			check(!(client.currentScreen instanceof ScoreScreen), "window closes on confirm");
		});

		// L on Blackjack: picker with 17-21 + Bust.
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			Bet bj = state.ledger.pending(Game.BLACKJACK).get(0);
			state.callLoss(bj);
			check(client.currentScreen instanceof ScoreScreen, "L opens the score picker");
		});
		context.runOnClient(client -> {
			// Pick host 18, viewer 18: a tie, which the house wins.
			client.currentScreen.children().stream().filter(c -> c instanceof com.donutgamble.client.ui.Button btn && btn.getMessage().getString().equals("18"))
				.map(c -> (com.donutgamble.client.ui.Button) c).findFirst().orElseThrow()
				.onClick(new net.minecraft.client.gui.Click(0, 0, new net.minecraft.client.input.MouseInput(0, 0)), false);
		});
		context.runOnClient(client -> {
			client.currentScreen.children().stream().filter(c -> c instanceof com.donutgamble.client.ui.Button btn && btn.getMessage().getString().equals("18"))
				.skip(1).map(c -> (com.donutgamble.client.ui.Button) c).findFirst().orElseThrow()
				.onClick(new net.minecraft.client.gui.Click(0, 0, new net.minecraft.client.input.MouseInput(0, 0)), false);
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-score-blackjack");
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			long pendingBefore = state.ledger.pending(Game.BLACKJACK).size();
			client.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
			check(state.ledger.pending(Game.BLACKJACK).size() == pendingBefore - 1, "confirm records the hand");
			Bet tied = state.ledger.bets.stream().filter(x -> x.game == Game.BLACKJACK && x.tie && x.scoreThem != null).findFirst().orElse(null);
			check(tied != null && tied.scoreThem == 18 && tied.scoreMe == 18, "same numbers = tie, house wins");
		});
		context.setScreen(() -> null);

		// T: no picker.
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			Bet next = state.ledger.pending(Game.PAPER).get(0);
			state.callTie(next);
			check(next.tie, "T records a tie");
			check(!(client.currentScreen instanceof ScoreScreen), "T doesn't open the score picker");
		});

		// W on Ely: price first, then the win.
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			Bet ely = state.ledger.pending(Game.ELY).get(0);
			check(state.config.elyShots(ely.amount) == 3, "120m at 40m a shot is 3x");
			state.callWin(ely);
			check(ely.isPending(), "Ely win waits for how many were won");
			client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_2, 0, 0));
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-elytra");
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			Bet ely = state.ledger.bets.stream().filter(b -> b.game == Game.ELY && b.elytrasWon == 0 && b.isPending()).findFirst().orElse(null);
			client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_ENTER, 0, 0));
			Bet won = state.ledger.bets.stream().filter(b -> b.elytrasWon == 2).findFirst().orElseThrow();
			check(won.outcome == Outcome.PLAYER_WIN && won.payout == 360 * M, "2 elytras owed at 180m each");
			check(Fmt.owedLabel(won).equals("2 Elytras"), "owed label counts elytras: " + Fmt.owedLabel(won));
		});
		context.setScreen(() -> null);

		context.setScreen(() -> new OverlayPreviewScreen());
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-toasts");

		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			state.config.hud.slot("profit", 0, 0, false).visible = true;
			client.setScreen(new HudEditorScreen(DonutGambleClient.hud, null));
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-hud-editor");
		// Select the queue: the inspector opens next to it.
		context.runOnClient(client -> {
			HudEditorScreen.selectedId = "queue";
			client.setScreen(new HudEditorScreen(DonutGambleClient.hud, null));
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-hud-editor-inspector");
		context.runOnClient(client -> {
			HudEditorScreen.selectedId = null;
			GambleState state = DonutGambleClient.state;
			HudManager hud = DonutGambleClient.hud;
			var layout = state.config.hud;
			layout.saveAs("Mine");
			hud.applyPreset("Competitive");
			check(layout.slots.get("queue.paper").visible && layout.slots.get("activity").visible, "competitive shows a queue per game");
			check(!layout.slots.get("queue").visible, "competitive hides the tabbed queue");
			check(layout.slots.get("queue.blackjack").y > layout.slots.get("queue.paper").y, "presets stack components without overlap");
		});
		context.setScreen(() -> new OverlayPreviewScreen());
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-hud-preset-competitive");
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			var layout = state.config.hud;
			DonutGambleClient.hud.applyPreset("Minimal");
			check(layout.slots.get("queue").compact, "minimal is compact");
			var q = layout.slots.get("queue");
			q.compact = false;
			q.opacity = 0.5f;
			q.accent = "GOLD";
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-hud-styled");
		context.runOnClient(client -> {
			var layout = DonutGambleClient.state.config.hud;
			check(layout.load("Mine"), "saved layout loads");
			check(layout.slots.get("queue.chat").visible && layout.slots.get("queue").opacity == 1f, "saved layout restored as it was");
			DonutGambleClient.state.save();
		});
		context.setScreen(() -> null);

		runAuction(context);
		runItemsAndStreamerMode(context);
		context.setScreen(() -> null);
	}

	/** Auction + finance on top of the gamble state above: routing, dedupe, corrections, refunds, exports, GUI. */
	private void runAuction(ClientGameTestContext context) {
		// Lay the manager out at 480x270, what 1080p with automatic GUI scale gives: the tightest common size.
		context.runOnClient(client -> com.donutgamble.client.ui.ScaledScreen.forcedFactor = client.getWindow().getScaledWidth() / 480f);
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
			state.config.activeTab = QueueHud.Tab.AUCTION.name();
		});
		context.setScreen(() -> new OverlayPreviewScreen());
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-auction-live");

		for (ManagerScreen.Section section : new ManagerScreen.Section[]{
			ManagerScreen.Section.DASHBOARD, ManagerScreen.Section.PLAYERS, ManagerScreen.Section.AUCTION, ManagerScreen.Section.LEADERBOARD}) {
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
			check(state.recentResult() == ended, "results card shows the auction that just ended");
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

			HudManager hud = DonutGambleClient.hud;
			var slot = state.config.hud.slots.get("queue.chat");
			int oldX = slot.x, oldY = slot.y;
			HudElement chat = hud.element("queue.chat");
			check(chat.screenW > 0, "chat queue is on screen");
			slot.x = 6;
			slot.y = 80;
			hud.snap = false;
			chat.screenX = 6;
			chat.screenY = 80;
			check(hud.startDrag(10, 84), "grab the queue");
			hud.drag(124, 104);
			hud.endDrag();
			check(slot.x == 120 && slot.y == 100, "queue was dragged to " + slot.x + "," + slot.y);
			slot.x = oldX;
			slot.y = oldY;
			hud.snap = true;
			state.config.activeTab = QueueHud.Tab.OWE.name();
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
		runManagerPages(context);
	}

	/** The overhaul's pages and states: profile notes/tags, customize mode, settings categories, ranges, notifications. */
	private void runManagerPages(ClientGameTestContext context) {
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			check(state.players.addTag("jimbo01", "Regular"), "tag added");
			check(!state.players.addTag("JIMBO01", "regular"), "duplicate tag ignored");
			state.players.addTag("jimbo01", "VIP");
			state.players.setNotes("jimbo01", "Always bids late");
			state.players.setUuid("jimbo01", "4b2d8c1e-0000-4000-8000-000000000001");
			ManagerScreen.DRAFTS.put("players.search", "jimbo");
		});
		context.runOnClient(client -> ManagerScreen.open(DonutGambleClient.state, ManagerScreen.Section.PLAYERS));
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-manager-player-profile");
		context.runOnClient(client -> {
			ManagerScreen.DRAFTS.remove("players.search");
			DashboardPage.customizing = true;
		});
		context.runOnClient(client -> ManagerScreen.open(DonutGambleClient.state, ManagerScreen.Section.DASHBOARD));
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-manager-dashboard-customize");
		context.runOnClient(client -> DashboardPage.customizing = false);

		for (SettingsPage.Category category : new SettingsPage.Category[]{
			SettingsPage.Category.AUCTIONS, SettingsPage.Category.NOTIFICATIONS, SettingsPage.Category.DATA}) {
			context.runOnClient(client -> {
				SettingsPage.category = category;
				ManagerScreen.open(DonutGambleClient.state, ManagerScreen.Section.SETTINGS);
			});
			context.waitTicks(3);
			context.takeScreenshot("donutgamble-settings-" + category.name().toLowerCase(Locale.ROOT));
		}
		context.runOnClient(client -> {
			SettingsPage.category = SettingsPage.Category.APPEARANCE;
			LeaderboardPage.scope = 1;
			ManagerScreen.open(DonutGambleClient.state, ManagerScreen.Section.LEADERBOARD);
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-manager-leaderboard-alltime");
		context.runOnClient(client -> {
			LeaderboardPage.scope = 0;
			FinancePage.range = com.donutgamble.core.FinanceSeries.Range.TODAY;
			ManagerScreen.open(DonutGambleClient.state, ManagerScreen.Section.FINANCE);
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-manager-finance-today");
		context.runOnClient(client -> FinancePage.range = com.donutgamble.core.FinanceSeries.Range.WEEK);

		// Full-size layout (no forced scale) for comparison with the 480x270 shots.
		context.runOnClient(client -> {
			com.donutgamble.client.ui.ScaledScreen.forcedFactor = 0;
			ManagerScreen.open(DonutGambleClient.state, ManagerScreen.Section.DASHBOARD);
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-manager-dashboard-large");
		context.runOnClient(client -> com.donutgamble.client.ui.ScaledScreen.forcedFactor = client.getWindow().getScaledWidth() / 480f);

		context.runOnClient(client -> {
			DonutGambleClient.state.previewNotifications();
			check(DonutGambleClient.state.activeToasts().size() >= 4, "notification preview shows each style");
		});
		context.setScreen(() -> new OverlayPreviewScreen());
		context.waitTicks(8);
		context.takeScreenshot("donutgamble-notifications");
		context.setScreen(() -> null);
	}

	/** Item picker, auction setup with a real item, and Streamer Mode (render audit + screenshots). */
	private void runItemsAndStreamerMode(ClientGameTestContext context) {
		// Item picker: search for spawner.
		context.runOnClient(client -> {
			client.setScreen(new ItemPickerScreen(DonutGambleClient.state, null, picked -> { }));
			client.currentScreen.children().stream().filter(c -> c instanceof com.donutgamble.client.ui.TextBox)
				.map(c -> (com.donutgamble.client.ui.TextBox) c).findFirst().orElseThrow().setText("spawner");
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-item-picker-search");
		context.runOnClient(client -> {
			client.currentScreen.children().stream().filter(c -> c instanceof com.donutgamble.client.ui.TextBox)
				.map(c -> (com.donutgamble.client.ui.TextBox) c).findFirst().orElseThrow().setText("");
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-item-picker");

		// An exact item (custom name + enchantment) survives save/load.
		context.runOnClient(client -> {
			ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
			sword.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Donut Slayer"));
			var sharpness = ItemIcons.lookup().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
			sword.addEnchantment(sharpness, 5);
			ItemStack back = ItemIcons.decode(ItemIcons.encode(sword));
			check(ItemStack.areItemsAndComponentsEqual(sword, back), "item components survive saving");
			check(ItemIcons.name(back).equals("Donut Slayer"), "custom name kept");
		});

		// Set up an auction for 2 spawners with a description, then start it with Enter.
		context.runOnClient(client -> {
			AuctionSetupScreen.Draft d = AuctionSetupScreen.DRAFT;
			d.prize = new ItemStack(Items.SPAWNER);
			d.exact = false;
			d.quantity = "2";
			d.title = "";
			d.description = "Fresh from the End";
			d.duration = "3m";
			d.cost = "300m";
			d.showTop = 5;
			client.setScreen(new AuctionSetupScreen(DonutGambleClient.state, null));
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-auction-setup");
		context.runOnClient(client -> {
			client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_ENTER, 0, 0));
			GambleState state = DonutGambleClient.state;
			Auction a = state.auctions.current;
			check(a != null, "setup starts the auction");
			check("minecraft:spawner".equals(a.itemId) && a.quantity == 2, "prize item and quantity saved");
			check(a.item.equals("2× Monster Spawner"), "title falls back to the item: " + a.item);
			check(a.description.equals("Fresh from the End"), "description saved");
			check(a.durationMs == 180_000, "length saved");
			check(state.config.recentItems.get(0).equals("minecraft:spawner"), "item remembered as recent");
			pay(client, "jimbo01 paid you $ 400M");
			pay(client, "BeanznBread paid you $ 250M");
			pay(client, "Tobias paid you $ 125M");
			state.config.activeTab = QueueHud.Tab.AUCTION.name();
			state.config.hud.slot("profit", 0, 0, false).visible = true;
		});
		context.setScreen(() -> new OverlayPreviewScreen());
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-hud-auction-item");
		// Last seconds: the big countdown in the middle of the screen.
		long[] endsAt = new long[1];
		context.runOnClient(client -> {
			Auction live = DonutGambleClient.state.auctions.current;
			endsAt[0] = live.endsAt;
			live.endsAt = System.currentTimeMillis() + 7_400;
		});
		context.waitTicks(4);
		context.takeScreenshot("donutgamble-auction-final-countdown");
		context.runOnClient(client -> DonutGambleClient.state.auctions.current.endsAt = endsAt[0]);

		// End now asks first; Escape keeps the auction running.
		context.runOnClient(client -> ManagerScreen.open(DonutGambleClient.state, ManagerScreen.Section.AUCTION));
		context.waitTicks(2);
		context.runOnClient(client -> {
			com.donutgamble.client.ui.Button end = client.currentScreen.children().stream()
				.filter(c -> c instanceof com.donutgamble.client.ui.Button b && b.getMessage().getString().equals("End now"))
				.map(c -> (com.donutgamble.client.ui.Button) c).findFirst().orElseThrow();
			end.onClick(new net.minecraft.client.gui.Click(end.getX() + 2, end.getY() + 2, new net.minecraft.client.input.MouseInput(0, 0)), false);
			check(DonutGambleClient.state.auctions.isLive(), "End now only asks");
		});
		context.waitTicks(4);
		context.takeScreenshot("donutgamble-confirm-end");
		context.runOnClient(client -> {
			client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE, 0, 0));
			check(DonutGambleClient.state.auctions.isLive(), "Escape cancels the dialog, auction keeps running");
			check(client.currentScreen instanceof ManagerScreen, "Escape closes only the dialog");
		});
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-manager-auction-item");

		// Streamer Mode: record every string drawn on every surface and check nothing private is among them.
		String[] secrets = new String[6];
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			var session = state.finance.report(state.ledger.sessionStart);
			var life = state.finance.report(0);
			secrets[0] = Fmt.signed(session.net());
			secrets[1] = Fmt.money(session.received());
			secrets[2] = Fmt.signed(life.net());
			secrets[3] = Fmt.signed(state.ledger.sessionStats().net());
			secrets[4] = Fmt.signed(state.sessionProfit());
			secrets[5] = Fmt.signed(state.totalProfit());
			state.notify("Exported test.csv", Colors.text());
			Bet b = state.ledger.pending(Game.PAPER).get(0);
			state.callTie(b);
			Paint.audit = new ArrayList<>();
		});
		// Control: with Streamer Mode off the dashboard does draw the session net, so the audit sees it.
		context.runOnClient(client -> ManagerScreen.open(DonutGambleClient.state, ManagerScreen.Section.DASHBOARD));
		context.waitTicks(3);
		context.runOnClient(client -> {
			check(Paint.audit.stream().anyMatch(t -> t.contains(secrets[4])), "audit sees the session profit when Streamer Mode is off");
			check(Paint.audit.stream().anyMatch(t -> t.contains(secrets[5])), "audit sees the total profit when Streamer Mode is off");
			Paint.audit.clear();
			DonutGambleClient.state.config.privacy.streamerMode = true;
		});
		context.setScreen(() -> new OverlayPreviewScreen());
		for (QueueHud.Tab tab : QueueHud.Tab.values()) {
			context.runOnClient(client -> DonutGambleClient.state.config.activeTab = tab.name());
			context.waitTicks(2);
		}
		context.runOnClient(client -> DonutGambleClient.state.config.activeTab = QueueHud.Tab.PAPER.name());
		context.waitTicks(2);
		context.takeScreenshot("donutgamble-streamer-hud");
		for (ManagerScreen.Section section : ManagerScreen.Section.values()) {
			context.runOnClient(client -> ManagerScreen.open(DonutGambleClient.state, section));
			context.waitTicks(2);
			if (section == ManagerScreen.Section.DASHBOARD || section == ManagerScreen.Section.FINANCE
				|| section == ManagerScreen.Section.PLAYERS || section == ManagerScreen.Section.STREAMER) {
				context.takeScreenshot("donutgamble-streamer-" + section.name().toLowerCase(Locale.ROOT));
			}
		}
		context.runOnClient(client -> {
			List<String> drawn = Paint.audit;
			Paint.audit = null;
			check(drawn.size() > 200, "audit recorded the frames (" + drawn.size() + " strings)");
			for (String t : drawn) {
				for (String secret : secrets) {
					check(!t.contains(secret), "Streamer Mode drew a private figure: \"" + t + "\" contains " + secret);
				}
				check(!t.contains("you keep"), "private toast text drawn: " + t);
				check(!t.contains("Exported"), "private toast drawn: " + t);
				check(!t.matches(".*profit [+-]?\\$.*"), "profit figure drawn: " + t);
			}
			check(drawn.stream().anyMatch(t -> t.contains("Leader jimbo01")), "auction leader still public");
			check(drawn.stream().anyMatch(t -> t.contains("2× Monster Spawner")), "auction item still public");
			check(drawn.stream().anyMatch(t -> t.contains("$400m")), "public bid amounts still shown");
		});

		// Preview applies the rules without turning Streamer Mode on.
		context.runOnClient(client -> {
			GambleState state = DonutGambleClient.state;
			state.config.privacy.streamerMode = false;
			state.preview = true;
			check(state.hides(com.donutgamble.core.PrivacySettings.Surface.FINANCE), "preview hides finance");
		});
		context.setScreen(() -> new OverlayPreviewScreen());
		context.waitTicks(3);
		context.takeScreenshot("donutgamble-stream-preview");
		context.runOnClient(client -> {
			DonutGambleClient.state.preview = false;
			DonutGambleClient.state.endAuction();
		});
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
			DonutGambleClient.hud.renderNow(ctx);
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
