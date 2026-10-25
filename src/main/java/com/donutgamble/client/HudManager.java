package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.HudLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

/**
 * Lays out, draws and routes input for every HUD component. Components can be clicked and dragged
 * while chat or the Insert screen is open, and arranged in the {@link HudEditorScreen}.
 */
final class HudManager {
	final GambleState state;
	final QueueHud queue;
	final List<HudElement> elements;
	private final Regions regions = new Regions();

	private HudElement dragging;
	private double grabX, grabY;
	boolean snap = true;

	HudManager(GambleState state) {
		this.state = state;
		this.queue = new QueueHud(state);
		this.elements = List.of(queue, new AuctionHud(state), new ProfitHud(state), new ToastHud(state));
		// Carry the old single-HUD settings over to the queue the first time.
		if (!state.config.hud.slots.containsKey(queue.id)) {
			HudLayout.Slot s = state.config.hud.slot(queue.id, state.config.hudX, state.config.hudY, state.config.hudVisible);
			s.scale = state.config.hudScale;
		}
	}

	HudLayout.Slot slot(HudElement e, TextRenderer tr) {
		MinecraftClient client = MinecraftClient.getInstance();
		int[] size = e.size(tr, false);
		int[] def = e.defaultPosition(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), size[0], size[1]);
		return state.config.hud.slot(e.id, def[0], def[1], e.visibleByDefault());
	}

	static boolean editing() {
		return MinecraftClient.getInstance().currentScreen instanceof HudEditorScreen;
	}

	/** Mouse in GUI coordinates while a screen frees it, else off-screen. */
	private static double[] mouse() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen == null) {
			return new double[]{-1, -1};
		}
		double sx = client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
		double sy = client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();
		return new double[]{client.mouse.getX() * sx, client.mouse.getY() * sy};
	}

	/** HUD layer entry point. While the editor is open it draws the HUD itself, on top of its backdrop. */
	void render(DrawContext ctx, RenderTickCounter tick) {
		if (!editing()) {
			renderNow(ctx);
		}
	}

	void renderNow(DrawContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		regions.clear();
		boolean editing = editing();
		if (client.options.hudHidden && !editing) {
			return;
		}
		TextRenderer tr = client.textRenderer;
		int sw = client.getWindow().getScaledWidth();
		int sh = client.getWindow().getScaledHeight();
		double[] m = mouse();
		for (HudElement e : elements) {
			HudLayout.Slot s = slot(e, tr);
			boolean show = editing || (s.visible && e.hasContent());
			if (!show) {
				e.screenW = e.screenH = 0;
				continue;
			}
			int[] size = e.size(tr, editing);
			int w = Math.round(size[0] * s.scale);
			int h = Math.round(size[1] * s.scale);
			int[] pos = HudLayout.clamp(s.x, s.y, w, h, sw, sh);
			e.screenX = pos[0];
			e.screenY = pos[1];
			e.screenW = w;
			e.screenH = h;

			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(pos[0], pos[1]);
			ctx.getMatrices().scale(s.scale, s.scale);
			regions.transform(pos[0], pos[1], s.scale);
			e.draw(ctx, tr, regions, m[0], m[1], editing);
			ctx.getMatrices().popMatrix();
			if (editing) {
				drawEditorFrame(ctx, tr, e, s);
			}
		}
		regions.transform(0, 0, 1);
	}

	private void drawEditorFrame(DrawContext ctx, TextRenderer tr, HudElement e, HudLayout.Slot s) {
		boolean active = e == dragging;
		int color = s.visible ? Colors.alpha(Colors.accent(), active ? 0xFF : 0xA0) : Colors.alpha(Colors.INK_FAINT, 0xC0);
		int x = e.screenX - 2, y = e.screenY - 2, w = e.screenW + 4, h = e.screenH + 4;
		for (int i = 0; i < w; i += 4) {
			ctx.fill(x + i, y, x + Math.min(i + 2, w), y + 1, color);
			ctx.fill(x + i, y + h - 1, x + Math.min(i + 2, w), y + h, color);
		}
		for (int i = 0; i < h; i += 4) {
			ctx.fill(x, y + i, x + 1, y + Math.min(i + 2, h), color);
			ctx.fill(x + w - 1, y + i, x + w, y + Math.min(i + 2, h), color);
		}
		String tag = e.title + (s.visible ? "" : " (hidden)") + "  " + Math.round(s.scale * 100) + "%";
		int tw = tr.getWidth(tag) + 8;
		int ty = y - 13 < 0 ? y + h + 1 : y - 13;
		Paint.pill(ctx, x, ty, tw, 12, Colors.alpha(s.visible ? Colors.accent() : Colors.LINE_HI, 0xE0));
		Paint.text(ctx, tr, tag, x + 4, ty + 2, s.visible ? Colors.ON_ACCENT : Colors.INK);
	}

	// ---- input (chat / Insert screen / editor) ----

	boolean click(double mx, double my, int button) {
		return regions.click(mx, my, button);
	}

	HudElement elementAt(double mx, double my) {
		for (int i = elements.size() - 1; i >= 0; i--) {
			HudElement e = elements.get(i);
			if (e.screenW > 0 && mx >= e.screenX && mx < e.screenX + e.screenW && my >= e.screenY && my < e.screenY + e.screenH) {
				return e;
			}
		}
		return null;
	}

	boolean startDrag(double mx, double my) {
		HudElement e = elementAt(mx, my);
		if (e == null) {
			return false;
		}
		dragging = e;
		grabX = mx - e.screenX;
		grabY = my - e.screenY;
		return true;
	}

	boolean drag(double mx, double my) {
		if (dragging == null) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int sw = client.getWindow().getScaledWidth();
		int sh = client.getWindow().getScaledHeight();
		int x = (int) Math.round(mx - grabX);
		int y = (int) Math.round(my - grabY);
		if (snap) {
			x = HudLayout.snap(x, dragging.screenW, sw);
			y = HudLayout.snap(y, dragging.screenH, sh);
		}
		int[] p = HudLayout.clamp(x, y, dragging.screenW, dragging.screenH, sw, sh);
		HudLayout.Slot s = state.config.hud.slots.get(dragging.id);
		s.x = p[0];
		s.y = p[1];
		dragging.screenX = p[0];
		dragging.screenY = p[1];
		return true;
	}

	boolean endDrag() {
		if (dragging == null) {
			return false;
		}
		dragging = null;
		state.save();
		return true;
	}

	/** Called from the chat screen: clicks first, then drags on empty parts. */
	void hookChat(ChatScreen screen) {
		net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((s, click) ->
			!(click(click.x(), click.y(), click.button()) || (click.button() == 0 && startDrag(click.x(), click.y()))));
		net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseDrag(screen).register((s, click, dx, dy) -> !drag(click.x(), click.y()));
		net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseRelease(screen).register((s, click) -> !endDrag());
	}

	void resetLayout() {
		state.config.hud.slots.clear();
		state.config.hudX = 6;
		state.config.hudY = 6;
		state.config.hudScale = 1f;
		state.config.hudVisible = true;
		state.save();
	}
}
