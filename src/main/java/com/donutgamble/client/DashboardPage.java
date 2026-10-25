package com.donutgamble.client;

import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.Auction;
import com.donutgamble.core.AuctionStats;
import com.donutgamble.core.Bet;
import com.donutgamble.core.FinanceReport;
import com.donutgamble.core.Game;
import com.donutgamble.core.Refund;
import com.donutgamble.core.Standing;
import com.donutgamble.core.Stats;
import com.donutgamble.core.Transaction;
import com.donutgamble.core.PrivacySettings.Surface;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command center: live auction, session and lifetime figures, games, payouts to send, recent
 * payments and auction statistics, as cards the streamer can reorder or hide.
 */
final class DashboardPage extends ManagerPage {
	static final Map<String, String> CARDS = new LinkedHashMap<>();

	static {
		CARDS.put("LIVE_AUCTION", "Live auction");
		CARDS.put("SESSION", "This session");
		CARDS.put("QUICK_ACTIONS", "Quick actions");
		CARDS.put("GAMES", "Games");
		CARDS.put("PAYOUTS", "Payouts to send");
		CARDS.put("RECENT_PAYMENTS", "Recent payments");
		CARDS.put("LIFETIME", "Lifetime");
		CARDS.put("AUCTION_STATS", "Auction stats");
	}

	private static final int HALF_H = 70;
	private static final String SCROLL_KEY = "dashboard";
	static boolean customizing;
	private int contentH;

	DashboardPage(ManagerScreen m) {
		super(m);
	}

	@Override
	ManagerScreen.Section section() {
		return ManagerScreen.Section.DASHBOARD;
	}

	@Override
	void init() {
		m.headerButton(customizing ? "Done" : "Customize", customizing ? Button.Variant.PRIMARY : Button.Variant.SECONDARY,
			Icons.LAYOUT, () -> customizing = !customizing);
	}

	@Override
	boolean scroll(double mx, double my, double vertical) {
		int max = Math.max(0, contentH - m.ch);
		int v = ManagerScreen.SCROLL.getOrDefault(SCROLL_KEY, 0) - (int) Math.signum(vertical) * 24;
		ManagerScreen.SCROLL.put(SCROLL_KEY, Math.max(0, Math.min(max, v)));
		return true;
	}

	/** Cards to show, in order: known ids only, no repeats. */
	private List<String> cards() {
		List<String> out = new ArrayList<>();
		for (String id : state.config.dashboardCards) {
			if (CARDS.containsKey(id) && !out.contains(id)) {
				out.add(id);
			}
		}
		return out;
	}

	private static boolean wide(String id) {
		return id.equals("LIVE_AUCTION") || id.equals("RECENT_PAYMENTS");
	}

	private static int height(String id) {
		return switch (id) {
			case "LIVE_AUCTION" -> 66;
			case "RECENT_PAYMENTS" -> 92;
			default -> HALF_H;
		};
	}

	@Override
	void draw() {
		int cx = m.cx, cy = m.cy, cw = m.cw, ch = m.ch;
		int off = Math.min(ManagerScreen.SCROLL.getOrDefault(SCROLL_KEY, 0), Math.max(0, contentH - ch));
		int gap = ManagerScreen.GAP;
		int half = (cw - gap) / 2;
		int[] col = {cy - off, cy - off};
		List<String> cards = cards();
		for (int i = 0; i < cards.size(); i++) {
			String id = cards.get(i);
			int h = height(id);
			int x, y, w;
			if (wide(id)) {
				x = cx;
				y = Math.max(col[0], col[1]);
				w = cw;
				col[0] = col[1] = y + h + gap;
			} else {
				int c = col[0] <= col[1] ? 0 : 1;
				x = cx + c * (half + gap);
				y = col[c];
				w = c == 0 ? half : cw - half - gap;
				col[c] += h + gap;
			}
			if (y + h >= cy - 2 && y <= cy + ch + 2) {
				drawCard(id, x, y, w, h);
				if (customizing) {
					controls(cards, i, x, y, w, h);
				}
			}
		}
		int bottom = Math.max(col[0], col[1]);
		if (cards.isEmpty() && !customizing) {
			m.card(cx, bottom, cw, 60);
			m.empty(Icons.DASHBOARD, "Every card is hidden", "Press Customize to add some back", cx, bottom, cw, 60);
			bottom += 66;
		}
		if (customizing) {
			bottom = hiddenCards(cards, cx, bottom, cw) + gap;
		}
		contentH = bottom + off - cy - gap;
		if (contentH > ch) {
			int trackH = ch - 4;
			int thumbH = Math.max(14, trackH * ch / contentH);
			int thumbY = cy + 2 + (trackH - thumbH) * off / Math.max(1, contentH - ch);
			Paint.round(m.ctx, cx + cw + 1, thumbY, 2, thumbH, 1, Colors.LINE_HI);
		}
	}

