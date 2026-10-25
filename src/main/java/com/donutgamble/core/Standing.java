package com.donutgamble.core;

/** A player's place in an auction. {@code share} is 0..1 of the auction total. */
public record Standing(String player, long total, int count, double share, long firstAt) {
}
