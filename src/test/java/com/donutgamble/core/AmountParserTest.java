package com.donutgamble.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmountParserTest {
	@Test
	void parsesSuffixes() {
		assertEquals(5_000_000L, AmountParser.parse("5M"));
		assertEquals(1_200_000_000L, AmountParser.parse("1.2b"));
		assertEquals(750_000L, AmountParser.parse("750k"));
		assertEquals(2_000_000_000_000L, AmountParser.parse("2T"));
	}

	@Test
	void parsesPlainAndFormatted() {
		assertEquals(500_000L, AmountParser.parse("$500,000"));
		assertEquals(1234L, AmountParser.parse("1234"));
		assertEquals(5_000_000L, AmountParser.parse(" $5M. "));
	}

	@Test
	void rejectsGarbage() {
		assertThrows(IllegalArgumentException.class, () -> AmountParser.parse("abc"));
		assertThrows(IllegalArgumentException.class, () -> AmountParser.parse(""));
	}

	@Test
	void formatsShort() {
		assertEquals("250M", AmountParser.format(250_000_000L));
		assertEquals("1.24B", AmountParser.format(1_240_000_000L));
		assertEquals("45K", AmountParser.format(45_000L));
		assertEquals("999", AmountParser.format(999L));
		assertEquals("-50M", AmountParser.format(-50_000_000L));
		assertEquals("1.5M", AmountParser.format(1_500_000L));
	}

	@Test
	void formatsSigned() {
		assertEquals("+250M", AmountParser.formatSigned(250_000_000L));
		assertEquals("-45M", AmountParser.formatSigned(-45_000_000L));
		assertEquals("0", AmountParser.formatSigned(0));
	}
}
