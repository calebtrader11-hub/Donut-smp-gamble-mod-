package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LedgerTest {
	private static final long M = 1_000_000L;
	private final GambleConfig cfg = new GambleConfig();

	private Ledger ledger() {
		Ledger l = new Ledger();
		l.startSession(1000);
		return l;
	}

	@Test
	void lossAddsBetToProfit() {
		Ledger l = ledger();
		Bet b = l.addPayment("Steve", 250 * M, Game.BLACKJACK, 2000);
		l.resolve(b.id, Outcome.PLAYER_LOSE, 18, 20, cfg);
		Stats s = l.sessionStats();
		assertEquals(250 * M, s.net());
		assertEquals(250 * M, s.wagered());
		assertEquals(0, s.playerWins());
		assertEquals(1, s.playerLosses());
		assertTrue(l.owed().isEmpty());
	}

	@Test
	void moneyWinCostsBetAndIsOwed() {
		Ledger l = ledger();
		Bet b = l.addPayment("Alex_", 45 * M, Game.PAPER, 2000);
		l.resolve(b.id, Outcome.PLAYER_WIN, 9, 8, cfg);
		assertEquals(-45 * M, l.sessionStats().net());
		assertEquals(1, l.owed().size());
		assertEquals(90 * M, l.owed().get(0).payout);
		assertFalse(l.owed().get(0).payoutIsElytra());
	}

	@Test
	void elyWinCostsElytraPrice() {
		cfg.elytraPrice = 180 * M;
		Ledger l = ledger();
		Bet b = l.addPayment("Kira", 120 * M, Game.ELY, 2000);
		l.resolve(b.id, Outcome.PLAYER_WIN, null, null, cfg);
		assertEquals(-60 * M, l.sessionStats().net());
		assertTrue(l.owed().get(0).payoutIsElytra());
		l.settle(b.id);
		assertTrue(l.owed().isEmpty());
		assertEquals(-60 * M, l.sessionStats().net());
	}

	@Test
	void pendingBetsDoNotCount() {
		Ledger l = ledger();
		l.addPayment("Bob", 20 * M, Game.PAPER, 2000);
		assertEquals(0, l.sessionStats().net());
		assertEquals(1, l.pending().size());
	}

	@Test
	void newSessionKeepsLifetimeAndOwed() {
		Ledger l = ledger();
		Bet a = l.addPayment("A", 100 * M, Game.PAPER, 2000);
		l.resolve(a.id, Outcome.PLAYER_LOSE, 1, 2, cfg);
		Bet b = l.addPayment("B", 10 * M, Game.PAPER, 2100);
		l.resolve(b.id, Outcome.PLAYER_WIN, 3, 2, cfg);
		l.startSession(5000);
		assertEquals(0, l.sessionStats().net());
		assertEquals(90 * M, l.lifetimeStats().net());
		assertEquals(1, l.owed().size());
	}

	@Test
	void recentNewestFirst() {
		Ledger l = ledger();
		for (int i = 0; i < 7; i++) {
			Bet b = l.addPayment("P" + i, 10 * M, Game.PAPER, 2000 + i);
			l.resolve(b.id, Outcome.PLAYER_LOSE, 1, 2, cfg);
		}
		assertEquals(5, l.sessionStats().recent().size());
		assertEquals("P6", l.sessionStats().recent().get(0).player);
	}

	@Test
	void changeGameBeforeResolve() {
		Ledger l = ledger();
		Bet b = l.addPayment("S", 200 * M, Game.PAPER, 2000);
		l.setGame(b.id, Game.BLACKJACK);
		assertEquals(Game.BLACKJACK, l.find(b.id).game);
	}
}
