package com.donutgamble.core;

import java.util.ArrayList;
import java.util.List;

/** User-editable settings, saved as config/donutgamble/config.json. */
public class GambleConfig {
	private static final long M = 1_000_000L;
	private static final int MAX_RECENT = 16;

	/** Moves an item to the front of the recent list. */
	public void rememberItem(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return;
		}
		recentItems.remove(itemId);
		recentItems.add(0, itemId);
		while (recentItems.size() > MAX_RECENT) {
			recentItems.remove(recentItems.size() - 1);
		}
	}

	/** Stars or un-stars an item; returns whether it's now a favorite. */
	public boolean toggleFavorite(String itemId) {
		if (favoriteItems.remove(itemId)) {
			return false;
		}
		favoriteItems.add(itemId);
		return true;
	}

	/**
	 * Extra regexes for incoming payments, tried after the built-in Donut SMP ones (see
	 * {@link PaymentDetector}). Each needs named groups {@code player} and {@code amount}; start
	 * with ^ so player chat can't spoof a payment.
	 */
	public List<String> extraPayPatterns = new ArrayList<>();

	public long paperMin = 5 * M;
	public long paperMax = 500 * M;
	public long blackjackMin = 101 * M;
	public long blackjackMax = 701 * M;
	public double winMultiplier = 2.0;
	public long elytraPrice = 0;

	public boolean hudVisible = true;
	/** Old single-HUD position; only read to seed the queue's slot in {@link #hud}. */
	public int hudX = 6;
	public int hudY = 6;
	public float hudScale = 1.0f;
	/** Position, size and visibility of each HUD component (queue, auction, profit, toasts). */
	public HudLayout hud = new HudLayout();
	/** Aqua, Purple, Gold, Red, Lime, Pink or White. Click "Profit" in game to cycle. */
	public String theme = "AQUA";

	// Auctions
	public int auctionDefaultDurationSec = 300;
	/** A payment with less than this many seconds left resets the clock to this many seconds. 0 = off. */
	public int auctionAntiSnipeSec = 0;
	/** Payments below this go to the gamble queue instead of the auction. */
	public long auctionMinContribution = 0;
	/** When an auction ends, owe everyone but the winner their money back. */
	public boolean auctionRefundLosers = false;
	/** End the auction automatically when its timer reaches zero. */
	public boolean auctionAutoEnd = true;
	/** While an auction is live, incoming payments count toward it instead of the gamble queue. */
	public boolean routePaymentsToAuction = true;

	/** The same player paying the same amount within this window is treated as one payment. 0 = off. */
	public long dedupeWindowMs = 1500;
	/** Small pop-up for each payment and auction event. */
	public boolean notifications = true;

	/** Streamer Mode and what it hides. */
	public PrivacySettings privacy = new PrivacySettings();
	/** Item ids picked for recent auctions, newest first (max 16). */
	public List<String> recentItems = new ArrayList<>();
	/** Item ids starred in the item picker. */
	public List<String> favoriteItems = new ArrayList<>();

	public Game lastGame = Game.PAPER;
	/** Which tab the queue shows: PAPER, BLACKJACK, ELY, AUCTION, OWE or STATS. */
	public String activeTab = "PAPER";
}
