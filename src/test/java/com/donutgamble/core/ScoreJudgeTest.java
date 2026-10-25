package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoreJudgeTest {
	@Test
	void higherScoreWins() {
		assertEquals(Outcome.PLAYER_WIN, ScoreJudge.judge(Game.PAPER, 9, 8));
		assertEquals(Outcome.PLAYER_LOSE, ScoreJudge.judge(Game.PAPER, 8, 9));
	}

	@Test
	void tiesGoToHouse() {
		assertEquals(Outcome.PLAYER_LOSE, ScoreJudge.judge(Game.PAPER, 5, 5));
		assertEquals(Outcome.PLAYER_LOSE, ScoreJudge.judge(Game.BLACKJACK, 18, 18));
	}

	@Test
	void blackjackBusts() {
		assertEquals(Outcome.PLAYER_LOSE, ScoreJudge.judge(Game.BLACKJACK, 22, 18));
		assertEquals(Outcome.PLAYER_LOSE, ScoreJudge.judge(Game.BLACKJACK, 23, 25));
		assertEquals(Outcome.PLAYER_WIN, ScoreJudge.judge(Game.BLACKJACK, 17, 24));
		assertEquals(Outcome.PLAYER_LOSE, ScoreJudge.judge(Game.BLACKJACK, 18, 20));
		assertEquals(Outcome.PLAYER_WIN, ScoreJudge.judge(Game.BLACKJACK, 21, 20));
	}

	@Test
	void elyHasNoScore() {
		assertThrows(IllegalArgumentException.class, () -> ScoreJudge.judge(Game.ELY, 1, 0));
	}
}
