package com.donutgamble.core;

import java.util.List;

public record Stats(long net, long wagered, int playerWins, int playerLosses, List<Bet> recent) {
}
