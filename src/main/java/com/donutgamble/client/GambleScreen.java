package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Game;
import com.donutgamble.core.Outcome;
import com.donutgamble.core.ScoreJudge;
import com.donutgamble.core.Stats;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The Insert panel: pending bets, who you owe, elytra price and stats. */
final class GambleScreen extends Screen {
	private static final int PAD = 10;
	private static final int BET_H = 38;
	private static final int OWED_H = 30;
	private static final int GAP = 4;

	/** Half-typed scores survive the panel being closed and reopened. */
	private static final Map<Long, String[]> SCORE_DRAFTS = new HashMap<>();

	private final GambleState state;
	private final Map<TextFieldWidget, Bet> scoreFieldBets = new IdentityHashMap<>();

	private int px, py, pw, ph, colTop, leftW, rightX, rightW;
	private List<Bet> shownPending = List.of();
	private List<Bet> shownOwed = List.of();
	private int hiddenPending, hiddenOwed;

	GambleScreen(GambleState state) {
		super(Text.literal("Donut Gamble"));
		this.state = state;
	}

	@Override
	protected void init() {
		scoreFieldBets.clear();
		pw = Math.min(width - 16, 460);
		ph = Math.min(height - 16, 290);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		colTop = py + 64;
		leftW = (pw - 2 * PAD - 8) * 3 / 5;
		rightX = px + PAD + leftW + 8;
		rightW = px + pw - PAD - rightX;

		int newSessionX = px + pw - PAD - 64;
		addDrawableChild(FlatButton.outline(newSessionX, py + 8, 64, 14, "New Session", false, () -> {
			state.newSession();
			clearAndInit();
		}));
		TextFieldWidget elytra = new TextFieldWidget(textRenderer, newSessionX - 6 - 52, py + 8, 52, 14, Text.literal("Elytra price"));
		elytra.setMaxLength(12);
		elytra.setText(state.config.elytraPrice > 0 ? AmountParser.format(state.config.elytraPrice) : "");
		elytra.setPlaceholder(Text.literal("set"));
		elytra.setTextPredicate(s -> s.matches("[0-9.,kKmMbB$]*"));
		elytra.setChangedListener(s -> state.setElytraPrice(AmountParser.parseOrZero(s)));
		addDrawableChild(elytra);

		int listBottom = py + ph - PAD;
		List<Bet> pending = state.ledger.pending();
		int fit = Math.max(0, (listBottom - (colTop + 12) + GAP) / (BET_H + GAP));
		shownPending = pending.subList(0, Math.min(fit, pending.size()));
		hiddenPending = pending.size() - shownPending.size();
		int y = colTop + 12;
		for (Bet b : shownPending) {
			addBetCard(b, px + PAD, y, leftW);
			y += BET_H + GAP;
		}

		List<Bet> owed = state.ledger.owed();
		int fitOwed = Math.max(0, (listBottom - (colTop + 12) + GAP) / (OWED_H + GAP));
		shownOwed = owed.subList(0, Math.min(fitOwed, owed.size()));
		hiddenOwed = owed.size() - shownOwed.size();
		y = colTop + 12;
		for (Bet b : shownOwed) {
			addOwedCard(b, rightX, y, rightW);
			y += OWED_H + GAP;
		}
	}

	private void addBetCard(Bet b, int x, int y, int w) {
		int right = x + w - 6;
		addDrawableChild(FlatButton.outline(right - 10, y + 4, 10, 12, "x", false, () -> {
			SCORE_DRAFTS.remove(b.id);
			state.dismiss(b);
			clearAndInit();
		}));
		int cx = right - 14;
		Game[] chips = {Game.ELY, Game.BLACKJACK, Game.PAPER};
		String[] labels = {"Ely", "Blackjack", "Paper"};
		for (int i = 0; i < chips.length; i++) {
			Game g = chips[i];
			int cw = textRenderer.getWidth(labels[i]) + 10;
			cx -= cw;
			addDrawableChild(FlatButton.outline(cx, y + 4, cw, 12, labels[i], b.game == g, () -> {
				state.setGame(b, g);
				clearAndInit();
			}));
			cx -= 3;
		}

		int row = y + 20;
		int accent = state.config.accentColor;
		if (b.game.usesScore()) {
			addDrawableChild(FlatButton.solid(right - 30, row, 30, 14, "LOSE", accent, () -> decide(b, Outcome.PLAYER_LOSE)));
			addDrawableChild(FlatButton.solid(right - 62, row, 30, 14, "WIN", Colors.RED, () -> decide(b, Outcome.PLAYER_WIN)));
			FlatButton ok = FlatButton.outline(right - 84, row, 20, 14, "OK", true, () -> confirmScore(b));
			ok.active = score(b) != null;
			addDrawableChild(ok);

			String[] draft = SCORE_DRAFTS.computeIfAbsent(b.id, k -> new String[]{"", ""});
			addScoreField(b, draft, 0, x + 32, row, ok);
			addScoreField(b, draft, 1, x + 62, row, ok);
		} else {
			addDrawableChild(FlatButton.solid(right - 32, row, 32, 14, "MISS", accent, () -> decide(b, Outcome.PLAYER_LOSE)));
			addDrawableChild(FlatButton.solid(right - 64, row, 30, 14, "HIT", Colors.RED, () -> decide(b, Outcome.PLAYER_WIN)));
		}
	}

