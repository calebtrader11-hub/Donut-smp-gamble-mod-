package com.donutgamble.core;

/** Money owed back to an auction participant who didn't win (or to everyone if it was cancelled). */
public class Refund {
	public long id;
	public long auctionId;
	public String player;
	public long amount;
	public long timestamp;
	public boolean settled;
}
