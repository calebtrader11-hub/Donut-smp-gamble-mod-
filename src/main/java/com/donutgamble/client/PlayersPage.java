package com.donutgamble.client;

import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.TextBox;
import com.donutgamble.core.PlayerDirectory;
import com.donutgamble.core.PlayerStats;
import com.donutgamble.core.PrivacySettings.Surface;
import com.donutgamble.core.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searchable, sortable players with a profile: totals, outstanding balance, notes, tags and history. */
final class PlayersPage extends ManagerPage {
	private static final String KEY = "players";
	private static PlayerStats.View view = PlayerStats.View.RECENT;
	private static String selected;

	/** Player stats walk every bet and payment, so they're rebuilt only when something changed. */
	private List<PlayerStats> cache = List.of();
	private long cacheAt;
	private int cacheSize = -1;
	private TextBox tagBox;

	PlayersPage(ManagerScreen m) {
		super(m);
	}

	@Override
	ManagerScreen.Section section() {
		return ManagerScreen.Section.PLAYERS;
	}

	private int listW() {
		return Math.min(150, m.cw * 2 / 5);
	}

	@Override
	String scrollKey(double mx, double my) {
		return mx < m.cx + listW() ? KEY : null;
	}

	private List<PlayerStats> all() {
		int size = state.finance.transactions.size() + state.ledger.bets.size() * 31 + state.auctions.history.size() * 7;
		long now = System.currentTimeMillis();
		if (size != cacheSize || now - cacheAt > 2000) {
			cache = PlayerStats.all(state.ledger, state.finance, state.auctions);
			cacheSize = size;
			cacheAt = now;
		}
		return cache;
	}

	private List<PlayerStats> shown() {
		return PlayerStats.view(PlayerStats.filter(all(), ManagerScreen.draft("players.search")), view);
	}

	private PlayerStats current(List<PlayerStats> shown) {
		PlayerStats p = selected == null ? null : PlayerStats.find(all(), selected);
		if (p == null && !shown.isEmpty()) {
			p = shown.get(0);
			selected = p.player();
		}
		return p;
	}

	@Override
	void init() {
		int lw = listW();
		m.box("players.search", m.cx, m.cy, lw, "Search players").setChangedListener(s -> {
			ManagerScreen.DRAFTS.put("players.search", s);
			ManagerScreen.SCROLL.put(KEY, 0);
		});
		List<String> views = new ArrayList<>();
		for (PlayerStats.View v : PlayerStats.View.values()) {
			views.add(v.label);
		}
		m.dropdown(m.cx, m.cy + 20, lw, views, () -> view.ordinal(), i -> {
			view = PlayerStats.View.values()[i];
			ManagerScreen.SCROLL.put(KEY, 0);
		});

		PlayerStats p = current(shown());
		tagBox = null;
		if (p == null) {
			return;
		}
		int dx = m.cx + lw + ManagerScreen.GAP;
		int dw = m.cx + m.cw - dx;
		String name = p.player();
		tagBox = m.box("players.tag", dx + dw - 8 - 62, m.cy + 34, 62, "+ tag");
		tagBox.setMaxLength(PlayerDirectory.MAX_TAG);
		if (!m.hides(Surface.PLAYER_STATS)) {
			PlayerDirectory.Profile profile = state.players.get(name);
			TextBox notes = m.setting(dx + 8, notesY() + 11, dw - 16, profile == null ? "" : profile.notes,
				s -> state.players.setNotes(name, s));
			notes.setPlaceholder(net.minecraft.text.Text.literal("Notes about " + name).withColor(Colors.INK_FAINT));
		}
	}

	private int detailW() {
		return m.cx + m.cw - (m.cx + listW() + ManagerScreen.GAP);
	}

	/** Stat columns: four when there's room, else three. */
	private int cols() {
		return (detailW() - 16) / 4 >= 66 ? 4 : 3;
	}

	private int notesY() {
		int rows = (8 + cols() - 1) / cols();
		return m.cy + 60 + rows * 23 + 2;
	}

	@Override
	boolean enter(TextBox box) {
		if (box == tagBox && selected != null) {
			if (state.players.addTag(selected, ManagerScreen.draft("players.tag"))) {
				state.save();
			}
			ManagerScreen.DRAFTS.remove("players.tag");
			return true;
		}
		return false;
	}

