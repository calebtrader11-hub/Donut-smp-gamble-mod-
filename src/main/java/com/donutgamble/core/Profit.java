package com.donutgamble.core;

/**
 * One profit number for everything: games, auctions and anything else.
 * <p>
 * Profit = all money in − all money out (payouts and expenses such as elytras and prize costs)
 * − what you still owe (winnings and refunds not paid yet, elytras at their price).
 * Subtracting what's owed means a win counts against you as soon as it's called, not only once
 * you pay it, and nothing is counted twice: once it's paid, it moves from "owed" to "paid out".
 */
public final class Profit {
	private Profit() {
	}

	/** Profit from activity at or after {@code since} (0 = all time). */
	public static long net(FinanceBook finance, Ledger ledger, AuctionManager auctions, long since) {
		return finance.report(since).net() - owed(ledger, auctions, since);
	}

	/** Unpaid winnings and refunds from at or after {@code since}. */
	public static long owed(Ledger ledger, AuctionManager auctions, long since) {
		long owed = 0;
		for (Bet b : ledger.owed()) {
			if (b.timestamp >= since) {
				owed += b.payout;
			}
		}
		for (Refund r : auctions.openRefunds()) {
			if (r.timestamp >= since) {
				owed += r.amount;
			}
		}
		return owed;
	}
}
