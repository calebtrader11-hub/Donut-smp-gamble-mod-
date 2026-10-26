package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Game;
import com.donutgamble.core.Refund;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The queue as chat-style lines, bottom-left:
 * <pre>
 *  > 1 jimbo01 paid you $ 50M - Paper [W] [L] [T]
 *  > Kira won 1 Elytra - [PAY] [DONE]
 * </pre>
 * Click the letters with chat or Insert open. The game tag switches the game; right-click a name
 * if the payment wasn't a bet.
 */
final class ChatQueueHud extends HudElement {
	private static final int LINE = 11;
	private static final int BG = 0x80000000;

	/** One line: pieces of text, each with a color and an optional click. */
	private record Piece(String text, int color, Runnable left, Runnable right) {
	}

	private final GambleState state;

	ChatQueueHud(GambleState state) {
		super("queue.chat", "Queue (chat style)");
		this.state = state;
	}

	private int maxLines() {
		return compact ? 4 : 8;
	}

	private static String money(long amount) {
		return AmountParser.format(amount).toUpperCase(Locale.ROOT);
	}

	private static String tag(Game g) {
		return g == Game.BLACKJACK ? "BJ" : g == Game.ELY ? "Ely" : "Paper";
	}

	private List<List<Piece>> lines(boolean editing) {
		List<List<Piece>> out = new ArrayList<>();
		List<Bet> queue = state.ledger.pending();
		int n = 0;
		for (Bet b : queue) {
			if (out.size() >= maxLines()) {
				break;
			}
			n++;
			List<Piece> l = new ArrayList<>();
			l.add(new Piece("> " + n + " ", Colors.GREY, null, null));
			l.add(new Piece(b.player, 0xFFFFFFFF, null, () -> state.dismiss(b)));
			l.add(new Piece(" paid you ", Colors.GREY, null, null));
			l.add(new Piece("$ ", Colors.GREEN, null, null));
			l.add(new Piece(money(b.amount), 0xFFFFFFFF, null, null));
			l.add(new Piece(" - ", Colors.GREY, null, null));
			String tag = b.game == Game.ELY ? "Ely " + state.config.elyShots(b.amount) + "x" : tag(b.game);
			l.add(new Piece(tag, Colors.muted(), () -> state.cycleGame(b), null));
			l.add(new Piece(" ", 0, null, null));
			l.add(new Piece("[W]", Colors.GREEN, () -> state.callWin(b), null));
			l.add(new Piece(" ", 0, null, null));
			l.add(new Piece("[L]", Colors.RED, () -> state.callLoss(b), null));
			if (b.game.usesScore()) {
				l.add(new Piece(" ", 0, null, null));
				l.add(new Piece("[T]", Colors.GREY, () -> state.callTie(b), null));
			}
			out.add(l);
		}
		int owed = 0;
		for (Bet b : state.ledger.owed()) {
			if (out.size() < maxLines()) {
				// Elytras are bought and placed in their /order, not /paid: only DONE.
				out.add(owedLine(b.player, Fmt.owedLabel(b), b.payoutIsElytra() ? null : () -> state.prefillPay(b), () -> state.settle(b)));
			} else {
				owed++;
			}
		}
		for (Refund r : state.auctions.openRefunds()) {
			if (out.size() < maxLines()) {
				out.add(owedLine(r.player, "refund $ " + money(r.amount), () -> state.prefillPay(r.player, r.amount), () -> state.settleRefund(r)));
			} else {
				owed++;
			}
		}
		int more = queue.size() - n + owed;
		if (more > 0) {
			out.add(List.of(new Piece("> +" + more + " more", Colors.GREY, null, null)));
		}
		if (out.isEmpty() && editing) {
			out.add(List.of(new Piece("> 1 ", Colors.GREY, null, null), new Piece("jimbo01", 0xFFFFFFFF, null, null),
				new Piece(" paid you ", Colors.GREY, null, null), new Piece("$ ", Colors.GREEN, null, null),
				new Piece("50M", 0xFFFFFFFF, null, null), new Piece(" - Paper ", Colors.GREY, null, null),
				new Piece("[W] ", Colors.GREEN, null, null), new Piece("[L] ", Colors.RED, null, null), new Piece("[T]", Colors.GREY, null, null)));
		}
		return out;
	}

	private List<Piece> owedLine(String player, String what, Runnable pay, Runnable done) {
		List<Piece> l = new ArrayList<>();
		l.add(new Piece("> ", Colors.GREY, null, null));
		l.add(new Piece(player, Colors.GOLD, null, null));
		l.add(new Piece(" is owed ", Colors.GREY, null, null));
		l.add(new Piece(what, Colors.GOLD, null, null));
		l.add(new Piece(" - ", Colors.GREY, null, null));
		if (pay != null) {
			l.add(new Piece("[PAY]", Colors.GREEN, pay, null));
			l.add(new Piece(" ", 0, null, null));
		}
		l.add(new Piece("[DONE]", Colors.GREY, done, null));
		return l;
	}

	private static int width(TextRenderer tr, List<Piece> line) {
		int w = 0;
		for (Piece p : line) {
			w += tr.getWidth(p.text());
		}
		return w;
	}

	@Override
	boolean hasContent() {
		return !state.ledger.pending().isEmpty() || !state.ledger.owed().isEmpty() || !state.auctions.openRefunds().isEmpty();
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		List<List<Piece>> lines = lines(editing);
		int w = 40;
		for (List<Piece> l : lines) {
			w = Math.max(w, width(tr, l) + 4);
		}
		return new int[]{w, Math.max(1, lines.size()) * LINE};
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{2, Math.max(6, screenH - h - 48)};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		List<List<Piece>> lines = lines(editing);
		int y = 0;
		for (List<Piece> line : lines) {
			int lw = width(tr, line) + 4;
			ctx.fill(0, y, lw, y + LINE, Colors.fade(BG, Paint.hudOpacity));
			int x = 2;
			for (Piece p : line) {
				int pw = tr.getWidth(p.text());
				boolean clickable = p.left() != null || p.right() != null;
				boolean over = clickable && !editing && regions.hovered(mouseX, mouseY, x - 1, y, pw + 2, LINE);
				if (over) {
					ctx.fill(x - 1, y, x + pw + 1, y + LINE, Colors.alpha(p.color(), 0x40));
				}
				if (!p.text().isBlank()) {
					Paint.hudText(ctx, tr, p.text(), x, y + 2, over ? 0xFFFFFFFF : p.color());
				}
				if (clickable && !editing) {
					regions.add(x - 1, y, pw + 2, LINE, p.left(), p.right());
				}
				x += pw;
			}
			y += LINE;
		}
	}
}
