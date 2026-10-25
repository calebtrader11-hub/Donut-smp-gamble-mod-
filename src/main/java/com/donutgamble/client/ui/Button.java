package com.donutgamble.client.ui;

import com.donutgamble.client.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** The one button style used everywhere: rounded, hover fades in, four variants. */
public final class Button extends ClickableWidget {
	public enum Variant {
		/** Accent fill: the main action. */
		PRIMARY,
		/** Card fill with a border. */
		SECONDARY,
		/** No fill until hovered. */
		GHOST,
		/** Red: destructive. */
		DANGER
	}

	public static final int HEIGHT = 16;

	private final Variant variant;
	private final Runnable action;
	private final Anim hover = new Anim(0);
	private boolean selected;

	public Button(int x, int y, int w, String label, Variant variant, Runnable action) {
		super(x, y, w, HEIGHT, Text.literal(label));
		this.variant = variant;
		this.action = action;
	}

	/** Width that fits the label with standard padding. */
	public static int widthFor(String label) {
		return MinecraftClient.getInstance().textRenderer.getWidth(label) + 2 * Paint.S5;
	}

	public static Button fit(int x, int y, String label, Variant variant, Runnable action) {
		return new Button(x, y, widthFor(label), label, variant, action);
	}

	public Button selected(boolean selected) {
		this.selected = selected;
		return this;
	}

	@Override
	protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		float h = hover.to(active && isHovered() ? 1f : 0f, 18f);
		int accent = Colors.accent();
		int fill, border, ink;
		switch (variant) {
			case PRIMARY -> {
				fill = Colors.mix(accent, 0xFFFFFFFF, 0.18f * h);
				border = fill;
				ink = Colors.ON_ACCENT;
			}
			case DANGER -> {
				fill = Colors.mix(Colors.alpha(Colors.RED, 0x30), Colors.alpha(Colors.RED, 0x55), h);
				border = Colors.alpha(Colors.RED, 0x90);
				ink = Colors.mix(Colors.RED, 0xFFFFFFFF, 0.3f);
			}
			case GHOST -> {
				fill = Colors.alpha(Colors.CARD_HI, Math.round(255 * h));
				border = fill;
				ink = Colors.mix(Colors.INK_DIM, Colors.INK, h);
			}
			default -> {
				fill = Colors.mix(Colors.CARD, Colors.CARD_HI, h);
				border = Colors.mix(Colors.LINE_HI, Colors.alpha(accent, 0xC0), h);
				ink = Colors.INK;
			}
		}
		if (selected) {
			fill = Colors.alpha(accent, 0x40);
			border = accent;
			ink = Colors.INK;
		}
		if (!active) {
			fill = Colors.alpha(fill, 0x50);
			border = Colors.alpha(border, 0x50);
			ink = Colors.INK_FAINT;
		}
		Paint.card(ctx, getX(), getY(), getWidth(), getHeight(), Paint.RADIUS, fill, border);
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		Paint.center(ctx, tr, getMessage().getString(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2 + 1, ink);
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
