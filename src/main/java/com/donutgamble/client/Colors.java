package com.donutgamble.client;

import java.util.Locale;

/** ARGB palette. Outline and text follow the chosen {@link Theme}; result colors stay fixed. */
final class Colors {
	enum Theme {
		AQUA("Aqua", 0xFF4FD8F0, 0xFFA8ECF7, 0xFF8FB3BD),
		PURPLE("Purple", 0xFFB07CFF, 0xFFD2B8FF, 0xFFA99BBF),
		GOLD("Gold", 0xFFFFC547, 0xFFFFE19A, 0xFFBFB08F),
		RED("Red", 0xFFFF5A5A, 0xFFFFB0B0, 0xFFBF9898),
		LIME("Lime", 0xFF7CFF6B, 0xFFC8FFBE, 0xFF9CBF96),
		PINK("Pink", 0xFFF08CD6, 0xFFF7A8E6, 0xFFB9A3B5),
		WHITE("White", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFB0B0B0);

		final String displayName;
		final int outline;
		final int text;
		final int muted;

		Theme(String displayName, int outline, int text, int muted) {
			this.displayName = displayName;
			this.outline = outline;
			this.text = text;
			this.muted = muted;
		}

		Theme next() {
			return values()[(ordinal() + 1) % values().length];
		}

		static Theme parse(String name) {
			try {
				return valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException | NullPointerException e) {
				return AQUA;
			}
		}
	}

	static final int BG = 0x66000000;
	static final int BOX_BG = 0xCC000000;
	static final int GOLD = 0xFFFFCF5C;
	static final int GREEN = 0xFF5CE08A;
	static final int RED = 0xFFFF6B6B;
	static final int GREY = 0xFFD0D0D0;

	static Theme theme = Theme.AQUA;

	private Colors() {
	}

	static int outline() {
		return theme.outline;
	}

	static int text() {
		return theme.text;
	}

	static int muted() {
		return theme.muted;
	}

	static int profit(long value) {
		return value >= 0 ? GREEN : RED;
	}
}
