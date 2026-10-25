package com.donutgamble.core;

/**
 * Streamer Mode: which parts of the interface stop drawing private money figures while it's on.
 * Saved inside config.json. Each flag only matters while {@link #streamerMode} (or a preview) is on.
 */
public class PrivacySettings {
	/** Where private figures can appear. */
	public enum Surface {
		/** Manager dashboard: session/lifetime net, received, paid out, latest payment amounts. */
		DASHBOARD,
		/** Queue HUD: per-game profit, the Stats tab, the total owed. */
		PAPER_HUD,
		/** Auction HUD and leaderboard: bid amounts and total collected (public by default). */
		AUCTION_HUD,
		/** Pop-ups that mention your profit, exports or backups. */
		TOASTS,
		/** Players page: what each player paid you, you paid them, net, wagered. */
		PLAYER_STATS,
		/** Finance and Payments pages, the Session profit HUD, auction profit in history. */
		FINANCE
	}

	public boolean streamerMode = false;
	public boolean hideDashboard = true;
	public boolean hidePaperHud = true;
	/** Auction bids are what viewers follow, so they stay visible unless this is turned on. */
	public boolean hideAuctionHud = false;
	public boolean hideToasts = true;
	public boolean hidePlayerStats = true;
	public boolean hideFinance = true;

	/** True if private figures on {@code surface} must not be drawn right now. */
	public boolean hides(Surface surface, boolean preview) {
		if (!streamerMode && !preview) {
			return false;
		}
		return switch (surface) {
			case DASHBOARD -> hideDashboard;
			case PAPER_HUD -> hidePaperHud;
			case AUCTION_HUD -> hideAuctionHud;
			case TOASTS -> hideToasts;
			case PLAYER_STATS -> hidePlayerStats;
			case FINANCE -> hideFinance;
		};
	}

	public boolean get(Surface surface) {
		return switch (surface) {
			case DASHBOARD -> hideDashboard;
			case PAPER_HUD -> hidePaperHud;
			case AUCTION_HUD -> hideAuctionHud;
			case TOASTS -> hideToasts;
			case PLAYER_STATS -> hidePlayerStats;
			case FINANCE -> hideFinance;
		};
	}

	public void set(Surface surface, boolean hide) {
		switch (surface) {
			case DASHBOARD -> hideDashboard = hide;
			case PAPER_HUD -> hidePaperHud = hide;
			case AUCTION_HUD -> hideAuctionHud = hide;
			case TOASTS -> hideToasts = hide;
			case PLAYER_STATS -> hidePlayerStats = hide;
			case FINANCE -> hideFinance = hide;
		}
	}
}
