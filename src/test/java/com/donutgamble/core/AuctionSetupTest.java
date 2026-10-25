package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuctionSetupTest {
	private static final long M = 1_000_000L;

	private static AuctionSetup setup(String title, String itemName, int qty) {
		return new AuctionSetup(title, "minecraft:spawner", itemName, qty, "Mined by hand", "", 120, 50 * M, false, 10, 3, false, true, "LEADER", true, false);
	}

	@Test
	void titleFallsBackToItemAndQuantity() {
		assertEquals("Mega Spawner", setup("Mega Spawner", "Spawner", 3).displayTitle());
		assertEquals("3× Spawner", setup("", "Spawner", 3).displayTitle());
		assertEquals("Spawner", setup(" ", "Spawner", 1).displayTitle());
	}

	@Test
	void startCopiesEverything() {
		AuctionManager am = new AuctionManager();
		Auction a = am.start(setup("", "Spawner", 2), 0);
		assertEquals("2× Spawner", a.item);
		assertEquals("minecraft:spawner", a.itemId);
		assertEquals(2, a.quantity);
		assertEquals("Mined by hand", a.description);
		assertNull(a.stackData, "blank stack data is stored as null");
		assertEquals(120_000, a.durationMs);
		assertEquals(3, a.showTop);
		assertFalse(a.showAmounts);
		assertTrue(a.summary().contains("Mined by hand"));
		assertEquals("LEADER", a.bidNotify);
		assertTrue(a.hudCard);
		assertFalse(a.hudLeaderboard);
	}

	@Test
	void perAuctionTimerRulesOverrideGlobal() {
		GambleConfig cfg = new GambleConfig();
		cfg.auctionAutoEnd = true;
		cfg.auctionAntiSnipeSec = 0;
		AuctionManager am = new AuctionManager();
		Auction a = am.start(setup("", "Spawner", 1), 0);
		assertNull(am.tick(200_000, cfg), "auto-end off for this auction");
		am.onPayment("Late", 10 * M, 115_000, 1, cfg);
		assertEquals(10_000, a.remaining(115_000), "this auction's 10s anti-snipe applies");
	}

	@Test
	void summaryCanLeaveOutProfit() {
		AuctionManager am = new AuctionManager();
		Auction a = am.start(setup("", "Spawner", 1), 0);
		am.onPayment("Bob", 100 * M, 1, 1, new GambleConfig());
		am.end(2, new GambleConfig());
		assertTrue(a.summary(true).stream().anyMatch(l -> l.startsWith("Prize cost")));
		assertTrue(a.summary(false).stream().noneMatch(l -> l.startsWith("Prize cost")));
	}

	@Test
	void recentAndFavoriteItems() {
		GambleConfig cfg = new GambleConfig();
		for (int i = 0; i < 20; i++) {
			cfg.rememberItem("minecraft:item" + i);
		}
		cfg.rememberItem("minecraft:item5");
		assertEquals(16, cfg.recentItems.size());
		assertEquals("minecraft:item5", cfg.recentItems.get(0));
		assertTrue(cfg.toggleFavorite("minecraft:diamond"));
		assertFalse(cfg.toggleFavorite("minecraft:diamond"));
		assertTrue(cfg.favoriteItems.isEmpty());
	}
}
