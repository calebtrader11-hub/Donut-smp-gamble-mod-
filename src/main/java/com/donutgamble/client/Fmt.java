package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Bet;

import java.util.Locale;

/** Display formatting shared by the HUD and screens. */
final class Fmt {
	private Fmt() {
	}

	/** "$50m", the way Donut SMP writes money. */
	static String money(long amount) {
		return "$" + AmountParser.format(amount).toLowerCase(Locale.ROOT);
	}

	/** "+$50m" / "-$50m" / "$0". */
	static String signed(long v) {
		return (v > 0 ? "+" : v < 0 ? "-" : "") + money(Math.abs(v));
	}

	/** m:ss countdown, rounded up so it never shows 0:00 while time remains. */
	static String clock(long millis) {
		long sec = (Math.max(0, millis) + 999) / 1000;
		return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
	}

	/** h:mm:ss elapsed. */
	static String duration(long millis) {
		long sec = Math.max(0, millis / 1000);
		return String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
	}

	static String owedLabel(Bet b) {
		return b.payoutIsElytra() ? "1 Elytra" : money(b.payout);
	}
}
