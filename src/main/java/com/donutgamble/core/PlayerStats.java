package com.donutgamble.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everything about one player across bets, payments and auctions. Names match case-insensitively. */
public record PlayerStats(String player, int bets, int wins, int losses, int ties, long wagered, long houseNetFromBets,
	long paidYou, long youPaid, long auctionTotal, int auctionsWon, long lastSeen,
	long outstanding, int elytrasOwed, int payments, int auctionsJoined) {

	/** Ways to order (and narrow) the player list. */
	public enum View {
		RECENT("Recent"),
		MOST_ACTIVE("Most active"),
		BIGGEST("Biggest payers"),
		OUTSTANDING("Owed money"),
		AUCTIONS("Auction bidders");

		public final String label;

		View(String label) {
			this.label = label;
		}
	}

	/** Bets plus payments: how much they've been involved. */
	public int activity() {
		return bets + payments;
	}

	/** Players in the order (and subset) a view shows. The input list isn't changed. */
	public static List<PlayerStats> view(List<PlayerStats> all, View view) {
		List<PlayerStats> out = new ArrayList<>(all);
		switch (view) {
			case RECENT -> out.sort((a, b) -> Long.compare(b.lastSeen(), a.lastSeen()));
			case MOST_ACTIVE -> out.sort((a, b) -> Integer.compare(b.activity(), a.activity()));
			case BIGGEST -> out.sort((a, b) -> Long.compare(b.paidYou(), a.paidYou()));
			case OUTSTANDING -> {
				out.removeIf(p -> p.outstanding() <= 0 && p.elytrasOwed() == 0);
				out.sort((a, b) -> Long.compare(b.outstanding(), a.outstanding()));
			}
			case AUCTIONS -> {
				out.removeIf(p -> p.auctionsJoined() == 0);
				out.sort((a, b) -> Long.compare(b.auctionTotal(), a.auctionTotal()));
			}
		}
		return out;
	}

	/** Cash between you and them: what they paid you minus what you paid them. */
	public long net() {
		return paidYou - youPaid;
	}

	private static final class Acc {
		String name;
		int bets, wins, losses, ties, auctionsWon, elytrasOwed, payments, auctionsJoined;
		long wagered, houseNet, paidYou, youPaid, auctionTotal, lastSeen, outstanding;

		void seen(long t) {
			lastSeen = Math.max(lastSeen, t);
		}
	}

	/** Every player who appears anywhere, most recently seen first. */
	public static List<PlayerStats> all(Ledger ledger, FinanceBook finance, AuctionManager auctions) {
		Map<String, Acc> map = new LinkedHashMap<>();
		for (Bet b : ledger.bets) {
			Acc a = acc(map, b.player);
			a.seen(b.timestamp);
			a.bets++;
			if (!b.isPending()) {
				a.wagered += b.amount;
				a.houseNet += b.houseNet;
				if (b.outcome == Outcome.PLAYER_WIN) {
					a.wins++;
					if (!b.settled) {
						if (b.payoutIsElytra()) {
							a.elytrasOwed++;
						} else {
							a.outstanding += b.payout;
						}
					}
				} else if (b.tie) {
					a.ties++;
				} else {
					a.losses++;
				}
			}
		}
		for (Transaction t : finance.transactions) {
			if (t.voided || t.type == Transaction.Type.EXPENSE) {
				continue;
			}
			Acc a = acc(map, t.player);
			a.seen(t.timestamp);
			if (t.type == Transaction.Type.INCOMING) {
				a.paidYou += t.amount;
				a.payments++;
			} else {
				a.youPaid += t.amount;
			}
		}
		List<Auction> every = new ArrayList<>(auctions.history);
		if (auctions.current != null) {
			every.add(auctions.current);
		}
		for (Auction auction : every) {
			for (Standing s : auction.rankings()) {
				Acc a = acc(map, s.player());
				a.auctionTotal += s.total();
				a.auctionsJoined++;
				a.seen(auction.endedAt > 0 ? auction.endedAt : auction.startedAt);
			}
			if (auction.winner != null) {
				acc(map, auction.winner).auctionsWon++;
			}
		}
		for (Refund r : auctions.openRefunds()) {
			acc(map, r.player).outstanding += r.amount;
		}
		List<PlayerStats> out = new ArrayList<>();
		for (Acc a : map.values()) {
			out.add(new PlayerStats(a.name, a.bets, a.wins, a.losses, a.ties, a.wagered, a.houseNet, a.paidYou, a.youPaid,
				a.auctionTotal, a.auctionsWon, a.lastSeen, a.outstanding, a.elytrasOwed, a.payments, a.auctionsJoined));
		}
		out.sort((x, y) -> Long.compare(y.lastSeen(), x.lastSeen()));
		return out;
	}

	private static Acc acc(Map<String, Acc> map, String name) {
		return map.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> {
			Acc a = new Acc();
			a.name = name;
			return a;
		});
	}

	public static List<PlayerStats> filter(List<PlayerStats> all, String query) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return all;
		}
		List<PlayerStats> out = new ArrayList<>();
		for (PlayerStats p : all) {
			if (p.player().toLowerCase(Locale.ROOT).contains(q)) {
				out.add(p);
			}
		}
		return out;
	}

	public static PlayerStats find(List<PlayerStats> all, String player) {
		for (PlayerStats p : all) {
			if (p.player().equalsIgnoreCase(player)) {
				return p;
			}
		}
		return null;
	}
}
