package com.donutgamble.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Which pop-ups show, how long they stay and whether they play a sound. Saved in config.json. */
public class NotificationSettings {
	public enum Kind {
		PAYMENT_RECEIVED("Payment received / joined queue"),
		PAYMENT_SENT("Payment sent"),
		GAME_STARTED("Next player up"),
		GAME_COMPLETED("Game result"),
		PAYOUT_DUE("Payout to send"),
		AUCTION_BID("Auction bid"),
		AUCTION_LEADER("New auction leader"),
		AUCTION_ENDING("Auction ending soon"),
		AUCTION_COMPLETED("Auction finished"),
		SYSTEM("Exports, backups, errors");

		public final String label;

		Kind(String label) {
			this.label = label;
		}
	}

	/** Kind name → enabled. Missing means enabled. */
	public Map<String, Boolean> enabled = new LinkedHashMap<>();
	public int durationSec = 4;
	public boolean sounds = true;
	public int maxVisible = 4;

	public boolean isEnabled(Kind kind) {
		return enabled.getOrDefault(kind.name(), true);
	}

	public void setEnabled(Kind kind, boolean on) {
		enabled.put(kind.name(), on);
	}

	public void setDuration(int seconds) {
		durationSec = Math.max(2, Math.min(15, seconds));
	}
}
