package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Auction;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Game;
import com.donutgamble.core.Outcome;
import com.donutgamble.core.Refund;
import com.donutgamble.core.Standing;
import com.donutgamble.core.Stats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The on-screen queue, split into tabs:
 * <pre>
 * [Paper 3] BJ 1  Ely 1  Auction 4:12  Owe 2  Stats
 * Paper profit +420M
 * > 1 RazorBroGaming  $50m   W L T  →BJ
 * > 2 jimbo01         $75m   W L T  →BJ
 * </pre>
 * Tabs and letters are clickable while chat or the Insert screen is open, and the box can be dragged
 * anywhere on screen by grabbing an empty spot.
 */
final class QueueOverlay {
	enum Tab {
		PAPER(Game.PAPER, "Paper"),
		BLACKJACK(Game.BLACKJACK, "BJ"),
		ELY(Game.ELY, "Ely"),
		AUCTION(null, "Auction"),
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

	/** A row in the Owe tab: a gamble winner or an auction refund. */
	private record OweRow(String player, String what, String from, long at, Runnable pay, Runnable done) {
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
	private int boxW, boxH;
	/** While dragging: where inside the box the mouse grabbed it. -1 when not dragging. */
	private double grabX = -1, grabY = -1;

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
		drawToasts(ctx, client.textRenderer, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
	}

	/** Payment and auction pop-ups, stacked bottom-right above the hotbar so they never cover the queue. */
	private void drawToasts(DrawContext ctx, TextRenderer tr, int screenW, int screenH) {
		List<GambleState.Toast> list = state.activeToasts();
		int y = screenH - 40 - list.size() * 17;
		for (GambleState.Toast t : list) {
			int w = tr.getWidth(t.text()) + 10;
			int x = screenW - w - 6;
			Draw.box(ctx, x, y, x + w, y + 14, Colors.BOX_BG);
			ctx.drawTextWithShadow(tr, t.text(), x + 5, y + 3, t.color());
			y += 17;
		}
	}

	// ---- dragging ----

	/** Mouse down on an empty part of the box: start moving it. */
	boolean startDrag(double mouseX, double mouseY) {
		double lx = (mouseX - originX) / scale;
		double ly = (mouseY - originY) / scale;
		if (lx < 0 || ly < 0 || lx >= boxW || ly >= boxH) {
			return false;
		}
		grabX = mouseX - originX;
		grabY = mouseY - originY;
		return true;
	}

	boolean drag(double mouseX, double mouseY) {
		if (grabX < 0) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int maxX = client.getWindow().getScaledWidth() - Math.round(boxW * scale);
		int maxY = client.getWindow().getScaledHeight() - Math.round(boxH * scale);
		state.config.hudX = (int) Math.max(0, Math.min(maxX, mouseX - grabX));
		state.config.hudY = (int) Math.max(0, Math.min(maxY, mouseY - grabY));
		originX = state.config.hudX;
		originY = state.config.hudY;
		return true;
	}

	boolean endDrag() {
		if (grabX < 0) {
			return false;
		}
		grabX = grabY = -1;
		state.save();
		return true;
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
		boxW = width;
		boxH = PAD * 2 + rows * ROW;
		Draw.box(ctx, 0, 0, boxW, boxH, Colors.BG);

		drawTabs(ctx, tr, tab);
		int y = PAD + ROW + 1;
		if (tab.game != null) {
			drawGame(ctx, tr, tab.game, y, width);
		} else if (tab == Tab.AUCTION) {
			drawAuction(ctx, tr, y, width);
		} else if (tab == Tab.OWE) {
			drawOwe(ctx, tr, y);
		} else {
			drawStats(ctx, tr, y, width);
		}
	}

	// ---- tabs ----