	@Override
	void draw() {
		int cx = m.cx, cy = m.cy, ch = m.ch;
		int lw = listW();
		List<PlayerStats> list = shown();
		PlayerStats sel = current(list);
		boolean hideMoney = m.hides(Surface.PLAYER_STATS);
		boolean hideBids = m.hides(Surface.AUCTION_HUD);

		int top = cy + 40;
		int lh = cy + ch - top;
		m.card(cx, top, lw, lh);
		if (list.isEmpty()) {
			boolean none = all().isEmpty();
			m.empty(Icons.PLAYERS, none ? "No players yet" : "No one here",
				none ? "Anyone who pays shows up" : view == PlayerStats.View.RECENT ? "No match" : "Nobody fits this view", cx, top, lw, lh);
		} else {
			m.list(cx, top, lw, lh, list.size(), KEY, 15, (i, y) -> {
				PlayerStats p = list.get(i);
				boolean on = sel != null && p.player().equalsIgnoreCase(sel.player());
				if (on) {
					Paint.round(m.ctx, cx + 2, y, lw - 4, 15, 2, Colors.alpha(Colors.accent(), 0x2C));
					Paint.round(m.ctx, cx + 2, y + 3, 2, 9, 1, Colors.accent());
				}
				String value = listValue(p, hideMoney, hideBids);
				int vw = value == null ? 0 : m.width(value) + 6;
				m.text(m.fit(p.player(), lw - 18 - vw), cx + 8, y + 4, on ? Colors.INK : Colors.INK_DIM);
				if (value != null) {
					m.right(value, cx + lw - 8, y + 4, valueColor(p, value));
				}
				m.region(cx + 2, y, lw - 4, 15, () -> selected = p.player());
			});
		}

		int dx = cx + lw + ManagerScreen.GAP;
		int dw = cx + m.cw - dx;
		m.card(dx, cy, dw, ch);
		if (sel == null) {
			m.empty(Icons.PLAYERS, "Pick a player", null, dx, cy, dw, ch);
			return;
		}
		profile(sel, dx, cy, dw, ch, hideMoney, hideBids);
	}

	/** Right-hand value in the list for the current view, or null when hidden. */
	private String listValue(PlayerStats p, boolean hideMoney, boolean hideBids) {
		return switch (view) {
			case RECENT -> ManagerScreen.ago(p.lastSeen());
			case MOST_ACTIVE -> p.activity() + "";
			case BIGGEST -> hideMoney ? null : Fmt.money(p.paidYou());
			case OUTSTANDING -> hideMoney ? (p.elytrasOwed() > 0 ? p.elytrasOwed() + " ely" : null)
				: Fmt.money(p.outstanding()) + (p.elytrasOwed() > 0 ? " +" + p.elytrasOwed() + " ely" : "");
			case AUCTIONS -> hideBids ? p.auctionsJoined() + "×" : Fmt.money(p.auctionTotal());
		};
	}

	private int valueColor(PlayerStats p, String value) {
		return switch (view) {
			case OUTSTANDING -> Colors.GOLD;
			case BIGGEST -> Colors.GREEN;
			default -> Colors.INK_FAINT;
		};
	}

