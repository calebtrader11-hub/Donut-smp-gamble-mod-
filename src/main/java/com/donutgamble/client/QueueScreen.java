package com.donutgamble.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** Opened with Insert: frees the mouse so the queue's letters can be clicked. Draws nothing itself. */
final class QueueScreen extends Screen {
	private final QueueOverlay overlay;

	QueueScreen(QueueOverlay overlay) {
		super(Text.literal("Gamble queue"));
		this.overlay = overlay;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		return overlay.click(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubled);
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
