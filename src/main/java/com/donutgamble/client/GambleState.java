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
import com.donutgamble.core.ScoreJudge;
import com.donutgamble.core.Standing;
import com.donutgamble.core.Transaction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.ItemStack;
import com.donutgamble.core.AuctionSetup;
import com.donutgamble.core.PrivacySettings;
import com.donutgamble.core.NotificationSettings.Kind;
import com.donutgamble.core.PlayerDirectory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The mod's live state and every action the queue, manager and hotkeys can take. Saves after each change. */
public final class GambleState {
	/**
	 * A pop-up. {@code text} is the full version; {@code publicText} is what may show while Streamer
	 * Mode hides private toasts (null = show nothing then). {@code icon} is an auction prize, or empty.
	 */
	public record Toast(Kind kind, String text, String publicText, int color, long start, long until, ItemStack icon) {
		/** The text to draw right now, or null if this toast must not be drawn. */
		String visibleText(boolean hidePrivate) {
			return hidePrivate ? publicText : text;
		}
	}

	/** Upper bound on toasts on screen; the setting can lower it. */
	static final int MAX_TOASTS = 6;

	private final Storage storage = new Storage();
	public final GambleConfig config;
	public final Ledger ledger;
	public final FinanceBook finance;
	public final AuctionManager auctions;
	public final PlayerDirectory players;
	private final PaymentDetector detector;
	private final PaymentDeduplicator dedupe = new PaymentDeduplicator();
	private final List<Toast> toasts = new ArrayList<>();
	/** Streamer Mode preview: apply its rules without turning it on. Not saved. */
	boolean preview;

	GambleState() {
		config = storage.loadConfig();
		ledger = storage.loadLedger();
		finance = storage.loadFinance();
		auctions = storage.loadAuctions();
		players = storage.loadPlayers();
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
		storage.save(config, ledger, finance, auctions, players);
	}

	/** Writes everything now (game closing). */
	void flush() {
		save();
		storage.flush();
	}

	// ---- privacy ----

	/** True if private figures on this surface must not be drawn right now (Streamer Mode or preview). */
	public boolean hides(PrivacySettings.Surface surface) {
		return config.privacy.hides(surface, preview);
	}

	/** Streamer Mode or its preview is in effect. */
	public boolean streaming() {
		return config.privacy.streamerMode || preview;
	}

	public void toggleStreamerMode() {
		config.privacy.streamerMode = !config.privacy.streamerMode;
		notify(Kind.SYSTEM, config.privacy.streamerMode ? "Streamer Mode on: private figures hidden" : "Streamer Mode off",
			config.privacy.streamerMode ? "Streamer Mode on" : null, Colors.accent(), ItemStack.EMPTY);
		save();
	}

	/** Privacy presets: BALANCED hides money but keeps the auction public; STRICT also hides bids. Doesn't switch the mode on. */
	public void applyPrivacyPreset(String preset) {
		PrivacySettings p = config.privacy;
		boolean strict = preset.equals("STRICT");
		for (PrivacySettings.Surface s : PrivacySettings.Surface.values()) {
			p.set(s, s != PrivacySettings.Surface.AUCTION_HUD || strict);
		}
		save();
	}

	/** "BALANCED", "STRICT" or "CUSTOM", from the current switches. */
	public String privacyPreset() {
		PrivacySettings p = config.privacy;
		for (PrivacySettings.Surface s : PrivacySettings.Surface.values()) {
			if (s != PrivacySettings.Surface.AUCTION_HUD && !p.get(s)) {
				return "CUSTOM";
			}
		}
		return p.get(PrivacySettings.Surface.AUCTION_HUD) ? "STRICT" : "BALANCED";
	}

	// ---- settings import / export ----

	public void exportSettings() {
		exported(storage.export("settings", "json", Storage.toJson(config)));
	}

	/** Loads config/donutgamble/import/settings.json over the current settings. Data isn't touched. */
	public boolean importSettings() {
		GambleConfig in = storage.readImport();
		if (in == null) {
			notify(Kind.SYSTEM, "No settings to import: put a file at config/donutgamble/import/settings.json", null, Colors.ORANGE, ItemStack.EMPTY);
			return false;
		}
		if (!copySettings(in)) {
			notify(Kind.SYSTEM, "Settings import failed: see the log", null, Colors.RED, ItemStack.EMPTY);
			return false;
		}
		Colors.theme = Colors.Theme.parse(config.theme);
		notify(Kind.SYSTEM, "Settings imported", null, Colors.GREEN, ItemStack.EMPTY);
		save();
		return true;
	}

