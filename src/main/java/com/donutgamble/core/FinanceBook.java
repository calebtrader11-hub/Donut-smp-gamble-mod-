package com.donutgamble.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Every payment in and out. Saved as config/donutgamble/finance.json. */
public class FinanceBook {
	public List<Transaction> transactions = new ArrayList<>();
	public long nextId = 1;

	public Transaction record(Transaction.Type type, Transaction.Source source, String player, long amount, String note,
		long now, boolean manual) {
		Transaction t = new Transaction();
		t.id = nextId++;
		t.timestamp = now;
		t.type = type;
		t.source = source;
		t.player = player;
		t.amount = amount;
		t.note = note == null ? "" : note;
		t.manual = manual;
		transactions.add(t);
		return t;
	}

	public Transaction find(long id) {
		for (Transaction t : transactions) {
			if (t.id == id) {
				return t;
			}
		}
		return null;
	}

	public boolean setVoided(long id, boolean voided) {
		Transaction t = find(id);
		if (t == null || t.voided == voided) {
			return false;
		}
		t.voided = voided;
		return true;
	}

	/** Totals of everything recorded at or after {@code since} (0 = lifetime), voided entries excluded. */
	public FinanceReport report(long since) {
		long received = 0;
		long paidOut = 0;
		long expenses = 0;
		int in = 0;
		int out = 0;
		Map<Transaction.Source, Long> bySource = new EnumMap<>(Transaction.Source.class);
		for (Transaction t : transactions) {
			if (t.voided || t.timestamp < since) {
				continue;
			}
			switch (t.type) {
				case INCOMING -> {
					received += t.amount;
					in++;
					bySource.merge(t.source, t.amount, Long::sum);
				}
				case PAYOUT -> {
					paidOut += t.amount;
					out++;
				}
				case EXPENSE -> expenses += t.amount;
			}
		}
		return new FinanceReport(received, paidOut, expenses, in, out, bySource);
	}

	/**
	 * Newest first. Matches the player, note, type, source or amount ("200m", "1.5b") case-insensitively;
	 * an empty query returns everything.
	 */
	public List<Transaction> search(String query) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<Transaction> out = new ArrayList<>();
		for (int i = transactions.size() - 1; i >= 0; i--) {
			Transaction t = transactions.get(i);
			if (q.isEmpty() || matches(t, q)) {
				out.add(t);
			}
		}
		return out;
	}

	private static boolean matches(Transaction t, String q) {
		return t.player.toLowerCase(Locale.ROOT).contains(q)
			|| t.note.toLowerCase(Locale.ROOT).contains(q)
			|| t.type.name().toLowerCase(Locale.ROOT).contains(q)
			|| t.source.name().toLowerCase(Locale.ROOT).contains(q)
			|| AmountParser.format(t.amount).toLowerCase(Locale.ROOT).equals(q.replace("$", ""))
			|| Long.toString(t.amount).equals(q);
	}

	/** One player's transactions, newest first. */
	public List<Transaction> history(String player) {
		List<Transaction> out = new ArrayList<>();
		for (int i = transactions.size() - 1; i >= 0; i--) {
			Transaction t = transactions.get(i);
			if (t.player.equalsIgnoreCase(player)) {
				out.add(t);
			}
		}
		return out;
	}

	/** Who paid you the most since {@code since}, biggest first. */
	public List<PlayerTotal> topContributors(long since, int limit) {
		Map<String, long[]> totals = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (Transaction t : transactions) {
			if (t.voided || t.timestamp < since || t.type != Transaction.Type.INCOMING) {
				continue;
			}
			String key = t.player.toLowerCase(Locale.ROOT);
			names.putIfAbsent(key, t.player);
			long[] v = totals.computeIfAbsent(key, k -> new long[2]);
			v[0] += t.amount;
			v[1]++;
		}
		List<PlayerTotal> list = new ArrayList<>();
		totals.forEach((k, v) -> list.add(new PlayerTotal(names.get(k), v[0], (int) v[1])));
		list.sort((a, b) -> Long.compare(b.total(), a.total()));
		return list.subList(0, Math.min(limit, list.size()));
	}
}
