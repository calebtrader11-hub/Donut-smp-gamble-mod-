package com.donutgamble.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Click targets registered while drawing (for things drawn in render code rather than as
 * widgets). Rebuilt every frame: call {@link #clear()} before drawing.
 */
public final class Regions {
	private record Region(double x, double y, double w, double h, Runnable left, Runnable right) {
	}

	private final List<Region> list = new ArrayList<>();
	/** Maps coordinates given to {@link #add} (e.g. a scaled HUD component's own) to screen space. */
	private double ox, oy, scale = 1;

	public void clear() {
		list.clear();
		transform(0, 0, 1);
	}

	/** Following {@link #add} calls are in a space translated by (x, y) and scaled by {@code s}. */
	public void transform(double x, double y, double s) {
		ox = x;
		oy = y;
		scale = s;
	}

	public void add(double x, double y, double w, double h, Runnable left) {
		add(x, y, w, h, left, null);
	}

	public void add(double x, double y, double w, double h, Runnable left, Runnable right) {
		list.add(new Region(ox + x * scale, oy + y * scale, w * scale, h * scale, left, right));
	}

	/** Is the screen-space mouse over a box given in the current transformed space? */
	public boolean hovered(double mx, double my, double x, double y, double w, double h) {
		double sx = ox + x * scale;
		double sy = oy + y * scale;
		return mx >= sx && mx < sx + w * scale && my >= sy && my < sy + h * scale;
	}

	/** Runs the topmost matching action; true if one ran. */
	public boolean click(double mx, double my, int button) {
		for (int i = list.size() - 1; i >= 0; i--) {
			Region r = list.get(i);
			if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) {
				Runnable action = button == 1 ? r.right : r.left;
				if (action != null) {
					action.run();
					return true;
				}
			}
		}
		return false;
	}

	/** True if the point is on any region (to show a hand cursor or skip dragging). */
	public boolean over(double mx, double my) {
		for (Region r : list) {
			if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) {
				return true;
			}
		}
		return false;
	}
}