	private void drawCard(String id, int x, int y, int w, int h) {
		m.card(x, y, w, h);
		switch (id) {
			case "LIVE_AUCTION" -> liveAuction(x, y, w, h);
			case "SESSION" -> money(x, y, w, "This session", state.finance.report(state.ledger.sessionStart),
				state.ledger.sessionStats(), Fmt.duration(System.currentTimeMillis() - state.ledger.sessionStart));
			case "LIFETIME" -> money(x, y, w, "Lifetime", state.finance.report(0), state.ledger.lifetimeStats(), "all time");
			case "QUICK_ACTIONS" -> quickActions(x, y, w);
			case "GAMES" -> games(x, y, w);
			case "PAYOUTS" -> payouts(x, y, w, h);
			case "RECENT_PAYMENTS" -> recentPayments(x, y, w, h);
			case "AUCTION_STATS" -> auctionStats(x, y, w);
			default -> {
			}
		}
	}

	// ---- cards ----

	private void liveAuction(int x, int y, int w, int h) {
		Auction a = state.auctions.current;
		long now = System.currentTimeMillis();
		if (a == null) {
			Auction done = state.recentResult();
			if (done != null && done.winner != null) {
				m.cardTitle(x, y, "Auction finished", Icons.TROPHY);
				m.itemTile(ItemIcons.of(done), x + 8, y + 22);
				m.text(m.fit(done.item, w - 140), x + 34, y + 24, Colors.INK);
				m.text("Winner " + done.winner, x + 34, y + 36, Colors.GOLD);
				m.link("View results →", x + w - 8 - m.width("View results →"), y + 7, Colors.accent(), () -> m.show(ManagerScreen.Section.AUCTION));
				return;
			}
			m.cardTitle(x, y, "Live auction", Icons.AUCTION);
			m.text("No auction running", x + 8, y + 26, Colors.INK_DIM);
			m.text(m.fit("Pick any item as the prize; every /pay counts toward it.", w - 120), x + 8, y + 38, Colors.INK_FAINT);
			m.action(x + w - 104, y + 26, 96, 18, Icons.PLUS, "Create auction", true, this::openSetup);
			return;
		}
		boolean paused = a.status == Auction.Status.PAUSED;
		m.cardTitle(x, y, "Live auction", Icons.AUCTION);
		String status = paused ? "PAUSED" : "LIVE";
		int sc = paused ? Colors.GOLD : Colors.GREEN;
		int bw = m.width(status) + 14;
		Paint.pill(m.ctx, x + w - 8 - bw, y + 5, bw, 12, Colors.alpha(sc, 0x33));
		if (!paused) {
			float pulse = 0.5f + 0.5f * (float) Math.sin(now / 250.0);
			Paint.round(m.ctx, x + w - 8 - bw + 4, y + 9, 4, 4, 2, Colors.fade(sc, 0.4f + 0.6f * pulse));
		}
		m.text(status, x + w - 8 - bw + (paused ? 4 : 10), y + 7, sc);

		long left = a.remaining(now);
		String clock = Fmt.clock(left);
		int clockW = m.width(clock) * 2;
		int clockColor = paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.INK;
		Paint.big(m.ctx, m.textRenderer(), clock, x + w - 8 - clockW, y + 24, 2, clockColor, false);

		m.itemTile(ItemIcons.of(a), x + 8, y + 22);
		int tx = x + 34;
		int tw = w - 34 - clockW - 16;
		m.text(m.fit(a.item, tw), tx, y + 22, Colors.INK);
		boolean amounts = !m.hides(Surface.AUCTION_HUD);
		int bidders = a.rankings().size();
		String sub = bidders + (bidders == 1 ? " bidder" : " bidders") + (amounts ? " · " + Fmt.money(a.total()) : "");
		m.text(m.fit(sub, tw), tx, y + 33, Colors.INK_DIM);
		Standing leader = a.leader();
		String lead = leader == null ? "No bids yet" : "Leader " + leader.player() + (amounts ? " " + Fmt.money(leader.total()) : "");
		m.text(m.fit(lead, tw), tx, y + 44, leader == null ? Colors.INK_FAINT : Colors.GOLD);
		float frac = (float) left / Math.max(1, Math.max(a.durationMs, left));
		Paint.bar(m.ctx, x + 8, y + h - 9, w - 16, 3, frac, Colors.LINE, paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.accent());
		m.region(x, y, w, h - 12, () -> m.show(ManagerScreen.Section.AUCTION));
	}

