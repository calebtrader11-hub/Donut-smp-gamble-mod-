package com.donutgamble.client;

import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.AmountParser;
import com.donutgamble.core.AuctionStats;
import com.donutgamble.core.FinanceReport;
import com.donutgamble.core.FinanceSeries;
import com.donutgamble.core.Game;
import com.donutgamble.core.PrivacySettings.Surface;
import com.donutgamble.core.Stats;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Profit and loss over a date range: totals, a net-per-day chart, per-game and auction stats, expenses and exports. */
final class FinancePage extends ManagerPage {
	static FinanceSeries.Range range = FinanceSeries.Range.WEEK;
	private static final long HOUR = 3_600_000L;

	private List<FinanceSeries.Bucket> series = List.of();
	private long seriesAt;
	private int seriesKey = -1;

	FinancePage(ManagerScreen m) {
		super(m);
	}

	@Override
	ManagerScreen.Section section() {
		return ManagerScreen.Section.FINANCE;
	}

	@Override
	void init() {
		if (m.hides(Surface.FINANCE)) {
			return;
		}
		m.headerButton("CSV", Button.Variant.SECONDARY, null, () -> state.exportTransactions(false));
		m.headerButton("Export report", Button.Variant.SECONDARY, Icons.HISTORY, state::exportReport);
		List<String> labels = new ArrayList<>();
		for (FinanceSeries.Range r : FinanceSeries.Range.values()) {
			labels.add(r.label);
		}
		m.segmented(m.cx, m.cy, Math.min(220, m.cw - 100), labels, () -> range.ordinal(), i -> range = FinanceSeries.Range.values()[i]);

		int y = m.cy + m.ch - 16;
		m.box("finance.note", m.cx + 50, y, 120, "What for");
		m.box("finance.amount", m.cx + 174, y, 60, "Amount");
		m.button(m.cx + 238, y, "Add expense", Button.Variant.SECONDARY, () -> {
			long amount = AmountParser.parseOrZero(ManagerScreen.draft("finance.amount"));
			if (amount <= 0) {
				return;
			}
			String note = ManagerScreen.draft("finance.note").isEmpty() ? "expense" : ManagerScreen.draft("finance.note");
			m.confirm("Add an expense?", Fmt.money(amount) + " for \"" + note + "\" is subtracted from profit.", "Add", false, () -> {
				state.addExpense(note, amount);
				ManagerScreen.DRAFTS.remove("finance.amount");
				ManagerScreen.DRAFTS.remove("finance.note");
			});
		});
	}

	private long from(long now) {
		return range.start(state.finance, now, ZoneId.systemDefault());
	}

	private List<FinanceSeries.Bucket> series(long now, int maxBars) {
		int key = range.ordinal() * 1_000_003 + state.finance.transactions.size() * 31 + maxBars;
		if (key != seriesKey || now - seriesAt > 5000) {
			long from = from(now);
			series = range == FinanceSeries.Range.TODAY
				? FinanceSeries.fixed(state.finance, from, Math.max(from + HOUR, now + 1), HOUR)
				: FinanceSeries.daily(state.finance, from, now, ZoneId.systemDefault(), maxBars);
			seriesKey = key;
			seriesAt = now;
		}
		return series;
	}

	@Override
	void draw() {
		int cx = m.cx, cy = m.cy, cw = m.cw, ch = m.ch;
		if (m.hides(Surface.FINANCE)) {
			m.locked(cx, cy, cw, ch, "Finance is");
			return;
		}
		long now = System.currentTimeMillis();
		long from = from(now);
		FinanceReport r = state.finance.report(from, Long.MAX_VALUE);
		m.right(range == FinanceSeries.Range.ALL ? "since " + ManagerScreen.date(from) : "from " + ManagerScreen.date(from),
			cx + cw, cy + 4, Colors.INK_FAINT);

		int gap = ManagerScreen.GAP;
		int tw = (cw - 3 * gap) / 4;
		int ty = cy + 22;
		m.statTile(cx, ty, tw, "Net profit", Fmt.signed(r.net()), Colors.profit(r.net()), false);
		m.statTile(cx + (tw + gap), ty, tw, "Received", Fmt.money(r.received()), Colors.GREEN, false);
		m.statTile(cx + 2 * (tw + gap), ty, tw, "Paid out", Fmt.money(r.paidOut()), Colors.RED, false);
		m.statTile(cx + 3 * (tw + gap), ty, cw - 3 * (tw + gap), "Expenses", Fmt.money(r.expenses()), Colors.ORANGE, false);

		int top = ty + 38;
		int h = cy + ch - 22 - top;
		int chartW = Math.round(cw * 0.56f);
		chart(cx, top, chartW, h, now);
		breakdown(cx + chartW + gap, top, cw - chartW - gap, h, from);
		m.text("Expense", cx, cy + ch - 12, Colors.INK_DIM);
	}

