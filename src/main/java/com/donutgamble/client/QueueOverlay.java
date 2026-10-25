package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Game;
import com.donutgamble.core.Outcome;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The on-screen queue:
 * <pre>
 * Profit +565M
 * > 1 Steve   $250m  BJ     W L T
 * > 2 xNova   $120m  Ely    W L
 * Owe Alex_   $90m          P D
 * </pre>
 * Letters are clickable while chat or the Insert screen is open.
 */
final class QueueOverlay {
	private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final int PAD = 4;
	private static final int ROW = 11;
	private static final int LETTER = 10;
	private static final int MAX_ROWS = 10;

	private final GambleState state;
	private final List<Hit> hits = new ArrayList<>();
	/** Where the box was last drawn, in GUI coordinates (used for hit-testing). */
	private float originX, originY, scale = 1;

	QueueOverlay(GambleState state) {
		this.state = state;
	}

	void render(DrawContext ctx, RenderTickCounter tick) {
		MinecraftClient client = MinecraftClient.getInstance();
		hits.clear();
		if (!state.config.hudVisible || client.options.hudHidden) {
			return;
		}
		originX = state.config.hudX;
		originY = state.config.hudY;
		scale = state.config.hudScale;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(originX, originY);
		ctx.getMatrices().scale(scale, scale);
		draw(ctx, client.textRenderer);
		ctx.getMatrices().popMatrix();
	}

	/** Left click runs a letter; right click on a name removes a payment that wasn't a bet. */
	boolean click(double mouseX, double mouseY, int button) {
		double lx = (mouseX - originX) / scale;
		double ly = (mouseY - originY) / scale;
		for (Hit h : hits) {
			if (h.contains(lx, ly)) {
				Runnable r = button == 1 ? h.right() : h.left();
				if (r != null) {
					r.run();
					return true;
				}
			}
		}
		return false;
	}

