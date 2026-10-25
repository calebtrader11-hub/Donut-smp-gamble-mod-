package com.donutgamble.core;

import java.util.ArrayList;
import java.util.List;

/** The live auction, past auctions and refunds owed. Saved as config/donutgamble/auctions.json. */
public class AuctionManager {
	public Auction current;
	public List<Auction> history = new ArrayList<>();
	public List<Refund> refunds = new ArrayList<>();
	public long nextId = 1;
	public long nextRefundId = 1;

	public Auction start(String item, int durationSec, long prizeCost, long now) {
		if (current != null && current.isLive()) {
			throw new IllegalStateException("An auction is already running");
		}
		Auction a = new Auction();
		a.id = nextId++;
		a.item = item == null || item.isBlank() ? "Auction #" + a.id : item.trim();
		a.prizeCost = prizeCost;
		a.durationMs = Math.max(1, durationSec) * 1000L;
		a.start(now);
		current = a;
		return a;
	}

	public boolean isLive() {
		return current != null && current.isLive();
	}

	/**
	 * Counts a payment toward the live auction (running or paused). Returns false if there is no live
	 * auction or the payment is below the minimum, so the caller can treat it as a normal bet instead.
	 */
	public boolean onPayment(String player, long amount, long now, long txId, GambleConfig cfg) {
		if (!isLive() || amount < cfg.auctionMinContribution) {
			return false;
		}
		current.addContribution(player, amount, now, txId, false);
		long antiSnipe = cfg.auctionAntiSnipeSec * 1000L;
		if (antiSnipe > 0 && current.status == Auction.Status.RUNNING && current.remaining(now) < antiSnipe) {
			current.endsAt = now + antiSnipe;
		}
		return true;
	}

	/** Call regularly. Returns the auction if this call ended it because the timer ran out. */
	public Auction tick(long now, GambleConfig cfg) {
		if (cfg.auctionAutoEnd && current != null && current.status == Auction.Status.RUNNING && current.remaining(now) == 0) {
			return end(now, cfg);
		}
		return null;
	}

	/** Ends the live auction: highest contributor wins; losers get refunds if the rules say so. */
	public Auction end(long now, GambleConfig cfg) {
		if (!isLive()) {
			return null;
		}
		Auction a = current;
		Standing leader = a.leader();
		a.winner = leader == null ? null : leader.player();
		finish(a, Auction.Status.ENDED, now);
		if (cfg.auctionRefundLosers) {
			for (Standing s : a.rankings()) {
				if (!s.player().equals(a.winner)) {
					addRefund(a, s, now);
				}
			}
		}
		return a;
	}

	/** Calls the auction off: no winner, everyone who paid is owed a refund. */
	public Auction cancel(long now) {
		if (!isLive()) {
			return null;
		}
		Auction a = current;
		a.winner = null;
		finish(a, Auction.Status.CANCELLED, now);
		for (Standing s : a.rankings()) {
			addRefund(a, s, now);
		}
		return a;
	}

	private void finish(Auction a, Auction.Status status, long now) {
		a.status = status;
		a.endedAt = now;
		a.remainingMs = 0;
		history.add(a);
		current = null;
	}

	private void addRefund(Auction a, Standing s, long now) {
		Refund r = new Refund();
		r.id = nextRefundId++;
		r.auctionId = a.id;
		r.player = s.player();
		r.amount = s.total();
		r.timestamp = now;
		refunds.add(r);
	}

	public List<Refund> openRefunds() {
		List<Refund> out = new ArrayList<>();
		for (Refund r : refunds) {
			if (!r.settled) {
				out.add(r);
			}
		}
		return out;
	}

	public void settleRefund(long id) {
		for (Refund r : refunds) {
			if (r.id == id) {
				r.settled = true;
			}
		}
	}

	/** You paid someone: clear the oldest open refund to them for exactly that amount. */
	public boolean settleRefundByPayment(String player, long amount) {
		for (Refund r : openRefunds()) {
			if (r.player.equalsIgnoreCase(player) && r.amount == amount) {
				r.settled = true;
				return true;
			}
		}
		return false;
	}

	/** The live auction, or else the most recent finished one. */
	public Auction latest() {
		if (current != null) {
			return current;
		}
		return history.isEmpty() ? null : history.get(history.size() - 1);
	}

	public Auction find(long id) {
		if (current != null && current.id == id) {
			return current;
		}
		for (Auction a : history) {
			if (a.id == id) {
				return a;
			}
		}
		return null;
	}
}
