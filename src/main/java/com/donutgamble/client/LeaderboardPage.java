package com.donutgamble.client;

import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.Auction;
import com.donutgamble.core.PrivacySettings.Surface;
import com.donutgamble.core.Standing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Podium and full ranking for the latest auction, or across every auction. */
final class LeaderboardPage extends ManagerPage {
	private static final String KEY = "leaderboard";
	static int scope;
	private final RankingView ranking = new RankingView();
	private List<Standing> allTime = List.of();
	private int allTimeStamp = -1;

	LeaderboardPage(ManagerScreen m) {
		super(m);
	}

	@Override
	ManagerScreen.Section section() {
		return ManagerScreen.Section.LEADERBOARD;
	}

	@Override
	String scrollKey(double mx, double my) {
		return KEY + scope;
	}

	@Override
	void init() {
		m.segmented(m.cx, m.cy, 130, List.of("Latest", "All time"), () -> scope, i -> scope = i);
	}

	/** Everyone's total across all auctions (cancelled ones are refunded, so left out). */
	private List<Standing> allTime() {
		int stamp = state.auctions.history.size() * 1_000_003 + (state.auctions.current == null ? 0 : state.auctions.current.contributions.size());
		if (stamp == allTimeStamp) {
			return allTime;
		}
		Map<String, long[]> totals = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		List<Auction> list = new ArrayList<>(state.auctions.history);
		if (state.auctions.current != null) {
			list.add(state.auctions.current);
		}
		long sum = 0;
		for (Auction a : list) {
			if (a.status == Auction.Status.CANCELLED) {
				continue;
			}
			for (Standing s : a.rankings()) {
				String k = s.player().toLowerCase(Locale.ROOT);
				names.putIfAbsent(k, s.player());
				long[] v = totals.computeIfAbsent(k, x -> new long[]{0, 0, s.firstAt()});
				v[0] += s.total();
				v[1]++;
				sum += s.total();
			}
		}
		List<Standing> out = new ArrayList<>();
		long total = Math.max(1, sum);
		totals.forEach((k, v) -> out.add(new Standing(names.get(k), v[0], (int) v[1], (double) v[0] / total, v[2])));
		out.sort((a, b) -> a.total() != b.total() ? Long.compare(b.total(), a.total()) : Long.compare(a.firstAt(), b.firstAt()));
		allTime = out;
		allTimeStamp = stamp;
		return out;
	}

	@Override
	void draw() {
		int cx = m.cx, cy = m.cy, cw = m.cw, ch = m.ch;
		boolean amounts = !m.hides(Surface.AUCTION_HUD);
		Auction a = state.auctions.latest();
		List<Standing> r;
		if (scope == 0) {
			if (a == null) {
				m.card(cx, cy + 22, cw, ch - 22);
				m.empty(Icons.TROPHY, "No auctions yet", "Rankings appear as soon as someone pays", cx, cy + 22, cw, ch - 22);
				return;
			}
			r = a.rankings();
			String status = a.isLive() ? "live" : a.status.name().toLowerCase(Locale.ROOT);
			m.right(m.fit(a.item, cw - 220) + " · " + status, cx + cw, cy + 4, Colors.INK_DIM);
			ranking.reset(a);
		} else {
			r = allTime();
			m.right(r.size() + " players", cx + cw, cy + 4, Colors.INK_DIM);
			ranking.reset(this);
		}
		int top = cy + 22;
		if (r.isEmpty()) {
			m.card(cx, top, cw, ch - 22);
			m.empty(Icons.TROPHY, "No bids", null, cx, top, cw, ch - 22);
			return;
		}

		// podium: 2nd, 1st, 3rd
		int pw = (cw - 2 * ManagerScreen.GAP) / 3;
		int[] order = {1, 0, 2};
		for (int k = 0; k < 3; k++) {
			int i = order[k];
			int px = cx + k * (pw + ManagerScreen.GAP);
			int lift = i == 0 ? 0 : 8;
			int ph = 52 - lift;
			int py = top + lift;
			int medal = i == 0 ? Colors.GOLD : i == 1 ? Colors.GREY : 0xFFD08A5B;
			if (i >= r.size()) {
				Paint.card(m.ctx, px, py, pw, ph, 4, Colors.alpha(Colors.CARD, 0x80), Colors.LINE);
				m.center("-", px + pw / 2, py + ph / 2 - 4, Colors.INK_FAINT);
				continue;
			}
			Standing s = r.get(i);
			Paint.round(m.ctx, px, py, pw, ph, 4, Colors.LINE);
			Paint.roundGradient(m.ctx, px + 1, py + 1, pw - 2, ph - 2, 3, Colors.mix(Colors.CARD_TOP, medal, 0.14f), Colors.CARD);
			Paint.round(m.ctx, px + pw / 2 - 9, py + 6, 18, 12, 3, Colors.alpha(medal, 0x40));
			m.center(Integer.toString(i + 1), px + pw / 2, py + 8, medal);
			m.center(m.fit(s.player(), pw - 8), px + pw / 2, py + 22, i == 0 ? Colors.GOLD : Colors.INK);
			String sub = amounts ? Fmt.money(s.total()) : Math.round(s.share() * 100) + "%";
			m.center(sub, px + pw / 2, py + 33, Colors.INK_DIM);
		}

		int ly = top + 58;
		int lh = cy + ch - ly;
		m.card(cx, ly, cw, lh);
		int rowH = 14;
		int visible = Math.max(1, (lh - 6) / rowH);
		int max = Math.max(0, r.size() - visible);
		int first = Math.min(ManagerScreen.SCROLL.getOrDefault(KEY + scope, 0), max);
		ManagerScreen.SCROLL.put(KEY + scope, first);
		m.ctx.enableScissor(cx + 1, ly + 1, cx + cw - 1, ly + lh - 1);
		ranking.draw(m.ctx, m.textRenderer(), r, cx + 4, ly + 3, cw - 8, rowH, first, visible, amounts, true);
		m.ctx.disableScissor();
	}
}
