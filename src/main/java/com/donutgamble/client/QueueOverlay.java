package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Game;
import com.donutgamble.core.Outcome;
import com.donutgamble.core.Stats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The on-screen queue, split into tabs:
 * <pre>
 * [Paper 3] BJ 1  Ely 1  Owe 2  Stats
 * Paper profit +420M
 * > 1 RazorBroGaming  $50m   W L T  →BJ
 * > 2 jimbo01         $75m   W L T  →BJ
 * </pre>
 * Tabs and letters are clickable while chat or the Insert screen is open.
 */
final class QueueOverlay {
	enum Tab {
		PAPER(Game.PAPER, "Paper"),
		BLACKJACK(Game.BLACKJACK, "BJ"),
		ELY(Game.ELY, "Ely"),
		OWE(null, "Owe"),
		STATS(null, "Stats");

		final Game game;
		final String label;

		Tab(Game game, String label) {
			this.game = game;
			this.label = label;
		}

		static Tab of(Game game) {
			for (Tab t : values()) {
				if (t.game == game) {
					return t;
				}
			}
			return PAPER;
		}

		static Tab parse(String name) {
			try {
				return valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException | NullPointerException e) {
				return PAPER;
			}
		}
	}

	private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final int PAD = 4;
	private static final int ROW = 11;
	private static final int LETTER = 10;
	private static final int TAB_GAP = 6;
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

	/** Left click runs a tab or letter; right click on a name removes a payment that wasn't a bet. */
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

	private Tab tab() {
		return Tab.parse(state.config.activeTab);
	}

	private void draw(DrawContext ctx, TextRenderer tr) {
		Tab tab = tab();
		int width = Math.max(tabsWidth(tr), contentWidth(tr, tab));
		int rows = 1 + contentRows(tab);
		Draw.box(ctx, 0, 0, width, PAD * 2 + rows * ROW, Colors.BG);

		drawTabs(ctx, tr, tab);
		int y = PAD + ROW + 1;
		if (tab.game != null) {
			drawGame(ctx, tr, tab.game, y, width);
		} else if (tab == Tab.OWE) {
			drawOwe(ctx, tr, y);
		} else {
			drawStats(ctx, tr, y, width);
		}
	}

	// ---- tabs ----

	private String tabLabel(Tab t) {
		int n = switch (t) {
			case OWE -> state.ledger.owed().size();
			case STATS -> 0;
			default -> state.ledger.pending(t.game).size();
		};
		return n > 0 ? t.label + " " + n : t.label;
	}

	private int tabsWidth(TextRenderer tr) {
		int w = PAD * 2 - TAB_GAP;
		for (Tab t : Tab.values()) {
			w += tr.getWidth(tabLabel(t)) + 4 + TAB_GAP;
		}
		return w;
	}

	private void drawTabs(DrawContext ctx, TextRenderer tr, Tab active) {
		int x = PAD;
		int y = PAD;
		for (Tab t : Tab.values()) {
			String label = tabLabel(t);
			int w = tr.getWidth(label) + 4;
			if (t == active) {
				ctx.fill(x - 1, y - 2, x + w - 1, y + 9, (Colors.outline() & 0x00FFFFFF) | 0x55000000);
				ctx.fill(x - 1, y + 9, x + w - 1, y + 10, Colors.outline());
			}
			ctx.drawTextWithShadow(tr, label, x + 1, y, t == active ? Colors.text() : Colors.muted());
			hits.add(new Hit(x - 1, y - 2, w, ROW, () -> setTab(t), null));
			x += w + TAB_GAP;
		}
	}

	private void setTab(Tab t) {
		state.config.activeTab = t.name();
		state.save();
	}

	// ---- game tabs ----

