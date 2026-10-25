package com.donutgamble.core;

import java.util.ArrayList;
import java.util.List;

/** User-editable settings, saved as config/donutgamble/config.json. */
public class GambleConfig {
	private static final long M = 1_000_000L;

	/**
	 * Regexes matched against incoming server messages (color codes stripped). Each needs named
	 * groups {@code player} and {@code amount}. Anchored with ^ so player chat can't spoof a payment.
	 */
	public static final List<String> DEFAULT_PAY_PATTERNS = List.of(
		"^(?<player>[.A-Za-z0-9_]{2,17}) paid you \\$?(?<amount>[0-9][0-9,.]*\\s*[KkMmBbTt]?)",
		"^You received \\$?(?<amount>[0-9][0-9,.]*\\s*[KkMmBbTt]?) from (?<player>[.A-Za-z0-9_]{2,17})"
	);

	public List<String> payPatterns = new ArrayList<>(DEFAULT_PAY_PATTERNS);

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
	/** ARGB accent used for "good for you" numbers. */
	public int accentColor = 0xFF3DDC97;

	public Game lastGame = Game.PAPER;
}
