package com.donutgamble.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** Opened with Insert: frees the mouse so the queue can be clicked and dragged. Draws nothing itself. */
final class QueueScreen extends Screen {
	private final QueueOverlay overlay;

	QueueScreen(QueueOverlay overlay) {
		super(Text.literal("Gamble queue"));
		this.overlay = overlay;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		return overlay.click(click.x(), click.y(), click.button())
			|| (click.button() == 0 && overlay.startDrag(click.x(), click.y()))
			|| super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double dx, double dy) {
		return overlay.drag(click.x(), click.y()) || super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(Click click) {
		return overlay.endDrag() || super.mouseReleased(click);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (DonutGambleClient.OPEN_QUEUE.matchesKey(input)) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
