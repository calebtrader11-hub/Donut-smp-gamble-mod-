package com.donutgamble.client;

/** ARGB palette shared by the HUD and the panel. */
final class Colors {
	static final int PANEL = 0xF20E1017;
	static final int HUD_BG = 0xDC0C0E14;
	static final int CARD = 0xFF1A1E29;
	static final int TILE = 0xFF181C26;
	static final int FIELD = 0xFF12151D;
	static final int BORDER = 0xFF2F3647;
	static final int TEXT = 0xFFE8ECF2;
	static final int MUTED = 0xFF9AA3B5;
	static final int GOLD = 0xFFFFCF5C;
	static final int OWED_CARD = 0xFF251F12;
	static final int OWED_BORDER = 0xFF5A4A22;
	static final int RED = 0xFFFF6B6B;
	static final int CHIP_ON = 0xFF1D3A55;
	static final int CHIP_ON_BORDER = 0xFF7CC4FF;
	static final int DARK_TEXT = 0xFF14161C;

	private Colors() {
	}

	static int profit(long value, int accent) {
		return value >= 0 ? accent : RED;
	}
}
