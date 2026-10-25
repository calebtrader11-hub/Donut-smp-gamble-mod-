package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentDeduplicatorTest {
	@Test
	void sameMessageTwiceInWindowIsDuplicate() {
		PaymentDeduplicator d = new PaymentDeduplicator();
		assertFalse(d.isDuplicate("Bob", 50, 1000, 1500));
		assertTrue(d.isDuplicate("bob", 50, 1400, 1500));
		assertFalse(d.isDuplicate("Bob", 60, 1400, 1500), "different amount");
		assertFalse(d.isDuplicate("Bob", 50, 5000, 1500), "outside window");
	}

	@Test
	void zeroWindowDisables() {
		PaymentDeduplicator d = new PaymentDeduplicator();
		assertFalse(d.isDuplicate("Bob", 50, 1000, 0));
		assertFalse(d.isDuplicate("Bob", 50, 1000, 0));
	}
}
