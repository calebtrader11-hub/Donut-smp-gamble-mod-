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

	private final List<Pattern> patterns = new ArrayList<>();

	public PaymentDetector(List<String> regexes) {
		for (String regex : regexes) {
			try {
				patterns.add(Pattern.compile(regex));
			} catch (PatternSyntaxException e) {
				System.err.println("[DonutGamble] Skipping invalid pay pattern: " + regex);
			}
		}
	}

	public Optional<Payment> detect(String message) {
		String clean = COLOR_CODES.matcher(message).replaceAll("").trim();
		for (Pattern p : patterns) {
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
