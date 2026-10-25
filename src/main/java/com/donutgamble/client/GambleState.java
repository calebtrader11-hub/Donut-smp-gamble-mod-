package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Auction;
import com.donutgamble.core.AuctionManager;
import com.donutgamble.core.Bet;
import com.donutgamble.core.CsvExport;
import com.donutgamble.core.FinanceBook;
import com.donutgamble.core.GambleConfig;
import com.donutgamble.core.Game;
import com.donutgamble.core.GameClassifier;
import com.donutgamble.core.Ledger;
import com.donutgamble.core.Outcome;
import com.donutgamble.core.PaymentDeduplicator;
import com.donutgamble.core.PaymentDetector;
import com.donutgamble.core.Refund;
import com.donutgamble.core.Standing;
import com.donutgamble.core.Transaction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The mod's live state and every action the queue, manager and hotkeys can take. Saves after each change. */
public final class GambleState {
	public record Toast(String text, int color, long start, long until) {
	}

	private static final long TOAST_MS = 4000;
	/** "Awaiting manual payment" stays up longer: it's a to-do. */
	private static final long PAYMENT_TOAST_MS = 8000;
	static final int MAX_TOASTS = 4;

	private final Storage storage = new Storage();
	public final GambleConfig config;
	public final Ledger ledger;
	public final FinanceBook finance;
	public final AuctionManager auctions;
	private final PaymentDetector detector;
	private final PaymentDeduplicator dedupe = new PaymentDeduplicator();
	private final List<Toast> toasts = new ArrayList<>();

	GambleState() {
		config = storage.loadConfig();
		ledger = storage.loadLedger();
		finance = storage.loadFinance();
		auctions = storage.loadAuctions();
		detector = new PaymentDetector(config.extraPayPatterns);
		Colors.theme = Colors.Theme.parse(config.theme);
		// A live auction's clock keeps running while the game is closed; pause it so no time is lost.
		if (auctions.current != null) {
			auctions.current.pause(System.currentTimeMillis());
		}
		storage.dailyBackup();
		save();
	}

	/** Queues a save; files are written in the background a moment later. */
	public void save() {
		storage.save(config, ledger, finance, auctions);
	}

	/** Writes everything now (game closing). */
	void flush() {
		save();
		storage.flush();
	}

	java.nio.file.Path dataDir() {
		return storage.dir();
	}

	public void backupNow() {
		java.nio.file.Path p = storage.backupNow();
		notify(p != null ? "Backed up to backups/" + p.getFileName() : "Backup failed: see the log", p != null ? Colors.text() : Colors.RED);
	}

	// ---- payments from chat ----

