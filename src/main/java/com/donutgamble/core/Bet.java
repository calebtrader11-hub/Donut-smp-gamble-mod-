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
	/** When the result was called (0 for bets saved before this was recorded). */
	public long resolvedAt;

	public boolean isPending() {
		return outcome == null;
	}

	/** Ely Arrows: shots bought and elytras won (set when the result is called). */
	public int shots = 1;
	public int elytrasWon;

	/** Elytras owed on a win: at least 1 for wins saved before this was recorded. */
	public int elytras() {
		return Math.max(1, elytrasWon);
	}

	public boolean payoutIsElytra() {
		return game == Game.ELY;
	}

	public String scoreLabel() {
		if (game == Game.ELY) {
			if (outcome != Outcome.PLAYER_WIN) {
				return "MISS";
			}
			return shots > 1 || elytrasWon > 1 ? elytras() + "/" + Math.max(shots, elytras()) : "HIT";
		}
		if (scoreThem == null || scoreMe == null) {
			return tie ? "T" : outcome == Outcome.PLAYER_WIN ? "W" : "L";
		}
		return ScoreOptions.label(game, scoreThem) + "-" + ScoreOptions.label(game, scoreMe);
	}
}