	/** Puts every setting back to its default. Payments, bets, auctions and players aren't touched. */
	public void resetSettings() {
		copySettings(new GambleConfig());
		Colors.theme = Colors.Theme.parse(config.theme);
		notify(Kind.SYSTEM, "Settings reset to defaults", null, Colors.accent(), ItemStack.EMPTY);
		save();
	}

	private boolean copySettings(GambleConfig in) {
		try {
			for (java.lang.reflect.Field f : GambleConfig.class.getFields()) {
				if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && !java.lang.reflect.Modifier.isFinal(f.getModifiers())) {
					f.set(config, f.get(in));
				}
			}
			return true;
		} catch (IllegalAccessException e) {
			DonutGambleClient.LOGGER.error("Copying settings failed", e);
			return false;
		}
	}

	java.nio.file.Path dataDir() {
		return storage.dir();
	}

	public void backupNow() {
		java.nio.file.Path p = storage.backupNow();
		notify(Kind.SYSTEM, p != null ? "Backed up to backups/" + p.getFileName() : "Backup failed: see the log", null,
			p != null ? Colors.GREEN : Colors.RED, ItemStack.EMPTY);
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
		rememberUuid(player);
		Standing before = auctions.isLive() ? auctions.current.leader() : null;
		if (config.routePaymentsToAuction && auctions.onPayment(player, amount, now, tx.id, config)) {
			tx.source = Transaction.Source.AUCTION;
			tx.auctionId = auctions.current.id;
			Auction a = auctions.current;
			Standing leader = a.leader();
			int rank = rankOf(a, player);
			boolean first = a.contributions.stream().filter(c -> c.player.equalsIgnoreCase(player)).count() == 1;
			boolean newLeader = leader != null && leader.player().equalsIgnoreCase(player)
				&& (before == null || !before.player().equalsIgnoreCase(player));
			boolean amounts = !config.privacy.hideAuctionHud;
			String full = "+" + Fmt.money(amount) + " " + player + " · #" + rank;
			// Bids are public unless the auction HUD is set to hide amounts.
			String shown = amounts ? full : player + (first ? " joined the auction" : " bid again") + " · #" + rank;
			if (!"OFF".equals(a.bidNotify) && !("LEADER".equals(a.bidNotify) && !newLeader)) {
				if (newLeader) {
					String lead = player + " takes the lead" + " · " + Fmt.money(leader.total());
					notify(Kind.AUCTION_LEADER, lead, amounts ? lead : player + " takes the lead", Colors.GOLD, ItemIcons.of(a));
				} else {
					notify(Kind.AUCTION_BID, full, shown, Colors.GREEN, ItemIcons.of(a));
				}
			}
		} else {
			Game game = GameClassifier.guess(amount, config.lastGame, config);
			Bet bet = ledger.addPayment(player, amount, game, now);
			tx.betId = bet.id;
			// Jump to the new bet's tab if the open game tab has nothing waiting.
			Game open = QueueHud.Tab.parse(config.activeTab).game;
			if (open != null && open != game && ledger.pending(open).isEmpty()) {
				config.activeTab = QueueHud.Tab.of(game).name();
			}
			int place = ledger.pending(game).size();
			String text = "+" + Fmt.money(amount) + " " + player + " → " + game.displayName + " #" + place;
			notify(Kind.PAYMENT_RECEIVED, text, text, Colors.GREEN, ItemStack.EMPTY);
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

	/**
	 * "You paid X $Y": every outgoing /pay is a payout. If it exactly matches an owed bet or refund,
	 * that one is marked paid and the payout is linked to it; otherwise it's recorded unlinked.
	 */
	void recordPayout(String player, long amount, long now) {
		Bet bet = ledger.settleMatching(player, amount);
		Refund refund = bet == null ? auctions.settleMatchingRefund(player, amount) : null;
		Transaction.Source source = bet != null ? Transaction.Source.GAMBLE : refund != null ? Transaction.Source.AUCTION : Transaction.Source.OTHER;
		Transaction tx = finance.record(Transaction.Type.PAYOUT, source, player, amount, "", now, false);
		if (bet != null) {
			tx.betId = bet.id;
		}
		if (refund != null) {
			tx.auctionId = refund.auctionId;
			tx.note = "refund";
		}
		String text = "Sent " + Fmt.money(amount) + " to " + player + (bet != null ? " · payout settled" : refund != null ? " · refund settled" : "");
		// Settling a winner's payout is public game activity; other payments are your business.
		notify(Kind.PAYMENT_SENT, text, bet != null || refund != null ? "Paid " + player : null, Colors.accent(), ItemStack.EMPTY);
		save();
	}

	/** Stores the payer's UUID when they're in the tab list (only possible while they're online). */
	private void rememberUuid(String player) {
		var handler = MinecraftClient.getInstance().getNetworkHandler();
		if (handler == null) {
			return;
		}
		var entry = handler.getPlayerListEntry(player);
		if (entry != null && entry.getProfile() != null && entry.getProfile().id() != null) {
			players.setUuid(player, entry.getProfile().id().toString());
		}
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
		return startAuction(AuctionSetup.simple(item, durationSec, prizeCost));
	}

	public boolean startAuction(AuctionSetup setup) {
		if (auctions.isLive()) {
			return false;
		}
		Auction a = auctions.start(setup, System.currentTimeMillis());
		config.activeTab = QueueHud.Tab.AUCTION.name();
		String text = "Auction started: " + a.item + " · " + Fmt.clock(a.durationMs);
		notify(Kind.AUCTION_COMPLETED, text, text, Colors.accent(), ItemIcons.of(a));
		endingSoonAnnounced = 0;
		save();
		return true;
	}

	public void pauseAuction() {
		if (auctions.isLive()) {
			auctions.current.pause(System.currentTimeMillis());
			notify(Kind.AUCTION_ENDING, "Auction paused", "Auction paused", Colors.GOLD, ItemIcons.of(auctions.current));
			save();
		}
	}

	public void resumeAuction() {
		if (auctions.isLive()) {
			auctions.current.resume(System.currentTimeMillis());
			notify(Kind.AUCTION_ENDING, "Auction resumed", "Auction resumed", Colors.accent(), ItemIcons.of(auctions.current));
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
			String text = "Auction cancelled: " + a.rankings().size() + " refunds owed";
			notify(Kind.AUCTION_COMPLETED, text, "Auction cancelled", Colors.ORANGE, ItemIcons.of(a));
			lastResult = a;
			lastResultUntil = System.currentTimeMillis() + RESULT_MS;
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

	/** Seconds-left thresholds already announced for the live auction (bit 1 = setting, bit 2 = 10 s). */
	private int endingSoonAnnounced;
	private static final long RESULT_MS = 12_000;
	/** The auction that just ended, shown on the results card until {@link #lastResultUntil}. */
	Auction lastResult;
	long lastResultUntil;

	/** Called every client tick: ending-soon notices, and ends the auction when its timer runs out. */
	void tick() {
		if (!auctions.isLive()) {
			return;
		}
		long now = System.currentTimeMillis();
		Auction a = auctions.current;
		if (a.status == Auction.Status.RUNNING) {
			long left = a.remaining(now);
			int soon = config.auctionEndingSoonSec;
			if (soon > 10 && left <= soon * 1000L && left > 10_000 && (endingSoonAnnounced & 1) == 0) {
				endingSoonAnnounced |= 1;
				String t = a.item + " ends in " + soon + "s";
				notify(Kind.AUCTION_ENDING, t, t, Colors.ORANGE, ItemIcons.of(a));
			}
			if (soon > 0 && left <= 10_000 && left > 0 && (endingSoonAnnounced & 2) == 0) {
				endingSoonAnnounced |= 2;
				String t = "Last 10 seconds: " + a.item;
				notify(Kind.AUCTION_ENDING, t, t, Colors.RED, ItemIcons.of(a));
			}
			if (left > Math.max(soon, 10) * 1000L) {
				endingSoonAnnounced = 0; // extended: announce again later
			}
		}
		finished(auctions.tick(now, config));
	}

	/** The results card shows for a few seconds after an auction ends. */
	Auction recentResult() {
		return lastResult != null && System.currentTimeMillis() < lastResultUntil ? lastResult : null;
	}

	private void finished(Auction a) {
		if (a == null) {
			return;
		}
		if (a.winner != null && a.prizeCost > 0) {
			finance.record(Transaction.Type.EXPENSE, Transaction.Source.AUCTION, a.winner, a.prizeCost,
				"Prize: " + a.item, a.endedAt, false);
		}
		String full = a.winner != null
			? a.item + " won by " + a.winner + " · total " + Fmt.money(a.total())
			: a.item + " ended with no bids";
		String shown = a.winner != null && config.privacy.hideAuctionHud ? a.item + " won by " + a.winner : full;
		notify(Kind.AUCTION_COMPLETED, full, shown, Colors.GOLD, ItemIcons.of(a));
		lastResult = a;
		lastResultUntil = System.currentTimeMillis() + RESULT_MS;
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
			notify(Kind.SYSTEM, "Exported " + file.getFileName(), null, Colors.GREEN, ItemStack.EMPTY);
		} else {
			notify(Kind.SYSTEM, "Export failed: see the log", null, Colors.RED, ItemStack.EMPTY);
		}
	}

	// ---- notifications ----

	/** A private system pop-up: hidden while Streamer Mode hides toasts. */
	void notify(String text, int color) {
		notify(Kind.SYSTEM, text, null, color, ItemStack.EMPTY);
	}

	/**
	 * Shows a pop-up if its kind is switched on.
	 *
	 * @param publicText what may show while Streamer Mode hides private toasts; null shows nothing
	 */
	void notify(Kind kind, String text, String publicText, int color, ItemStack icon) {
		if (!config.notifications || !config.notices.isEnabled(kind)) {
			return;
		}
		long now = System.currentTimeMillis();
		long ms = config.notices.durationSec * 1000L * (kind == Kind.PAYOUT_DUE ? 2 : 1);
		toasts.add(new Toast(kind, text, publicText, color, now, now + ms, icon == null ? ItemStack.EMPTY : icon));
		while (toasts.size() > Math.max(1, Math.min(MAX_TOASTS, config.notices.maxVisible))) {
			toasts.remove(0);
		}
		Sounds.play(kind, config.notices);
	}

	/** A sample of each style, for the settings preview. */
	void previewNotifications() {
		long now = System.currentTimeMillis();
		toasts.clear();
		toasts.add(new Toast(Kind.PAYMENT_RECEIVED, "+$50m jimbo01 → Paper #2", "+$50m jimbo01 → Paper #2", Colors.GREEN, now, now + 5000, ItemStack.EMPTY));
		toasts.add(new Toast(Kind.AUCTION_LEADER, "BeanznBread takes the lead · $400m", "BeanznBread takes the lead", Colors.GOLD,
			now + 150, now + 5150, new ItemStack(net.minecraft.item.Items.SPAWNER)));
		toasts.add(new Toast(Kind.AUCTION_ENDING, "Spawner ends in 30s", "Spawner ends in 30s", Colors.ORANGE, now + 300, now + 5300, ItemStack.EMPTY));
		toasts.add(new Toast(Kind.SYSTEM, "Exported transactions.csv", null, Colors.accent(), now + 450, now + 5450, ItemStack.EMPTY));
		Sounds.play(Kind.AUCTION_LEADER, config.notices);
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
		announceNext(bet.game);
		save();
	}

	public void resolveTie(Bet bet) {
		resolveTie(bet, null, null);
	}

	public void resolveTie(Bet bet, Integer them, Integer me) {
		config.lastGame = bet.game;
		ledger.resolveTie(bet.id, them, me, config);
		announceNext(bet.game);
		save();
	}

	/** After a result, whoever is now #1 in that game is up. */
	private void announceNext(Game game) {
		List<Bet> queue = ledger.pending(game);
		if (!queue.isEmpty()) {
			Bet next = queue.get(0);
			String t = "Up next: " + next.player + " · " + game.displayName + " " + Fmt.money(next.amount);
			notify(Kind.GAME_STARTED, t, t, Colors.accent(), ItemStack.EMPTY);
		}
	}

	public void setScore(Bet bet, Integer them, Integer me) {
		ledger.setScore(bet.id, them, me);
		save();
	}

	// ---- calling a result (queue letters and hotkeys) ----

	/**
	 * W: Paper/Blackjack open the result window, where the host and viewer numbers decide the
	 * result (nothing is recorded until Confirm). Ely first confirms what the elytra costs.
	 */
	public void callWin(Bet bet) {
		if (bet.game == Game.ELY) {
			MinecraftClient.getInstance().setScreen(ScoreScreen.elytraPrice(this, bet));
			return;
		}
		MinecraftClient.getInstance().setScreen(ScoreScreen.result(this, bet, Outcome.PLAYER_WIN));
	}

	/** L: Paper/Blackjack open the result window; Ely is just a miss, recorded straight away. */
	public void callLoss(Bet bet) {
		if (bet.game.usesScore()) {
			MinecraftClient.getInstance().setScreen(ScoreScreen.result(this, bet, Outcome.PLAYER_LOSE));
			return;
		}
		resolve(bet, Outcome.PLAYER_LOSE, null, null);
		notify(Kind.GAME_COMPLETED, bet.player + " lost · you keep " + Fmt.money(bet.amount), bet.player + " lost", Colors.GREEN, ItemStack.EMPTY);
	}

	/**
	 * Confirm in the result window. With both numbers the numbers decide (same number = tie, the
	 * house wins); without them, the letter that was clicked does.
	 */
	void recordResult(Bet bet, Integer viewer, Integer host, Outcome called) {
		if (!bet.isPending()) {
			return;
		}
		if (viewer != null && host != null && ScoreJudge.isTie(bet.game, viewer, host)) {
			resolveTie(bet, viewer, host);
			notify(Kind.GAME_COMPLETED, bet.player + " tied · you keep " + Fmt.money(bet.amount), bet.player + " tied", Colors.GREEN, ItemStack.EMPTY);
			return;
		}
		Outcome outcome = viewer != null && host != null ? ScoreJudge.judge(bet.game, viewer, host) : called;
		resolve(bet, outcome, viewer, host);
		if (outcome == Outcome.PLAYER_WIN) {
			awaitingPayment(bet);
		} else {
			notify(Kind.GAME_COMPLETED, bet.player + " lost · you keep " + Fmt.money(bet.amount), bet.player + " lost", Colors.GREEN, ItemStack.EMPTY);
		}
	}

	/** T: a tie goes to you. No numbers needed. */
	public void callTie(Bet bet) {
		resolveTie(bet);
		notify(Kind.GAME_COMPLETED, bet.player + " tied · you keep " + Fmt.money(bet.amount), bet.player + " tied", Colors.GREEN, ItemStack.EMPTY);
	}

	/** Ely Arrows result: {@code won} elytras at {@code price} each (0 = they missed every shot). */
	void confirmElytraWin(Bet bet, long price, int won) {
		setElytraPrice(price);
		config.lastGame = bet.game;
		ledger.resolveEly(bet.id, won, config);
		announceNext(bet.game);
		save();
		if (won > 0) {
			awaitingPayment(bet);
		} else {
			notify(Kind.GAME_COMPLETED, bet.player + " missed · you keep " + Fmt.money(bet.amount), bet.player + " missed", Colors.GREEN, ItemStack.EMPTY);
		}
	}

	void awaitingPayment(Bet bet) {
		String text = bet.payoutIsElytra() ? "Buy " + Fmt.owedLabel(bet) + " for " + bet.player + " · put in their /order"
			: "Awaiting manual payment of " + Fmt.owedLabel(bet) + " to " + bet.player;
		notify(Kind.PAYOUT_DUE, text, text, Colors.GOLD, ItemStack.EMPTY);
	}

	/** Hotkeys act on #1 in the open game tab (or the oldest bet overall on other tabs), without the score picker. */
	public void resolveFirst(Outcome outcome) {
		Game game = QueueHud.Tab.parse(config.activeTab).game;
		List<Bet> queue = game != null ? ledger.pending(game) : ledger.pending();
		if (queue.isEmpty()) {
			return;
		}
		Bet bet = queue.get(0);
		if (outcome == Outcome.PLAYER_WIN && bet.game == Game.ELY && (config.elytraPrice <= 0 || config.elyShots(bet.amount) > 1)) {
			// No price yet (it would count as costing nothing), or several shots: ask how many were won.
			MinecraftClient.getInstance().setScreen(ScoreScreen.elytraPrice(this, bet));
			return;
		}
		resolve(bet, outcome, null, null);
		if (outcome == Outcome.PLAYER_WIN) {
			awaitingPayment(bet);
		} else {
			notify(Kind.GAME_COMPLETED, bet.player + " lost · you keep " + Fmt.money(bet.amount), bet.player + " lost", Colors.GREEN, ItemStack.EMPTY);
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

	/** All-time profit across games, auctions and everything else (see {@link com.donutgamble.core.Profit}). */
	public long totalProfit() {
		return com.donutgamble.core.Profit.net(finance, ledger, auctions, 0);
	}

	/** Profit since the session started. */
	public long sessionProfit() {
		return com.donutgamble.core.Profit.net(finance, ledger, auctions, ledger.sessionStart);
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
		com.donutgamble.core.HudLayout.Slot s = config.hud.slots.get("queue.chat");
		if (s != null) {
			s.visible = !s.visible;
		}
		save();
	}
}
