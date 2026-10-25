package com.donutgamble.client;

import com.donutgamble.client.ui.Regions;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * One movable piece of the HUD. It draws in its own coordinates starting at (0, 0); the
 * {@link HudManager} positions and scales it from the saved {@link com.donutgamble.core.HudLayout}.
 */
abstract class HudElement {
	final String id;
	final String title;

	/** Compact layout requested by the component's style (fewer rows, less detail). */
	boolean compact;

	/** Screen-space bounds from the last frame (after scaling), for dragging and the editor. */
	int screenX, screenY, screenW, screenH;

	HudElement(String id, String title) {
		this.id = id;
		this.title = title;
	}

	/** Unscaled width and height for the content it would draw now. */
	abstract int[] size(TextRenderer tr, boolean editing);

	/** Draw at (0, 0). Clickable parts go into {@code regions} in the same local coordinates. */
	abstract void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing);

	/** Whether there's anything to show right now (outside the editor). */
	boolean hasContent() {
		return true;
	}

	/** Where it goes the first time, given the screen and its own size. */
	abstract int[] defaultPosition(int screenW, int screenH, int w, int h);

	boolean visibleByDefault() {
		return true;
	}
}
