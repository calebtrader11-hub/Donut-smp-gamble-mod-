package com.donutgamble.core;

import java.util.List;

/** {@code playerLosses} excludes ties, which are counted separately (both go to the house). */
public record Stats(long net, long wagered, int playerWins, int playerLosses, int ties, List<Bet> recent) {
}
