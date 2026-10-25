package com.donutgamble.client.ui;

import com.donutgamble.client.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Base for the mod's full screens: crisp scaling ({@link ScaledScreen}), dropdown lists drawn above
 * everything, confirmation dialogs for risky actions, and a short fade when the content changes.
 */
public abstract class AppScreen extends ScaledScreen {
	private record Confirm(String title, String message, String action, boolean danger, Runnable onYes) {
	}

	private Dropdown popup;
	private Confirm confirm;
	private final Anim dialog = new Anim(0);
	private final Anim content = new Anim(1);
	private final Anim yesHover = new Anim(0);
	private final Anim noHover = new Anim(0);
	private int dx, dy, dw, dh;

	protected AppScreen(Text title, int minW, int minH) {
		super(title, minW, minH);
	}

	// ---- dropdowns ----

	void openPopup(Dropdown d) {
		popup = popup == d ? null : d;
	}

	Dropdown popup() {
		return popup;
	}

	// ---- confirmation ----

	/** Asks before doing something that can't be undone. Enter confirms, Escape cancels. */
	public void confirm(String title, String message, String action, boolean danger, Runnable onYes) {
		confirm = new Confirm(title, message, action, danger, onYes);
		dialog.snap(0);
		popup = null;
	}

	protected boolean dialogOpen() {
		return confirm != null;
	}

	// ---- content transitions ----

	/** Fades the content area in; call when switching pages. */
	protected void startTransition() {
		content.snap(0);
	}

	/** The area that fades on {@link #startTransition}; null for none. */
	protected int[] contentArea() {
		return null;
	}

	/** Background color the fade starts from. */
	protected int contentBackground() {
		return Colors.WINDOW;
	}

	// ---- rendering ----

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// Widgets under a dialog don't react to the mouse.
		super.render(ctx, confirm != null ? -10_000 : mouseX, confirm != null ? -10_000 : mouseY, delta);
	}

	@Override
	protected void drawOverlay(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int[] area = contentArea();
		float c = content.to(1f, 12f);
		if (area != null && c < 0.999f) {
			ctx.fill(area[0], area[1], area[0] + area[2], area[1] + area[3], Colors.fade(contentBackground(), 1f - Anim.ease(c)));
		}
		if (popup != null) {
			popup.drawPopup(ctx, mouseX, mouseY);
		}
		if (confirm != null) {
			drawDialog(ctx, mouseX, mouseY);
		}
	}

	private void drawDialog(DrawContext ctx, int mouseX, int mouseY) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		float t = Anim.ease(dialog.to(1f, 16f));
		ctx.fill(0, 0, vw, vh, Colors.fade(Colors.SCRIM, t));
		List<net.minecraft.text.OrderedText> lines = tr.wrapLines(Text.literal(confirm.message()), 196);
		dw = 220;
		dh = 52 + lines.size() * 10;
		dx = (vw - dw) / 2;
		dy = (vh - dh) / 2 + Math.round((1f - t) * 8);
		Paint.window(ctx, dx, dy, dw, dh);
		int color = confirm.danger() ? Colors.RED : Colors.accent();
		Paint.iconChip(ctx, confirm.danger() ? Icons.WARN : Icons.CHECK, dx + 10, dy + 9, color);
		Paint.text(ctx, tr, confirm.title(), dx + 30, dy + 12, Colors.INK);
		int ly = dy + 30;
		Paint.record(confirm.message());
		for (var line : lines) {
			ctx.drawText(tr, line, dx + 12, ly, Colors.INK_DIM, false);
			ly += 10;
		}
		int by = dy + dh - 22;
		int yesW = tr.getWidth(confirm.action()) + 20;
		int yesX = dx + dw - 10 - yesW;
		int noW = tr.getWidth("Cancel") + 20;
		int noX = yesX - 4 - noW;
		boolean overYes = in(mouseX, mouseY, yesX, by, yesW, 16);
		boolean overNo = in(mouseX, mouseY, noX, by, noW, 16);
		float yh = yesHover.to(overYes ? 1 : 0, 18f);
		float nh = noHover.to(overNo ? 1 : 0, 18f);
		Paint.round(ctx, noX, by, noW, 16, Paint.RADIUS, Colors.alpha(Colors.CARD_HI, Math.round(255 * nh)));
		Paint.center(ctx, tr, "Cancel", noX + noW / 2, by + 5, Colors.mix(Colors.INK_DIM, Colors.INK, nh));
		int fill = Colors.mix(color, 0xFFFFFFFF, 0.15f * yh);
		Paint.round(ctx, yesX, by, yesW, 16, Paint.RADIUS, fill);
		Paint.center(ctx, tr, confirm.action(), yesX + yesW / 2, by + 5, Colors.ON_ACCENT);
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---- input ----

	@Override
	protected boolean onMouseClicked(Click click, boolean doubled) {
		if (confirm != null) {
			TextRenderer tr = MinecraftClient.getInstance().textRenderer;
			int by = dy + dh - 22;
			int yesW = tr.getWidth(confirm.action()) + 20;
			int yesX = dx + dw - 10 - yesW;
			int noW = tr.getWidth("Cancel") + 20;
			int noX = yesX - 4 - noW;
			if (in(click.x(), click.y(), yesX, by, yesW, 16)) {
				Runnable run = confirm.onYes();
				confirm = null;
				run.run();
				afterDialog();
			} else if (in(click.x(), click.y(), noX, by, noW, 16) || !in(click.x(), click.y(), dx, dy, dw, dh)) {
				confirm = null;
			}
			return true;
		}
		if (popup != null) {
			Dropdown d = popup;
			popup = null;
			if (d.clickPopup(click.x(), click.y())) {
				afterDialog();
			}
			return true;
		}
		return onClick(click, doubled);
	}

	/** Mouse down when no dialog or list is open, in virtual coordinates. */
	protected boolean onClick(Click click, boolean doubled) {
		return super.onMouseClicked(click, doubled);
	}

	/** Called after a dialog action or dropdown choice ran (e.g. to rebuild widgets). */
	protected void afterDialog() {
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (confirm != null) {
			if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
				Runnable run = confirm.onYes();
				confirm = null;
				run.run();
				afterDialog();
			} else if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
				confirm = null;
			}
			return true;
		}
		if (popup != null && input.key() == GLFW.GLFW_KEY_ESCAPE) {
			popup = null;
			return true;
		}
		return super.keyPressed(input);
	}
}
