package com.donutgamble.client;

/** ARGB palette: see-through box, pink outline and text. */
final class Colors {
	static final int BG = 0x66000000;
	static final int BOX_BG = 0xCC000000;
	static final int OUTLINE = 0xFFF08CD6;
	static final int PINK = 0xFFF7A8E6;
	static final int MUTED = 0xFFB9A3B5;
	static final int GOLD = 0xFFFFCF5C;
	static final int GREEN = 0xFF5CE08A;
	static final int RED = 0xFFFF6B6B;
	static final int GREY = 0xFFD0D0D0;

	private Colors() {
	}

	static int profit(long value) {
		return value >= 0 ? GREEN : RED;
	}
}
