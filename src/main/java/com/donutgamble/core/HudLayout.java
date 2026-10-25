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

		public void addScale(float delta) {
			scale = Math.round(Math.max(0.5f, Math.min(2f, scale + delta)) * 20f) / 20f;
		}
	}

	private static final int GRID = 4;
	private static final int SNAP = 6;

	public Map<String, Slot> slots = new LinkedHashMap<>();

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
