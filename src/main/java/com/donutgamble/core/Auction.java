package com.donutgamble.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One auction: players /pay you, the biggest total contributor when the timer ends wins. */
public class Auction {
	public enum Status {
		RUNNING,
		PAUSED,
		ENDED,
		CANCELLED
	}

	public long id;
	/** Title shown everywhere (the item name unless a custom title was set). */
	public String item;
	/** Prize item registry id, e.g. "minecraft:spawner"; null/blank for a text-only auction. */
	public String itemId;
	public int quantity = 1;
	public String description = "";
	/** The exact prize stack (with components) as JSON, when picked from the inventory. */
	public String stackData;
	/** Per-auction timer rules; null means "use the global setting". */
	public Boolean autoEnd;
	public Integer antiSnipeSec;
	/** What the HUD shows about bidders. */
	public int showTop = 5;
	public boolean showAmounts = true;
	public boolean showShare = true;
	/** Which bids pop up: ALL, LEADER (only a new leader) or OFF. */
	public String bidNotify = "ALL";
	/** Show the auction card / leaderboard HUD components for this auction. */
	public boolean hudCard = true;
	public boolean hudLeaderboard = true;
	/** What the prize costs you; counted as an expense when the auction ends with a winner. */
	public long prizeCost;
	public long startedAt;
	public long endedAt;
	public long durationMs;
	/** While running: when the timer hits zero. */
	public long endsAt;
	/** While paused: time left on the clock. */
	public long remainingMs;
	public Status status;
	public String winner;
	public List<Contribution> contributions = new ArrayList<>();

	public boolean isLive() {
		return status == Status.RUNNING || status == Status.PAUSED;
	}

	public void start(long now) {
		startedAt = now;
		endsAt = now + durationMs;
		status = Status.RUNNING;
	}

	public void pause(long now) {
		if (status == Status.RUNNING) {
			remainingMs = remaining(now);
			status = Status.PAUSED;
		}
	}

	public void resume(long now) {
		if (status == Status.PAUSED) {
			endsAt = now + remainingMs;
			status = Status.RUNNING;
		}
	}

	public long remaining(long now) {
		return switch (status) {
			case RUNNING -> Math.max(0, endsAt - now);
			case PAUSED -> remainingMs;
			default -> 0;
		};
	}

	/** Adds (or with a negative value, removes) time. Never goes below zero. */
	public void extend(long ms, long now) {
		if (status == Status.RUNNING) {
			endsAt = now + Math.max(0, remaining(now) + ms);
		} else if (status == Status.PAUSED) {
			remainingMs = Math.max(0, remainingMs + ms);
		}
	}

	public void addContribution(String player, long amount, long now, long txId, boolean manual) {
		Contribution c = new Contribution();
		c.player = player;
		c.amount = amount;
		c.timestamp = now;
		c.txId = txId;
		c.manual = manual;
		contributions.add(c);
		version++;
	}

	/** Manual fix: add or (negative) take away from a player's total. */
	public void correct(String player, long delta, long now) {
		addContribution(canonicalName(player), delta, now, 0, true);
	}

	/** A payment was voided in the finance book; drop it from the auction too. */
	public boolean removeTransaction(long txId) {
		boolean removed = txId != 0 && contributions.removeIf(c -> c.txId == txId);
		if (removed) {
			version++;
		}
		return removed;
	}

	public long total() {
		long sum = 0;
		for (Contribution c : contributions) {
			sum += c.amount;
		}
		return sum;
	}

	/** Bumped on every change to {@link #contributions}, so the ranking is only rebuilt when needed. */
	private transient int version;
	private transient int cachedVersion = -1;
	private transient int cachedSize = -1;
	private transient List<Standing> cachedRankings;

	/**
	 * Biggest total first; on a tie, whoever paid first ranks higher. Players at 0 or below are left
	 * out. Cached until the contributions change (the HUD asks several times a frame). Read-only.
	 */
	public List<Standing> rankings() {
		if (cachedRankings == null || cachedVersion != version || cachedSize != contributions.size()) {
			cachedRankings = java.util.Collections.unmodifiableList(computeRankings());
			cachedVersion = version;
			cachedSize = contributions.size();
		}
		return cachedRankings;
	}

	private List<Standing> computeRankings() {
		Map<String, long[]> totals = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (Contribution c : contributions) {
			String key = c.player.toLowerCase(Locale.ROOT);
			names.putIfAbsent(key, c.player);
			long[] v = totals.computeIfAbsent(key, k -> new long[]{0, 0, c.timestamp});
			v[0] += c.amount;
			if (!c.manual) {
				v[1]++;
			}
		}
		long total = Math.max(1, total());
		List<Standing> list = new ArrayList<>();
		totals.forEach((k, v) -> {
			if (v[0] > 0) {
				list.add(new Standing(names.get(k), v[0], (int) v[1], (double) v[0] / total, v[2]));
			}
		});
		list.sort((a, b) -> a.total() != b.total() ? Long.compare(b.total(), a.total()) : Long.compare(a.firstAt(), b.firstAt()));
		return list;
	}

	public Standing leader() {
		List<Standing> r = rankings();
		return r.isEmpty() ? null : r.get(0);
	}

	private String canonicalName(String player) {
		for (Contribution c : contributions) {
			if (c.player.equalsIgnoreCase(player)) {
				return c.player;
			}
		}
		return player;
	}

	/** Plain-text recap shown in the GUI and exports. */
	public List<String> summary() {
		return summary(true);
	}

	/** @param includeProfit false leaves out the prize cost and profit line (Streamer Mode) */
	public List<String> summary(boolean includeProfit) {
		List<String> lines = new ArrayList<>();
		String when = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(startedAt));
		lines.add(item + " · " + status.name().toLowerCase(Locale.ROOT) + " · " + when);
		if (description != null && !description.isBlank()) {
			lines.add(description.trim());
		}
		List<Standing> r = rankings();
		lines.add("Total $" + AmountParser.format(total()) + " from " + r.size() + (r.size() == 1 ? " player" : " players"));
		if (winner != null) {
			Standing w = r.isEmpty() ? null : r.get(0);
			lines.add("Winner: " + winner + (w != null ? " ($" + AmountParser.format(w.total()) + ")" : ""));
		} else if (status == Status.CANCELLED) {
			lines.add("Cancelled: everyone is refunded");
		} else if (!isLive()) {
			lines.add("No winner");
		}
		if (includeProfit && prizeCost > 0) {
			lines.add("Prize cost $" + AmountParser.format(prizeCost) + " · profit $" + AmountParser.format(total() - prizeCost));
		}
		for (int i = 0; i < Math.min(5, r.size()); i++) {
			Standing s = r.get(i);
			lines.add("#" + (i + 1) + " " + s.player() + " $" + AmountParser.format(s.total())
				+ " (" + Math.round(s.share() * 100) + "%)");
		}
		return lines;
	}
}
