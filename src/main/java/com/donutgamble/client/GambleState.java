package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
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

/** The mod's live state and every action the GUI and hotkeys can take. Saves after each change. */
public final class GambleState {
	public record Toast(String text, int color, long until) {
	}

	private static final long TOAST_MS = 4000;

	private final Storage storage = new Storage();
	public final GambleConfig config;
	public final Ledger ledger;
	private PaymentDetector detector;
	private Toast toast;

	GambleState() {
		config = storage.loadConfig();
		ledger = storage.loadLedger();
		detector = new PaymentDetector(config.payPatterns);
		save();
	}

	public void save() {
		storage.save(config, ledger);
	}

	void onServerMessage(String message) {
		detector.detect(message).ifPresent(p -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null && client.player.getName().getString().equalsIgnoreCase(p.player())) {
				return;
			}
			Game game = GameClassifier.guess(p.amount(), config.lastGame, config);
			ledger.addPayment(p.player(), p.amount(), game, System.currentTimeMillis());
			showToast("New bet: " + p.player() + " paid " + AmountParser.format(p.amount()) + " (" + game.displayName + ")", Colors.GOLD);
			save();
		});
	}

	public void setGame(Bet bet, Game game) {
		ledger.setGame(bet.id, game);
		save();
	}

	public void resolve(Bet bet, Outcome outcome, Integer them, Integer me) {
		config.lastGame = bet.game;
		ledger.resolve(bet.id, outcome, them, me, config);
		if (outcome == Outcome.PLAYER_WIN) {
			showToast("You owe " + bet.player + " " + owedLabel(bet) + (bet.payoutIsElytra() ? " · fill their order" : ""), Colors.GOLD);
		} else {
			showToast(bet.player + " lost · " + AmountParser.formatSigned(bet.houseNet), config.accentColor);
		}
		save();
	}

	public void resolveNewest(Outcome outcome) {
		List<Bet> pending = ledger.pending();
		if (!pending.isEmpty()) {
			resolve(pending.get(0), outcome, null, null);
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
		showToast("New session started", config.accentColor);
		save();
	}

	public void setElytraPrice(long price) {
		config.elytraPrice = price;
		save();
	}

	public void toggleHud() {
		config.hudVisible = !config.hudVisible;
		save();
	}

	public static String owedLabel(Bet bet) {
		return bet.payoutIsElytra() ? "1 Elytra" : AmountParser.format(bet.payout);
	}

	private void showToast(String text, int color) {
		toast = new Toast(text, color, System.currentTimeMillis() + TOAST_MS);
	}

	public Toast activeToast() {
		return toast != null && toast.until() > System.currentTimeMillis() ? toast : null;
	}
}
