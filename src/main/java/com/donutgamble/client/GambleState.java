package com.donutgamble.client;

import com.donutgamble.core.Bet;
import com.donutgamble.core.GambleConfig;
import com.donutgamble.core.Game;
import com.donutgamble.core.GameClassifier;
import com.donutgamble.core.Ledger;
import com.donutgamble.core.Outcome;
import com.donutgamble.core.PaymentDetector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

import java.util.List;

/** The mod's live state and every action the queue and hotkeys can take. Saves after each change. */
public final class GambleState {
	private final Storage storage = new Storage();
	public final GambleConfig config;
	public final Ledger ledger;
	private final PaymentDetector detector;

	GambleState() {
		config = storage.loadConfig();
		ledger = storage.loadLedger();
		detector = new PaymentDetector(config.extraPayPatterns);
		Colors.theme = Colors.Theme.parse(config.theme);
		save();
	}

	public void save() {
		storage.save(config, ledger);
	}

	void onServerMessage(String message) {
		detector.detectOutgoing(message).ifPresent(p -> {
			if (ledger.settleByPayment(p.player(), p.amount())) {
				save();
			}
		});
		detector.detect(message).ifPresent(p -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null && client.player.getName().getString().equalsIgnoreCase(p.player())) {
				return;
			}
			Game game = GameClassifier.guess(p.amount(), config.lastGame, config);
			ledger.addPayment(p.player(), p.amount(), game, System.currentTimeMillis());
			// Jump to the new bet's tab if the open game tab has nothing waiting.
			Game open = QueueOverlay.Tab.parse(config.activeTab).game;
			if (open != null && open != game && ledger.pending(open).isEmpty()) {
				config.activeTab = QueueOverlay.Tab.of(game).name();
			}
			save();
		});
	}

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

	/** Hotkeys act on #1 in the open game tab (or the oldest bet overall on Owe/Stats). */
	public void resolveFirst(Outcome outcome) {
		Game game = QueueOverlay.Tab.parse(config.activeTab).game;
		List<Bet> queue = game != null ? ledger.pending(game) : ledger.pending();
		if (!queue.isEmpty()) {
			resolve(queue.get(0), outcome, null, null);
		}
	}

	public void settle(Bet bet) {
		ledger.settle(bet.id);
		save();
	}

	public void dismiss(Bet bet) {
		ledger.remove(bet.id);
		save();
	}

	/** Puts "/pay name amount" in the chat box; the streamer presses Enter to send it. */
	public void prefillPay(Bet bet) {
		MinecraftClient.getInstance().setScreen(new ChatScreen("/pay " + bet.player + " " + bet.payout, false));
	}

	public void newSession() {
		ledger.startSession(System.currentTimeMillis());
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

	public void toggleHud() {
		config.hudVisible = !config.hudVisible;
		save();
	}
}
