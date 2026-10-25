package com.donutgamble.client;

import com.donutgamble.client.ui.AppScreen;
import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Dropdown;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Slider;
import com.donutgamble.client.ui.TextBox;
import com.donutgamble.client.ui.Toggle;
import com.donutgamble.core.HudLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Arrange the HUD: drag components, click one to style it in the inspector (size, background,
 * accent, compact, visibility, reset), apply a preset or save and load named layouts. Components
 * snap to screen edges, the center and a 4px grid. Arrow keys nudge the selected one.
 */
final class HudEditorScreen extends AppScreen {
	private static final int PANEL_W = 172;
	private static final int ROW = 20;
	static String selectedId;

	private final HudManager hud;
	private final Screen parent;
	private int px, py, ph;
	private int toolbarX, toolbarY, toolbarW, toolbarH;
	private TextBox nameBox;
	private static String layoutName = "";

	HudEditorScreen(HudManager hud, Screen parent) {
		// No minimum size: the editor must line up 1:1 with the HUD it edits.
		super(Text.literal("HUD editor"), 1, 1);
		this.hud = hud;
		this.parent = parent;
	}

	private HudElement selected() {
		return selectedId == null ? null : hud.element(selectedId);
	}

	private HudLayout.Slot slot(HudElement e) {
		return hud.slot(e, textRenderer);
	}

	@Override
	protected void layout() {
		toolbar();
		HudElement e = selected();
		if (e != null) {
			inspector(e);
		}
	}

	@Override
	protected void afterDialog() {
		clearAndInit();
	}

	// ---- toolbar ----

	private List<String> layoutOptions() {
		List<String> out = new ArrayList<>();
		out.add("Layouts…");
		for (String p : HudManager.PRESETS) {
			out.add("Preset: " + p);
		}
		for (String name : hud.state.config.hud.saved.keySet()) {
			out.add("Saved: " + name);
		}
		return out;
	}

	/** Hidden components, to show again from the toolbar. */
	private List<HudElement> hiddenElements() {
		List<HudElement> out = new ArrayList<>();
		for (HudElement e : hud.elements) {
			if (!slot(e).visible) {
				out.add(e);
			}
		}
		return out;
	}

	private void toolbar() {
		int y = vh - 24;
		int ddW = 104;
		List<HudElement> hidden = hiddenElements();
		int addW = hidden.isEmpty() ? 0 : 96;
		int boxW = 72;
		String snapLabel = "Snap " + (hud.snap ? "on" : "off");
		int first = (addW > 0 ? addW + 4 : 0) + ddW + 4 + boxW + 4 + Button.widthFor("Save");
		int second = Button.widthFor(snapLabel) + 4 + Button.widthFor("Reset all") + 4 + Button.widthFor("Done");
		// One row when it fits, else layouts on top and the actions underneath.
		boolean twoRows = first + 10 + second + 16 > vw;
		int total = twoRows ? Math.max(first, second) : first + 10 + second;
		int y1 = twoRows ? y - 20 : y;
		int x = Math.max(4, (vw - total) / 2);
		toolbarX = x - 6;
		toolbarY = y1 - 5;
		toolbarW = total + 12;
		toolbarH = twoRows ? 46 : 26;
		int rowStart = x;
		y = y1;
		if (addW > 0) {
			List<String> names = new ArrayList<>();
			names.add("Show…");
			for (HudElement e : hidden) {
				names.add(e.title);
			}
			addDrawableChild(new Dropdown(this, x, y, addW, names, () -> 0, i -> {
				if (i > 0) {
					HudElement e = hidden.get(i - 1);
					slot(e).visible = true;
					selectedId = e.id;
					hud.state.save();
				}
			}));
			x += addW + 4;
		}
		List<String> options = layoutOptions();
		addDrawableChild(new Dropdown(this, x, y, ddW, options, () -> 0, i -> {
			String o = options.get(i);
			if (o.startsWith("Preset: ")) {
				String preset = o.substring(8);
				confirm("Apply the " + preset + " layout?", "Moves and styles every component for this screen size. "
					+ "Save your current layout first if you want to keep it.", "Apply", false, () -> hud.applyPreset(preset));
			} else if (o.startsWith("Saved: ")) {
				String name = o.substring(7);
				if (hud.state.config.hud.load(name)) {
					hud.state.save();
					hud.state.notify(com.donutgamble.core.NotificationSettings.Kind.SYSTEM, "Loaded layout " + name, null, Colors.accent(), null);
				}
			}
		}));
		x += ddW + 4;
		nameBox = new TextBox(textRenderer, x, y, boxW, "Layout name");
		nameBox.setMaxLength(24);
		nameBox.setText(layoutName);
		nameBox.setChangedListener(s -> layoutName = s);
		addDrawableChild(nameBox);
		x += boxW + 4;
		Button save = Button.fit(x, y, "Save", Button.Variant.SECONDARY, this::saveLayout);
		addDrawableChild(save);
		x = save.getRight() + 10;
		if (twoRows) {
			x = rowStart + (total - second) / 2;
			y = vh - 24;
		}
		Button snap = Button.fit(x, y, snapLabel, Button.Variant.SECONDARY, () -> {
			hud.snap = !hud.snap;
			clearAndInit();
		}).selected(hud.snap);
		addDrawableChild(snap);
		x = snap.getRight() + 4;
		Button reset = Button.fit(x, y, "Reset all", Button.Variant.DANGER, () -> confirm("Reset the whole HUD?",
			"Every component goes back to its default place, size and style. Saved layouts are kept.", "Reset", true, hud::resetLayout));
		addDrawableChild(reset);
		x = reset.getRight() + 4;
		addDrawableChild(Button.fit(x, y, "Done", Button.Variant.PRIMARY, this::close));
	}

