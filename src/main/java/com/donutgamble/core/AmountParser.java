package com.donutgamble.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts Donut SMP money strings ("5M", "$1.2b", "500,000") to numbers and back. */
public final class AmountParser {
	private static final Pattern AMOUNT = Pattern.compile("^\\$?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KMBT]?)$");
	private static final String[] SUFFIXES = {"", "K", "M", "B", "T"};

	private AmountParser() {
	}

	public static long parse(String raw) {
		String s = raw.trim().replaceAll("[.!]+$", "").toUpperCase(Locale.ROOT);
		Matcher m = AMOUNT.matcher(s);
		if (!m.matches()) {
			throw new IllegalArgumentException("Not an amount: " + raw);
		}
		BigDecimal value = new BigDecimal(m.group(1).replace(",", ""));
		int power = suffixPower(m.group(2));
		return value.movePointRight(3 * power).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	public static long parseOrZero(String raw) {
		try {
			return parse(raw);
		} catch (IllegalArgumentException | ArithmeticException e) {
			return 0;
		}
	}

	public static String format(long amount) {
		if (amount < 0) {
			return "-" + format(-amount);
		}
		int power = 0;
		BigDecimal v = BigDecimal.valueOf(amount);
		while (power < SUFFIXES.length - 1 && v.compareTo(BigDecimal.valueOf(1000)) >= 0) {
			v = v.movePointLeft(3);
			power++;
		}
		v = v.setScale(2, RoundingMode.DOWN).stripTrailingZeros();
		return v.toPlainString() + SUFFIXES[power];
	}

	public static String formatSigned(long amount) {
		return amount > 0 ? "+" + format(amount) : format(amount);
	}

	private static int suffixPower(String suffix) {
		for (int i = 0; i < SUFFIXES.length; i++) {
			if (SUFFIXES[i].equals(suffix)) {
				return i;
			}
		}
		return 0;
	}
}
