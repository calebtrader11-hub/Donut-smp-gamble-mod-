package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinanceBookTest {
	private static final long M = 1_000_000L;

	private FinanceBook book() {
		FinanceBook f = new FinanceBook();
		f.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "Alex_", 50 * M, "", 1000, false);
		f.record(Transaction.Type.INCOMING, Transaction.Source.AUCTION, "Bob", 200 * M, "", 2000, false);
		f.record(Transaction.Type.PAYOUT, Transaction.Source.GAMBLE, "Alex_", 100 * M, "", 3000, false);
		f.record(Transaction.Type.EXPENSE, Transaction.Source.GAMBLE, "Kira", 180 * M, "Elytra for Kira", 4000, false);
		return f;
	}

	@Test
	void reportTotals() {
		FinanceReport r = book().report(0);
		assertEquals(250 * M, r.received());
		assertEquals(100 * M, r.paidOut());
		assertEquals(180 * M, r.expenses());
		assertEquals(-30 * M, r.net());
		assertEquals(50 * M, r.receivedFrom(Transaction.Source.GAMBLE));
		assertEquals(200 * M, r.receivedFrom(Transaction.Source.AUCTION));
		assertEquals(2, r.incomingCount());
	}

	@Test
	void reportSinceSessionStart() {
		FinanceReport r = book().report(2500);
		assertEquals(0, r.received());
		assertEquals(-280 * M, r.net());
	}

	@Test
	void voidedTransactionsDoNotCount() {
		FinanceBook f = book();
		Transaction t = f.transactions.get(1);
		assertTrue(f.setVoided(t.id, true));
		assertEquals(50 * M, f.report(0).received());
		f.setVoided(t.id, false);
		assertEquals(250 * M, f.report(0).received());
	}

	@Test
	void searchMatchesPlayerNoteTypeAndAmount() {
		FinanceBook f = book();
		assertEquals(2, f.search("alex").size());
		assertEquals(1, f.search("elytra").size());
		assertEquals(1, f.search("payout").size());
		assertEquals(1, f.search("200m").size());
		assertEquals(4, f.search("").size());
		assertEquals("Kira", f.search("").get(0).player, "newest first");
	}

	@Test
	void playerHistoryAndTopContributors() {
		FinanceBook f = book();
		f.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "alex_", 500 * M, "", 5000, false);
		assertEquals(3, f.history("ALEX_").size());
		List<PlayerTotal> top = f.topContributors(0, 5);
		assertEquals("Alex_", top.get(0).player());
		assertEquals(550 * M, top.get(0).total());
		assertEquals(2, top.get(0).count());
		assertEquals("Bob", top.get(1).player());
	}
}
