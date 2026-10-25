package com.donutgamble.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** A small outlined text button matching the queue's look. */
final class FlatButton extends ClickableWidget {
	private final int textColor;
	private final Runnable action;

	FlatButton(int x, int y, int w, int h, String label, int textColor, Runnable action) {
		super(x, y, w, h, Text.literal(label));
		this.textColor = textColor;
		this.action = action;
	}

	@Override
	protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		Draw.box(ctx, getX(), getY(), getRight(), getBottom(), isHovered() ? 0x44FFFFFF : 0x33000000);
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		int tx = getX() + (getWidth() - tr.getWidth(getMessage())) / 2;
		ctx.drawTextWithShadow(tr, getMessage(), tx, getY() + (getHeight() - 8) / 2, textColor);
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		action.run();
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}
}
