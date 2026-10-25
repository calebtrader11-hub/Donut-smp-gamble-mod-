package com.donutgamble.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Where each HUD component sits, its size and whether it's shown. Saved inside config.json. */
public class HudLayout {
	public static class Slot {
		public int x;
		public int y;
		public float scale = 1f;
		public boolean visible = true;
		/** Background opacity, 0.2 to 1. */
		public float opacity = 1f;
		/** Accent color name for this component ("" = the global color). */
		public String accent = "";
		/** Compact layout: less padding and fewer rows. */
		public boolean compact = false;

		public Slot copy() {
			Slot s = new Slot();
			s.x = x;
			s.y = y;
			s.scale = scale;
			s.visible = visible;
			s.opacity = opacity;
			s.accent = accent;
			s.compact = compact;
			return s;
		}

		public void addOpacity(float delta) {
			opacity = Math.round(Math.max(0.2f, Math.min(1f, opacity + delta)) * 20f) / 20f;
		}

		public void addScale(float delta) {
			scale = Math.round(Math.max(0.5f, Math.min(2f, scale + delta)) * 20f) / 20f;
		}
	}

	private static final int GRID = 4;
	private static final int SNAP = 6;

	public Map<String, Slot> slots = new LinkedHashMap<>();
	/** Named layouts the player saved. */
	public Map<String, Map<String, Slot>> saved = new LinkedHashMap<>();

	/** Saves a copy of the current layout under {@code name} (replacing one with that name). */
	public void saveAs(String name) {
		Map<String, Slot> copy = new LinkedHashMap<>();
		slots.forEach((k, v) -> copy.put(k, v.copy()));
		saved.put(name, copy);
	}

	/** Replaces the current layout with a copy of a saved one; false if there's no such layout. */
	public boolean load(String name) {
		Map<String, Slot> layout = saved.get(name);
		if (layout == null) {
			return false;
		}
		slots.clear();
		layout.forEach((k, v) -> slots.put(k, v.copy()));
		return true;
	}

	/** The saved slot, or a new one at the given default (kept from then on). */
	public Slot slot(String id, int defaultX, int defaultY, boolean defaultVisible) {
		return slots.computeIfAbsent(id, k -> {
			Slot s = new Slot();
			s.x = defaultX;
			s.y = defaultY;
			s.visible = defaultVisible;
			return s;
		});
	}

	/** Top-left position that keeps a w×h box fully on a screenW×screenH screen. */
	public static int[] clamp(int x, int y, int w, int h, int screenW, int screenH) {
		return new int[]{Math.max(0, Math.min(x, screenW - w)), Math.max(0, Math.min(y, screenH - h))};
	}

	/** Snaps one axis to the screen edges or center when close, else to a 4px grid. */
	public static int snap(int pos, int size, int screen) {
		int far = screen - size;
		int center = (screen - size) / 2;
		if (Math.abs(pos) <= SNAP) {
			return 0;
		}
		if (Math.abs(pos - far) <= SNAP) {
			return far;
		}
		if (Math.abs(pos - center) <= SNAP) {
			return center;
		}
		return Math.round(pos / (float) GRID) * GRID;
	}
}
