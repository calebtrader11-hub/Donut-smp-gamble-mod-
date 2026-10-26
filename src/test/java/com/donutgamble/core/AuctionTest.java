package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuctionTest {
	private static final long M = 1_000_000L;
	private final GambleConfig cfg = new GambleConfig();

	@Test
	void timerStartsPausesResumes() {
		AuctionManager am = new AuctionManager();
		Auction a = am.start("Diamond Sword", 60, 0, 0);
		assertEquals(Auction.Status.RUNNING, a.status);
		assertEquals(50_000, a.remaining(10_000));
		a.pause(10_000);
		assertEquals(50_000, a.remaining(40_000), "paused timer doesn't move");
		a.resume(40_000);
		assertEquals(40_000, a.remaining(50_000));
		a.extend(30_000, 50_000);
		assertEquals(70_000, a.remaining(50_000));
		a.extend(-100_000, 50_000);
		assertEquals(0, a.remaining(50_000));
	}

	@Test
	void cannotStartTwoAuctions() {
		AuctionManager am = new AuctionManager();
		am.start("A", 60, 0, 0);
		assertThrows(IllegalStateException.class, () -> am.start("B", 60, 0, 0));
	}

	@Test
	void rankingsAndLeader() {
		AuctionManager am = new AuctionManager();
		am.start("Spawner", 60, 0, 0);
		assertTrue(am.onPayment("Bob", 100 * M, 1000, 1, cfg));
		assertTrue(am.onPayment("Alex_", 150 * M, 2000, 2, cfg));
		assertTrue(am.onPayment("bob", 100 * M, 3000, 3, cfg));
		List<Standing> r = am.current.rankings();
		assertEquals("Bob", r.get(0).player());
		assertEquals(200 * M, r.get(0).total());
		assertEquals(2, r.get(0).count());
		assertEquals(350 * M, am.current.total());
		assertEquals(57, Math.round(r.get(0).share() * 100));
	}

	@Test
	void earlierContributorWinsTies() {
		AuctionManager am = new AuctionManager();
		am.start("X", 60, 0, 0).winRule = "TOP";
		am.onPayment("First", 100 * M, 1000, 1, cfg);
		am.onPayment("Second", 100 * M, 2000, 2, cfg);
		assertEquals("First", am.current.leader().player());
	}

	@Test
	void minimumContributionRejected() {
		cfg.auctionMinContribution = 10 * M;
		AuctionManager am = new AuctionManager();
		am.start("X", 60, 0, 0);
		assertFalse(am.onPayment("Cheap", 5 * M, 1000, 1, cfg));
		assertTrue(am.current.rankings().isEmpty());
	}

	@Test
	void noPaymentsWhenNoLiveAuction() {
		AuctionManager am = new AuctionManager();
		assertFalse(am.onPayment("Bob", 100 * M, 1000, 1, cfg));
	}

	@Test
	void antiSnipeExtendsTimer() {
		cfg.auctionAntiSnipeSec = 15;
		AuctionManager am = new AuctionManager();
		Auction a = am.start("X", 60, 0, 0);
		am.onPayment("Late", 10 * M, 55_000, 1, cfg);
		assertEquals(15_000, a.remaining(55_000));
		am.onPayment("Early", 10 * M, 56_000, 2, cfg);
		assertEquals(15_000, a.remaining(56_000));
	}

	@Test
	void correctionsAdjustTotals() {
		AuctionManager am = new AuctionManager();
		am.start("X", 60, 0, 0).winRule = "TOP";
		am.onPayment("Bob", 100 * M, 1000, 1, cfg);
		am.current.correct("bob", -40 * M, 2000);
		am.current.correct("Zed", 70 * M, 2000);
		assertEquals(130 * M, am.current.total());
		assertEquals("Zed", am.current.leader().player());
		am.current.removeTransaction(1);
		assertEquals(30 * M, am.current.total());
	}

	@Test
	void autoEndsAndRefundsLosers() {
		cfg.auctionRefundLosers = true;
		AuctionManager am = new AuctionManager();
		am.start("Elytra", 60, 400 * M, 0);
		am.onPayment("Bob", 100 * M, 1000, 1, cfg);
		am.onPayment("Alex_", 300 * M, 2000, 2, cfg);
		assertNull(am.tick(59_000, cfg));
		Auction ended = am.tick(60_000, cfg);
		assertNotNull(ended);
		assertEquals(Auction.Status.ENDED, ended.status);
		assertEquals("Alex_", ended.winner);
		assertEquals(400 * M, ended.total());
		assertNull(am.current);
		assertEquals(1, am.history.size());
		assertEquals(1, am.openRefunds().size());
		assertEquals("Bob", am.openRefunds().get(0).player);
		assertTrue(am.settleRefundByPayment("bob", 100 * M));
		assertTrue(am.openRefunds().isEmpty());
		assertTrue(ended.summary().get(0).contains("Elytra"));
	}

	@Test
	void autoEndCanBeTurnedOff() {
		cfg.auctionAutoEnd = false;
		AuctionManager am = new AuctionManager();
		am.start("X", 1, 0, 0);
		assertNull(am.tick(5000, cfg));
		assertNotNull(am.current);
	}

	@Test
	void cancelRefundsEveryone() {
		AuctionManager am = new AuctionManager();
		am.start("X", 60, 0, 0);
		am.onPayment("Bob", 100 * M, 1000, 1, cfg);
		am.onPayment("Alex_", 300 * M, 2000, 2, cfg);
		Auction a = am.cancel(3000);
		assertEquals(Auction.Status.CANCELLED, a.status);
		assertNull(a.winner);
		assertEquals(2, am.openRefunds().size());
	}
}