	private void drawGame(DrawContext ctx, TextRenderer tr, Game game, int y, int width) {
		List<Bet> queue = state.ledger.pending(game);
		List<Bet> shown = queue.subList(0, Math.min(MAX_ROWS, queue.size()));
		long net = state.ledger.sessionStats(game).net();
		String head = game.displayName + " profit ";
		ctx.drawTextWithShadow(tr, head, PAD, y, Colors.text());
		ctx.drawTextWithShadow(tr, AmountParser.formatSigned(net), PAD + tr.getWidth(head), y, Colors.profit(net));
		y += ROW;

		if (shown.isEmpty()) {
			ctx.drawTextWithShadow(tr, "> waiting for /pay...", PAD, y, Colors.muted());
			return;
		}
		int[] cols = gameColumns(tr, shown);
		for (int i = 0; i < shown.size(); i++) {
			Bet b = shown.get(i);
			String name = "> " + (i + 1) + " " + b.player;
			ctx.drawTextWithShadow(tr, name, PAD, y, Colors.text());
			hits.add(new Hit(PAD, y - 1, tr.getWidth(name), ROW, null, () -> state.dismiss(b)));
			ctx.drawTextWithShadow(tr, money(b.amount), cols[0], y, Colors.text());

			int lx = cols[1];
			lx = letter(ctx, tr, lx, y, "W", Colors.GREEN, () -> win(b));
			lx = letter(ctx, tr, lx, y, "L", Colors.RED, () -> lose(b));
			if (game.usesScore()) {
				letter(ctx, tr, lx, y, "T", Colors.GREY, () -> tie(b));
			}

			String move = "→" + Tab.of(next(game)).label;
			ctx.drawTextWithShadow(tr, move, cols[2], y, Colors.muted());
			hits.add(new Hit(cols[2] - 1, y - 1, tr.getWidth(move) + 2, ROW, () -> state.setGame(b, next(game)), null));
			y += ROW;
		}
		if (queue.size() > shown.size()) {
			ctx.drawTextWithShadow(tr, "  +" + (queue.size() - shown.size()) + " more waiting", PAD, y, Colors.muted());
		}
	}

	/** x of: amount, letters, move. */
	private int[] gameColumns(TextRenderer tr, List<Bet> shown) {
		int nameW = 0;
		int amountW = 0;
		for (int i = 0; i < shown.size(); i++) {
			Bet b = shown.get(i);
			nameW = Math.max(nameW, tr.getWidth("> " + (i + 1) + " " + b.player));
			amountW = Math.max(amountW, tr.getWidth(money(b.amount)));
		}
		int amountX = PAD + nameW + 8;
		int lettersX = amountX + amountW + 8;
		int moveX = lettersX + 3 * LETTER + 4;
		return new int[]{amountX, lettersX, moveX};
	}

	private static Game next(Game g) {
		return Game.values()[(g.ordinal() + 1) % Game.values().length];
	}

	// ---- owe tab ----

	private void drawOwe(DrawContext ctx, TextRenderer tr, int y) {
		List<Bet> owed = state.ledger.owed();
		List<Bet> shown = owed.subList(0, Math.min(MAX_ROWS, owed.size()));
		ctx.drawTextWithShadow(tr, oweHeader(owed), PAD, y, Colors.text());
		y += ROW;
		if (shown.isEmpty()) {
			ctx.drawTextWithShadow(tr, "Nobody - all paid", PAD, y, Colors.muted());
			return;
		}
		int[] cols = oweColumns(tr, shown);
		for (Bet b : shown) {
			ctx.drawTextWithShadow(tr, b.player, PAD, y, Colors.text());
			ctx.drawTextWithShadow(tr, owedLabel(b), cols[0], y, Colors.GOLD);
			ctx.drawTextWithShadow(tr, Tab.of(b.game).label, cols[1], y, Colors.muted());
			int lx = cols[2];
			if (!b.payoutIsElytra()) {
				lx = letter(ctx, tr, lx, y, "P", Colors.GOLD, () -> state.prefillPay(b));
			}
			letter(ctx, tr, lx, y, "D", Colors.GREEN, () -> state.settle(b));
			y += ROW;
		}
		if (owed.size() > shown.size()) {
			ctx.drawTextWithShadow(tr, "  +" + (owed.size() - shown.size()) + " more owed", PAD, y, Colors.muted());
		}
	}

	private static String oweHeader(List<Bet> owed) {
		long money = 0;
		int elytras = 0;
		for (Bet b : owed) {
			if (b.payoutIsElytra()) {
				elytras++;
			} else {
				money += b.payout;
			}
		}
		String s = "You owe " + money(money);
		return elytras > 0 ? s + " + " + elytras + " Elytra" : s;
	}

	/** x of: amount, game, letters. */
	private int[] oweColumns(TextRenderer tr, List<Bet> shown) {
		int nameW = 0;
		int amountW = 0;
		for (Bet b : shown) {
			nameW = Math.max(nameW, tr.getWidth(b.player));
			amountW = Math.max(amountW, tr.getWidth(owedLabel(b)));
		}
		int amountX = PAD + nameW + 8;
		int gameX = amountX + amountW + 8;
		int lettersX = gameX + tr.getWidth("Paper") + 6;
		return new int[]{amountX, gameX, lettersX};
	}

	// ---- stats tab ----

