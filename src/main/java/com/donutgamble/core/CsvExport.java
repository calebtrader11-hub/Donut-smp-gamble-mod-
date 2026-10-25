package com.donutgamble.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Spreadsheet-friendly exports. Amounts are written as whole numbers. */
public final class CsvExport {
	private static final SimpleDateFormat TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

	private CsvExport() {
	}

	public static String transactions(List<Transaction> list) {
		StringBuilder sb = new StringBuilder("id,time,type,source,player,amount,note,manual,voided\n");
		for (Transaction t : list) {
			row(sb, t.id, time(t.timestamp), t.type, t.source, t.player, t.amount, t.note, t.manual, t.voided);
		}
		return sb.toString();
	}

	public static String auctions(List<Auction> list) {
		StringBuilder sb = new StringBuilder("id,item,status,winner,total,participants,started,ended,prize_cost\n");
		for (Auction a : list) {
			row(sb, a.id, a.item, a.status, a.winner == null ? "" : a.winner, a.total(), a.rankings().size(),
				time(a.startedAt), a.endedAt == 0 ? "" : time(a.endedAt), a.prizeCost);
		}
		return sb.toString();
	}

	private static String time(long millis) {
		synchronized (TIME) {
			return TIME.format(new Date(millis));
		}
	}

	private static void row(StringBuilder sb, Object... fields) {
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(escape(String.valueOf(fields[i])));
		}
		sb.append('\n');
	}

	static String escape(String s) {
		if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}
}
