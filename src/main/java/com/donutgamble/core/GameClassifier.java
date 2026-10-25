package com.donutgamble.core;

/** Guesses which game a payment is for from its size. */
public final class GameClassifier {
	private GameClassifier() {
	}

	public static Game guess(long amount, Game lastGame, GambleConfig cfg) {
		boolean paper = amount >= cfg.paperMin && amount <= cfg.paperMax;
		boolean blackjack = amount >= cfg.blackjackMin && amount <= cfg.blackjackMax;
		if (paper && blackjack) {
			return lastGame == Game.BLACKJACK ? Game.BLACKJACK : Game.PAPER;
		}
		if (paper) {
			return Game.PAPER;
		}
		if (blackjack) {
			return Game.BLACKJACK;
		}
		return Game.ELY;
	}
}
