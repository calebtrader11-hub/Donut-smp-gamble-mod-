package com.donutgamble.client;

import java.util.Locale;

/**
 * Design tokens: charcoal surfaces, three text weights, meaning colors, and an accent that follows
 * the chosen {@link Theme} (electric cyan by default). HUD components can override the accent for
 * themselves while they draw ({@link #pushAccent}).
 */
public final class Colors {
	public enum Theme {
		AQUA("Cyan", 0xFF22D3EE, 0xFFA5F3FC, 0xFF6FA9B6),
		BLUE("Electric blue", 0xFF3B82F6, 0xFFBFDBFE, 0xFF7D93B8),
		PURPLE("Violet", 0xFFA78BFA, 0xFFDDD6FE, 0xFF9C93B8),
		GOLD("Gold", 0xFFFBBF24, 0xFFFDE68A, 0xFFB5A27A),
		RED("Red", 0xFFF87171, 0xFFFECACA, 0xFFB89090),
		LIME("Lime", 0xFFA3E635, 0xFFD9F99D, 0xFF98AE7C),
		PINK("Pink", 0xFFF472B6, 0xFFFBCFE8, 0xFFB693A8),
		WHITE("White", 0xFFF1F5F9, 0xFFFFFFFF, 0xFFA8B0BC);

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

		public Theme next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public static Theme parse(String name) {
			try {
				return valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException | NullPointerException e) {
				return AQUA;
			}
		}
	}

	// Surfaces, darkest to lightest.
	public static final int SCRIM = 0xA6040507;
	public static final int WINDOW = 0xF70D0F14;
	public static final int WINDOW_LOW = 0xF7090B0F;
	public static final int SIDEBAR = 0xFF0A0C10;
	public static final int CARD = 0xFF12151C;
	public static final int CARD_TOP = 0xFF161A22;
	public static final int CARD_HI = 0xFF1C212B;
	public static final int INPUT = 0xFF090B0F;
	public static final int LINE = 0xFF1F242E;
	public static final int LINE_HI = 0xFF2D3441;
	/** HUD panels: see-through so the world shows behind. */
	public static final int HUD = 0xC00A0C11;

	// Text.
	public static final int INK = 0xFFE8ECF2;
	public static final int INK_DIM = 0xFF8C95A8;
	public static final int INK_FAINT = 0xFF5B6377;
	public static final int ON_ACCENT = 0xFF071014;

	// Meaning.
	public static final int GOLD = 0xFFFBBF24;
	public static final int GREEN = 0xFF34D399;
	public static final int RED = 0xFFF87171;
	public static final int ORANGE = 0xFFFB923C;
	public static final int GREY = 0xFFCBD2DC;

	// Legacy names still used by the HUD.
	public static final int BG = HUD;
	public static final int BOX_BG = 0xE60A0C11;

	static Theme theme = Theme.AQUA;
	/** Accent override while one HUD component draws (null = the global theme). */
	private static Theme override;

	private Colors() {
	}

	public static Theme theme() {
		return theme;
	}

	private static Theme active() {
		return override != null ? override : theme;
	}

	/** Use another theme's accent until {@link #popAccent}; blank or unknown = the global theme. */
	public static void pushAccent(String themeName) {
		override = themeName == null || themeName.isBlank() ? null : Theme.parse(themeName);
	}

	public static void popAccent() {
		override = null;
	}

	/** The theme's strong color: outlines, selection, primary buttons. */
	public static int accent() {
		return active().outline;
	}

	public static int outline() {
		return active().outline;
	}

	/** Light tint of the accent; HUD body text. */
	public static int text() {
		return active().text;
	}

	public static int muted() {
		return active().muted;
	}

	public static int profit(long value) {
		return value >= 0 ? GREEN : RED;
	}

	/** Same color with a new alpha (0-255). */
	public static int alpha(int argb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0x00FFFFFF);
	}

	/** Multiplies the color's alpha by {@code f} (0..1). */
	public static int fade(int argb, float f) {
		return alpha(argb, Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f))));
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
