package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoreOptionsTest {
	@Test
	void paperIsOneToNine() {
		List<ScoreOptions.Option> o = ScoreOptions.forGame(Game.PAPER);
		assertEquals(9, o.size());
		assertEquals("1", o.get(0).label());
		assertEquals(9, o.get(8).value());
	}

	@Test
	void blackjackIsSeventeenToTwentyOnePlusBust() {
		List<ScoreOptions.Option> o = ScoreOptions.forGame(Game.BLACKJACK);
		assertEquals(List.of("17", "18", "19", "20", "21", "Bust"), o.stream().map(ScoreOptions.Option::label).toList());
		assertEquals(Outcome.PLAYER_LOSE, ScoreJudge.judge(Game.BLACKJACK, o.get(5).value(), 17), "a bust loses");
	}

	@Test
	void elyHasNoScores() {
		assertTrue(ScoreOptions.forGame(Game.ELY).isEmpty());
	}

	@Test
	void labelForStoredScore() {
		assertEquals("Bust", ScoreOptions.label(Game.BLACKJACK, 22));
		assertEquals("19", ScoreOptions.label(Game.BLACKJACK, 19));
		assertEquals("7", ScoreOptions.label(Game.PAPER, 7));
	}

	@Test
	void scoreLabelUsesBust() {
		Bet b = new Bet();
		b.game = Game.BLACKJACK;
		b.outcome = Outcome.PLAYER_LOSE;
		b.scoreThem = 22;
		b.scoreMe = 18;
		assertEquals("Bust-18", b.scoreLabel());
	}
}
