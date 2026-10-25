package com.donutgamble.client.ui;

import com.donutgamble.client.Colors;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.gui.widget.TextFieldWidget;

/**
 * Text input in the design-system style. The vanilla field is placed inside the padding (it draws
 * text at its own top-left when it has no background); this draws the rounded box around it and
 * makes the whole box clickable.
 */
public final class TextBox extends TextFieldWidget {
	public static final int HEIGHT = 16;
	private static final int PAD_X = 5;

	private final Anim focus = new Anim(0);

	public TextBox(TextRenderer tr, int x, int y, int w, String placeholder) {
		super(tr, x + PAD_X, y + (HEIGHT - 8) / 2, w - 2 * PAD_X, 9, Text.literal(placeholder));
		setDrawsBackground(false);
		setMaxLength(48);
		setPlaceholder(Text.literal(placeholder).withColor(Colors.INK_FAINT));
		setEditableColor(Colors.INK);
	}

	@Override
	public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		float f = focus.to(isFocused() ? 1f : 0f, 18f);
		int border = Colors.mix(isHovered() ? Colors.LINE_HI : Colors.LINE, Colors.accent(), f);
		Paint.card(ctx, boxX(), boxY(), boxW(), HEIGHT, Paint.RADIUS, Colors.INPUT, border);
		super.renderWidget(ctx, mouseX, mouseY, delta);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return visible && mouseX >= boxX() && mouseX < boxX() + boxW() && mouseY >= boxY() && mouseY < boxY() + HEIGHT;
	}

	private int boxX() {
		return getX() - PAD_X;
	}

	private int boxY() {
		return getY() - (HEIGHT - 8) / 2;
	}

	private int boxW() {
		return getWidth() + 2 * PAD_X;
	}
}