	private String tabLabel(Tab t) {
		if (t == Tab.AUCTION) {
			Auction a = state.auctions.current;
			if (a == null) {
				return t.label;
			}
			return t.label + " " + clock(a.remaining(System.currentTimeMillis())) + (a.status == Auction.Status.PAUSED ? " II" : "");
		}
		int n = switch (t) {
			case OWE -> oweRows().size();
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

	// ---- auction tab ----

	private static final int AUCTION_RANKS = 5;

	private void drawAuction(DrawContext ctx, TextRenderer tr, int y, int width) {
		Auction a = state.auctions.current;
		if (a == null) {
			Auction last = state.auctions.latest();
			ctx.drawTextWithShadow(tr, "No auction running", PAD, y, Colors.muted());
			y += ROW;
			if (last != null) {
				String result = last.winner != null
					? "Last: " + last.item + " → " + last.winner + " " + money(last.total())
					: "Last: " + last.item + " (" + last.status.name().toLowerCase(Locale.ROOT) + ")";
				ctx.drawTextWithShadow(tr, result, PAD, y, Colors.text());
			}
			y += ROW;
			int x = link(ctx, tr, PAD, y, "Start " + clock(state.config.auctionDefaultDurationSec * 1000L), Colors.GREEN,
				() -> state.startAuction("", state.config.auctionDefaultDurationSec, 0));
			link(ctx, tr, x, y, "Manager", Colors.text(), () -> ManagerScreen.open(state, ManagerScreen.Section.AUCTION));
			return;
		}

		long left = a.remaining(System.currentTimeMillis());
		String clock = clock(left) + (a.status == Auction.Status.PAUSED ? " paused" : "");
		ctx.drawTextWithShadow(tr, a.item, PAD, y, Colors.text());
		int clockColor = a.status == Auction.Status.PAUSED ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.GREEN;
		ctx.drawTextWithShadow(tr, clock, width - PAD - tr.getWidth(clock), y, clockColor);
		y += ROW;
		List<Standing> ranks = a.rankings();
		ctx.drawTextWithShadow(tr, "Total " + money(a.total()) + " · " + ranks.size() + (ranks.size() == 1 ? " player" : " players"),
			PAD, y, Colors.muted());
		y += ROW;
		if (ranks.isEmpty()) {
			ctx.drawTextWithShadow(tr, "> waiting for /pay...", PAD, y, Colors.muted());
			y += ROW;
		}
		int amountX = auctionAmountX(tr, ranks);
		for (int i = 0; i < Math.min(AUCTION_RANKS, ranks.size()); i++) {
			Standing st = ranks.get(i);
			ctx.drawTextWithShadow(tr, "#" + (i + 1) + " " + st.player(), PAD, y, i == 0 ? Colors.GOLD : Colors.text());
			ctx.drawTextWithShadow(tr, money(st.total()), amountX, y, Colors.text());
			String pct = Math.round(st.share() * 100) + "%";
			ctx.drawTextWithShadow(tr, pct, width - PAD - tr.getWidth(pct), y, Colors.muted());
			y += ROW;
		}
		if (ranks.size() > AUCTION_RANKS) {
			ctx.drawTextWithShadow(tr, "  +" + (ranks.size() - AUCTION_RANKS) + " more", PAD, y, Colors.muted());
			y += ROW;
		}
		int x = link(ctx, tr, PAD, y, a.status == Auction.Status.PAUSED ? "Resume" : "Pause", Colors.GOLD, state::togglePauseAuction);
		x = link(ctx, tr, x, y, "+30s", Colors.text(), () -> state.extendAuction(30));
		x = link(ctx, tr, x, y, "End", Colors.RED, state::endAuction);
		link(ctx, tr, x, y, "Manager", Colors.text(), () -> ManagerScreen.open(state, ManagerScreen.Section.AUCTION));
	}

	private int auctionAmountX(TextRenderer tr, List<Standing> ranks) {
		int nameW = tr.getWidth("No auction running");
		for (int i = 0; i < Math.min(AUCTION_RANKS, ranks.size()); i++) {
			nameW = Math.max(nameW, tr.getWidth("#" + (i + 1) + " " + ranks.get(i).player()) + 8);
		}
		return PAD + Math.min(nameW, 110);
	}

	/** A clickable word; returns where the next one goes. */
	private int link(DrawContext ctx, TextRenderer tr, int x, int y, String text, int color, Runnable action) {
		ctx.drawTextWithShadow(tr, text, x, y, color);
		hits.add(new Hit(x - 1, y - 1, tr.getWidth(text) + 2, ROW, action, null));
		return x + tr.getWidth(text) + 8;
	}

	// ---- owe tab ----

	/** Gamble winners and auction refunds, oldest first. */
	private List<OweRow> oweRows() {
		List<OweRow> rows = new ArrayList<>();
		for (Bet b : state.ledger.owed()) {
			rows.add(new OweRow(b.player, owedLabel(b), Tab.of(b.game).label, b.timestamp,
				b.payoutIsElytra() ? null : () -> state.prefillPay(b), () -> state.settle(b)));
		}
		for (Refund r : state.auctions.openRefunds()) {
			rows.add(new OweRow(r.player, money(r.amount), "Refund", r.timestamp,
				() -> state.prefillPay(r.player, r.amount), () -> state.settleRefund(r)));
		}
		rows.sort(Comparator.comparingLong(OweRow::at));
		return rows;
	}

	private void drawOwe(DrawContext ctx, TextRenderer tr, int y) {
		List<OweRow> owed = oweRows();
		List<OweRow> shown = owed.subList(0, Math.min(MAX_ROWS, owed.size()));
		ctx.drawTextWithShadow(tr, oweHeader(), PAD, y, Colors.text());
		y += ROW;
		if (shown.isEmpty()) {
			ctx.drawTextWithShadow(tr, "Nobody - all paid", PAD, y, Colors.muted());
			return;
		}
		int[] cols = oweColumns(tr, shown);
		for (OweRow r : shown) {
			ctx.drawTextWithShadow(tr, r.player(), PAD, y, Colors.text());
			ctx.drawTextWithShadow(tr, oweText(r), cols[0], y, Colors.GOLD);
			ctx.drawTextWithShadow(tr, r.from(), cols[1], y, Colors.muted());
			int lx = cols[2];
			if (r.pay() != null) {
				lx = letter(ctx, tr, lx, y, "P", Colors.GOLD, r.pay());
			}
			letter(ctx, tr, lx, y, "D", Colors.GREEN, r.done());
			y += ROW;
		}
		if (owed.size() > shown.size()) {
			ctx.drawTextWithShadow(tr, "  +" + (owed.size() - shown.size()) + " more owed", PAD, y, Colors.muted());
		}
	}

	private String oweHeader() {
		long money = 0;
		int elytras = 0;
		for (Bet b : state.ledger.owed()) {
			if (b.payoutIsElytra()) {
				elytras++;
			} else {
				money += b.payout;
			}
		}
		for (Refund r : state.auctions.openRefunds()) {
			money += r.amount;
		}
		String s = "You owe " + money(money);
		return elytras > 0 ? s + " + " + elytras + " Elytra" : s;
	}

	/** x of: amount, source, letters. */
	private int[] oweColumns(TextRenderer tr, List<OweRow> shown) {
		int nameW = 0;
		int amountW = 0;
		for (OweRow r : shown) {
			nameW = Math.max(nameW, tr.getWidth(r.player()));
			amountW = Math.max(amountW, tr.getWidth(oweText(r)));
		}
		int amountX = PAD + nameW + 6;
		int gameX = amountX + amountW + 8;
		int lettersX = gameX + tr.getWidth("Refund") + 6;
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

		int x = link(ctx, tr, PAD, y, "Color: " + Colors.theme.displayName, Colors.text(), state::nextTheme);
		link(ctx, tr, x, y, "Manager", Colors.text(), () -> ManagerScreen.open(state, ManagerScreen.Section.DASHBOARD));
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
		if (tab == Tab.AUCTION) {
			Auction a = state.auctions.current;
			if (a == null) {
				return 3;
			}
			int ranks = a.rankings().size();
			return 2 + Math.max(1, Math.min(AUCTION_RANKS, ranks)) + (ranks > AUCTION_RANKS ? 1 : 0) + 1;
		}
		List<?> list = tab == Tab.OWE ? oweRows() : state.ledger.pending(tab.game);
		int shown = Math.min(MAX_ROWS, list.size());
		return 1 + Math.max(1, shown) + (list.size() > shown ? 1 : 0);
	}

	private int contentWidth(TextRenderer tr, Tab tab) {
		if (tab == Tab.STATS) {
			return statsColumns(tr)[2] + tr.getWidth("$999.99m") + PAD;
		}
		if (tab == Tab.AUCTION) {
			int w = tr.getWidth("Resume  +30s  End  Manager") + 2 * PAD + 8;
			Auction a = state.auctions.current;
			if (a == null) {
				Auction last = state.auctions.latest();
				if (last != null) {
					w = Math.max(w, tr.getWidth("Last: " + last.item + " → " + last.winner + " " + money(last.total())) + 2 * PAD);
				}
				return w;
			}
			w = Math.max(w, tr.getWidth(a.item + "  99:99 paused") + 2 * PAD);
			return Math.max(w, auctionAmountX(tr, a.rankings()) + tr.getWidth("$999.99m  100%") + PAD + 4);
		}
		if (tab == Tab.OWE) {
			List<OweRow> owed = oweRows();
			List<OweRow> shown = owed.subList(0, Math.min(MAX_ROWS, owed.size()));
			int w = tr.getWidth(oweHeader()) + 2 * PAD;
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

	/** L is instant; W and T open the score box. */
	private void lose(Bet b) {
		state.resolve(b, Outcome.PLAYER_LOSE, null, null);
	}

	private void tie(Bet b) {
		state.resolveTie(b);
		MinecraftClient.getInstance().setScreen(ScoreScreen.score(state, b, "TIED"));
	}

	/** "$50m" like Donut SMP's own lists. */
	static String money(long amount) {
		return "$" + AmountParser.format(amount).toLowerCase(Locale.ROOT);
	}

	private static String oweText(OweRow r) {
		return "awaiting manual payment of " + r.what();
	}

	static String owedLabel(Bet b) {
		return b.payoutIsElytra() ? "1 Elytra" : money(b.payout);
	}

	/** m:ss countdown. */
	static String clock(long millis) {
		long sec = (Math.max(0, millis) + 999) / 1000;
		return String.format("%d:%02d", sec / 60, sec % 60);
	}

	static String duration(long millis) {
		long sec = Math.max(0, millis / 1000);
		return String.format("%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
	}
}
