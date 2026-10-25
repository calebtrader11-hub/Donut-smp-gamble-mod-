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
		this.elements = List.of(queue,
			new QueueHud(state, QueueHud.Tab.PAPER), new QueueHud(state, QueueHud.Tab.BLACKJACK), new QueueHud(state, QueueHud.Tab.ELY),
			new ActivityHud(state), new AuctionHud(state), new LeaderboardHud(state), new ProfitHud(state), new ToastHud(state));
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
			// The editor shows visible components, plus a hidden one while it's selected.
			boolean show = editing ? s.visible || e.id.equals(HudEditorScreen.selectedId) : s.visible && e.hasContent();
			if (!show) {
				e.screenW = e.screenH = 0;
				continue;
			}
			e.compact = s.compact;
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
			Paint.hudOpacity = Math.max(0.2f, Math.min(1f, s.opacity));
			Colors.pushAccent(s.accent);
			try {
				e.draw(ctx, tr, regions, m[0], m[1], editing);
			} finally {
				Colors.popAccent();
				Paint.hudOpacity = 1f;
			}
			ctx.getMatrices().popMatrix();
			if (editing) {
				drawEditorFrame(ctx, tr, e, s);
			}
		}
		regions.transform(0, 0, 1);
		if (state.preview && !editing) {
			String banner = "STREAM PREVIEW · what viewers see";
			int bw = tr.getWidth(banner) + 14;
			int by = sh - 62;
			Paint.pill(ctx, (sw - bw) / 2, by, bw, 14, Colors.alpha(Colors.RED, 0xD0));
			Paint.hudText(ctx, tr, banner, (sw - bw) / 2 + 7, by + 3, 0xFFFFFFFF);
		}
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

	HudElement element(String id) {
		for (HudElement e : elements) {
			if (e.id.equals(id)) {
				return e;
			}
		}
		return null;
	}

	/** Puts one component back to its default place, size and style. */
	void resetSlot(HudElement e) {
		state.config.hud.slots.remove(e.id);
		slot(e, MinecraftClient.getInstance().textRenderer);
		state.save();
	}

	static final java.util.List<String> PRESETS = java.util.List.of("Minimal", "Standard", "Streamer", "Competitive");

	/**
	 * Arranges every component for a use case, from their current sizes and this screen:
	 * Minimal (queue and auction, compact), Standard (the defaults), Streamer (viewer-facing auction,
	 * leaderboard and game activity large; private session figures off), Competitive (a queue per
	 * game, activity and session).
	 */
	void applyPreset(String preset) {
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer tr = client.textRenderer;
		int sw = client.getWindow().getScaledWidth();
		int sh = client.getWindow().getScaledHeight();
		state.config.hud.slots.clear();
		java.util.Set<String> on = new java.util.HashSet<>();
		float big = 1f;
		boolean compact = false;
		switch (preset) {
			case "Minimal" -> {
				on.addAll(java.util.List.of("queue", "auction", "toasts"));
				compact = true;
			}
			case "Streamer" -> {
				on.addAll(java.util.List.of("queue", "auction", "leaderboard", "activity", "toasts"));
				big = sw >= 600 ? 1.25f : 1f;
			}
			case "Competitive" -> on.addAll(java.util.List.of("queue.paper", "queue.blackjack", "queue.ely", "activity", "auction",
				"leaderboard", "profit", "toasts"));
			default -> on.addAll(java.util.List.of("queue", "auction", "leaderboard", "toasts"));
		}
		java.util.Map<String, int[]> sizes = new java.util.HashMap<>();
		for (HudElement e : elements) {
			slot(e, tr);
			e.compact = compact;
			sizes.put(e.id, e.size(tr, true));
		}
		int leftX = 6, leftY = 6, leftColW = 0;
		int rightX = sw - 6, rightY = 6, rightColW = 0;
		int bottom = sh - 40;
		for (HudElement e : elements) {
			HudLayout.Slot s = state.config.hud.slots.get(e.id);
			s.visible = on.contains(e.id);
			s.compact = compact && !e.id.equals("toasts");
			int[] size = sizes.get(e.id);
			boolean right = e.id.equals("auction") || e.id.equals("leaderboard") || e.id.equals("profit");
			if (right) {
				s.scale = e.id.equals("profit") ? 1f : big;
			}
			if (!s.visible || e.id.equals("toasts")) {
				continue;
			}
			int w = Math.round(size[0] * s.scale);
			int h = Math.round(size[1] * s.scale);
			// Stack down each side; start a new column when the screen runs out.
			if (right) {
				if (rightY > 6 && rightY + h > bottom) {
					rightX -= rightColW + 6;
					rightY = 6;
					rightColW = 0;
				}
				s.x = rightX - w;
				s.y = rightY;
				rightY += h + 6;
				rightColW = Math.max(rightColW, w);
			} else {
				if (leftY > 6 && leftY + h > bottom) {
					leftX += leftColW + 6;
					leftY = 6;
					leftColW = 0;
				}
				s.x = leftX;
				s.y = leftY;
				leftY += h + 6;
				leftColW = Math.max(leftColW, w);
			}
		}
		state.save();
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
