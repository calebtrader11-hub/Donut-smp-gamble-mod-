package com.donutgamble.client;

import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.HudLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Arrange the HUD: drag components, scroll over one to resize, right-click to show/hide.
 * Components snap to screen edges, the center and a 4px grid (toggle with Snap). Saved on close.
 */
final class HudEditorScreen extends Screen {
	private final HudManager hud;
	private final Screen parent;
	private int buttonsX;

	HudEditorScreen(HudManager hud, Screen parent) {
		super(Text.literal("HUD editor"));
		this.hud = hud;
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = height - 24;
		String[] labels = {"Snap: " + (hud.snap ? "On" : "Off"), "Reset layout", "Done"};
		int total = 0;
		for (String l : labels) {
			total += Button.widthFor(l) + 6;
		}
		int x = width - 8 - total + 6;
		buttonsX = x;
		Button snap = Button.fit(x, y, labels[0], Button.Variant.SECONDARY, () -> {
			hud.snap = !hud.snap;
			clearAndInit();
		});
		x += snap.getWidth() + 6;
		Button reset = Button.fit(x, y, labels[1], Button.Variant.DANGER, hud::resetLayout);
		x += reset.getWidth() + 6;
		Button done = Button.fit(x, y, labels[2], Button.Variant.PRIMARY, this::close);
		addDrawableChild(snap);
		addDrawableChild(reset);
		addDrawableChild(done);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x40000000);
		int grid = Colors.alpha(0xFFFFFFFF, 0x0C);
		for (int x = 0; x < width; x += 16) {
			ctx.fill(x, 0, x + 1, height, grid);
		}
		for (int y = 0; y < height; y += 16) {
			ctx.fill(0, y, width, y + 1, grid);
		}
		ctx.fill(width / 2, 0, width / 2 + 1, height, Colors.alpha(Colors.accent(), 0x30));
		ctx.fill(0, height / 2, width, height / 2 + 1, Colors.alpha(Colors.accent(), 0x30));
		String hint = "Drag · Scroll to resize · Right-click to hide";
		if (8 + textRenderer.getWidth(hint) + 24 > buttonsX) {
			hint = "Drag · Scroll · Right-click";
		}
		int w = textRenderer.getWidth(hint) + 16;
		int y = height - 24;
		Paint.pill(ctx, 8, y, w, 16, Colors.alpha(Colors.WINDOW, 0xE0));
		Paint.text(ctx, textRenderer, hint, 16, y + 4, Colors.INK_DIM);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		hud.renderNow(ctx);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		HudElement e = hud.elementAt(click.x(), click.y());
		if (e == null) {
			return false;
		}
		if (click.button() == 1) {
			HudLayout.Slot s = hud.state.config.hud.slots.get(e.id);
			s.visible = !s.visible;
			hud.state.save();
			return true;
		}
		return hud.startDrag(click.x(), click.y());
	}

	@Override
	public boolean mouseDragged(Click click, double dx, double dy) {
		return hud.drag(click.x(), click.y()) || super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseReleased(Click click) {
		return hud.endDrag() || super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		HudElement e = hud.elementAt(mouseX, mouseY);
		if (e == null) {
			return false;
		}
		hud.state.config.hud.slots.get(e.id).addScale(vertical > 0 ? 0.1f : -0.1f);
		hud.state.save();
		return true;
	}

	@Override
	public void close() {
		hud.state.save();
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