	void onServerMessage(String message) {
		long now = System.currentTimeMillis();
		detector.detectOutgoing(message).ifPresent(p -> recordPayout(p.player(), p.amount(), now));
		detector.detect(message).ifPresent(p -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null && client.player.getName().getString().equalsIgnoreCase(p.player())) {
				return;
			}
			if (dedupe.isDuplicate(p.player(), p.amount(), now, config.dedupeWindowMs)) {
				DonutGambleClient.LOGGER.info("Ignored duplicate payment {} {}", p.player(), p.amount());
				return;
			}
			receive(p.player(), p.amount(), now, false, "");
		});
	}

	/**
	 * Every incoming payment is written to the finance book, then counted toward the live auction
	 * (if routing is on and it meets the minimum) or queued as a gamble bet.
	 */
	Transaction receive(String player, long amount, long now, boolean manual, String note) {
		Transaction tx = finance.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, player, amount, note, now, manual);
		if (config.routePaymentsToAuction && auctions.onPayment(player, amount, now, tx.id, config)) {
			tx.source = Transaction.Source.AUCTION;
			tx.auctionId = auctions.current.id;
			Standing leader = auctions.current.leader();
			int rank = rankOf(auctions.current, player);
			notify("+" + Fmt.money(amount) + " " + player + " → auction #" + rank
				+ (leader != null && leader.player().equalsIgnoreCase(player) ? " (leading)" : ""), Colors.GREEN);
		} else {
			Game game = GameClassifier.guess(amount, config.lastGame, config);
			Bet bet = ledger.addPayment(player, amount, game, now);
			tx.betId = bet.id;
			// Jump to the new bet's tab if the open game tab has nothing waiting.
			Game open = QueueHud.Tab.parse(config.activeTab).game;
			if (open != null && open != game && ledger.pending(open).isEmpty()) {
				config.activeTab = QueueHud.Tab.of(game).name();
			}
			notify("+" + Fmt.money(amount) + " " + player + " → " + game.displayName, Colors.GREEN);
		}
		save();
		return tx;
	}

	private static int rankOf(Auction a, String player) {
		List<Standing> r = a.rankings();
		for (int i = 0; i < r.size(); i++) {
			if (r.get(i).player().equalsIgnoreCase(player)) {
				return i + 1;
			}
		}
		return r.size() + 1;
	}

	/** "You paid X $Y": every outgoing /pay is a payout; it also clears a matching owed bet or refund. */
	void recordPayout(String player, long amount, long now) {
		Transaction.Source source = Transaction.Source.OTHER;
		if (ledger.settleByPayment(player, amount)) {
			source = Transaction.Source.GAMBLE;
		} else if (auctions.settleRefundByPayment(player, amount)) {
			source = Transaction.Source.AUCTION;
		}
		finance.record(Transaction.Type.PAYOUT, source, player, amount, "", now, false);
		save();
	}

	// ---- manual corrections ----

	/** A payment that chat missed: recorded and routed exactly like a detected one. */
	public void addManualPayment(String player, long amount) {
		receive(player, amount, System.currentTimeMillis(), true, "added by hand");
	}

	public void addManualPayout(String player, long amount) {
		finance.record(Transaction.Type.PAYOUT, Transaction.Source.OTHER, player, amount, "added by hand", System.currentTimeMillis(), true);
		save();
	}

	public void addExpense(String note, long amount) {
		finance.record(Transaction.Type.EXPENSE, Transaction.Source.OTHER, "-", amount, note, System.currentTimeMillis(), true);
		save();
	}

	/**
	 * Voids a transaction (it stops counting) or restores it. Voiding a payment also pulls it out of
	 * the gamble queue (if still waiting) or the auction it went to.
	 */
	public void setVoided(Transaction tx, boolean voided) {
		if (!finance.setVoided(tx.id, voided)) {
			return;
		}
		if (voided) {
			if (tx.betId != 0) {
				ledger.remove(tx.betId);
			}
			Auction a = tx.auctionId != 0 ? auctions.find(tx.auctionId) : null;
			if (a != null && a.isLive()) {
				a.removeTransaction(tx.id);
			}
		} else {
			Auction a = tx.auctionId != 0 ? auctions.find(tx.auctionId) : null;
			if (a != null && a.isLive()) {
				a.addContribution(tx.player, tx.amount, tx.timestamp, tx.id, tx.manual);
			}
		}
		save();
	}

	// ---- auctions ----

	public boolean startAuction(String item, int durationSec, long prizeCost) {
		if (auctions.isLive()) {
			return false;
		}
		Auction a = auctions.start(item, durationSec, prizeCost, System.currentTimeMillis());
		config.activeTab = QueueHud.Tab.AUCTION.name();
		notify("Auction started: " + a.item + " (" + Fmt.clock(a.durationMs) + ")", Colors.text());
		save();
		return true;
	}

	public void pauseAuction() {
		if (auctions.isLive()) {
			auctions.current.pause(System.currentTimeMillis());
			notify("Auction paused", Colors.GOLD);
			save();
		}
	}

	public void resumeAuction() {
		if (auctions.isLive()) {
			auctions.current.resume(System.currentTimeMillis());
			notify("Auction resumed", Colors.text());
			save();
		}
	}

	public void togglePauseAuction() {
		if (auctions.isLive() && auctions.current.status == Auction.Status.PAUSED) {
			resumeAuction();
		} else {
			pauseAuction();
		}
	}

	public void extendAuction(int seconds) {
		if (auctions.isLive()) {
			auctions.current.extend(seconds * 1000L, System.currentTimeMillis());
			save();
		}
	}

	public void endAuction() {
		finished(auctions.end(System.currentTimeMillis(), config));
	}

	public void cancelAuction() {
		Auction a = auctions.cancel(System.currentTimeMillis());
		if (a != null) {
			notify("Auction cancelled: " + a.rankings().size() + " refunds owed", Colors.GOLD);
			save();
		}
	}

	/** Add or (negative) take away from a player's auction total. Doesn't touch the finance book. */
	public void correctAuction(String player, long delta) {
		if (auctions.isLive() && !player.isBlank() && delta != 0) {
			auctions.current.correct(player.trim(), delta, System.currentTimeMillis());
			save();
		}
	}

	/** Called every client tick: ends the auction when its timer runs out. */
	void tick() {
		if (auctions.isLive()) {
			finished(auctions.tick(System.currentTimeMillis(), config));
		}
	}

	private void finished(Auction a) {
		if (a == null) {
			return;
		}
		if (a.winner != null && a.prizeCost > 0) {
			finance.record(Transaction.Type.EXPENSE, Transaction.Source.AUCTION, a.winner, a.prizeCost,
				"Prize: " + a.item, a.endedAt, false);
		}
		notify(a.winner != null
			? a.item + " won by " + a.winner + " · total " + Fmt.money(a.total())
			: a.item + " ended with no bids", Colors.GOLD);
		save();
	}

	public void settleRefund(Refund r) {
		auctions.settleRefund(r.id);
		save();
	}

	public void prefillPay(String player, long amount) {
		MinecraftClient.getInstance().setScreen(new ChatScreen("/pay " + player + " " + amount, false));
	}

	// ---- exports ----

	public void exportTransactions(boolean json) {
		exported(json
			? storage.export("transactions", "json", Storage.toJson(finance.transactions))
			: storage.export("transactions", "csv", CsvExport.transactions(finance.transactions)));
	}

	public void exportAuctions(boolean json) {
		exported(json
			? storage.export("auctions", "json", Storage.toJson(auctions.history))
			: storage.export("auctions", "csv", CsvExport.auctions(auctions.history)));
	}

	/** A readable session + lifetime report. */
	public void exportReport() {
		StringBuilder sb = new StringBuilder("Donut Gamble financial report\n\n");
		appendReport(sb, "This session", finance.report(ledger.sessionStart));
		appendReport(sb, "Lifetime", finance.report(0));
		sb.append("Top contributors (lifetime)\n");
		finance.topContributors(0, 10).forEach(p ->
			sb.append("  ").append(p.player()).append("  $").append(AmountParser.format(p.total()))
				.append("  (").append(p.count()).append(" payments)\n"));
		sb.append("\nAuctions\n");
		for (Auction a : auctions.history) {
			a.summary().forEach(line -> sb.append("  ").append(line).append('\n'));
			sb.append('\n');
		}
		exported(storage.export("report", "txt", sb.toString()));
	}

	private static void appendReport(StringBuilder sb, String title, com.donutgamble.core.FinanceReport r) {
		sb.append(title).append('\n');
		sb.append("  Received   $").append(AmountParser.format(r.received())).append(" (").append(r.incomingCount()).append(" payments)\n");
		for (Map.Entry<Transaction.Source, Long> e : r.receivedBySource().entrySet()) {
			sb.append("    from ").append(e.getKey().name().toLowerCase()).append("  $").append(AmountParser.format(e.getValue())).append('\n');
		}
		sb.append("  Paid out   $").append(AmountParser.format(r.paidOut())).append('\n');
		sb.append("  Expenses   $").append(AmountParser.format(r.expenses())).append('\n');
		sb.append("  Net        ").append(AmountParser.formatSigned(r.net())).append("\n\n");
	}

	private void exported(Path file) {
		if (file != null) {
			notify("Exported " + file.getFileName(), Colors.text());
		} else {
			notify("Export failed: see the log", Colors.RED);
		}
	}

	// ---- notifications ----

	void notify(String text, int color) {
		notify(text, color, TOAST_MS);
	}

	void notify(String text, int color, long durationMs) {
		if (!config.notifications) {
			return;
		}
		long now = System.currentTimeMillis();
		toasts.add(new Toast(text, color, now, now + durationMs));
		while (toasts.size() > MAX_TOASTS) {
			toasts.remove(0);
		}
	}

	/** A snapshot of the toasts still showing (safe to iterate while new ones arrive). */
	List<Toast> activeToasts() {
		long now = System.currentTimeMillis();
		toasts.removeIf(t -> t.until() < now);
		return List.copyOf(toasts);
	}

	// ---- existing gamble actions ----

	public void setGame(Bet bet, Game game) {
		ledger.setGame(bet.id, game);
		save();
	}

	/** Paper → Blackjack → Ely → Paper. */
	public void cycleGame(Bet bet) {
		Game[] games = Game.values();
		ledger.setGame(bet.id, games[(bet.game.ordinal() + 1) % games.length]);
		save();
	}

	public void resolve(Bet bet, Outcome outcome, Integer them, Integer me) {
		config.lastGame = bet.game;
		ledger.resolve(bet.id, outcome, them, me, config);
		save();
	}

	public void resolveTie(Bet bet) {
		config.lastGame = bet.game;
		ledger.resolveTie(bet.id, null, null, config);
		save();
	}

	public void setScore(Bet bet, Integer them, Integer me) {
		ledger.setScore(bet.id, them, me);
		save();
	}

	// ---- calling a result (queue letters and hotkeys) ----

	/**
	 * W: they won. Paper/Blackjack record it, show "Awaiting manual payment" and open the score
	 * picker. Ely first confirms what the elytra costs.
	 */
	public void callWin(Bet bet) {
		if (bet.game == Game.ELY) {
			MinecraftClient.getInstance().setScreen(ScoreScreen.elytraPrice(this, bet));
			return;
		}
		resolve(bet, Outcome.PLAYER_WIN, null, null);
		awaitingPayment(bet);
		MinecraftClient.getInstance().setScreen(ScoreScreen.score(this, bet));
	}

	/** L: they lost. Paper/Blackjack open the score picker; Ely is just a miss. */
	public void callLoss(Bet bet) {
		resolve(bet, Outcome.PLAYER_LOSE, null, null);
		notify(bet.player + " lost · you keep " + Fmt.money(bet.amount), Colors.GREEN);
		if (bet.game.usesScore()) {
			MinecraftClient.getInstance().setScreen(ScoreScreen.score(this, bet));
		}
	}

	/** T: a tie goes to you. No numbers needed. */
	public void callTie(Bet bet) {
		resolveTie(bet);
		notify(bet.player + " tied · you keep " + Fmt.money(bet.amount), Colors.GREEN);
	}

	/** Ely hit, once the price is confirmed. */
	void confirmElytraWin(Bet bet, long price) {
		setElytraPrice(price);
		resolve(bet, Outcome.PLAYER_WIN, null, null);
		awaitingPayment(bet);
	}

	void awaitingPayment(Bet bet) {
		notify("Awaiting manual payment of " + Fmt.owedLabel(bet) + " to " + bet.player, Colors.GOLD, PAYMENT_TOAST_MS);
	}

	/** Hotkeys act on #1 in the open game tab (or the oldest bet overall on other tabs), without the score picker. */
	public void resolveFirst(Outcome outcome) {
		Game game = QueueHud.Tab.parse(config.activeTab).game;
		List<Bet> queue = game != null ? ledger.pending(game) : ledger.pending();
		if (queue.isEmpty()) {
			return;
		}
		Bet bet = queue.get(0);
		if (outcome == Outcome.PLAYER_WIN && bet.game == Game.ELY && config.elytraPrice <= 0) {
			// No price yet: an Ely win would count as costing nothing.
			MinecraftClient.getInstance().setScreen(ScoreScreen.elytraPrice(this, bet));
			return;
		}
		resolve(bet, outcome, null, null);
		if (outcome == Outcome.PLAYER_WIN) {
			awaitingPayment(bet);
		} else {
			notify(bet.player + " lost · you keep " + Fmt.money(bet.amount), Colors.GREEN);
		}
	}

	/** D: winner paid. Delivering an elytra is money spent, so it's recorded as an expense. */
	public void settle(Bet bet) {
		ledger.settle(bet.id);
		if (bet.payoutIsElytra() && bet.payout > 0) {
			finance.record(Transaction.Type.EXPENSE, Transaction.Source.GAMBLE, bet.player, bet.payout,
				"Elytra for " + bet.player, System.currentTimeMillis(), false);
		}
		save();
	}

	/** Right-click a name: the payment wasn't a bet. The money was still received, so it stays in the books as "other". */
	public void dismiss(Bet bet) {
		ledger.remove(bet.id);
		for (Transaction t : finance.transactions) {
			if (t.betId == bet.id) {
				t.source = Transaction.Source.OTHER;
				t.betId = 0;
			}
		}
		save();
	}

	/** Puts "/pay name amount" in the chat box; the streamer presses Enter to send it. */
	public void prefillPay(Bet bet) {
		prefillPay(bet.player, bet.payout);
	}

	public void newSession() {
		ledger.startSession(System.currentTimeMillis());
		notify("New session started", Colors.text());
		save();
	}

	public void setElytraPrice(long price) {
		config.elytraPrice = price;
		save();
	}

	public void nextTheme() {
		setTheme(Colors.theme.next());
	}

	void setTheme(Colors.Theme theme) {
		Colors.theme = theme;
		config.theme = theme.name();
		save();
	}

	/** H: show or hide the queue. */
	public void toggleHud() {
		com.donutgamble.core.HudLayout.Slot s = config.hud.slots.get("queue");
		if (s != null) {
			s.visible = !s.visible;
		}
		save();
	}
}
