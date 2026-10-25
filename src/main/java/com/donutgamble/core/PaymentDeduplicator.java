package com.donutgamble.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Drops a payment that repeats the same player and amount within a short window, so one /pay
 * that shows up twice (a mod or the server echoing the line) isn't counted twice.
 */
public final class PaymentDeduplicator {
	private record Seen(String player, long amount, long at) {
	}

	private final Deque<Seen> recent = new ArrayDeque<>();

	public boolean isDuplicate(String player, long amount, long now, long windowMs) {
		if (windowMs <= 0) {
			return false;
		}
		while (!recent.isEmpty() && now - recent.peekFirst().at() > windowMs) {
			recent.removeFirst();
		}
		for (Seen s : recent) {
			if (s.amount() == amount && s.player().equalsIgnoreCase(player)) {
				return true;
			}
		}
		recent.addLast(new Seen(player, amount, now));
		return false;
	}
}
