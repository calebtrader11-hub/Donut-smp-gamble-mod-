package com.donutgamble.client.ui;

import com.donutgamble.client.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** A row of options with a selection pill that slides to the chosen one. */
public final class Segmented extends ClickableWidget {
	public static final int HEIGHT = 16;

	private final List<String> labels;
	private final IntSupplier selected;
	private final IntConsumer onSelect;
	private final Anim pillX = new Anim(-1);
	private final Anim pillW = new Anim(0);
	private int hoverIndex = -1;

	public Segmented(int x, int y, int w, List<String> labels, IntSupplier selected, IntConsumer onSelect) {
		super(x, y, w, HEIGHT, Text.literal(String.join(" / ", labels)));
		this.labels = labels;
		this.selected = selected;
		this.onSelect = onSelect;
	}

	/** Width that fits every label with padding. */
	public static int widthFor(List<String> labels) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		int w = 4;
		for (String l : labels) {
			w += tr.getWidth(l) + 14;
		}
		return w;
	}

	private int[] bounds(int i) {
		int inner = getWidth() - 4;
		int x = getX() + 2 + inner * i / labels.size();
		int x2 = getX() + 2 + inner * (i + 1) / labels.size();
		return new int[]{x, x2 - x};
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		for (int i = 0; i < labels.size(); i++) {
			int[] b = bounds(i);
			if (click.x() >= b[0] && click.x() < b[0] + b[1]) {
				onSelect.accept(i);
				return;
			}
		}
	}

	@Override
	protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		Paint.card(ctx, getX(), getY(), getWidth(), getHeight(), Paint.RADIUS, Colors.INPUT, Colors.LINE);
		int sel = Math.max(0, Math.min(labels.size() - 1, selected.getAsInt()));
		int[] b = bounds(sel);
		if (pillX.get() < 0) {
			pillX.snap(b[0]);
			pillW.snap(b[1]);
		}
		int px = Math.round(pillX.to(b[0], 18f));
		int pw = Math.round(pillW.to(b[1], 18f));
		hoverIndex = -1;
		for (int i = 0; i < labels.size(); i++) {
			int[] bi = bounds(i);
			if (isHovered() && mouseX >= bi[0] && mouseX < bi[0] + bi[1]) {
				hoverIndex = i;
				if (i != sel) {
					Paint.round(ctx, bi[0], getY() + 2, bi[1], getHeight() - 4, 2, Colors.CARD_HI);
				}
			}
		}
		Paint.round(ctx, px, getY() + 2, pw, getHeight() - 4, 2, Colors.alpha(Colors.accent(), 0xD8));
		for (int i = 0; i < labels.size(); i++) {
			int[] bi = bounds(i);
			int color = i == sel ? Colors.ON_ACCENT : i == hoverIndex ? Colors.INK : Colors.INK_DIM;
			Paint.center(ctx, tr, Paint.fit(tr, labels.get(i), bi[1] - 4), bi[0] + bi[1] / 2, getY() + (getHeight() - 8) / 2 + 1, color);
		}
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}
}