	private void saveLayout() {
		String name = layoutName.trim();
		if (name.isEmpty()) {
			name = "Layout " + (hud.state.config.hud.saved.size() + 1);
		}
		hud.state.config.hud.saveAs(name);
		hud.state.save();
		hud.state.notify(com.donutgamble.core.NotificationSettings.Kind.SYSTEM, "Saved layout " + name, null, Colors.GREEN, null);
		layoutName = "";
		clearAndInit();
	}

	// ---- inspector ----

	private int panelHeight(HudElement e) {
		return 24 + 6 * ROW + (e instanceof ToastHud || e instanceof AuctionHud ? ROW : 0) + 6;
	}

	private void inspector(HudElement e) {
		HudLayout.Slot s = slot(e);
		ph = panelHeight(e);
		boolean leftSide = e.screenX + e.screenW / 2 > vw / 2;
		px = leftSide ? 8 : vw - PANEL_W - 8;
		py = Math.max(8, Math.min(e.screenY, toolbarY - 20 - ph));
		int cx = px + 8, cw = PANEL_W - 16;
		int y = py + 24;
		int right = px + PANEL_W - 8;

		addDrawableChild(new Toggle(right - Toggle.W, y + 3, "Visible", () -> s.visible, () -> {
			s.visible = !s.visible;
			hud.state.save();
		}));
		y += ROW;
		int sw = cw - 66;
		addDrawableChild(new Slider(right - sw, y, sw, 50, 200, 5, Math.round(s.scale * 100), v -> v + "%", v -> {
			s.scale = v / 100f;
			hud.state.save();
		}));
		y += ROW;
		addDrawableChild(new Slider(right - sw, y, sw, 20, 100, 5, Math.round(s.opacity * 100), v -> v + "%", v -> {
			s.opacity = v / 100f;
			hud.state.save();
		}));
		y += ROW;
		List<String> accents = new ArrayList<>();
		accents.add("Global");
		for (Colors.Theme t : Colors.Theme.values()) {
			accents.add(t.displayName);
		}
		addDrawableChild(new Dropdown(this, right - sw, y - 1, sw, accents, () -> {
			if (s.accent == null || s.accent.isBlank()) {
				return 0;
			}
			return Colors.Theme.parse(s.accent).ordinal() + 1;
		}, i -> {
			s.accent = i == 0 ? "" : Colors.Theme.values()[i - 1].name();
			hud.state.save();
		}));
		y += ROW;
		addDrawableChild(new Toggle(right - Toggle.W, y + 3, "Compact", () -> s.compact, () -> {
			s.compact = !s.compact;
			hud.state.save();
		}));
		y += ROW;
		Button reset = Button.fit(cx, y - 1, "Reset", Button.Variant.SECONDARY, () -> {
			hud.resetSlot(e);
			clearAndInit();
		});
		addDrawableChild(reset);
		if (e instanceof ToastHud) {
			addDrawableChild(Button.fit(reset.getRight() + 4, y - 1, "Preview", Button.Variant.PRIMARY, hud.state::previewNotifications)
				.icon(Icons.PLAY));
		}
		Button close = new Button(right - 14, py + 5, 14, "", Button.Variant.GHOST, () -> {
			selectedId = null;
			clearAndInit();
		}).icon(Icons.CLOSE);
		close.setWidth(14);
		close.setX(right - 14);
		addDrawableChild(close);
	}

	// ---- drawing ----

