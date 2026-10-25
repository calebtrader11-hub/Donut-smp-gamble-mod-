package com.donutgamble.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;

/**
 * A screen laid out for at least {@code minW × minH} GUI pixels. When the player's GUI scale
 * leaves less room than that, it draws at the largest smaller whole scale that fits (e.g. GUI
 * scale 4 → 3), so text stays pixel-crisp instead of overlapping. Subclasses lay out in the
 * virtual size {@link #vw} × {@link #vh} and receive mouse input already converted to it.
 */
public abstract class ScaledScreen extends Screen {
	/** Tests can force a factor; 0 means automatic. */
	public static float forcedFactor;

	private final int minW, minH;
	protected float factor = 1f;
	protected int vw, vh;

	protected ScaledScreen(Text title, int minW, int minH) {
		super(title);
		this.minW = minW;
		this.minH = minH;
	}

	@Override
	protected final void init() {
		Window window = MinecraftClient.getInstance().getWindow();
		int gui = Math.max(1, window.getScaleFactor());
		int scale = gui;
		while (scale > 1 && (window.getFramebufferWidth() / scale < minW || window.getFramebufferHeight() / scale < minH)) {
			scale--;
		}
		factor = forcedFactor > 0 ? forcedFactor : scale / (float) gui;
		vw = Math.round(width / factor);
		vh = Math.round(height / factor);
		layout();
	}

	/** Build widgets for a {@link #vw} × {@link #vh} screen. */
	protected abstract void layout();

	/** The game draws the background in its own pass before {@link #render}, so it's scaled here too. */
	@Override
	public final void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(factor, factor);
		drawBackground(ctx, Math.round(mouseX / factor), Math.round(mouseY / factor), delta);
		ctx.getMatrices().popMatrix();
	}

	/** Background and non-widget content, in virtual coordinates. */
	protected abstract void drawBackground(DrawContext ctx, int mouseX, int mouseY, float delta);

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(factor, factor);
		int mx = Math.round(mouseX / factor);
		int my = Math.round(mouseY / factor);
		super.render(ctx, mx, my, delta);
		drawOverlay(ctx, mx, my, delta);
		ctx.getMatrices().popMatrix();
	}

	/** Drawn after the widgets (popups, dialogs), in virtual coordinates. */
	protected void drawOverlay(DrawContext ctx, int mouseX, int mouseY, float delta) {
	}

	private Click scaled(Click c) {
		return new Click(c.x() / factor, c.y() / factor, c.buttonInfo());
	}

	@Override
	public final boolean mouseClicked(Click click, boolean doubled) {
		return onMouseClicked(scaled(click), doubled);
	}

	/** Mouse down, in virtual coordinates. */
	protected boolean onMouseClicked(Click click, boolean doubled) {
		return super.mouseClicked(click, doubled);
	}

	@Override
	public final boolean mouseReleased(Click click) {
		return super.mouseReleased(scaled(click));
	}

	@Override
	public final boolean mouseDragged(Click click, double dx, double dy) {
		return super.mouseDragged(scaled(click), dx / factor, dy / factor);
	}

	@Override
	public final boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return onMouseScrolled(mouseX / factor, mouseY / factor, horizontal, vertical);
	}

	protected boolean onMouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}
}
