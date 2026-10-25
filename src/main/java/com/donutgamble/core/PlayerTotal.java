package com.donutgamble.core;

/** A player's summed payments. */
public record PlayerTotal(String player, long total, int count) {
}