	@Override
	protected void drawBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, vw, vh, 0x40000000);
		int grid = Colors.alpha(0xFFFFFFFF, 0x0C);
		for (int x = 0; x < vw; x += 16) {
			ctx.fill(x, 0, x + 1, vh, grid);
		}
		for (int y = 0; y < vh; y += 16) {
			ctx.fill(0, y, vw, y + 1, grid);
		}
		ctx.fill(vw / 2, 0, vw / 2 + 1, vh, Colors.alpha(Colors.accent(), 0x30));
		ctx.fill(0, vh / 2, vw, vh / 2 + 1, Colors.alpha(Colors.accent(), 0x30));
		hud.renderNow(ctx);

		HudElement e = selected();
		if (e != null && e.screenW > 0) {
			Paint.round(ctx, e.screenX - 3, e.screenY - 3, e.screenW + 6, 2, 1, Colors.accent());
		}
		// toolbar backdrop and hint
		Paint.shadow(ctx, toolbarX, toolbarY, toolbarW, toolbarH, 6);
		Paint.card(ctx, toolbarX, toolbarY, toolbarW, toolbarH, 6, Colors.alpha(Colors.WINDOW, 0xF0), Colors.LINE_HI);
		String hint = e == null ? "Click a component to style it · drag to move · scroll to resize · right-click hides"
			: "Arrow keys nudge · Shift = 4px";
		hint = Paint.fit(textRenderer, hint, vw - 16);
		int hw = textRenderer.getWidth(hint) + 14;
		Paint.pill(ctx, (vw - hw) / 2, toolbarY - 16, hw, 13, Colors.alpha(Colors.WINDOW, 0xD8));
		Paint.text(ctx, textRenderer, hint, (vw - hw) / 2 + 7, toolbarY - 13, Colors.INK_DIM);

		if (e != null) {
			drawInspector(ctx, e);
		}
	}

	private void drawInspector(DrawContext ctx, HudElement e) {
		Paint.window(ctx, px, py, PANEL_W, ph);
		Icons.LAYOUT.draw(ctx, px + 8, py + 8, Colors.accent());
		Paint.text(ctx, textRenderer, Paint.fit(textRenderer, e.title, PANEL_W - 40), px + 20, py + 8, Colors.INK);
		String[] labels = {"Visible", "Size", "Background", "Accent", "Compact", ""};
		int y = py + 24;
		for (String l : labels) {
			if (!l.isEmpty()) {
				Paint.text(ctx, textRenderer, l, px + 8, y + 4, Colors.INK_DIM);
			}
			y += ROW;
		}
	}

	// ---- input ----

	@Override
	protected boolean onClick(Click click, boolean doubled) {
		if (super.onClick(click, doubled)) {
			return true;
		}
		HudElement e = hud.elementAt(click.x(), click.y());
		if (e == null) {
			if (selectedId != null && !inPanel(click.x(), click.y())) {
				selectedId = null;
				clearAndInit();
			}
			return inPanel(click.x(), click.y());
		}
		if (click.button() == 1) {
			HudLayout.Slot s = slot(e);
			s.visible = !s.visible;
			hud.state.save();
			return true;
		}
		if (!e.id.equals(selectedId)) {
			selectedId = e.id;
			clearAndInit();
		}
		return hud.startDrag(click.x(), click.y());
	}

	private boolean inPanel(double x, double y) {
		return selected() != null && x >= px && x < px + PANEL_W && y >= py && y < py + ph;
	}

	@Override
	protected boolean onMouseDragged(Click click, double dx, double dy) {
		return hud.drag(click.x(), click.y()) || super.onMouseDragged(click, dx, dy);
	}

	@Override
	protected boolean onMouseReleased(Click click) {
		if (hud.endDrag()) {
			clearAndInit(); // the inspector follows the component
			return true;
		}
		return super.onMouseReleased(click);
	}

	@Override
	protected boolean onMouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		HudElement e = hud.elementAt(mouseX, mouseY);
		if (e == null || inPanel(mouseX, mouseY)) {
			return super.onMouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
		slot(e).addScale(vertical > 0 ? 0.05f : -0.05f);
		hud.state.save();
		if (e.id.equals(selectedId)) {
			clearAndInit();
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		HudElement e = selected();
		if (e != null && !dialogOpen() && !(getFocused() instanceof TextBox)) {
			int step = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 4 : 1;
			int dx = 0, dy = 0;
			switch (input.key()) {
				case GLFW.GLFW_KEY_LEFT -> dx = -step;
				case GLFW.GLFW_KEY_RIGHT -> dx = step;
				case GLFW.GLFW_KEY_UP -> dy = -step;
				case GLFW.GLFW_KEY_DOWN -> dy = step;
				default -> {
				}
			}
			if (dx != 0 || dy != 0) {
				HudLayout.Slot s = slot(e);
				int[] p = HudLayout.clamp(s.x + dx, s.y + dy, e.screenW, e.screenH, vw, vh);
				s.x = p[0];
				s.y = p[1];
				hud.state.save();
				return true;
			}
		}
		if (input.key() == GLFW.GLFW_KEY_ENTER && getFocused() == nameBox) {
			saveLayout();
			return true;
		}
		return super.keyPressed(input);
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
