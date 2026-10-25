package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.Auction;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Game;
import com.donutgamble.core.Refund;
import com.donutgamble.core.Standing;
import com.donutgamble.core.Stats;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The queue, split into tabs:
 * <pre>
 *  Paper 3   BJ 1   Ely 1   Auction 4:12   Owe 2   Stats
 *  Paper profit +420M
 *  1  RazorBroGaming   $50m   W L T   →BJ
 *  2  jimbo01          $75m   W L T   →BJ
 * </pre>
 */
final class QueueHud extends HudElement {
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

	private static final int PAD = 6;
	private static final int ROW = 12;
	private static final int TAB_H = 16;
	private static final int TAB_GAP = 3;
	private static final int LETTER = 11;
	private static final int MAX_ROWS = 10;
	private static final int AUCTION_RANKS = 5;

	private final GambleState state;
	private final Anim tabX = new Anim(-1);
	private final Anim tabW = new Anim(0);

	private DrawContext ctx;
	private TextRenderer tr;
	private Regions regions;
	private double mx, my;

	QueueHud(GambleState state) {
		super("queue", "Queue");
		this.state = state;
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{6, 6};
	}

	Tab tab() {
		return Tab.parse(state.config.activeTab);
	}

	private void setTab(Tab t) {
		state.config.activeTab = t.name();
		state.save();
	}

