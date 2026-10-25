package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameClassifierTest {
	private final GambleConfig cfg = new GambleConfig();
	private static final long M = 1_000_000L;

	@Test
	void paperOnlyRange() {
		assertEquals(Game.PAPER, GameClassifier.guess(50 * M, Game.BLACKJACK, cfg));
	}

	@Test
	void blackjackOnlyRange() {
		assertEquals(Game.BLACKJACK, GameClassifier.guess(600 * M, Game.PAPER, cfg));
	}

	@Test
	void overlapUsesLastGame() {
		assertEquals(Game.BLACKJACK, GameClassifier.guess(200 * M, Game.BLACKJACK, cfg));
		assertEquals(Game.PAPER, GameClassifier.guess(200 * M, Game.PAPER, cfg));
		assertEquals(Game.PAPER, GameClassifier.guess(200 * M, Game.ELY, cfg));
	}

	@Test
	void outsideAllRangesIsEly() {
		assertEquals(Game.ELY, GameClassifier.guess(2 * M, Game.PAPER, cfg));
		assertEquals(Game.ELY, GameClassifier.guess(900 * M, Game.PAPER, cfg));
	}
}
