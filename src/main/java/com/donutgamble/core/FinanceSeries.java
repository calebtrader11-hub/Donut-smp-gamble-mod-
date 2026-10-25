package com.donutgamble.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Money in and out grouped into time buckets, for charts. */
public final class FinanceSeries {
	/** One bar: {@code start} inclusive to {@code end} exclusive. */
	public record Bucket(long start, long end, long received, long paidOut, long expenses) {
		public long net() {
			return received - paidOut - expenses;
		}
	}

	/** A date range ending today. */
	public enum Range {
		TODAY("Today", 1),
		WEEK("7 days", 7),
		MONTH("30 days", 30),
		ALL("All time", 0);

		public final String label;
		public final int days;

		Range(String label, int days) {
			this.label = label;
			this.days = days;
		}

		/** First millisecond the range covers. ALL starts at the first transaction (or today). */
		public long start(FinanceBook book, long now, ZoneId zone) {
			LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
			if (this == ALL) {
				long first = now;
				for (Transaction t : book.transactions) {
					first = Math.min(first, t.timestamp);
				}
				return Instant.ofEpochMilli(first).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
			}
			return today.minusDays(days - 1L).atStartOfDay(zone).toInstant().toEpochMilli();
		}
	}

	private FinanceSeries() {
	}

	/**
	 * Daily buckets from the start of {@code from}'s day through the day containing {@code to},
	 * merged evenly into at most {@code maxBuckets} bars. Days with nothing are zero, never skipped.
	 */
	public static List<Bucket> daily(FinanceBook book, long from, long to, ZoneId zone, int maxBuckets) {
		LocalDate first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
		LocalDate last = Instant.ofEpochMilli(to).atZone(zone).toLocalDate();
		List<long[]> days = new ArrayList<>();
		for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
			days.add(new long[]{d.atStartOfDay(zone).toInstant().toEpochMilli(),
				d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), 0, 0, 0});
		}
		for (Transaction t : book.transactions) {
			if (t.voided) {
				continue;
			}
			for (long[] d : days) {
				if (t.timestamp >= d[0] && t.timestamp < d[1]) {
					switch (t.type) {
						case INCOMING -> d[2] += t.amount;
						case PAYOUT -> d[3] += t.amount;
						case EXPENSE -> d[4] += t.amount;
					}
					break;
				}
			}
		}
		int per = Math.max(1, (int) Math.ceil(days.size() / (double) Math.max(1, maxBuckets)));
		List<Bucket> out = new ArrayList<>();
		for (int i = 0; i < days.size(); i += per) {
			long start = days.get(i)[0];
			long end = days.get(Math.min(days.size(), i + per) - 1)[1];
			long in = 0, paid = 0, exp = 0;
			for (int j = i; j < Math.min(days.size(), i + per); j++) {
				in += days.get(j)[2];
				paid += days.get(j)[3];
				exp += days.get(j)[4];
			}
			out.add(new Bucket(start, end, in, paid, exp));
		}
		return out;
	}

	/** Fixed-width buckets of {@code stepMs} from {@code from} up to {@code to} (e.g. hours of today). */
	public static List<Bucket> fixed(FinanceBook book, long from, long to, long stepMs) {
		List<Bucket> out = new ArrayList<>();
		if (stepMs <= 0 || to <= from) {
			return out;
		}
		int n = (int) Math.min(500, (to - from + stepMs - 1) / stepMs);
		long[][] acc = new long[n][3];
		for (Transaction t : book.transactions) {
			if (t.voided || t.timestamp < from || t.timestamp >= from + n * stepMs) {
				continue;
			}
			long[] a = acc[(int) ((t.timestamp - from) / stepMs)];
			switch (t.type) {
				case INCOMING -> a[0] += t.amount;
				case PAYOUT -> a[1] += t.amount;
				case EXPENSE -> a[2] += t.amount;
			}
		}
		for (int i = 0; i < n; i++) {
			out.add(new Bucket(from + i * stepMs, from + (i + 1) * stepMs, acc[i][0], acc[i][1], acc[i][2]));
		}
		return out;
	}
}
