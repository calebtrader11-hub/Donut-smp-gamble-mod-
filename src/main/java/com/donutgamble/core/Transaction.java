package com.donutgamble.core;

/** One money movement. Public fields so Gson can save it directly. */
public class Transaction {
	public enum Type {
		/** Someone paid you. */
		INCOMING,
		/** You paid someone (detected from chat). */
		PAYOUT,
		/** Money spent that isn't a /pay, e.g. an elytra bought for a winner. */
		EXPENSE
	}

	public enum Source {
		GAMBLE,
		AUCTION,
		OTHER
	}

	public long id;
	public long timestamp;
	public Type type;
	public Source source;
	public String player;
	public long amount;
	public String note = "";
	/** Entered by hand rather than detected from chat. */
	public boolean manual;
	/** Excluded from every total (a correction). */
	public boolean voided;
	/** The gamble bet or auction this payment went to, 0 if none. */
	public long betId;
	public long auctionId;

	/** + for money in, - for money out. */
	public long signedAmount() {
		return type == Type.INCOMING ? amount : -amount;
	}
}
