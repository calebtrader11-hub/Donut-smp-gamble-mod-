package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CsvExportTest {
	@Test
	void transactionsCsvEscapesFields() {
		FinanceBook f = new FinanceBook();
		f.record(Transaction.Type.EXPENSE, Transaction.Source.OTHER, "Kira", 5_000_000, "Elytra, \"fast\"", 0, true);
		String csv = CsvExport.transactions(f.transactions);
		String[] lines = csv.split("\n");
		assertEquals("id,time,type,source,player,amount,note,manual,voided", lines[0]);
		assertTrue(lines[1].endsWith(",EXPENSE,OTHER,Kira,5000000,\"Elytra, \"\"fast\"\"\",true,false"), lines[1]);
	}

	@Test
	void auctionsCsv() {
		AuctionManager am = new AuctionManager();
		am.start("Spawner", 60, 0, 0);
		am.onPayment("Bob", 100, 10, 1, new GambleConfig());
		am.end(20, new GambleConfig());
		String csv = CsvExport.auctions(am.history);
		assertTrue(csv.split("\n")[1].contains(",Spawner,ENDED,Bob,100,1,"), csv);
	}
}
