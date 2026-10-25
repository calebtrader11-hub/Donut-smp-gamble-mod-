package com.donutgamble.core;

import java.util.ArrayList;
import java.util.List;

/** The numbers a round can end on: Paper 1-9, Blackjack 17-21 (players hit to 17+) or Bust. */
public final class ScoreOptions {
	public record Option(String label, int value) {
	}

	/** Stored value for a blackjack bust; {@link ScoreJudge} treats anything over 21 as a bust. */
	public static final int BUST = 22;

	private ScoreOptions() {
	}

	public static List<Option> forGame(Game game) {
		List<Option> out = new ArrayList<>();
		switch (game) {
			case PAPER -> {
				for (int i = 1; i <= 9; i++) {
					out.add(new Option(Integer.toString(i), i));
				}
			}
			case BLACKJACK -> {
				for (int i = 17; i <= 21; i++) {
					out.add(new Option(Integer.toString(i), i));
				}
				out.add(new Option("Bust", BUST));
			}
			case ELY -> {
			}
		}
		return out;
	}

	public static String label(Game game, int value) {
		return game == Game.BLACKJACK && value > 21 ? "Bust" : Integer.toString(value);
	}
}
