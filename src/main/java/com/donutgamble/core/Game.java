package com.donutgamble.core;

public enum Game {
	PAPER("Paper"),
	BLACKJACK("Blackjack"),
	ELY("Ely Arrows");

	public final String displayName;

	Game(String displayName) {
		this.displayName = displayName;
	}

	public boolean usesScore() {
		return this != ELY;
	}
}
