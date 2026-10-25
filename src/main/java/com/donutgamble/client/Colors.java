package com.donutgamble.client;

import java.util.Locale;

/**
 * Design tokens. Neutral surfaces and text are fixed; the accent follows the chosen {@link Theme}.
 * HUD text uses the accent's light tint ({@link #text()}) so it stays readable over the world.
 */
public final class Colors {
	public enum Theme {
		AQUA("Aqua", 0xFF4FD8F0, 0xFFA8ECF7, 0xFF8FB3BD),
		PURPLE("Purple", 0xFFB07CFF, 0xFFD2B8FF, 0xFFA99BBF),
		GOLD("Gold", 0xFFFFC547, 0xFFFFE19A, 0xFFBFB08F),
		RED("Red", 0xFFFF5A5A, 0xFFFFB0B0, 0xFFBF9898),
		LIME("Lime", 0xFF7CFF6B, 0xFFC8FFBE, 0xFF9CBF96),
		PINK("Pink", 0xFFF08CD6, 0xFFF7A8E6, 0xFFB9A3B5),
		WHITE("White", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFB0B0B0);

		public final String displayName;
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

	// Surfaces (windows, cards, inputs), darkest to lightest.
	public static final int SCRIM = 0x8C05070A;
	public static final int WINDOW = 0xF20D1016;
	public static final int CARD = 0xFF151922;
	public static final int CARD_HI = 0xFF1C212C;
	public static final int INPUT = 0xFF0A0C11;
	public static final int LINE = 0xFF262C38;
	public static final int LINE_HI = 0xFF363D4C;
	/** HUD panels: see-through so the world shows behind. */
	public static final int HUD = 0xB30A0C11;

	// Text on surfaces.
	public static final int INK = 0xFFE9EDF3;
	public static final int INK_DIM = 0xFF8B94A5;
	public static final int INK_FAINT = 0xFF596173;
	public static final int ON_ACCENT = 0xFF0A0C11;

	// Meaning.
	public static final int GOLD = 0xFFFFCF5C;
	public static final int GREEN = 0xFF5CE08A;
	public static final int RED = 0xFFFF6B6B;
	public static final int GREY = 0xFFD0D0D0;

	// Legacy names still used by the HUD.
	public static final int BG = HUD;
	public static final int BOX_BG = 0xE60A0C11;

	static Theme theme = Theme.AQUA;

	private Colors() {
	}

	public static Theme theme() {
		return theme;
	}

	/** The theme's strong color: outlines, selection, primary buttons. */
	public static int accent() {
		return theme.outline;
	}

	public static int outline() {
		return theme.outline;
	}

	/** Light tint of the accent; HUD body text. */
	public static int text() {
		return theme.text;
	}

	public static int muted() {
		return theme.muted;
	}

	public static int profit(long value) {
		return value >= 0 ? GREEN : RED;
	}

	/** Same color with a new alpha (0-255). */
	public static int alpha(int argb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0x00FFFFFF);
	}

	/** Linear blend from a to b (t 0..1), alpha included. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			r |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return r;
	}
}
