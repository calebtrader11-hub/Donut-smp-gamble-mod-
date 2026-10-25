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

/**
 * A closed box showing the current choice; clicking opens a list drawn above everything by the
 * owning {@link AppScreen}. The list unfolds smoothly and closes on outside click or Escape.
 */
public final class Dropdown extends ClickableWidget {
	public static final int HEIGHT = 16;
	private static final int ROW = 13;

	private final AppScreen owner;
	private final List<String> options;
	private final IntSupplier selected;
	private final IntConsumer onSelect;
	private final Anim hover = new Anim(0);
	private final Anim open = new Anim(0);

	public Dropdown(AppScreen owner, int x, int y, int w, List<String> options, IntSupplier selected, IntConsumer onSelect) {
		super(x, y, w, HEIGHT, Text.literal(""));
		this.owner = owner;
		this.options = options;
		this.selected = selected;
		this.onSelect = onSelect;
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		owner.openPopup(this);
		open.snap(0);
	}

	@Override
	protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		float h = hover.to(isHovered() || owner.popup() == this ? 1 : 0, 18f);
		int border = Colors.mix(Colors.LINE, Colors.alpha(Colors.accent(), 0xC0), h);
		Paint.card(ctx, getX(), getY(), getWidth(), getHeight(), Paint.RADIUS, Colors.INPUT, border);
		int sel = selected.getAsInt();
		String label = sel >= 0 && sel < options.size() ? options.get(sel) : "";
		Paint.text(ctx, tr, Paint.fit(tr, label, getWidth() - 20), getX() + 6, getY() + 4, Colors.INK);
		// chevron
		int cx = getX() + getWidth() - 10;
		int cy = getY() + 7;
		boolean up = owner.popup() == this;
		for (int i = 0; i < 3; i++) {
			int row = up ? 2 - i : i;
			ctx.fill(cx - 2 + i, cy + row, cx + 3 - i, cy + row + 1, Colors.INK_DIM);
		}
	}

	/** The open list, drawn by the owner on top of everything. */
	void drawPopup(DrawContext ctx, int mouseX, int mouseY) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		float t = Anim.ease(open.to(1f, 20f));
		int fullH = options.size() * ROW + 4;
		int h = Math.max(4, Math.round(fullH * t));
		int x = getX();
		int y = getY() + getHeight() + 2;
		Paint.shadow(ctx, x, y, getWidth(), h, 4);
		Paint.card(ctx, x, y, getWidth(), h, Paint.RADIUS, Colors.CARD_HI, Colors.LINE_HI);
		ctx.enableScissor(x, y, x + getWidth(), y + h);
		int sel = selected.getAsInt();
		for (int i = 0; i < options.size(); i++) {
			int ry = y + 2 + i * ROW;
			boolean hov = mouseX >= x && mouseX < x + getWidth() && mouseY >= ry && mouseY < ry + ROW;
			if (hov) {
				Paint.round(ctx, x + 2, ry, getWidth() - 4, ROW, 2, Colors.alpha(Colors.accent(), 0x30));
			}
			if (i == sel) {
				Paint.round(ctx, x + 4, ry + 4, 3, 5, 1, Colors.accent());
			}
			Paint.text(ctx, tr, Paint.fit(tr, options.get(i), getWidth() - 16), x + 10, ry + 3, i == sel || hov ? Colors.INK : Colors.INK_DIM);
		}
		ctx.disableScissor();
	}

	/** Handles a click while open; returns true if it landed on the list. */
	boolean clickPopup(double mx, double my) {
		int x = getX();
		int y = getY() + getHeight() + 2;
		if (mx < x || mx >= x + getWidth() || my < y || my >= y + options.size() * ROW + 4) {
			return false;
		}
		int i = (int) ((my - y - 2) / ROW);
		if (i >= 0 && i < options.size()) {
			onSelect.accept(i);
		}
		return true;
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}
}
