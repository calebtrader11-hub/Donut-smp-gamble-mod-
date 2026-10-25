package com.donutgamble.core;

/** One payment from a player. Public fields so Gson can save it directly. */
public class Bet {
	public long id;
	public String player;
	public long amount;
	public Game game;
	public long timestamp;

	/** Null while pending. */
	public Outcome outcome;
	public Integer scoreThem;
	public Integer scoreMe;
	/** What the house owes on a player win (money, or the elytra price for Ely). */
	public long payout;
	/** Profit for the house from this bet once resolved. */
	public long houseNet;
	/** Called as a tie (counts as a player loss). */
	public boolean tie;
	/** True once the winner has been paid / their elytra delivered. */
	public boolean settled;

	public boolean isPending() {
		return outcome == null;
	}

	public boolean payoutIsElytra() {
		return game == Game.ELY;
	}

	public String scoreLabel() {
		if (game == Game.ELY) {
			return outcome == Outcome.PLAYER_WIN ? "HIT" : "MISS";
		}
		if (scoreThem == null || scoreMe == null) {
			return tie ? "T" : outcome == Outcome.PLAYER_WIN ? "W" : "L";
		}
		return scoreThem + "-" + scoreMe;
	}
}