	private void chart(int x, int y, int w, int h, long now) {
		var ctx = m.ctx;
		m.card(x, y, w, h);
		m.cardTitle(x, y, range == FinanceSeries.Range.TODAY ? "Net per hour" : "Net per day", Icons.FINANCE);
		int px = x + 8, pw = w - 16;
		int py = y + 22, ph = h - 36;
		List<FinanceSeries.Bucket> b = series(now, Math.max(1, pw / 5));
		long peak = 0;
		for (FinanceSeries.Bucket k : b) {
			peak = Math.max(peak, Math.abs(k.net()));
		}
		if (peak == 0) {
			m.empty(null, "No money moved in this range", "Bars appear as payments come in", x, y + 10, w, h - 10);
			return;
		}
		boolean anyNegative = b.stream().anyMatch(k -> k.net() < 0);
		boolean anyPositive = b.stream().anyMatch(k -> k.net() > 0);
		int zero = anyNegative && anyPositive ? py + ph / 2 : anyNegative ? py : py + ph;
		int up = zero - py;
		int down = py + ph - zero;
		for (int i = 1; i <= 3; i++) {
			int gy = py + ph * i / 4;
			ctx.fill(px, gy, px + pw, gy + 1, Colors.alpha(Colors.LINE, 0x70));
		}
		ctx.fill(px, zero, px + pw, zero + 1, Colors.LINE_HI);
		m.right(Fmt.money(peak), x + w - 8, y + 7, Colors.INK_FAINT);

		int n = b.size();
		float slot = pw / (float) n;
		int barW = Math.max(1, Math.min(14, Math.round(slot) - 2));
		int hover = -1;
		for (int i = 0; i < n; i++) {
			FinanceSeries.Bucket k = b.get(i);
			int bx = px + Math.round(i * slot + (slot - barW) / 2f);
			long net = k.net();
			boolean over = m.in(px + Math.round(i * slot), py, Math.max(1, Math.round(slot)), ph);
			if (over) {
				hover = i;
				Paint.round(ctx, px + Math.round(i * slot), py, Math.max(1, Math.round(slot)), ph, 2, Colors.alpha(Colors.CARD_HI, 0xA0));
			}
			if (net == 0) {
				continue;
			}
			int bh = Math.max(1, Math.round((net > 0 ? up : down) * Math.abs(net) / (float) peak));
			int color = net > 0 ? Colors.GREEN : Colors.RED;
			if (!over) {
				color = Colors.mix(color, Colors.CARD, 0.2f);
			}
			if (net > 0) {
				Paint.roundGradient(ctx, bx, zero - bh, barW, bh, Math.min(2, barW / 2), Colors.mix(color, 0xFFFFFFFF, 0.15f), color);
			} else {
				Paint.roundGradient(ctx, bx, zero + 1, barW, bh, Math.min(2, barW / 2), color, Colors.mix(color, 0xFF000000, 0.2f));
			}
		}
		String first = label(b.get(0).start());
		String last = label(b.get(n - 1).start());
		m.text(first, px, y + h - 11, Colors.INK_FAINT);
		if (n > 1) {
			m.right(last, px + pw, y + h - 11, Colors.INK_FAINT);
		}
		if (hover >= 0) {
			tooltip(b.get(hover), px + Math.round(hover * slot + slot / 2), py, x, w);
		}
	}