	private void money(int x, int y, int w, String title, FinanceReport r, Stats games, String note) {
		m.cardTitle(x, y, title, title.equals("Lifetime") ? Icons.FINANCE : Icons.COIN);
		m.right(note, x + w - 8, y + 7, Colors.INK_FAINT);
		boolean hide = m.hides(Surface.DASHBOARD);
		int cw = (w - 16) / 2;
		int played = games.playerWins() + games.playerLosses() + games.ties();
		m.stat(x + 8, y + 22, cw - 4, "Net", Fmt.signed(r.net()), Colors.profit(r.net()), hide);
		m.stat(x + 8 + cw, y + 22, cw - 4, "Received", Fmt.money(r.received()), Colors.INK, hide);
		m.stat(x + 8, y + 45, cw - 4, "Paid out", Fmt.money(r.paidOut() + r.expenses()), Colors.INK, hide);
		m.stat(x + 8 + cw, y + 45, cw - 4, "Games played", Integer.toString(played), Colors.INK, false);
	}

	private void quickActions(int x, int y, int w) {
		m.cardTitle(x, y, "Quick actions", Icons.PLAY);
		int bw = (w - 20) / 2;
		int bh = 14;
		int x1 = x + 8, x2 = x + 12 + bw;
		int y1 = y + 21;
		boolean live = state.auctions.current != null;
		m.action(x1, y1, bw, bh, live ? Icons.AUCTION : Icons.PLUS, live ? "Auction" : "Auction", true,
			live ? () -> m.show(ManagerScreen.Section.AUCTION) : this::openSetup);
		boolean on = state.config.privacy.streamerMode;
		m.action(x2, y1, bw, bh, on ? Icons.LOCK : Icons.EYE, on ? "Streaming" : "Streamer", on, state::toggleStreamerMode);
		m.action(x1, y1 + bh + 2, bw, bh, Icons.GAME, "Paper", false, () -> openQueue(QueueHud.Tab.PAPER));
		m.action(x2, y1 + bh + 2, bw, bh, Icons.GAME, "Blackjack", false, () -> openQueue(QueueHud.Tab.BLACKJACK));
		m.action(x1, y1 + 2 * (bh + 2), bw, bh, Icons.GAME, "Ely", false, () -> openQueue(QueueHud.Tab.ELY));
		m.action(x2, y1 + 2 * (bh + 2), bw, bh, Icons.PAYMENTS, "Payments", false, () -> m.show(ManagerScreen.Section.PAYMENTS));
	}

	private void openSetup() {
		MinecraftClient.getInstance().setScreen(new AuctionSetupScreen(state, m));
	}

	/**
	 * Games start when someone pays, so "start" means: show that game's queue and free the mouse
	 * to call results.
	 */
	private void openQueue(QueueHud.Tab tab) {
		state.config.activeTab = tab.name();
		var slot = state.config.hud.slots.get("queue");
		if (slot != null) {
			slot.visible = true;
		}
		state.save();
		MinecraftClient.getInstance().setScreen(new QueueScreen(DonutGambleClient.hud));
	}

