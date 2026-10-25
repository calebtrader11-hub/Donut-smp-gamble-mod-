package com.donutgamble.client;

import com.donutgamble.client.ui.TextBox;

/**
 * One section of the manager window. {@link #init} adds widgets (it runs again on every rebuild,
 * so fields of the page survive); {@link #draw} paints everything else each frame through the
 * shell's helpers.
 */
abstract class ManagerPage {
	final ManagerScreen m;
	final GambleState state;

	ManagerPage(ManagerScreen m) {
		this.m = m;
		this.state = m.state;
	}

	abstract ManagerScreen.Section section();

	/** Add widgets. The content area is {@code m.cx, m.cy, m.cw, m.ch}. */
	void init() {
	}

	abstract void draw();

	/** Handle the wheel yourself; return false to scroll {@link #scrollKey}. */
	boolean scroll(double mx, double my, double vertical) {
		return false;
	}

	/** Which list the wheel scrolls at this point, or null. */
	String scrollKey(double mx, double my) {
		return null;
	}

	/** Enter pressed in a (non-settings) text box; return true if handled. */
	boolean enter(TextBox box) {
		return false;
	}
}
