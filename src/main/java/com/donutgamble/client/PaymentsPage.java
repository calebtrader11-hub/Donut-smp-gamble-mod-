package com.donutgamble.client;

import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Auction;
import com.donutgamble.core.Bet;
import com.donutgamble.core.PrivacySettings.Surface;
import com.donutgamble.core.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every payment in and out: search, categories, status, what it's linked to, void and manual entries. */
final class PaymentsPage extends ManagerPage {
	enum Filter {
		ALL("All payments"),
		IN("Received"),
		OUT("Sent"),
		GAMBLE("Gambling"),
		AUCTION("Auctions"),
		EXPENSE("Expenses"),
		MANUAL("Added by hand"),
		VOIDED("Voided");

		final String label;

		Filter(String label) {
			this.label = label;
		}

		boolean test(Transaction t) {
			return switch (this) {
				case ALL -> true;
				case IN -> t.type == Transaction.Type.INCOMING;
				case OUT -> t.type == Transaction.Type.PAYOUT;
				case GAMBLE -> t.source == Transaction.Source.GAMBLE;
				case AUCTION -> t.source == Transaction.Source.AUCTION;
				case EXPENSE -> t.type == Transaction.Type.EXPENSE;
				case MANUAL -> t.manual;
				case VOIDED -> t.voided;
			};
		}
	}

	private static Filter filter = Filter.ALL;
	private static final String KEY = "payments";

	PaymentsPage(ManagerScreen m) {
		super(m);
	}

	@Override
	ManagerScreen.Section section() {
		return ManagerScreen.Section.PAYMENTS;
	}

	@Override
	String scrollKey(double mx, double my) {
		return KEY;
	}

	@Override
	void init() {
		if (m.hides(Surface.FINANCE)) {
			return;
		}
		m.headerButton("JSON", Button.Variant.SECONDARY, null, () -> state.exportTransactions(true));
		m.headerButton("Export CSV", Button.Variant.SECONDARY, Icons.HISTORY, () -> state.exportTransactions(false));
		m.box("payments.search", m.cx, m.cy, 140, "Search player, 200m, note").setChangedListener(s -> {
			ManagerScreen.DRAFTS.put("payments.search", s);
			ManagerScreen.SCROLL.put(KEY, 0);
		});
		List<String> labels = new ArrayList<>();
		for (Filter f : Filter.values()) {
			labels.add(f.label);
		}
		m.dropdown(m.cx + 144, m.cy, 96, labels, () -> filter.ordinal(), i -> {
			filter = Filter.values()[i];
			ManagerScreen.SCROLL.put(KEY, 0);
		});

		int y = m.cy + m.ch - 16;
		m.box("payments.player", m.cx + 26, y, 84, "Player");
		m.box("payments.amount", m.cx + 114, y, 58, "Amount");
		Button in = m.button(m.cx + 176, y, "+ Received", Button.Variant.SECONDARY, () -> manual(true));
		m.button(in.getRight() + 4, y, "+ Sent", Button.Variant.SECONDARY, () -> manual(false));
	}

	private void manual(boolean incoming) {
		String player = ManagerScreen.draft("payments.player");
		long amount = AmountParser.parseOrZero(ManagerScreen.draft("payments.amount"));
		if (player.isEmpty() || amount <= 0) {
			state.notify(com.donutgamble.core.NotificationSettings.Kind.SYSTEM, "Enter a player and an amount like 50m", null,
				Colors.ORANGE, null);
			return;
		}
		String verb = incoming ? "received from " : "sent to ";
		m.confirm("Add a payment by hand?", "Records " + Fmt.money(amount) + " " + verb + player
				+ (incoming ? ". It's routed like a detected payment (queue or live auction)." : "."),
			"Add", false, () -> {
				if (incoming) {
					state.addManualPayment(player, amount);
				} else {
					state.addManualPayout(player, amount);
				}
				ManagerScreen.DRAFTS.remove("payments.amount");
			});
	}

	private List<Transaction> rows() {
		List<Transaction> out = new ArrayList<>();
		for (Transaction t : state.finance.search(ManagerScreen.draft("payments.search"))) {
			if (filter.test(t)) {
				out.add(t);
			}
		}
		return out;
	}

	@Override
	void draw() {
		int cx = m.cx, cy = m.cy, cw = m.cw, ch = m.ch;
		if (m.hides(Surface.FINANCE)) {
			m.locked(cx, cy, cw, ch, "Payments are");
			return;
		}
		List<Transaction> list = rows();
		m.right(list.size() + (list.size() == 1 ? " entry" : " entries"), cx + cw, cy + 4, Colors.INK_FAINT);
		int top = cy + 22;
		int h = ch - 44;
		m.card(cx, top, cw, h);
		Paint.label(m.ctx, m.textRenderer(), "Time", cx + 8, top + 6);
		Paint.label(m.ctx, m.textRenderer(), "Player", cx + 70, top + 6);
		Paint.label(m.ctx, m.textRenderer(), "Linked to", cx + 190, top + 6);
		m.right("STATUS", cx + cw - 8, top + 6, Colors.INK_FAINT);
		Paint.hline(m.ctx, cx + 1, cx + cw - 1, top + 17, Colors.LINE);
		if (list.isEmpty()) {
			boolean searching = !ManagerScreen.draft("payments.search").isEmpty() || filter != Filter.ALL;
			m.empty(Icons.PAYMENTS, searching ? "Nothing matches" : "No payments yet",
				searching ? "Try another search or category" : "Detected /pay messages are listed here", cx, top + 18, cw, h - 18);
		} else {
			m.list(cx, top + 18, cw, h - 18, list.size(), KEY, 13, (i, y) -> row(m, state, list.get(i), cx + 8, y + 3, cw - 16, false, true));
		}
		m.text("Add", cx, cy + ch - 12, Colors.INK_DIM);
	}

