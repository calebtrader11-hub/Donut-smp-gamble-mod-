package com.donutgamble.core;

import java.util.Map;

public record FinanceReport(long received, long paidOut, long expenses, int incomingCount, int payoutCount,
	Map<Transaction.Source, Long> receivedBySource) {

	public long net() {
		return received - paidOut - expenses;
	}

	public long receivedFrom(Transaction.Source source) {
		return receivedBySource.getOrDefault(source, 0L);
	}
}