	private void drawStats(DrawContext ctx, TextRenderer tr, int y, int width) {
		long sessionMs = System.currentTimeMillis() - state.ledger.sessionStart;
		ctx.drawTextWithShadow(tr, "This session " + duration(sessionMs), PAD, y, Colors.text());
		y += ROW;
		int[] cols = statsColumns(tr);
		ctx.drawTextWithShadow(tr, "Profit", cols[0], y, Colors.muted());
		ctx.drawTextWithShadow(tr, "W-L-T", cols[1], y, Colors.muted());
		ctx.drawTextWithShadow(tr, "Bet", cols[2], y, Colors.muted());
		y += ROW;
		for (Game g : Game.values()) {
			statsRow(ctx, tr, cols, y, g.displayName, state.ledger.sessionStats(g));
			y += ROW;
		}
		statsRow(ctx, tr, cols, y, "Total", state.ledger.sessionStats());
		y += ROW;
		statsRow(ctx, tr, cols, y, "Lifetime", state.ledger.lifetimeStats());
		y += ROW + 2;

		String color = "Color: " + Colors.theme.displayName;
		ctx.drawTextWithShadow(tr, color, PAD, y, Colors.text());
		hits.add(new Hit(PAD - 1, y - 1, tr.getWidth(color) + 2, ROW, state::nextTheme, null));
		String reset = "New session";
		int rx = width - PAD - tr.getWidth(reset);
		ctx.drawTextWithShadow(tr, reset, rx, y, Colors.muted());
		hits.add(new Hit(rx - 1, y - 1, tr.getWidth(reset) + 2, ROW, state::newSession, null));
	}

	private void statsRow(DrawContext ctx, TextRenderer tr, int[] cols, int y, String label, Stats s) {
		ctx.drawTextWithShadow(tr, label, PAD, y, Colors.text());
		ctx.drawTextWithShadow(tr, AmountParser.formatSigned(s.net()), cols[0], y, Colors.profit(s.net()));
		ctx.drawTextWithShadow(tr, s.playerWins() + "-" + s.playerLosses() + "-" + s.ties(), cols[1], y, Colors.text());
		ctx.drawTextWithShadow(tr, money(s.wagered()), cols[2], y, Colors.text());
	}

	/** x of: profit, W-L-T, wagered. */
	private int[] statsColumns(TextRenderer tr) {
		int labelW = tr.getWidth("Ely Arrows") + 8;
		int profitX = PAD + labelW;
		int wltX = profitX + tr.getWidth("+999.99M") + 6;
		int betX = wltX + tr.getWidth("99-99-99") + 6;
		return new int[]{profitX, wltX, betX};
	}

	// ---- sizing ----

	private int contentRows(Tab tab) {
		if (tab == Tab.STATS) {
			return 1 + 1 + Game.values().length + 2 + 1;
		}
		List<Bet> list = tab == Tab.OWE ? state.ledger.owed() : state.ledger.pending(tab.game);
		int shown = Math.min(MAX_ROWS, list.size());
		return 1 + Math.max(1, shown) + (list.size() > shown ? 1 : 0);
	}

	private int contentWidth(TextRenderer tr, Tab tab) {
		if (tab == Tab.STATS) {
			return statsColumns(tr)[2] + tr.getWidth("$999.99m") + PAD;
		}
		if (tab == Tab.OWE) {
			List<Bet> owed = state.ledger.owed();
			List<Bet> shown = owed.subList(0, Math.min(MAX_ROWS, owed.size()));
			int w = tr.getWidth(oweHeader(owed)) + 2 * PAD;
			return shown.isEmpty() ? w : Math.max(w, oweColumns(tr, shown)[2] + 2 * LETTER + PAD - 4);
		}
		List<Bet> queue = state.ledger.pending(tab.game);
		List<Bet> shown = queue.subList(0, Math.min(MAX_ROWS, queue.size()));
		int w = tr.getWidth("> waiting for /pay...") + 2 * PAD;
		return shown.isEmpty() ? w : Math.max(w, gameColumns(tr, shown)[2] + tr.getWidth("→Paper") + PAD);
	}

	// ---- actions ----

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

	/** L and T are instant; only W opens a box. */
	private void lose(Bet b) {
		state.resolve(b, Outcome.PLAYER_LOSE, null, null);
	}

	private void tie(Bet b) {
		state.resolveTie(b);
	}

	/** "$50m" like Donut SMP's own lists. */
	static String money(long amount) {
		return "$" + AmountParser.format(amount).toLowerCase(Locale.ROOT);
	}

	static String owedLabel(Bet b) {
		return b.payoutIsElytra() ? "1 Elytra" : money(b.payout);
	}

	static String duration(long millis) {
		long sec = Math.max(0, millis / 1000);
		return String.format("%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
	}
}