	/** What a payment belongs to, from the ids recorded when it was matched. Never guessed. */
	static String linked(GambleState state, Transaction t) {
		if (t.type == Transaction.Type.EXPENSE) {
			return t.note.isEmpty() ? "expense" : t.note;
		}
		if (t.betId != 0) {
			Bet b = state.ledger.find(t.betId);
			String game = b == null ? "bet" : b.game.displayName;
			return t.type == Transaction.Type.PAYOUT ? "Payout · " + game : game;
		}
		if (t.auctionId != 0) {
			Auction a = state.auctions.find(t.auctionId);
			String name = a == null ? "auction" : a.item;
			return (t.type == Transaction.Type.PAYOUT ? "Refund · " : "Bid · ") + name;
		}
		if (t.type == Transaction.Type.PAYOUT) {
			return "Not matched";
		}
		return t.source.name().toLowerCase(Locale.ROOT);
	}

	/** Pending (bet not called yet), Confirmed (from chat), Manual or Voided. */
	static String status(GambleState state, Transaction t) {
		if (t.voided) {
			return "Voided";
		}
		if (t.manual) {
			return "Manual";
		}
		if (t.betId != 0 && t.type == Transaction.Type.INCOMING) {
			Bet b = state.ledger.find(t.betId);
			if (b != null && b.outcome == null) {
				return "Pending";
			}
		}
		return "Confirmed";
	}

	private static int statusColor(String s) {
		return switch (s) {
			case "Voided" -> Colors.INK_FAINT;
			case "Manual" -> Colors.GOLD;
			case "Pending" -> Colors.accent();
			default -> Colors.GREEN;
		};
	}

	/** One payment. {@code y} is the text line. Interactive rows get status, Void/Restore and a player link. */
	static void row(ManagerScreen m, GambleState state, Transaction t, int x, int y, int w, boolean hideAmount, boolean interactive) {
		int ink = t.voided ? Colors.INK_FAINT : Colors.INK;
		m.text(ManagerScreen.shortTime(t.timestamp), x, y, Colors.INK_FAINT);
		int bx = x + 32;
		String type = switch (t.type) {
			case INCOMING -> "IN";
			case PAYOUT -> "OUT";
			case EXPENSE -> "EXP";
		};
		int typeColor = t.voided ? Colors.INK_FAINT : t.type == Transaction.Type.INCOMING ? Colors.GREEN : Colors.RED;
		Paint.pill(m.ctx, bx, y - 2, 25, 11, Colors.alpha(typeColor, 0x2C));
		m.center(type, bx + 13, y, typeColor);
		int px = bx + 30;
		String name = m.fit(t.player, 70);
		if (interactive) {
			m.link(name, px, y, ink, () -> {
				ManagerScreen.DRAFTS.put("payments.search", t.player);
				ManagerScreen.SCROLL.put(KEY, 0);
			});
		} else {
			m.text(name, px, y, ink);
		}
		int amountRight = px + 112;
		if (!hideAmount) {
			int amountColor = t.voided ? Colors.INK_FAINT : t.type == Transaction.Type.INCOMING ? Colors.GREEN : Colors.INK;
			String amount = Fmt.money(t.amount);
			m.right(amount, amountRight, y, amountColor);
			if (t.voided) {
				Paint.hline(m.ctx, amountRight - m.width(amount), amountRight, y + 3, Colors.INK_FAINT);
			}
		}
		int lx = amountRight + 8;
		int linkRight = interactive ? x + w - 66 : x + w;
		String link = linked(state, t);
		m.text(m.fit(link, linkRight - lx), lx, y, link.equals("Not matched") ? Colors.ORANGE : Colors.INK_DIM);
		if (!interactive) {
			return;
		}
		// The status badge turns into Void / Restore while the row is hovered.
		boolean over = m.in(x - 6, y - 3, w + 12, 13);
		if (!over) {
			String st = status(state, t);
			int sc = statusColor(st);
			int sw = m.width(st) + 8;
			Paint.pill(m.ctx, x + w - sw, y - 2, sw, 11, Colors.alpha(sc, 0x26));
			m.text(st, x + w - sw + 4, y, sc);
			return;
		}
		String act = t.voided ? "Restore" : "Void";
		m.link(act, x + w - m.width(act) - 2, y, t.voided ? Colors.GREEN : Colors.RED, () -> {
			if (t.voided) {
				state.setVoided(t, false);
			} else {
				m.confirm("Void this payment?", t.player + " · " + Fmt.money(t.amount) + " stops counting toward totals."
						+ " If it's waiting in a queue or a live auction, it's taken out. You can restore it later.",
					"Void", true, () -> state.setVoided(t, true));
			}
		});
	}
}