	private void addScoreField(Bet b, String[] draft, int index, int x, int y, FlatButton ok) {
		TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, 20, 14, Text.literal(index == 0 ? "Their score" : "My score"));
		field.setMaxLength(2);
		field.setText(draft[index]);
		field.setTextPredicate(s -> s.matches("\\d{0,2}"));
		field.setChangedListener(s -> {
			draft[index] = s;
			ok.active = score(b) != null;
		});
		scoreFieldBets.put(field, b);
		addDrawableChild(field);
	}

	private void addOwedCard(Bet b, int x, int y, int w) {
		int right = x + w - 6;
		if (b.payoutIsElytra()) {
			addDrawableChild(FlatButton.solid(right - 52, y + 8, 52, 14, "Delivered", Colors.GOLD, () -> {
				state.settle(b);
				clearAndInit();
			}));
		} else {
			addDrawableChild(FlatButton.solid(right - 28, y + 8, 28, 14, "Paid", Colors.GOLD, () -> {
				state.settle(b);
				clearAndInit();
			}));
			addDrawableChild(FlatButton.outline(right - 56, y + 8, 26, 14, "Pay", true, () -> state.prefillPay(b)));
		}
	}

	static void draftScore(long betId, String them, String me) {
		SCORE_DRAFTS.put(betId, new String[]{them, me});
	}

	/** Both scores typed in → {them, me}; else null. */
	private static int[] score(Bet b) {
		String[] d = SCORE_DRAFTS.get(b.id);
		if (d == null || d[0].isEmpty() || d[1].isEmpty()) {
			return null;
		}
		return new int[]{Integer.parseInt(d[0]), Integer.parseInt(d[1])};
	}

	private void confirmScore(Bet b) {
		int[] s = score(b);
		if (s != null) {
			finish(b, ScoreJudge.judge(b.game, s[0], s[1]), s);
		}
	}

	/** WIN/LOSE buttons override the score but still record it if one was typed. */
	private void decide(Bet b, Outcome outcome) {
		finish(b, outcome, b.game.usesScore() ? score(b) : null);
	}

	private void finish(Bet b, Outcome outcome, int[] s) {
		state.resolve(b, outcome, s == null ? null : s[0], s == null ? null : s[1]);
		SCORE_DRAFTS.remove(b.id);
		clearAndInit();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
			if (getFocused() instanceof TextFieldWidget f && scoreFieldBets.containsKey(f)) {
				confirmScore(scoreFieldBets.get(f));
				return true;
			}
		}
		if (DonutGambleClient.OPEN_PANEL.matchesKey(input)) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		int accent = state.config.accentColor;
		Draw.roundedBordered(ctx, px, py, px + pw, py + ph, Colors.PANEL, Colors.BORDER);

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(px + PAD, py + 8);
		ctx.getMatrices().scale(1.5f, 1.5f);
		ctx.drawText(textRenderer, "Donut Gamble", 0, 0, Colors.TEXT, true);
		ctx.getMatrices().popMatrix();
		ctx.drawText(textRenderer, "Session " + HudRenderer.duration(System.currentTimeMillis() - state.ledger.sessionStart) + " · Insert to close",
			px + PAD, py + 22, Colors.MUTED, false);
		String ely = "Elytra price";
		ctx.drawText(textRenderer, ely, px + pw - PAD - 64 - 6 - 52 - 5 - textRenderer.getWidth(ely), py + 11, Colors.MUTED, false);

		Stats session = state.ledger.sessionStats();
		Stats life = state.ledger.lifetimeStats();
		int tileW = (pw - 2 * PAD - 3 * 6) / 4;
		String[][] tiles = {
			{"NET PROFIT", AmountParser.formatSigned(session.net())},
			{"WAGERED", AmountParser.format(session.wagered())},
			{"WON / LOST", session.playerWins() + " / " + session.playerLosses()},
			{"LIFETIME", AmountParser.formatSigned(life.net())},
		};
		int[] tileColors = {Colors.profit(session.net(), accent), Colors.TEXT, Colors.TEXT, Colors.profit(life.net(), accent)};
		for (int i = 0; i < tiles.length; i++) {
			int tx = px + PAD + i * (tileW + 6);
			Draw.rounded(ctx, tx, py + 32, tx + tileW, py + 56, Colors.TILE);
			ctx.drawText(textRenderer, tiles[i][0], tx + 5, py + 35, Colors.MUTED, false);
			ctx.drawText(textRenderer, tiles[i][1], tx + 5, py + 46, tileColors[i], false);
		}

		ctx.drawText(textRenderer, "PENDING BETS · " + state.ledger.pending().size(), px + PAD, colTop, Colors.MUTED, false);
		ctx.drawText(textRenderer, "YOU OWE · " + state.ledger.owed().size(), rightX, colTop, Colors.MUTED, false);

		int y = colTop + 12;
		for (Bet b : shownPending) {
			renderBetCard(ctx, b, px + PAD, y, leftW, accent);
			y += BET_H + GAP;
		}
		if (state.ledger.pending().isEmpty()) {
			ctx.drawText(textRenderer, "Waiting for someone to /pay you…", px + PAD, y, Colors.MUTED, false);
		} else if (hiddenPending > 0) {
			ctx.drawText(textRenderer, "+" + hiddenPending + " more", px + PAD, y, Colors.MUTED, false);
		}

		y = colTop + 12;
		for (Bet b : shownOwed) {
			Draw.roundedBordered(ctx, rightX, y, rightX + rightW, y + OWED_H, Colors.OWED_CARD, Colors.OWED_BORDER);
			ctx.drawText(textRenderer, b.player, rightX + 6, y + 5, Colors.TEXT, false);
			String detail = b.payoutIsElytra() ? "1 Elytra" : GambleState.owedLabel(b) + " · " + b.game.displayName;
			ctx.drawText(textRenderer, textRenderer.trimToWidth(detail, rightW - 66), rightX + 6, y + 17, Colors.GOLD, false);
			y += OWED_H + GAP;
		}
		if (hiddenOwed > 0) {
			ctx.drawText(textRenderer, "+" + hiddenOwed + " more", rightX, y, Colors.MUTED, false);
		}
	}

	private void renderBetCard(DrawContext ctx, Bet b, int x, int y, int w, int accent) {
		Draw.roundedBordered(ctx, x, y, x + w, y + BET_H, Colors.CARD, Colors.BORDER);
		ctx.drawText(textRenderer, b.player, x + 6, y + 6, Colors.TEXT, false);
		ctx.drawText(textRenderer, AmountParser.format(b.amount), x + 10 + textRenderer.getWidth(b.player), y + 6, Colors.GOLD, false);
		int row = y + 23;
		if (b.game.usesScore()) {
			ctx.drawText(textRenderer, "Them", x + 6, row, Colors.MUTED, false);
			ctx.drawText(textRenderer, "–", x + 55, row, Colors.MUTED, false);
			ctx.drawText(textRenderer, "Me", x + 85, row, Colors.MUTED, false);
			int[] s = score(b);
			if (s != null) {
				boolean playerWins = ScoreJudge.judge(b.game, s[0], s[1]) == Outcome.PLAYER_WIN;
				long cost = Math.round(b.amount * state.config.winMultiplier) - b.amount;
				String preview = playerWins ? "-" + AmountParser.format(cost) : "+" + AmountParser.format(b.amount);
				int maxW = (x + w - 6 - 86) - (x + 102);
				ctx.drawText(textRenderer, textRenderer.trimToWidth(preview, maxW), x + 102, row, playerWins ? Colors.RED : accent, false);
			}
		} else {
			String q = state.config.elytraPrice > 0 ? "Arrow hit the frame?" : "Set elytra price ↑";
			ctx.drawText(textRenderer, q, x + 6, row, state.config.elytraPrice > 0 ? Colors.MUTED : Colors.GOLD, false);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
