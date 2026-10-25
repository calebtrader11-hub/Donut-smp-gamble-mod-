package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatsTest {
	private static final long M = 1_000_000L;

	@Test
	void combinesBetsPaymentsAndAuctions() {
		GambleConfig cfg = new GambleConfig();
		Ledger ledger = new Ledger();
		FinanceBook finance = new FinanceBook();
		AuctionManager auctions = new AuctionManager();

		Bet a = ledger.addPayment("Alex_", 50 * M, Game.PAPER, 1000);
		finance.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "Alex_", 50 * M, "", 1000, false);
		ledger.resolve(a.id, Outcome.PLAYER_WIN, 9, 8, cfg);
		finance.record(Transaction.Type.PAYOUT, Transaction.Source.GAMBLE, "alex_", 100 * M, "", 2000, false);
		Bet b = ledger.addPayment("alex_", 30 * M, Game.BLACKJACK, 3000);
		ledger.resolveTie(b.id, null, null, cfg);

		auctions.start("Spawner", 60, 0, 4000);
		finance.record(Transaction.Type.INCOMING, Transaction.Source.AUCTION, "Alex_", 200 * M, "", 5000, false);
		auctions.onPayment("Alex_", 200 * M, 5000, 3, cfg);
		auctions.end(6000, cfg);

		finance.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "Bob", 10 * M, "", 7000, false);

		List<PlayerStats> all = PlayerStats.all(ledger, finance, auctions);
		assertEquals("Bob", all.get(0).player(), "most recent first");
		PlayerStats alex = PlayerStats.find(all, "ALEX_");
		assertNotNull(alex);
		assertEquals(2, alex.bets());
		assertEquals(1, alex.wins());
		assertEquals(0, alex.losses());
		assertEquals(1, alex.ties());
		assertEquals(80 * M, alex.wagered());
		assertEquals(-50 * M + 30 * M, alex.houseNetFromBets());
		assertEquals(250 * M, alex.paidYou());
		assertEquals(100 * M, alex.youPaid());
		assertEquals(150 * M, alex.net());
		assertEquals(200 * M, alex.auctionTotal());
		assertEquals(1, alex.auctionsWon());
		assertEquals(6000, alex.lastSeen());
	}

	@Test
	void filterBySearch() {
		FinanceBook finance = new FinanceBook();
		finance.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "jimbo01", 1, "", 1, false);
		finance.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "Tobias", 1, "", 2, false);
		List<PlayerStats> all = PlayerStats.all(new Ledger(), finance, new AuctionManager());
		assertEquals(1, PlayerStats.filter(all, "JIM").size());
		assertEquals(2, PlayerStats.filter(all, "").size());
	}
}