	private void draw(DrawContext ctx, TextRenderer tr) {
		List<Bet> queue = state.ledger.pending();
		List<Bet> owed = state.ledger.owed();
		List<Bet> shownQueue = queue.subList(0, Math.min(MAX_ROWS, queue.size()));
		List<Bet> shownOwed = owed.subList(0, Math.min(MAX_ROWS, owed.size()));

		int nameW = tr.getWidth("Owe ");
		int amountW = 0;
		for (int i = 0; i < shownQueue.size(); i++) {
			Bet b = shownQueue.get(i);
			nameW = Math.max(nameW, tr.getWidth("> " + (i + 1) + " " + b.player));
			amountW = Math.max(amountW, tr.getWidth(money(b.amount)));
		}
		for (Bet b : shownOwed) {
			nameW = Math.max(nameW, tr.getWidth("Owe " + b.player));
			amountW = Math.max(amountW, tr.getWidth(owedLabel(b)));
		}
		int amountX = PAD + nameW + 8;
		int gameX = amountX + amountW + 8;
		int lettersX = gameX + tr.getWidth("Paper") + 6;
		int width = Math.max(lettersX + 3 * LETTER + PAD - 4, tr.getWidth("> waiting for /pay...") + 2 * PAD);

		int moreQueue = queue.size() - shownQueue.size();
		int moreOwed = owed.size() - shownOwed.size();
		int rows = 1 + Math.max(1, shownQueue.size()) + shownOwed.size() + (moreQueue > 0 ? 1 : 0) + (moreOwed > 0 ? 1 : 0);
		int height = PAD * 2 + rows * ROW - 2;
		Draw.box(ctx, 0, 0, width, height, Colors.BG);

		long net = state.ledger.sessionStats().net();
		int y = PAD;
		ctx.drawTextWithShadow(tr, "Profit ", PAD, y, Colors.text());
		ctx.drawTextWithShadow(tr, AmountParser.formatSigned(net), PAD + tr.getWidth("Profit "), y, Colors.profit(net));
		hits.add(new Hit(PAD, y - 1, width - 2 * PAD, ROW, state::nextTheme, state::newSession));
		y += ROW;

		if (shownQueue.isEmpty()) {
			ctx.drawTextWithShadow(tr, "> waiting for /pay...", PAD, y, Colors.muted());
			y += ROW;
		}
		for (int i = 0; i < shownQueue.size(); i++) {
			Bet b = shownQueue.get(i);
			String name = "> " + (i + 1) + " " + b.player;
			ctx.drawTextWithShadow(tr, name, PAD, y, Colors.text());
			hits.add(new Hit(PAD, y - 1, tr.getWidth(name), ROW, null, () -> state.dismiss(b)));
			ctx.drawTextWithShadow(tr, money(b.amount), amountX, y, Colors.text());

			String game = shortName(b.game);
			ctx.drawTextWithShadow(tr, game, gameX, y, Colors.muted());
			hits.add(new Hit(gameX, y - 1, tr.getWidth("Paper"), ROW, () -> state.cycleGame(b), null));

			int lx = lettersX;
			lx = letter(ctx, tr, lx, y, "W", Colors.GREEN, () -> win(b));
			lx = letter(ctx, tr, lx, y, "L", Colors.RED, () -> lose(b));
			if (b.game.usesScore()) {
				letter(ctx, tr, lx, y, "T", Colors.GREY, () -> tie(b));
			}
			y += ROW;
		}

		if (moreQueue > 0) {
			ctx.drawTextWithShadow(tr, "  +" + moreQueue + " more waiting", PAD, y, Colors.muted());
			y += ROW;
		}

		for (Bet b : shownOwed) {
			ctx.drawTextWithShadow(tr, "Owe ", PAD, y, Colors.muted());
			ctx.drawTextWithShadow(tr, b.player, PAD + tr.getWidth("Owe "), y, Colors.text());
			ctx.drawTextWithShadow(tr, owedLabel(b), amountX, y, Colors.GOLD);
			int lx = lettersX;
			if (!b.payoutIsElytra()) {
				lx = letter(ctx, tr, lx, y, "P", Colors.GOLD, () -> state.prefillPay(b));
			}
			letter(ctx, tr, lx, y, "D", Colors.GREEN, () -> state.settle(b));
			y += ROW;
		}
		if (moreOwed > 0) {
			ctx.drawTextWithShadow(tr, "  +" + moreOwed + " more owed", PAD, y, Colors.muted());
		}
	}

	private int letter(DrawContext ctx, TextRenderer tr, int x, int y, String text, int color, Runnable action) {
		ctx.drawTextWithShadow(tr, text, x, y, color);
		hits.add(new Hit(x - 2, y - 2, LETTER, ROW, action, null));
		return x + LETTER;
	}

	private void win(Bet b) {
		if (b.game == Game.ELY) {
			MinecraftClient.getInstance().setScreen(ScoreScreen.elytraPrice(state, b));
		} else {
			state.resolve(b, Outcome.PLAYER_WIN, null, null);
			MinecraftClient.getInstance().setScreen(ScoreScreen.score(state, b, "WON"));
		}
	}

	private void lose(Bet b) {
		state.resolve(b, Outcome.PLAYER_LOSE, null, null);
		if (b.game.usesScore()) {
			MinecraftClient.getInstance().setScreen(ScoreScreen.score(state, b, "LOST"));
		}
	}

	private void tie(Bet b) {
		state.resolveTie(b);
		MinecraftClient.getInstance().setScreen(ScoreScreen.score(state, b, "TIED"));
	}

	private static String shortName(Game g) {
		return switch (g) {
			case PAPER -> "Paper";
			case BLACKJACK -> "BJ";
			case ELY -> "Ely";
		};
	}

	/** "$50m" like Donut SMP's own lists. */
	static String money(long amount) {
		return "$" + AmountParser.format(amount).toLowerCase(Locale.ROOT);
	}

	static String owedLabel(Bet b) {
		return b.payoutIsElytra() ? "1 Elytra" : money(b.payout);
	}
}