	// ---- size ----

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		Tab tab = tab();
		int width = Math.max(tabsWidth(tr), contentWidth(tr, tab));
		return new int[]{width, PAD + TAB_H + 4 + contentRows(tab) * ROW + PAD - 3};
	}

	private int tabsWidth(TextRenderer tr) {
		int w = 2 * PAD - TAB_GAP;
		for (Tab t : Tab.values()) {
			w += tr.getWidth(tabLabel(t)) + 8 + TAB_GAP;
		}
		return w;
	}

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
			int w = tr.getWidth("Resume   +30s   End   Manager") + 2 * PAD + 8;
			Auction a = state.auctions.current;
			if (a == null) {
				Auction last = state.auctions.latest();
				if (last != null) {
					w = Math.max(w, tr.getWidth(lastLine(last)) + 2 * PAD);
				}
				return w;
			}
			w = Math.max(w, tr.getWidth(a.item + "   99:99 paused") + 2 * PAD);
			return Math.max(w, auctionAmountX(tr, a.rankings()) + tr.getWidth("$999.99m   100%") + PAD + 4);
		}
		if (tab == Tab.OWE) {
			List<OweRow> owed = oweRows();
			List<OweRow> shown = owed.subList(0, Math.min(MAX_ROWS, owed.size()));
			int w = tr.getWidth(oweHeader()) + 2 * PAD;
			return shown.isEmpty() ? w : Math.max(w, oweColumns(tr, shown)[2] + 2 * LETTER + PAD - 2);
		}
		List<Bet> queue = state.ledger.pending(tab.game);
		List<Bet> shown = queue.subList(0, Math.min(MAX_ROWS, queue.size()));
		int w = tr.getWidth("Waiting for /pay…") + 2 * PAD;
		return shown.isEmpty() ? w : Math.max(w, gameColumns(tr, shown)[2] + tr.getWidth("→Paper") + PAD);
	}

	// ---- draw ----

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		this.ctx = ctx;
		this.tr = tr;
		this.regions = editing ? new Regions() : regions;
		this.mx = mouseX;
		this.my = mouseY;
		int[] size = size(tr, editing);
		Paint.hud(ctx, 0, 0, size[0], size[1]);
		Tab tab = tab();
		drawTabs(tab);
		Paint.hline(ctx, PAD, size[0] - PAD, PAD + TAB_H + 1, Colors.alpha(Colors.accent(), 0x40));
		int y = PAD + TAB_H + 5;
		switch (tab) {
			case AUCTION -> drawAuction(y, size[0]);
			case OWE -> drawOwe(y);
			case STATS -> drawStats(y, size[0]);
			default -> drawGame(tab.game, y);
		}
	}

	private String tabLabel(Tab t) {
		if (t == Tab.AUCTION) {
			Auction a = state.auctions.current;
			return a == null ? t.label : t.label + " " + Fmt.clock(a.remaining(System.currentTimeMillis()));
		}
		int n = switch (t) {
			case OWE -> oweRows().size();
			case STATS -> 0;
			default -> state.ledger.pending(t.game).size();
		};
		return n > 0 ? t.label + " " + n : t.label;
	}

	private void drawTabs(Tab active) {
		int x = PAD;
		int y = PAD;
		for (Tab t : Tab.values()) {
			String label = tabLabel(t);
			int w = tr.getWidth(label) + 8;
			if (t == active) {
				float ax = tabX.get() < 0 ? x : tabX.to(x, 16f);
				float aw = tabW.get() == 0 ? w : tabW.to(w, 16f);
				if (tabX.get() < 0) {
					tabX.snap(x);
					tabW.snap(w);
				}
				Paint.round(ctx, Math.round(ax), y, Math.round(aw), TAB_H - 2, Paint.RADIUS, Colors.alpha(Colors.accent(), 0x48));
				Paint.hline(ctx, Math.round(ax) + 2, Math.round(ax + aw) - 2, y + TAB_H - 3, Colors.accent());
			} else if (regions.hovered(mx, my, x, y, w, TAB_H - 2)) {
				Paint.round(ctx, x, y, w, TAB_H - 2, Paint.RADIUS, Colors.alpha(Colors.accent(), 0x20));
			}
			boolean live = t == Tab.AUCTION && state.auctions.isLive();
			int color = t == active ? Colors.text() : live ? Colors.GREEN : Colors.muted();
			Paint.hudText(ctx, tr, label, x + 4, y + 3, color);
			regions.add(x, y, w, TAB_H - 2, () -> setTab(t));
			x += w + TAB_GAP;
		}
	}

	/** A clickable letter with a hover highlight. */
	private int letter(int x, int y, String text, int color, Runnable action) {
		if (regions.hovered(mx, my, x - 2, y - 2, LETTER - 1, ROW)) {
			Paint.round(ctx, x - 2, y - 2, LETTER - 1, ROW - 1, 2, Colors.alpha(color, 0x38));
		}
		Paint.hudText(ctx, tr, text, x, y, color);
		regions.add(x - 2, y - 2, LETTER - 1, ROW, action);
		return x + LETTER;
	}

	/** A clickable word with a hover highlight; returns where the next one goes. */
	private int link(int x, int y, String text, int color, Runnable action) {
		int w = tr.getWidth(text);
		if (regions.hovered(mx, my, x - 2, y - 2, w + 4, ROW)) {
			Paint.round(ctx, x - 2, y - 2, w + 4, ROW - 1, 2, Colors.alpha(color, 0x30));
		}
		Paint.hudText(ctx, tr, text, x, y, color);
		regions.add(x - 2, y - 2, w + 4, ROW, action);
		return x + w + 10;
	}

	// ---- game tabs ----

	private void drawGame(Game game, int y) {
		List<Bet> queue = state.ledger.pending(game);
		List<Bet> shown = queue.subList(0, Math.min(MAX_ROWS, queue.size()));
		long net = state.ledger.sessionStats(game).net();
		int x = Paint.hudText(ctx, tr, game.displayName + " profit ", PAD, y, Colors.muted());
		Paint.hudText(ctx, tr, Fmt.signed(net), x, y, Colors.profit(net));
		y += ROW;

		if (shown.isEmpty()) {
			Paint.hudText(ctx, tr, "Waiting for /pay…", PAD, y, Colors.muted());
			return;
		}
		int[] cols = gameColumns(tr, shown);
		for (int i = 0; i < shown.size(); i++) {
			Bet b = shown.get(i);
			Paint.hudText(ctx, tr, Integer.toString(i + 1), PAD, y, i == 0 ? Colors.accent() : Colors.muted());
			int nameX = PAD + 12;
			Paint.hudText(ctx, tr, b.player, nameX, y, Colors.text());
			regions.add(nameX, y - 1, tr.getWidth(b.player), ROW, null, () -> state.dismiss(b));
			Paint.hudText(ctx, tr, Fmt.money(b.amount), cols[0], y, Colors.GOLD);

			int lx = cols[1];
			lx = letter(lx, y, "W", Colors.GREEN, () -> state.callWin(b));
			lx = letter(lx, y, "L", Colors.RED, () -> state.callLoss(b));
			if (game.usesScore()) {
				letter(lx, y, "T", Colors.GREY, () -> state.callTie(b));
			}
			Game next = next(game);
			link(cols[2], y, "→" + Tab.of(next).label, Colors.muted(), () -> state.setGame(b, next));
			y += ROW;
		}
		if (queue.size() > shown.size()) {
			Paint.hudText(ctx, tr, "+" + (queue.size() - shown.size()) + " more waiting", PAD + 12, y, Colors.muted());
		}
	}

	/** x of: amount, letters, move. */
	private int[] gameColumns(TextRenderer tr, List<Bet> shown) {
		int nameW = 0;
		int amountW = 0;
		for (Bet b : shown) {
			nameW = Math.max(nameW, tr.getWidth(b.player));
			amountW = Math.max(amountW, tr.getWidth(Fmt.money(b.amount)));
		}
		int amountX = PAD + 12 + nameW + 10;
		int lettersX = amountX + amountW + 10;
		int moveX = lettersX + 3 * LETTER + 6;
		return new int[]{amountX, lettersX, moveX};
	}

	private static Game next(Game g) {
		return Game.values()[(g.ordinal() + 1) % Game.values().length];
	}

	// ---- auction tab ----

	private String lastLine(Auction last) {
		return last.winner != null
			? "Last: " + last.item + " → " + last.winner + " " + Fmt.money(last.total())
			: "Last: " + last.item + " (" + last.status.name().toLowerCase(Locale.ROOT) + ")";
	}

	private void drawAuction(int y, int width) {
		Auction a = state.auctions.current;
		if (a == null) {
			Auction last = state.auctions.latest();
			Paint.hudText(ctx, tr, "No auction running", PAD, y, Colors.muted());
			y += ROW;
			if (last != null) {
				Paint.hudText(ctx, tr, lastLine(last), PAD, y, Colors.text());
			}
			y += ROW;
			int x = link(PAD, y, "Start " + Fmt.clock(state.config.auctionDefaultDurationSec * 1000L), Colors.GREEN,
				() -> state.startAuction("", state.config.auctionDefaultDurationSec, 0));
			link(x, y, "Manager", Colors.text(), () -> ManagerScreen.open(state, ManagerScreen.Section.AUCTION));
			return;
		}

		long left = a.remaining(System.currentTimeMillis());
		boolean paused = a.status == Auction.Status.PAUSED;
		String clock = Fmt.clock(left) + (paused ? " paused" : "");
		Paint.hudText(ctx, tr, a.item, PAD, y, Colors.text());
		int clockColor = paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.GREEN;
		Paint.hudText(ctx, tr, clock, width - PAD - tr.getWidth(clock), y, clockColor);
		y += ROW;
		List<Standing> ranks = a.rankings();
		Paint.hudText(ctx, tr, "Total " + Fmt.money(a.total()) + " · " + ranks.size() + (ranks.size() == 1 ? " player" : " players"),
			PAD, y, Colors.muted());
		y += ROW;
		if (ranks.isEmpty()) {
			Paint.hudText(ctx, tr, "Waiting for /pay…", PAD, y, Colors.muted());
			y += ROW;
		}
		int amountX = auctionAmountX(tr, ranks);
		for (int i = 0; i < Math.min(AUCTION_RANKS, ranks.size()); i++) {
			Standing st = ranks.get(i);
			int barW = Math.round((width - 2 * PAD) * (float) st.share());
			Paint.round(ctx, PAD - 2, y - 2, barW + 2, ROW - 1, 2, Colors.alpha(Colors.accent(), 0x22));
			Paint.hudText(ctx, tr, (i + 1) + "  " + st.player(), PAD, y, i == 0 ? Colors.GOLD : Colors.text());
			Paint.hudText(ctx, tr, Fmt.money(st.total()), amountX, y, Colors.text());
			String pct = Math.round(st.share() * 100) + "%";
			Paint.hudText(ctx, tr, pct, width - PAD - tr.getWidth(pct), y, Colors.muted());
			y += ROW;
		}
		if (ranks.size() > AUCTION_RANKS) {
			Paint.hudText(ctx, tr, "+" + (ranks.size() - AUCTION_RANKS) + " more", PAD, y, Colors.muted());
			y += ROW;
		}
		int x = link(PAD, y, paused ? "Resume" : "Pause", Colors.GOLD, state::togglePauseAuction);
		x = link(x, y, "+30s", Colors.text(), () -> state.extendAuction(30));
		x = link(x, y, "End", Colors.RED, state::endAuction);
		link(x, y, "Manager", Colors.text(), () -> ManagerScreen.open(state, ManagerScreen.Section.AUCTION));
	}

	private int auctionAmountX(TextRenderer tr, List<Standing> ranks) {
		int nameW = tr.getWidth("No auction running");
		for (int i = 0; i < Math.min(AUCTION_RANKS, ranks.size()); i++) {
			nameW = Math.max(nameW, tr.getWidth((i + 1) + "  " + ranks.get(i).player()) + 10);
		}
		return PAD + Math.min(nameW, 120);
	}

	// ---- owe tab ----

	/** Gamble winners and auction refunds, oldest first. */
	private List<OweRow> oweRows() {
		List<OweRow> rows = new ArrayList<>();
		for (Bet b : state.ledger.owed()) {
			rows.add(new OweRow(b.player, Fmt.owedLabel(b), Tab.of(b.game).label, b.timestamp,
				b.payoutIsElytra() ? null : () -> state.prefillPay(b), () -> state.settle(b)));
		}
		for (Refund r : state.auctions.openRefunds()) {
			rows.add(new OweRow(r.player, Fmt.money(r.amount), "Refund", r.timestamp,
				() -> state.prefillPay(r.player, r.amount), () -> state.settleRefund(r)));
		}
		rows.sort(Comparator.comparingLong(OweRow::at));
		return rows;
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
		String s = "You owe " + Fmt.money(money);
		return elytras > 0 ? s + " + " + elytras + " Elytra" : s;
	}

	private void drawOwe(int y) {
		List<OweRow> owed = oweRows();
		List<OweRow> shown = owed.subList(0, Math.min(MAX_ROWS, owed.size()));
		Paint.hudText(ctx, tr, oweHeader(), PAD, y, Colors.text());
		y += ROW;
		if (shown.isEmpty()) {
			Paint.hudText(ctx, tr, "Nobody, all paid", PAD, y, Colors.muted());
			return;
		}
		int[] cols = oweColumns(tr, shown);
		for (OweRow r : shown) {
			Paint.hudText(ctx, tr, r.player(), PAD, y, Colors.text());
			Paint.hudText(ctx, tr, r.what(), cols[0], y, Colors.GOLD);
			Paint.hudText(ctx, tr, r.from(), cols[1], y, Colors.muted());
			int lx = cols[2];
			if (r.pay() != null) {
				lx = letter(lx, y, "P", Colors.GOLD, r.pay());
			}
			letter(lx, y, "D", Colors.GREEN, r.done());
			y += ROW;
		}
		if (owed.size() > shown.size()) {
			Paint.hudText(ctx, tr, "+" + (owed.size() - shown.size()) + " more owed", PAD, y, Colors.muted());
		}
	}

	/** x of: amount, source, letters. */
	private int[] oweColumns(TextRenderer tr, List<OweRow> shown) {
		int nameW = 0;
		int amountW = 0;
		for (OweRow r : shown) {
			nameW = Math.max(nameW, tr.getWidth(r.player()));
			amountW = Math.max(amountW, tr.getWidth(r.what()));
		}
		int amountX = PAD + nameW + 10;
		int gameX = amountX + amountW + 10;
		int lettersX = gameX + tr.getWidth("Refund") + 8;
		return new int[]{amountX, gameX, lettersX};
	}

	// ---- stats tab ----

	private void drawStats(int y, int width) {
		long sessionMs = System.currentTimeMillis() - state.ledger.sessionStart;
		Paint.hudText(ctx, tr, "This session " + Fmt.duration(sessionMs), PAD, y, Colors.text());
		y += ROW;
		int[] cols = statsColumns(tr);
		Paint.hudText(ctx, tr, "Profit", cols[0], y, Colors.muted());
		Paint.hudText(ctx, tr, "W-L-T", cols[1], y, Colors.muted());
		Paint.hudText(ctx, tr, "Bet", cols[2], y, Colors.muted());
		y += ROW;
		for (Game g : Game.values()) {
			statsRow(cols, y, g.displayName, state.ledger.sessionStats(g));
			y += ROW;
		}
		statsRow(cols, y, "Total", state.ledger.sessionStats());
		y += ROW;
		statsRow(cols, y, "Lifetime", state.ledger.lifetimeStats());
		y += ROW + 1;

		int x = link(PAD, y, "Color: " + Colors.theme().displayName, Colors.text(), state::nextTheme);
		link(x, y, "Manager", Colors.text(), () -> ManagerScreen.open(state, ManagerScreen.Section.DASHBOARD));
		String reset = "New session";
		link(width - PAD - tr.getWidth(reset), y, reset, Colors.muted(), state::newSession);
	}

	private void statsRow(int[] cols, int y, String label, Stats s) {
		Paint.hudText(ctx, tr, label, PAD, y, Colors.text());
		Paint.hudText(ctx, tr, Fmt.signed(s.net()), cols[0], y, Colors.profit(s.net()));
		Paint.hudText(ctx, tr, s.playerWins() + "-" + s.playerLosses() + "-" + s.ties(), cols[1], y, Colors.text());
		Paint.hudText(ctx, tr, Fmt.money(s.wagered()), cols[2], y, Colors.text());
	}

	/** x of: profit, W-L-T, wagered. */
	private int[] statsColumns(TextRenderer tr) {
		int profitX = PAD + tr.getWidth("Ely Arrows") + 10;
		int wltX = profitX + tr.getWidth("+$999.99m") + 8;
		int betX = wltX + tr.getWidth("99-99-99") + 8;
		return new int[]{profitX, wltX, betX};
	}
}
