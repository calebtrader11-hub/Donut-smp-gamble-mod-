package com.donutgamble.client;

import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.Auction;
import com.donutgamble.core.PrivacySettings.Surface;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Every finished auction with its recap, and exports. */
final class HistoryPage extends ManagerPage {
	private static final String KEY = "history";
	private static long selectedId;

	HistoryPage(ManagerScreen m) {
		super(m);
	}

	@Override
	ManagerScreen.Section section() {
		return ManagerScreen.Section.HISTORY;
	}

	private int listW() {
		return Math.min(170, m.cw * 2 / 5);
	}

	@Override
	String scrollKey(double mx, double my) {
		return mx < m.cx + listW() ? KEY : null;
	}

	@Override
	void init() {
		m.headerButton("JSON", Button.Variant.SECONDARY, null, () -> state.exportAuctions(true));
		m.headerButton("Export CSV", Button.Variant.SECONDARY, Icons.HISTORY, () -> state.exportAuctions(false));
	}

	@Override
	void draw() {
		int cx = m.cx, cy = m.cy, cw = m.cw, ch = m.ch;
		List<Auction> list = new ArrayList<>(state.auctions.history);
		Collections.reverse(list);
		if (list.isEmpty()) {
			m.card(cx, cy, cw, ch);
			m.empty(Icons.HISTORY, "No finished auctions", "Each one is kept here with its full ranking", cx, cy, cw, ch);
			return;
		}
		int lw = listW();
		m.card(cx, cy, lw, ch);
		Auction found = state.auctions.find(selectedId);
		Auction sel = found == null || found.isLive() ? list.get(0) : found;
		selectedId = sel.id;
		boolean amounts = !m.hides(Surface.AUCTION_HUD);
		m.list(cx, cy, lw, ch, list.size(), KEY, 26, (i, y) -> {
			Auction a = list.get(i);
			boolean on = a.id == sel.id;
			if (on) {
				Paint.round(m.ctx, cx + 2, y, lw - 4, 26, 3, Colors.alpha(Colors.accent(), 0x2C));
				Paint.round(m.ctx, cx + 2, y + 6, 2, 14, 1, Colors.accent());
			}
			ItemStack prize = ItemIcons.of(a);
			int tx = prize.isEmpty() ? cx + 8 : cx + 27;
			m.icon(prize, cx + 7, y + 5, 1f);
			int moneyW = amounts ? m.width(Fmt.money(a.total())) + 6 : 0;
			m.text(m.fit(a.item, lw - (tx - cx) - 8 - moneyW), tx, y + 4, on ? Colors.INK : Colors.INK_DIM);
			if (amounts) {
				m.right(Fmt.money(a.total()), cx + lw - 8, y + 4, Colors.INK);
			}
			String who = a.winner != null ? a.winner : a.status.name().toLowerCase(Locale.ROOT);
			m.text(m.fit(ManagerScreen.date(a.startedAt) + " · " + who, lw - (tx - cx) - 8), tx, y + 15,
				a.winner != null ? Colors.mix(Colors.GOLD, Colors.INK_FAINT, 0.4f) : Colors.INK_FAINT);
			long id = a.id;
			m.region(cx + 2, y, lw - 4, 26, () -> selectedId = id);
		});

		int dx = cx + lw + ManagerScreen.GAP;
		int dw = cx + cw - dx;
		m.card(dx, cy, dw, ch);
		ItemStack prize = ItemIcons.of(sel);
		if (!prize.isEmpty()) {
			m.itemTile(prize, dx + dw - 28, cy + 6);
		}
		List<String> lines = new ArrayList<>(sel.summary(!m.hides(Surface.FINANCE)));
		if (!amounts) {
			lines.removeIf(l -> l.startsWith("Total ") || l.startsWith("#"));
			lines.replaceAll(l -> l.startsWith("Winner:") ? l.replaceAll(" \\(\\$[^)]*\\)", "") : l);
		}
		int sy = cy + 9;
		int limit = cy + ch - 22;
		for (int i = 0; i < lines.size() && sy < limit; i++) {
			String line = lines.get(i);
			int color = line.startsWith("Winner") ? Colors.GOLD : line.startsWith("#") ? Colors.INK_DIM
				: line.startsWith("Prize cost") ? Colors.INK_FAINT : Colors.INK;
			Paint.record(line);
			for (var wrapped : m.textRenderer().wrapLines(Text.literal(line), dw - 16 - (i == 0 && !prize.isEmpty() ? 28 : 0))) {
				if (sy >= limit) {
					break;
				}
				m.ctx.drawText(m.textRenderer(), wrapped, dx + 8, sy, color, false);
				sy += 11;
			}
			if (i == 0) {
				Paint.hline(m.ctx, dx + 1, dx + dw - 1, Math.max(sy + 1, cy + 30), Colors.LINE);
				sy = Math.max(sy + 6, cy + 35);
			}
		}
		long open = state.auctions.openRefunds().stream().filter(rf -> rf.auctionId == sel.id).count();
		if (open > 0) {
			m.link(open + (open == 1 ? " refund" : " refunds") + " still owed →", dx + 8, cy + ch - 14, Colors.GOLD, () -> {
				state.config.activeTab = QueueHud.Tab.OWE.name();
				state.save();
				m.close();
			});
		}
	}
}
