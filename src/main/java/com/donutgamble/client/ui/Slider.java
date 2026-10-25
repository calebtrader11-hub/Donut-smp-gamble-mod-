package com.donutgamble.client.ui;

import com.donutgamble.client.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Drag or click to pick a whole number in a range. The value label sits at the right end. */
public final class Slider extends ClickableWidget {
	public static final int HEIGHT = 14;
	private static final int LABEL_W = 38;

	private final int min, max, step;
	private final IntFunction<String> format;
	private final IntConsumer onChange;
	private final Anim hover = new Anim(0);
	private final Anim pos;
	private int value;

	public Slider(int x, int y, int w, int min, int max, int step, int value, IntFunction<String> format, IntConsumer onChange) {
		super(x, y, w, HEIGHT, Text.literal(""));
		this.min = min;
		this.max = max;
		this.step = Math.max(1, step);
		this.value = clamp(value);
		this.format = format;
		this.onChange = onChange;
		this.pos = new Anim(fraction());
	}

	private int clamp(int v) {
		v = Math.max(min, Math.min(max, v));
		return min + Math.round((v - min) / (float) step) * step;
	}

	private float fraction() {
		return max == min ? 0 : (value - min) / (float) (max - min);
	}

	private int trackX() {
		return getX() + 4;
	}

	private int trackW() {
		return getWidth() - LABEL_W - 8;
	}

	private void setFromMouse(double mx) {
		float f = (float) ((mx - trackX()) / Math.max(1, trackW()));
		int v = clamp(Math.round(min + f * (max - min)));
		if (v != value) {
			value = v;
			onChange.accept(v);
		}
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		setFromMouse(click.x());
	}

	@Override
	protected void onDrag(Click click, double dx, double dy) {
		setFromMouse(click.x());
	}

	@Override
	protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		float h = hover.to(isHovered() ? 1 : 0, 18f);
		float f = pos.to(fraction(), 22f);
		int ty = getY() + getHeight() / 2 - 2;
		int accent = Colors.accent();
		Paint.bar(ctx, trackX(), ty, trackW(), 4, f, Colors.LINE_HI, accent);
		int kx = trackX() + Math.round(trackW() * f) - 4;
		int ks = 8 + Math.round(h * 2);
		Paint.round(ctx, kx - (ks - 8) / 2, getY() + (getHeight() - ks) / 2, ks, ks, ks / 2, Colors.mix(Colors.INK, 0xFFFFFFFF, h));
		Paint.round(ctx, kx + 2, getY() + getHeight() / 2 - 2, 4, 4, 2, accent);
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		Paint.right(ctx, tr, format.apply(value), getX() + getWidth(), getY() + (getHeight() - 8) / 2 + 1, Colors.INK);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}
}