	private void games(int x, int y, int w) {
		m.cardTitle(x, y, "Games", Icons.GAME);
		int waiting = state.ledger.pending().size();
		m.right(waiting + " waiting", x + w - 8, y + 7, waiting > 0 ? Colors.accent() : Colors.INK_FAINT);
		boolean hide = m.hides(Surface.DASHBOARD);
		int ry = y + 22;
		for (Game g : Game.values()) {
			Stats s = state.ledger.sessionStats(g);
			int q = state.ledger.pending(g).size();
			float hv = m.hover("game." + g, m.in(x + 3, ry - 2, w - 6, 13));
			if (hv > 0.01f) {
				Paint.round(m.ctx, x + 3, ry - 2, w - 6, 13, 2, Colors.fade(Colors.CARD_HI, hv));
			}
			String count = Integer.toString(q);
			int cw = m.width(count) + 8;
			Paint.pill(m.ctx, x + 8, ry - 1, cw, 10, q > 0 ? Colors.alpha(Colors.accent(), 0x40) : Colors.alpha(Colors.LINE_HI, 0x80));
			m.center(count, x + 8 + cw / 2, ry, q > 0 ? Colors.INK : Colors.INK_FAINT);
			String wlt = s.playerWins() + "-" + s.playerLosses() + "-" + s.ties();
			String value = hide ? wlt : Fmt.signed(s.net());
			int vw = m.width(value);
			m.text(m.fit(g.displayName, w - 30 - cw - vw), x + 12 + cw, ry, Colors.INK);
			m.right(value, x + w - 8, ry, hide ? Colors.INK_DIM : Colors.profit(s.net()));
			QueueHud.Tab tab = QueueHud.Tab.valueOf(g == Game.ELY ? "ELY" : g.name());
			m.region(x + 3, ry - 2, w - 6, 13, () -> openQueue(tab));
			ry += 14;
		}
	}

	private void payouts(int x, int y, int w, int h) {
		m.cardTitle(x, y, "Payouts to send", Icons.WARN);
		List<Bet> owed = state.ledger.owed();
		List<Refund> refunds = state.auctions.openRefunds();
		int n = owed.size() + refunds.size();
		m.right(Integer.toString(n), x + w - 8, y + 7, n > 0 ? Colors.GOLD : Colors.INK_FAINT);
		if (n == 0) {
			Icons.CHECK.draw(m.ctx, x + 8, y + 32, Colors.GREEN);
			m.text("All paid up", x + 20, y + 32, Colors.INK_DIM);
			m.text("Winners and refunds show here", x + 8, y + 46, Colors.INK_FAINT);
			return;
		}
		int ry = y + 22;
		int shown = 0;
		int rows = (h - 26) / 12;
		for (Bet b : owed) {
			if (shown >= rows - (n > rows ? 1 : 0)) {
				break;
			}
			payoutRow(x, ry, w, b.player, Fmt.owedLabel(b), b.game.displayName, () -> state.prefillPay(b));
			ry += 12;
			shown++;
		}
		for (Refund r : refunds) {
			if (shown >= rows - (n > rows ? 1 : 0)) {
				break;
			}
			payoutRow(x, ry, w, r.player, Fmt.money(r.amount), "refund", () -> state.prefillPay(r.player, r.amount));
			ry += 12;
			shown++;
		}
		if (n > shown) {
			m.link("+" + (n - shown) + " more in the Owe tab", x + 8, ry, Colors.accent(), () -> openQueue(QueueHud.Tab.OWE));
		}
	}

	private void payoutRow(int x, int y, int w, String player, String amount, String what, Runnable pay) {
		boolean over = m.in(x + 3, y - 2, w - 6, 12);
		if (over) {
			Paint.round(m.ctx, x + 3, y - 2, w - 6, 12, 2, Colors.CARD_HI);
		}
		String shown = over ? "/pay →" : amount;
		int aw = m.width(shown);
		boolean room = w >= 190;
		m.text(m.fit(player, (room ? w / 2 : w - 24 - aw) - 8), x + 8, y, Colors.INK);
		m.right(shown, x + w - 8, y, over ? Colors.accent() : Colors.GOLD);
		if (room) {
			m.text(m.fit(what, w / 2 - aw - 16), x + w / 2, y, Colors.INK_FAINT);
		}
		m.region(x + 3, y - 2, w - 6, 12, pay);
	}

	private void recentPayments(int x, int y, int w, int h) {
		m.cardTitle(x, y, "Recent payments", Icons.PAYMENTS);
		if (!m.hides(Surface.FINANCE)) {
			String all = "View all →";
			m.link(all, x + w - 8 - m.width(all), y + 7, Colors.accent(), () -> m.show(ManagerScreen.Section.PAYMENTS));
		}
		List<Transaction> recent = state.finance.search("");
		if (recent.isEmpty()) {
			m.empty(null, "No payments yet", "They show up here the moment the chat message arrives", x, y + 10, w, h - 10);
			return;
		}
		boolean hide = m.hides(Surface.DASHBOARD);
		int rows = (h - 26) / 13;
		for (int i = 0; i < Math.min(rows, recent.size()); i++) {
			PaymentsPage.row(m, state, recent.get(i), x + 8, y + 22 + i * 13, w - 16, hide, false);
		}
	}

