package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Player directory, finance series, auction stats, player views, HUD layouts, notifications. */
class CoreV3Test {
	private static final long M = 1_000_000L;
	private static final ZoneId UTC = ZoneOffset.UTC;
	private static final long DAY = 86_400_000L;

	@Test
	void directoryNotesTagsUuid() {
		PlayerDirectory d = new PlayerDirectory();
		assertNull(d.get("Bob"));
		assertTrue(d.addTag("Bob", " VIP "));
		assertFalse(d.addTag("bob", "vip"), "duplicate tag ignoring case");
		assertTrue(d.addTag("Bob", "a-very-long-tag-name-that-goes-on"));
		assertEquals(PlayerDirectory.MAX_TAG, d.tags("BOB").get(1).length());
		d.setNotes("bob", "  pays fast ");
		assertEquals("pays fast", d.get("Bob").notes);
		assertTrue(d.setUuid("Bob", "069a79f4-44e9-4726-a5be-fca90e38aaf5"));
		assertFalse(d.setUuid("Bob", "069a79f4-44e9-4726-a5be-fca90e38aaf5"));
		assertTrue(d.removeTag("Bob", "vip"));
		assertEquals(1, d.tags("Bob").size());
	}

	@Test
	void fixedSeriesBucketsByStep() {
		FinanceBook b = new FinanceBook();
		long h = 3_600_000L;
		b.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "A", 10 * M, "", 5, false);
		b.record(Transaction.Type.EXPENSE, Transaction.Source.OTHER, "-", 4 * M, "", 2 * h + 1, false);
		b.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "Late", 1 * M, "", 3 * h, false);
		var s = FinanceSeries.fixed(b, 0, 3 * h, h);
		assertEquals(3, s.size());
		assertEquals(10 * M, s.get(0).net());
		assertEquals(0, s.get(1).net());
		assertEquals(-4 * M, s.get(2).net());
		assertTrue(FinanceSeries.fixed(b, 5, 5, h).isEmpty());
	}

	@Test
	void dailySeriesZeroFillsAndMerges() {
		FinanceBook b = new FinanceBook();
		long day0 = LocalDate.of(2026, 10, 1).atStartOfDay(UTC).toInstant().toEpochMilli();
		b.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "A", 100 * M, "", day0 + 1000, false);
		b.record(Transaction.Type.PAYOUT, Transaction.Source.GAMBLE, "A", 30 * M, "", day0 + 2 * DAY + 5, false);
		Transaction v = b.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "B", 999 * M, "", day0 + 3 * DAY, false);
		b.setVoided(v.id, true);
		List<FinanceSeries.Bucket> days = FinanceSeries.daily(b, day0, day0 + 3 * DAY + 10, UTC, 30);
		assertEquals(4, days.size());
		assertEquals(100 * M, days.get(0).received());
		assertEquals(0, days.get(1).received(), "empty day is kept");
		assertEquals(-30 * M, days.get(2).net());
		assertEquals(0, days.get(3).received(), "voided ignored");
		List<FinanceSeries.Bucket> merged = FinanceSeries.daily(b, day0, day0 + 3 * DAY, UTC, 2);
		assertEquals(2, merged.size());
		assertEquals(100 * M, merged.get(0).net());
		assertEquals(-30 * M, merged.get(1).net());
	}

	@Test
	void rangeStarts() {
		FinanceBook b = new FinanceBook();
		long now = LocalDate.of(2026, 10, 25).atStartOfDay(UTC).toInstant().toEpochMilli() + 5 * 3_600_000L;
		long today = LocalDate.of(2026, 10, 25).atStartOfDay(UTC).toInstant().toEpochMilli();
		assertEquals(today, FinanceSeries.Range.TODAY.start(b, now, UTC));
		assertEquals(today - 6 * DAY, FinanceSeries.Range.WEEK.start(b, now, UTC));
		b.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "A", 1, "", today - 40 * DAY + 77, false);
		assertEquals(today - 40 * DAY, FinanceSeries.Range.ALL.start(b, now, UTC));
		assertEquals(1, b.report(today - 41 * DAY, today).received());
		assertEquals(0, b.report(today, now).received());
	}

	@Test
	void auctionStats() {
		GambleConfig cfg = new GambleConfig();
		AuctionManager am = new AuctionManager();
		am.start("A", 60, 0, 0);
		am.onPayment("x", 100 * M, 1, 1, cfg);
		am.end(10, cfg);
		am.start("B", 60, 0, 20);
		am.onPayment("y", 300 * M, 21, 2, cfg);
		am.end(30, cfg);
		am.start("C", 60, 0, 40);
		am.onPayment("z", 900 * M, 41, 3, cfg);
		am.cancel(50);
		AuctionStats s = am.stats(0);
		assertEquals(2, s.completed(), "cancelled doesn't count");
		assertEquals(400 * M, s.totalCollected());
		assertEquals(200 * M, s.average());
		assertEquals("B", s.biggest().item);
		assertEquals(1, am.stats(25).completed());
		assertNull(new AuctionManager().stats(0).biggest());
	}

	@Test
	void playerViewsAndOutstanding() {
		GambleConfig cfg = new GambleConfig();
		Ledger l = new Ledger();
		FinanceBook f = new FinanceBook();
		AuctionManager am = new AuctionManager();
		Bet w = l.addPayment("Winner", 50 * M, Game.PAPER, 1);
		l.resolve(w.id, Outcome.PLAYER_WIN, 9, 1, cfg);
		f.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "Big", 900 * M, "", 2, false);
		f.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "Busy", 1 * M, "", 3, false);
		f.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "Busy", 1 * M, "", 4, false);
		f.record(Transaction.Type.INCOMING, Transaction.Source.GAMBLE, "Busy", 1 * M, "", 5, false);
		am.start("A", 60, 0, 6);
		am.onPayment("Bidder", 10 * M, 7, 9, cfg);
		List<PlayerStats> all = PlayerStats.all(l, f, am);
		assertEquals(100 * M, PlayerStats.find(all, "Winner").outstanding());
		assertEquals("Big", PlayerStats.view(all, PlayerStats.View.BIGGEST).get(0).player());
		assertEquals("Busy", PlayerStats.view(all, PlayerStats.View.MOST_ACTIVE).get(0).player());
		assertEquals(List.of("Winner"), PlayerStats.view(all, PlayerStats.View.OUTSTANDING).stream().map(PlayerStats::player).toList());
		assertEquals(List.of("Bidder"), PlayerStats.view(all, PlayerStats.View.AUCTIONS).stream().map(PlayerStats::player).toList());
		assertEquals("Bidder", PlayerStats.view(all, PlayerStats.View.RECENT).get(0).player());
	}

	@Test
	void payoutMatchingReturnsWhatWasPaid() {
		GambleConfig cfg = new GambleConfig();
		Ledger l = new Ledger();
		Bet b = l.addPayment("A", 10 * M, Game.PAPER, 1);
		l.resolve(b.id, Outcome.PLAYER_WIN, null, null, cfg);
		assertNull(l.settleMatching("A", 5 * M));
		assertSame(b, l.settleMatching("a", 20 * M));
	}

	@Test
	void savedHudLayouts() {
		HudLayout h = new HudLayout();
		HudLayout.Slot s = h.slot("queue", 10, 20, true);
		s.opacity = 0.5f;
		s.compact = true;
		h.saveAs("Mine");
		s.x = 300;
		s.addOpacity(-1f);
		assertEquals(0.2f, s.opacity, 1e-6);
		assertTrue(h.load("Mine"));
		assertEquals(10, h.slots.get("queue").x);
		assertEquals(0.5f, h.slots.get("queue").opacity, 1e-6);
		assertTrue(h.slots.get("queue").compact);
		h.slots.get("queue").x = 1;
		assertEquals(10, h.saved.get("Mine").get("queue").x, "loading copies, the saved layout stays intact");
		assertFalse(h.load("Nope"));
	}

	@Test
	void notificationSettings() {
		NotificationSettings n = new NotificationSettings();
		assertTrue(n.isEnabled(NotificationSettings.Kind.AUCTION_BID));
		n.setEnabled(NotificationSettings.Kind.AUCTION_BID, false);
		assertFalse(n.isEnabled(NotificationSettings.Kind.AUCTION_BID));
		n.setDuration(100);
		assertEquals(15, n.durationSec);
		n.setDuration(0);
		assertEquals(2, n.durationSec);
	}
}
