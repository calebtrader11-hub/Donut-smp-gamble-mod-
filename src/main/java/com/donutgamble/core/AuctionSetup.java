package com.donutgamble.core;

/**
 * Everything chosen on the auction setup screen before starting.
 *
 * @param title        shown as the auction's name (blank = item name)
 * @param itemId       registry id of the prize, e.g. "minecraft:spawner" (blank = none)
 * @param itemName     the item's display name, used when the title is blank
 * @param quantity     how many of the item
 * @param description  optional text shown under the title
 * @param stackData    the exact item with its components (enchantments, custom name…) as JSON,
 *                     when picked from the inventory; blank otherwise
 * @param autoEnd      end when the timer hits zero (null = use the global setting)
 * @param antiSnipeSec late-bid extension in seconds (null = use the global setting)
 * @param showTop      how many bidders the HUD lists
 * @param showAmounts  show each bidder's total on the HUD
 * @param showShare    show each bidder's share of the pot on the HUD
 * @param bidNotify    which bids pop up: ALL, LEADER or OFF
 * @param hudCard      show the auction card on the HUD
 * @param hudLeaderboard show the leaderboard on the HUD
 */
public record AuctionSetup(String title, String itemId, String itemName, int quantity, String description, String stackData,
	int durationSec, long prizeCost, Boolean autoEnd, Integer antiSnipeSec, int showTop, boolean showAmounts, boolean showShare,
	String bidNotify, boolean hudCard, boolean hudLeaderboard) {

	/** The name an auction is listed under. */
	public String displayTitle() {
		if (title != null && !title.isBlank()) {
			return title.trim();
		}
		if (itemName != null && !itemName.isBlank()) {
			return (quantity > 1 ? quantity + "× " : "") + itemName.trim();
		}
		return "";
	}

	public static AuctionSetup simple(String title, int durationSec, long prizeCost) {
		return new AuctionSetup(title, "", "", 1, "", "", durationSec, prizeCost, null, null, 5, true, true, "ALL", true, true);
	}
}