	private void auctionStats(int x, int y, int w) {
		m.cardTitle(x, y, "Auctions", Icons.TROPHY);
		AuctionStats all = state.auctions.stats(0);
		AuctionStats session = state.auctions.stats(state.ledger.sessionStart);
		m.right(session.completed() + " this session", x + w - 8, y + 7, Colors.INK_FAINT);
		if (all.completed() == 0) {
			m.text("No finished auctions yet", x + 8, y + 30, Colors.INK_DIM);
			m.text("Totals and records appear here", x + 8, y + 42, Colors.INK_FAINT);
			return;
		}
		boolean hide = m.hides(Surface.AUCTION_HUD);
		int cw = (w - 16) / 2;
		m.stat(x + 8, y + 22, cw - 4, "Completed", Integer.toString(all.completed()), Colors.INK, false);
		m.stat(x + 8 + cw, y + 22, cw - 4, "Collected", Fmt.money(all.totalCollected()), Colors.GREEN, hide);
		m.stat(x + 8, y + 45, cw - 4, "Average", Fmt.money(all.average()), Colors.INK, hide);
		Auction big = all.biggest();
		m.stat(x + 8 + cw, y + 45, cw - 4, "Biggest", big == null ? "-" : Fmt.money(big.total()) + " " + big.item, Colors.GOLD, hide);
	}

	// ---- customizing ----

	private void controls(List<String> cards, int i, int x, int y, int w, int h) {
		Paint.round(m.ctx, x, y, w, h, 4, Colors.alpha(Colors.WINDOW, 0xE8));
		Paint.card(m.ctx, x, y, w, h, 4, 0, Colors.alpha(Colors.accent(), 0x90));
		Paint.round(m.ctx, x + 1, y + 1, w - 2, h - 2, 3, Colors.alpha(Colors.WINDOW_LOW, 0xF0));
		int bx = x + w - 8;
		int by = y + h - 22;
		String[] labels = {"Hide", "Down", "Up"};
		for (String label : labels) {
			boolean enabled = switch (label) {
				case "Up" -> i > 0;
				case "Down" -> i < cards.size() - 1;
				default -> true;
			};
			int bw = m.width(label) + 10;
			bx -= bw;
			if (enabled) {
				m.action(bx, by, bw, 14, null, label, label.equals("Hide"), () -> move(cards, i, label));
			}
			bx -= 3;
		}
		Icons.LAYOUT.draw(m.ctx, x + 8, y + 8, Colors.accent());
		m.text(CARDS.get(cards.get(i)), x + 20, y + 8, Colors.INK);
		m.text("#" + (i + 1) + (wide(cards.get(i)) ? " · full width" : " · half width"), x + 8, y + 22, Colors.INK_FAINT);
	}

	private void move(List<String> cards, int i, String how) {
		List<String> list = new ArrayList<>(cards);
		switch (how) {
			case "Up" -> java.util.Collections.swap(list, i, i - 1);
			case "Down" -> java.util.Collections.swap(list, i, i + 1);
			default -> list.remove(i);
		}
		state.config.dashboardCards = list;
		state.save();
	}

	/** The row of hidden cards (click to add back) and Reset. Returns its bottom. */
	private int hiddenCards(List<String> cards, int x, int y, int w) {
		m.card(x, y, w, 40);
		m.cardTitle(x, y, "Hidden cards: click to add", Icons.PLUS);
		String reset = "Reset";
		m.link(reset, x + w - 8 - m.width(reset), y + 7, Colors.accent(), () -> {
			state.config.dashboardCards = new ArrayList<>(new com.donutgamble.core.GambleConfig().dashboardCards);
			state.save();
		});
		int bx = x + 8;
		boolean any = false;
		for (String id : CARDS.keySet()) {
			if (cards.contains(id)) {
				continue;
			}
			any = true;
			int bw = m.width(CARDS.get(id)) + 22;
			if (bx + bw > x + w - 8) {
				break;
			}
			m.action(bx, y + 21, bw, 14, Icons.PLUS, CARDS.get(id), false, () -> {
				List<String> list = new ArrayList<>(cards);
				list.add(id);
				state.config.dashboardCards = list;
				state.save();
			});
			bx += bw + 4;
		}
		if (!any) {
			m.text("Every card is showing", x + 8, y + 24, Colors.INK_FAINT);
		}
		return y + 40;
	}
}
