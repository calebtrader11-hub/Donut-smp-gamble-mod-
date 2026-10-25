package com.donutgamble.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** A flat colored button or toggle chip. */
final class FlatButton extends ClickableWidget {
	private final int fill;
	private final int border;
	private final int textColor;
	private final Runnable action;

	FlatButton(int x, int y, int w, int h, String label, int fill, int border, int textColor, Runnable action) {
		super(x, y, w, h, Text.literal(label));
		this.fill = fill;
		this.border = border;
		this.textColor = textColor;
		this.action = action;
	}

	static FlatButton solid(int x, int y, int w, int h, String label, int color, Runnable action) {
		return new FlatButton(x, y, w, h, label, color, color, Colors.DARK_TEXT, action);
	}

	static FlatButton outline(int x, int y, int w, int h, String label, boolean selected, Runnable action) {
		return selected
			? new FlatButton(x, y, w, h, label, Colors.CHIP_ON, Colors.CHIP_ON_BORDER, Colors.TEXT, action)
			: new FlatButton(x, y, w, h, label, Colors.CARD, Colors.BORDER, Colors.MUTED, action);
	}

	@Override
	protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int f = active ? fill : 0xFF2A2E38;
		if (active && isHovered()) {
			f = brighten(f);
		}
		int b = active && isHovered() ? brighten(border) : border;
		Draw.roundedBordered(ctx, getX(), getY(), getRight(), getBottom(), f, b);
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		int tx = getX() + (getWidth() - tr.getWidth(getMessage())) / 2;
		int ty = getY() + (getHeight() - 8) / 2;
		ctx.drawText(tr, getMessage(), tx, ty, active ? textColor : Colors.MUTED, false);
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		action.run();
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}

	private static int brighten(int argb) {
		int a = argb >>> 24;
		int r = Math.min(255, ((argb >> 16) & 0xFF) + 24);
		int g = Math.min(255, ((argb >> 8) & 0xFF) + 24);
		int b = Math.min(255, (argb & 0xFF) + 24);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
