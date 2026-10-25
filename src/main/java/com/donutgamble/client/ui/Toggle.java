package com.donutgamble.client.ui;

import com.donutgamble.client.Colors;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** On/off switch with a sliding knob. */
public final class Toggle extends ClickableWidget {
	public static final int W = 20, H = 10;

	private final BooleanSupplier value;
	private final Runnable flip;
	private final Anim knob;

	public Toggle(int x, int y, String label, BooleanSupplier value, Runnable flip) {
		super(x, y, W, H, Text.literal(label));
		this.value = value;
		this.flip = flip;
		this.knob = new Anim(value.getAsBoolean() ? 1 : 0);
	}

	@Override
	protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		boolean on = value.getAsBoolean();
		float k = knob.to(on ? 1 : 0, 18f);
		int track = Colors.mix(Colors.LINE_HI, Colors.alpha(Colors.accent(), 0xC0), k);
		if (isHovered()) {
			track = Colors.mix(track, 0xFFFFFFFF, 0.08f);
		}
		Paint.pill(ctx, getX(), getY(), W, H, track);
		int kx = getX() + 1 + Math.round((W - H) * k);
		Paint.round(ctx, kx, getY() + 1, H - 2, H - 2, (H - 2) / 2, Colors.mix(Colors.INK_DIM, Colors.ON_ACCENT, k));
	}

	@Override
	public void onClick(Click click, boolean doubled) {
		flip.run();
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}
}