	private void profile(PlayerStats p, int dx, int cy, int dw, int ch, boolean hideMoney, boolean hideBids) {
		var ctx = m.ctx;
		// avatar: first letter on an accent tile
		Paint.roundGradient(ctx, dx + 8, cy + 8, 20, 20, 5, Colors.mix(Colors.accent(), 0xFFFFFFFF, 0.15f), Colors.mix(Colors.accent(), 0xFF000000, 0.25f));
		m.center(p.player().substring(0, 1).toUpperCase(Locale.ROOT), dx + 18, cy + 14, Colors.ON_ACCENT);
		m.text(m.fit(p.player(), dw - 110), dx + 34, cy + 9, Colors.INK);
		PlayerDirectory.Profile profile = state.players.get(p.player());
		String uuid = profile == null || profile.uuid == null ? "UUID not seen yet" : profile.uuid;
		m.text(m.fit(uuid, dw - 44), dx + 34, cy + 20, Colors.INK_FAINT);
		m.right("seen " + ManagerScreen.ago(p.lastSeen()), dx + dw - 8, cy + 9, Colors.INK_FAINT);

		// tags
		int tx = dx + 8;
		int ty = cy + 38;
		List<String> tags = state.players.tags(p.player());
		if (tags.isEmpty()) {
			m.text("No tags", tx, ty, Colors.INK_FAINT);
		}
		for (String tag : tags) {
			int w = m.width(tag) + 16;
			if (tx + w > dx + dw - 76) {
				break;
			}
			boolean over = m.in(tx, ty - 2, w, 11);
			Paint.pill(ctx, tx, ty - 2, w, 11, Colors.alpha(over ? Colors.RED : Colors.accent(), 0x30));
			m.text(tag, tx + 4, ty, over ? Colors.RED : Colors.mix(Colors.accent(), 0xFFFFFFFF, 0.3f));
			m.text("×", tx + w - 9, ty, over ? Colors.RED : Colors.INK_FAINT);
			m.region(tx, ty - 2, w, 11, () -> {
				state.players.removeTag(p.player(), tag);
				state.save();
			});
			tx += w + 3;
		}
		Paint.hline(ctx, dx + 1, dx + dw - 1, cy + 54, Colors.LINE);

		// stats
		String hidden = "Hidden";
		String[][] cells = {
			{"Paid you", hideMoney ? hidden : Fmt.money(p.paidYou())},
			{"You paid", hideMoney ? hidden : Fmt.money(p.youPaid())},
			{"Net", hideMoney ? hidden : Fmt.signed(p.net())},
			{"Owed", hideMoney ? hidden : Fmt.money(p.outstanding()) + (p.elytrasOwed() > 0 ? " +" + p.elytrasOwed() + " ely" : "")},
			{"W-L-T", p.wins() + "-" + p.losses() + "-" + p.ties()},
			{"Wagered", hideMoney ? hidden : Fmt.money(p.wagered())},
			{"Bids", hideBids ? hidden : Fmt.money(p.auctionTotal())},
			{"Joined/won", p.auctionsJoined() + " / " + p.auctionsWon()},
		};
		int cols = cols();
		int colW = (dw - 16) / cols;
		for (int i = 0; i < cells.length; i++) {
			int x = dx + 8 + (i % cols) * colW;
			int y = cy + 60 + (i / cols) * 23;
			String v = cells[i][1];
			int vc = v.startsWith("+") ? Colors.GREEN : v.startsWith("-") ? Colors.RED : i == 3 && !v.equals(hidden) && p.outstanding() > 0 ? Colors.GOLD : Colors.INK;
			m.stat(x, y, colW - 4, cells[i][0], v, vc, v.equals(hidden));
		}

		// notes (the box is a widget)
		int ny = notesY();
		Paint.label(ctx, m.textRenderer(), "Notes", dx + 8, ny);
		if (hideMoney) {
			Icons.LOCK.draw(ctx, dx + 8, ny + 15, Colors.INK_FAINT);
			m.text("Notes hidden in Streamer Mode", dx + 19, ny + 15, Colors.INK_FAINT);
		}

		// recent activity
		int hy = ny + 32;
		Paint.hline(ctx, dx + 1, dx + dw - 1, hy - 4, Colors.LINE);
		Paint.label(ctx, m.textRenderer(), "Recent activity", dx + 8, hy);
		List<Transaction> hist = state.finance.history(p.player());
		int rows = Math.max(0, (cy + ch - hy - 26) / 11);
		if (hist.isEmpty()) {
			m.text("No payments", dx + 8, hy + 13, Colors.INK_FAINT);
		}
		for (int i = 0; i < Math.min(rows, hist.size()); i++) {
			Transaction t = hist.get(i);
			int y = hy + 13 + i * 11;
			m.text(ManagerScreen.shortTime(t.timestamp), dx + 8, y, Colors.INK_FAINT);
			String what = PaymentsPage.linked(state, t);
			m.text(m.fit(what, dw - 110), dx + 40, y, t.voided ? Colors.INK_FAINT : Colors.INK_DIM);
			if (!hideMoney) {
				int c = t.voided ? Colors.INK_FAINT : t.type == Transaction.Type.INCOMING ? Colors.GREEN : Colors.RED;
				m.right(Fmt.money(t.amount), dx + dw - 8, y, c);
			}
		}
		if (!m.hides(Surface.FINANCE)) {
			m.link("All their payments →", dx + 8, cy + ch - 13, Colors.accent(), () -> {
				ManagerScreen.DRAFTS.put("payments.search", p.player());
				m.show(ManagerScreen.Section.PAYMENTS);
			});
		}
	}
}