	private String label(long t) {
		if (range == FinanceSeries.Range.TODAY) {
			return ManagerScreen.shortTime(t);
		}
		return ManagerScreen.date(t);
	}

	private void tooltip(FinanceSeries.Bucket k, int anchorX, int y, int cardX, int cardW) {
		String title = range == FinanceSeries.Range.TODAY
			? ManagerScreen.shortTime(k.start()) + "–" + ManagerScreen.shortTime(k.end())
			: k.end() - k.start() > 90_000_000L ? ManagerScreen.date(k.start()) + "–" + ManagerScreen.date(k.end() - 1) : ManagerScreen.date(k.start());
		String[] lines = {
			"In " + Fmt.money(k.received()),
			"Out " + Fmt.money(k.paidOut() + k.expenses()),
			"Net " + Fmt.signed(k.net()),
		};
		int w = m.width(title);
		for (String l : lines) {
			w = Math.max(w, m.width(l));
		}
		w += 12;
		int h = 12 + lines.length * 10 + 2;
		int x = Math.max(cardX + 4, Math.min(cardX + cardW - 4 - w, anchorX - w / 2));
		Paint.shadow(m.ctx, x, y, w, h, 3);
		Paint.card(m.ctx, x, y, w, h, 3, Colors.CARD_HI, Colors.LINE_HI);
		m.text(title, x + 6, y + 4, Colors.INK);
		for (int i = 0; i < lines.length; i++) {
			int c = i == 0 ? Colors.GREEN : i == 1 ? Colors.RED : Colors.profit(k.net());
			m.text(lines[i], x + 6, y + 15 + i * 10, c);
		}
	}

	private void breakdown(int x, int y, int w, int h, long from) {
		m.card(x, y, w, h);
		m.cardTitle(x, y, "Games", Icons.GAME);
		m.right("W-L-T · net", x + w - 8, y + 7, Colors.INK_FAINT);
		int ry = y + 21;
		boolean any = false;
		for (Game g : Game.values()) {
			Stats s = state.ledger.stats(g, from, Long.MAX_VALUE);
			int played = s.playerWins() + s.playerLosses() + s.ties();
			any |= played > 0;
			String net = played == 0 ? "-" : Fmt.signed(s.net());
			String wlt = s.playerWins() + "-" + s.playerLosses() + "-" + s.ties();
			int netX = x + w - 8 - m.width(net);
			int wltX = netX - 6 - m.width(wlt);
			String name = w >= 170 ? g.displayName : g == Game.BLACKJACK ? "BJ" : g == Game.ELY ? "Ely" : g.displayName;
			m.text(m.fit(name, wltX - x - 12), x + 8, ry, Colors.INK);
			m.text(wlt, wltX, ry, Colors.INK_DIM);
			m.right(net, x + w - 8, ry, played == 0 ? Colors.INK_FAINT : Colors.profit(s.net()));
			ry += 12;
		}
		if (!any) {
			m.text("No games called in this range", x + 8, ry, Colors.INK_FAINT);
			ry += 12;
		}
		ry += 4;
		Paint.hline(m.ctx, x + 1, x + w - 1, ry - 3, Colors.LINE);
		Icons.AUCTION.draw(m.ctx, x + 8, ry + 3, Colors.accent());
		Paint.label(m.ctx, m.textRenderer(), "Auctions", x + 20, ry + 3);
		ry += 16;
		AuctionStats a = state.auctions.stats(from);
		if (a.completed() == 0) {
			m.text("None finished in this range", x + 8, ry, Colors.INK_FAINT);
			return;
		}
		String[][] rows = {
			{"Completed", Integer.toString(a.completed())},
			{"Collected", Fmt.money(a.totalCollected())},
			{"Average", Fmt.money(a.average())},
			{"Biggest", a.biggest() == null ? "-" : Fmt.money(a.biggest().total())},
		};
		for (String[] row : rows) {
			if (ry > y + h - 10) {
				break;
			}
			m.text(row[0], x + 8, ry, Colors.INK_DIM);
			m.right(row[1], x + w - 8, ry, Colors.INK);
			ry += 11;
		}
	}

}
