package com.donutgamble.core;

public class Contribution {
	public String player;
	/** Negative for a manual correction that takes money off. */
	public long amount;
	public long timestamp;
	/** The finance transaction behind it, 0 for a manual correction. */
	public long txId;
	public boolean manual;
}
