package com.donutgamble.core;

import java.util.ArrayList;
import java.util.List;

/** User-editable settings, saved as config/donutgamble/config.json. */
public class GambleConfig {
	private static final long M = 1_000_000L;

	/**
	 * Extra regexes for incoming payments, tried after the built-in Donut SMP ones (see
	 * {@link PaymentDetector}). Each needs named groups {@code player} and {@code amount}; start
	 * with ^ so player chat can't spoof a payment.
	 */
	public List<String> extraPayPatterns = new ArrayList<>();

	public long paperMin = 5 * M;
	public long paperMax = 500 * M;
	public long blackjackMin = 101 * M;
	public long blackjackMax = 701 * M;
	public double winMultiplier = 2.0;
	public long elytraPrice = 0;

	public boolean hudVisible = true;
	public int hudX = 6;
	public int hudY = 6;
	public float hudScale = 1.0f;

	public Game lastGame = Game.PAPER;
}
