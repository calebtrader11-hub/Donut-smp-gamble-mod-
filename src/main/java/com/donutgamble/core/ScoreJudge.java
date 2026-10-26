package com.donutgamble.core;

/** Decides a round from its score. Ties go to the house; blackjack scores over 21 bust. */
public final class ScoreJudge {
	private ScoreJudge() {
	}

	public static Outcome judge(Game game, int them, int me) {
		if (!game.usesScore()) {
			throw new IllegalArgumentException(game + " has no score");
		}
		if (game == Game.BLACKJACK) {
			if (them > 21) {
				return Outcome.PLAYER_LOSE;
			}
			if (me > 21) {
				return Outcome.PLAYER_WIN;
			}
		}
		return them > me ? Outcome.PLAYER_WIN : Outcome.PLAYER_LOSE;
	}

	/** Same number (and not both bust): the house wins, recorded as a tie. */
	public static boolean isTie(Game game, int them, int me) {
		return them == me && !(game == Game.BLACKJACK && them > 21);
	}
}
