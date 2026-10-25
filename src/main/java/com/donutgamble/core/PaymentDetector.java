package com.donutgamble.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Finds incoming /pay messages in server chat. */
public final class PaymentDetector {
	public record Payment(String player, long amount) {
	}

	private static final Pattern COLOR_CODES = Pattern.compile("§.");
	private static final String NAME = "(?<player>[.A-Za-z0-9_]{2,17})";
	/** Donut SMP shows "$ 75M": an optional currency symbol, optional space, then the amount. */
	private static final String MONEY = "[^0-9\\s]?\\s*(?<amount>[0-9][0-9,.]*\\s*[KkMmBbTt]?)";

	/** Built in, anchored with ^ so player chat can't spoof them. */
	private static final List<Pattern> INCOMING = List.of(
		Pattern.compile("^" + NAME + " paid you " + MONEY),
		Pattern.compile("^You received " + MONEY + " from " + NAME)
	);
	private static final List<Pattern> OUTGOING = List.of(
		Pattern.compile("^You paid " + NAME + " " + MONEY)
	);

	private final List<Pattern> patterns = new ArrayList<>(INCOMING);

	/** @param extraRegexes user-added incoming patterns from config, tried after the built-in ones */
	public PaymentDetector(List<String> extraRegexes) {
		for (String regex : extraRegexes) {
			try {
				patterns.add(Pattern.compile(regex));
			} catch (PatternSyntaxException e) {
				System.err.println("[DonutGamble] Skipping invalid pay pattern: " + regex);
			}
		}
	}

	/** Someone paid you. */
	public Optional<Payment> detect(String message) {
		return match(patterns, message);
	}

	/** You paid someone (used to clear the owed list automatically). */
	public Optional<Payment> detectOutgoing(String message) {
		return match(OUTGOING, message);
	}

	private static Optional<Payment> match(List<Pattern> list, String message) {
		String clean = COLOR_CODES.matcher(message).replaceAll("").trim();
		for (Pattern p : list) {
			Matcher m = p.matcher(clean);
			if (m.find()) {
				try {
					long amount = AmountParser.parse(m.group("amount"));
					if (amount > 0) {
						return Optional.of(new Payment(m.group("player"), amount));
					}
				} catch (IllegalArgumentException | ArithmeticException e) {
					// not a usable amount; try the next pattern
				}
			}
		}
		return Optional.empty();
	}
}
