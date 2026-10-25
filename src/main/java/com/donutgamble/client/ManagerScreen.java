package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.AppScreen;
import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Dropdown;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.client.ui.Segmented;
import com.donutgamble.client.ui.Slider;
import com.donutgamble.client.ui.TextBox;
import com.donutgamble.client.ui.Toggle;
import com.donutgamble.core.PrivacySettings.Surface;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * The manager window (Home). This class is the shell: window, sidebar, header and the drawing
 * helpers every page shares. Each section is its own {@link ManagerPage}.
 */
final class ManagerScreen extends AppScreen {
	enum Section {
		DASHBOARD("Dashboard", "Overview", "Your command center", Icons.DASHBOARD),
		PLAYERS("Players", "Overview", "Everyone who has paid, bet or bid", Icons.PLAYERS),
		AUCTION("Auction", "Auctions", "Run the live auction", Icons.AUCTION),
		LEADERBOARD("Leaderboard", "Auctions", "Biggest contributors", Icons.TROPHY),
		HISTORY("History", "Auctions", "Every finished auction", Icons.HISTORY),
		PAYMENTS("Payments", "Money", "Every payment in and out", Icons.PAYMENTS),
		FINANCE("Finance", "Money", "Profit, trends and stats", Icons.FINANCE),
		STREAMER("Streamer", "System", "What viewers can see", Icons.EYE),
		SETTINGS("Settings", "System", "Rules, look and data", Icons.SETTINGS);

		final String label;
		final String group;
		final String subtitle;
		final Icons icon;

		Section(String label, String group, String subtitle, Icons icon) {
			this.label = label;
			this.group = group;
			this.subtitle = subtitle;
			this.icon = icon;
		}
	}

	@FunctionalInterface
	interface RowPainter {
		void paint(int index, int y);
	}

	static final int SIDEBAR = 104;
	static final int HEADER = 30;
	static final int PAD = Paint.S5;
	static final int ROW = 13;
	static final int GAP = 6;
	private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
	private static final SimpleDateFormat SHORT = new SimpleDateFormat("HH:mm", Locale.ROOT);
	private static final SimpleDateFormat DAY = new SimpleDateFormat("MMM d, HH:mm", Locale.ROOT);
	private static final SimpleDateFormat DATE = new SimpleDateFormat("MMM d", Locale.ROOT);

	/** Remembered while the game runs. */
	private static Section lastSection = Section.DASHBOARD;
	static final Map<String, String> DRAFTS = new HashMap<>();
	static final Map<String, Integer> SCROLL = new HashMap<>();

	final GambleState state;
	Section section;
	private ManagerPage page;
	private final Regions nav = new Regions();
	private final Regions regions = new Regions();
	private final Anim navY = new Anim(-1);
	private final Anim fade = new Anim(0);
	private final Map<String, Anim> hovers = new HashMap<>();
	/** Settings fields, applied on Enter, focus loss or leaving the page. */
	private final Map<TextBox, Consumer<String>> commits = new LinkedHashMap<>();
	private int headerRight;

	int wx, wy, ww, wh, cx, cy, cw, ch;
	int mouseX, mouseY;
	DrawContext ctx;

	private ManagerScreen(GambleState state, Section section) {
		super(Text.literal("Donut Gamble"), 480, 270);
		this.state = state;
		this.section = section;
	}

	static void open(GambleState state, Section section) {
		lastSection = section;
		MinecraftClient.getInstance().setScreen(new ManagerScreen(state, section));
	}

	static void openLast(GambleState state) {
		open(state, lastSection);
	}

	private ManagerPage create(Section s) {
		return switch (s) {
			case DASHBOARD -> new DashboardPage(this);
			case PLAYERS -> new PlayersPage(this);
			case AUCTION -> new AuctionPage(this);
			case LEADERBOARD -> new LeaderboardPage(this);
			case HISTORY -> new HistoryPage(this);
			case PAYMENTS -> new PaymentsPage(this);
			case FINANCE -> new FinancePage(this);
			case STREAMER -> new StreamerPage(this);
			case SETTINGS -> new SettingsPage(this);
		};
	}

	// ================= layout =================

	@Override
	protected void layout() {
		commits.clear();
		ww = Math.min(vw - 16, 600);
		wh = Math.min(vh - 12, 340);
		wx = (vw - ww) / 2;
		wy = (vh - wh) / 2;
		cx = wx + SIDEBAR + PAD;
		cy = wy + HEADER + PAD;
		cw = wx + ww - PAD - cx;
		ch = wy + wh - PAD - cy;
		lastSection = section;
		if (page == null || page.section() != section) {
			page = create(section);
		}

		Button close = new Button(0, wy + 7, 16, "", Button.Variant.GHOST, this::close).icon(Icons.CLOSE);
		close.setWidth(16);
		close.setX(wx + ww - 7 - 16);
		addDrawableChild(close);
		headerRight = close.getX() - 4;
		page.init();
	}

	@Override
	protected int[] contentArea() {
		return new int[]{cx - 2, cy - 2, cw + 4, ch + 4};
	}

	@Override
	protected void afterDialog() {
		rebuild();
	}

	void show(Section s) {
		if (s == section) {
			return;
		}
		commitAll();
		section = s;
		startTransition();
		clearAndInit();
	}

	/** Re-creates widgets (labels and states refresh); the page object and its state stay. */
	void rebuild() {
		commitAll();
		if (MinecraftClient.getInstance().currentScreen == this) {
			clearAndInit();
		}
	}

	// ================= widget builders =================

	/** A button that rebuilds the screen after its action, so labels and states refresh. */
	Button button(int x, int y, String label, Button.Variant variant, Runnable action) {
		Button b = Button.fit(x, y, label, variant, () -> {
			action.run();
			rebuild();
		});
		addDrawableChild(b);
		return b;
	}

	Button buttonRight(int rightX, int y, String label, Button.Variant variant, Runnable action) {
		Button b = button(0, y, label, variant, action);
		b.setX(rightX - b.getWidth());
		return b;
	}

	/** Header button, placed right to left before Close. */
	Button headerButton(String label, Button.Variant variant, Icons icon, Runnable action) {
		Button b = Button.fit(0, wy + 7, label, variant, () -> {
			action.run();
			rebuild();
		});
		if (icon != null) {
			b.icon(icon);
		}
		b.setX(headerRight - b.getWidth());
		headerRight = b.getX() - 4;
		addDrawableChild(b);
		return b;
	}

	/** A text box whose contents survive rebuilds, remembered under {@code id}. */
	TextBox box(String id, int x, int y, int w, String placeholder) {
		TextBox t = new TextBox(textRenderer, x, y, w, placeholder);
		t.setText(DRAFTS.getOrDefault(id, ""));
		t.setChangedListener(s -> DRAFTS.put(id, s));
		addDrawableChild(t);
		return t;
	}

	static String draft(String id) {
		return DRAFTS.getOrDefault(id, "").trim();
	}

	/** A field showing a live value; applied on Enter, focus loss or leaving. Invalid input is ignored. */
	TextBox setting(int x, int y, int w, String current, Consumer<String> apply) {
		TextBox t = new TextBox(textRenderer, x, y, w, "");
		t.setMaxLength(200);
		t.setText(current);
		commits.put(t, apply);
		addDrawableChild(t);
		return t;
	}

	Toggle toggle(int rightX, int y, String label, BooleanSupplier value, Runnable flip) {
		Toggle t = new Toggle(rightX - Toggle.W, y, label, value, () -> {
			flip.run();
			state.save();
		});
		addDrawableChild(t);
		return t;
	}

	Dropdown dropdown(int x, int y, int w, List<String> options, IntSupplier selected, IntConsumer onSelect) {
		Dropdown d = new Dropdown(this, x, y, w, options, selected, i -> {
			onSelect.accept(i);
			state.save();
		});
		addDrawableChild(d);
		return d;
	}

	Segmented segmented(int x, int y, int w, List<String> labels, IntSupplier selected, IntConsumer onSelect) {
		Segmented s = new Segmented(x, y, w, labels, selected, i -> {
			onSelect.accept(i);
			state.save();
		});
		addDrawableChild(s);
		return s;
	}

	Slider slider(int x, int y, int w, int min, int max, int step, int value, IntFunction<String> format, IntConsumer onChange) {
		Slider s = new Slider(x, y, w, min, max, step, value, format, v -> {
			onChange.accept(v);
			state.save();
		});
		addDrawableChild(s);
		return s;
	}

	void commitAll() {
		if (commits.isEmpty()) {
			return;
		}
		commits.forEach((t, apply) -> apply.accept(t.getText().trim()));
		state.save();
	}

	// ================= input =================

	@Override
	protected boolean onClick(Click click, boolean doubled) {
		TextBox before = getFocused() instanceof TextBox t && commits.containsKey(t) ? t : null;
		boolean handled = super.onClick(click, doubled);
		if (before != null && getFocused() != before) {
			commitAll();
		}
		if (handled) {
			return true;
		}
		if (nav.click(click.x(), click.y(), click.button())) {
			return true;
		}
		boolean inContent = click.x() >= cx - 2 && click.x() < cx + cw + 2 && click.y() >= cy - 2 && click.y() < cy + ch + 2;
		return inContent && regions.click(click.x(), click.y(), click.button());
	}

	@Override
	protected boolean onMouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (popup() != null || dialogOpen()) {
			return true;
		}
		if (page.scroll(mx, my, vertical)) {
			return true;
		}
		scroll(page.scrollKey(mx, my), vertical, 1);
		return true;
	}

	static void scroll(String key, double vertical, int step) {
		if (key != null) {
			SCROLL.merge(key, -(int) Math.signum(vertical) * step, (a, b) -> Math.max(0, a + b));
		}
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (!dialogOpen() && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			if (getFocused() instanceof TextBox t && commits.containsKey(t)) {
				commitAll();
				rebuild();
				return true;
			}
			if (getFocused() instanceof TextBox t && page.enter(t)) {
				rebuild();
				return true;
			}
		}
		if (!dialogOpen() && DonutGambleClient.OPEN_MANAGER.matchesKey(input) && !(getFocused() instanceof TextBox)) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void removed() {
		commitAll();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ================= frame =================

	@Override
	protected void drawBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.ctx = ctx;
		boolean blocked = dialogOpen() || popup() != null;
		this.mouseX = blocked ? -10_000 : mouseX;
		this.mouseY = blocked ? -10_000 : mouseY;
		nav.clear();
		regions.clear();
		float t = Anim.ease(fade.to(1f, 14f));
		ctx.fill(0, 0, vw, vh, Colors.fade(Colors.SCRIM, t));
		Paint.window(ctx, wx, wy, ww, wh);
		drawSidebar();

		int tx = cx;
		Paint.iconChip(ctx, section.icon, tx, wy + 8, Colors.accent());
		tx += 19;
		Paint.text(ctx, textRenderer, section.label, tx, wy + 9, Colors.INK);
		int subX = tx + textRenderer.getWidth(section.label) + 7;
		if (subX + 30 < headerRight) {
			Paint.text(ctx, textRenderer, Paint.fit(textRenderer, section.subtitle, headerRight - subX - 4), subX, wy + 9, Colors.INK_FAINT);
		}
		Paint.hline(ctx, wx + SIDEBAR + 1, wx + ww - 1, wy + HEADER, Colors.LINE);

		ctx.enableScissor(cx - 2, cy - 2, cx + cw + 2, cy + ch + 2);
		page.draw();
		ctx.disableScissor();
	}

	private void drawSidebar() {
		int x = wx + 1;
		int top = wy + 1;
		Paint.round(ctx, x, top, SIDEBAR, wh - 2, 5, Colors.alpha(Colors.SIDEBAR, 0xC8));
		ctx.fill(wx + SIDEBAR, top + 4, wx + SIDEBAR + 1, wy + wh - 4, Colors.LINE);

		// brand
		Paint.roundGradient(ctx, x + 8, wy + 8, 14, 14, 4, Colors.mix(Colors.accent(), 0xFFFFFFFF, 0.2f), Colors.accent());
		Icons.COIN.draw(ctx, x + 11, wy + 11, Colors.ON_ACCENT);
		Paint.text(ctx, textRenderer, "Donut Gamble", x + 27, wy + 12, Colors.INK);

		int y = wy + HEADER + 6;
		String group = "";
		int selectedY = 0;
		List<int[]> items = new ArrayList<>();
		for (Section s : Section.values()) {
			if (!s.group.equals(group)) {
				y += group.isEmpty() ? 0 : 3;
				group = s.group;
				Paint.label(ctx, textRenderer, group, x + 10, y);
				y += 11;
			}
			if (s == section) {
				selectedY = y;
			}
			items.add(new int[]{s.ordinal(), y});
			y += 15;
		}
		if (navY.get() < 0) {
			navY.snap(selectedY);
		}
		int sy = Math.round(navY.to(selectedY, 18f));
		int iw = SIDEBAR - 12;
		Paint.round(ctx, x + 5, sy - 4, iw, 15, 3, Colors.alpha(Colors.accent(), 0x26));
		Paint.round(ctx, x + 5, sy - 1, 2, 9, 1, Colors.accent());
		for (int[] item : items) {
			Section s = Section.values()[item[0]];
			int iy = item[1];
			boolean sel = s == section;
			float h = hover("nav." + s.name(), !sel && in(x + 5, iy - 4, iw, 15));
			if (h > 0.01f) {
				Paint.round(ctx, x + 5, iy - 4, iw, 15, 3, Colors.fade(Colors.CARD_HI, h));
			}
			int ink = sel ? Colors.INK : Colors.mix(Colors.INK_DIM, Colors.INK, h);
			s.icon.draw(ctx, x + 12, iy, sel ? Colors.accent() : Colors.mix(Colors.INK_FAINT, Colors.INK_DIM, h));
			Paint.text(ctx, textRenderer, s.label, x + 25, iy, ink);
			if (s == Section.AUCTION && state.auctions.isLive()) {
				Paint.right(ctx, textRenderer, Fmt.clock(state.auctions.current.remaining(System.currentTimeMillis())),
					x + SIDEBAR - 9, iy, Colors.GREEN);
			}
			nav.add(x + 5, iy - 4, iw, 15, () -> show(s));
		}

		// Streamer Mode switch, always one click away.
		int by = wy + wh - 24;
		if (by > y + 2) {
			boolean on = state.config.privacy.streamerMode;
			boolean prev = state.preview && !on;
			int color = on ? Colors.RED : prev ? Colors.GOLD : Colors.INK_FAINT;
			float h = hover("nav.streamer", in(x + 5, by, iw, 16));
			Paint.round(ctx, x + 5, by, iw, 16, 4, on ? Colors.alpha(Colors.RED, 0x30 + Math.round(0x20 * h))
				: Colors.mix(Colors.alpha(Colors.CARD, 0xC0), Colors.CARD_HI, h));
			if (on) {
				float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 300.0);
				Paint.round(ctx, x + 10, by + 6, 4, 4, 2, Colors.fade(Colors.RED, pulse));
			} else {
				Paint.round(ctx, x + 10, by + 6, 4, 4, 2, color);
			}
			String label = on ? "STREAMING" : prev ? "Previewing" : "Streamer off";
			Paint.text(ctx, textRenderer, label, x + 18, by + 4, on ? Colors.mix(Colors.RED, 0xFFFFFFFF, 0.45f) : Colors.mix(Colors.INK_DIM, Colors.INK, h));
			nav.add(x + 5, by, iw, 16, () -> {
				state.toggleStreamerMode();
				rebuild();
			});
		}
	}

	// ================= drawing helpers for pages =================

	net.minecraft.client.font.TextRenderer textRenderer() {
		return textRenderer;
	}

	boolean in(int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	/** Eased 0..1 hover amount for a named element. */
	float hover(String key, boolean over) {
		return hovers.computeIfAbsent(key, k -> new Anim(0)).to(over ? 1 : 0, 18f);
	}

	boolean hides(Surface s) {
		return state.hides(s);
	}

	/** Click target in the content area (ignored outside it, e.g. when scrolled out of view). */
	void region(int x, int y, int w, int h, Runnable action) {
		regions.add(x, y, w, h, () -> {
			action.run();
			rebuild();
		});
	}

	void card(int x, int y, int w, int h) {
		Paint.panel(ctx, x, y, w, h);
	}

	/** Card header: small icon and uppercase title. Returns the y where content starts. */
	int cardTitle(int x, int y, String title, Icons icon) {
		if (icon != null) {
			icon.draw(ctx, x + 8, y + 7, Colors.accent());
			Paint.label(ctx, textRenderer, title, x + 20, y + 7);
		} else {
			Paint.label(ctx, textRenderer, title, x + 8, y + 7);
		}
		return y + 21;
	}

	void text(String s, int x, int y, int color) {
		Paint.text(ctx, textRenderer, s, x, y, color);
	}

	void right(String s, int rightX, int y, int color) {
		Paint.right(ctx, textRenderer, s, rightX, y, color);
	}

	void center(String s, int centerX, int y, int color) {
		Paint.center(ctx, textRenderer, s, centerX, y, color);
	}

	String fit(String s, int w) {
		return Paint.fit(textRenderer, s, w);
	}

	int width(String s) {
		return textRenderer.getWidth(s);
	}

	/** Clickable inline text with a hover highlight; returns where the next one goes. */
	int link(String s, int x, int y, int color, Runnable action) {
		int w = width(s);
		float h = hover("link." + s + x + "," + y, in(x - 2, y - 2, w + 4, 12));
		if (h > 0.01f) {
			Paint.round(ctx, x - 2, y - 2, w + 4, 12, 2, Colors.fade(Colors.alpha(color, 0x26), h));
		}
		text(s, x, y, Colors.mix(color, 0xFFFFFFFF, 0.25f * h));
		region(x - 2, y - 2, w + 4, 12, action);
		return x + w + 8;
	}

	/** A drawn action tile: icon and label, hover glow. For buttons inside scrolling content. */
	void action(int x, int y, int w, int h, Icons icon, String label, boolean primary, Runnable run) {
		float hv = hover("action." + label, in(x, y, w, h));
		int accent = Colors.accent();
		if (primary) {
			Paint.roundGradient(ctx, x, y, w, h, Paint.RADIUS, Colors.mix(accent, 0xFFFFFFFF, 0.12f + 0.15f * hv), Colors.mix(accent, 0xFF000000, 0.08f));
		} else {
			Paint.card(ctx, x, y, w, h, Paint.RADIUS, Colors.mix(Colors.CARD_TOP, Colors.CARD_HI, hv),
				Colors.mix(Colors.LINE_HI, Colors.alpha(accent, 0xC0), hv));
		}
		int ink = primary ? Colors.ON_ACCENT : Colors.mix(Colors.INK_DIM, Colors.INK, hv);
		int ty = y + (h - 8) / 2 + 1;
		if (icon != null) {
			icon.draw(ctx, x + 6, ty, primary ? Colors.ON_ACCENT : accent);
			text(fit(label, w - 22), x + 18, ty, ink);
		} else {
			center(fit(label, w - 8), x + w / 2, ty, ink);
		}
		region(x, y, w, h, run);
	}

	/** A stat: small label, value underneath; replaced by "Hidden" while Streamer Mode hides it. */
	void stat(int x, int y, int w, String label, String value, int color, boolean hidden) {
		Paint.label(ctx, textRenderer, fit(label, w), x, y);
		if (hidden) {
			Icons.LOCK.draw(ctx, x, y + 11, Colors.INK_FAINT);
			text("Hidden", x + 11, y + 11, Colors.INK_FAINT);
		} else {
			text(fit(value, w), x, y + 11, color);
		}
	}

	/** A stat tile in its own card. */
	void statTile(int x, int y, int w, String label, String value, int color, boolean hidden) {
		card(x, y, w, 32);
		stat(x + 8, y + 7, w - 16, label, value, color, hidden);
	}

	/** Draws an item icon at (x, y), optionally scaled. */
	void icon(ItemStack stack, int x, int y, float scale) {
		if (stack.isEmpty()) {
			return;
		}
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x, y);
		ctx.getMatrices().scale(scale, scale);
		ctx.drawItem(stack, 0, 0);
		ctx.getMatrices().popMatrix();
	}

	/** Item icon in a rounded tile (size 20). */
	void itemTile(ItemStack stack, int x, int y) {
		Paint.round(ctx, x, y, 20, 20, 4, Colors.alpha(Colors.accent(), 0x22));
		icon(stack, x + 2, y + 2, 1f);
	}

	/** Scrollable rows with hover highlight and a scrollbar. Returns the first visible index. */
	int list(int x, int y, int w, int h, int count, String key, int rowH, RowPainter painter) {
		int rows = Math.max(1, (h - 4) / rowH);
		int max = Math.max(0, count - rows);
		int scroll = Math.min(SCROLL.getOrDefault(key, 0), max);
		SCROLL.put(key, scroll);
		int ry = y + 2;
		for (int i = scroll; i < Math.min(count, scroll + rows); i++) {
			if (in(x + 2, ry, w - 4, rowH)) {
				Paint.round(ctx, x + 2, ry, w - 4, rowH, 2, Colors.alpha(Colors.CARD_HI, 0xD0));
			}
			painter.paint(i, ry);
			ry += rowH;
		}
		if (max > 0) {
			int trackH = h - 8;
			int thumbH = Math.max(10, trackH * rows / count);
			int thumbY = y + 4 + (trackH - thumbH) * scroll / max;
			Paint.round(ctx, x + w - 4, y + 4, 2, trackH, 1, Colors.alpha(Colors.LINE, 0x80));
			Paint.round(ctx, x + w - 4, thumbY, 2, thumbH, 1, Colors.LINE_HI);
		}
		return scroll;
	}

	/** Empty state: icon, message and an optional hint, centered. */
	void empty(Icons icon, String message, String hint, int x, int y, int w, int h) {
		int my = y + h / 2 - (hint == null ? 10 : 14);
		if (icon != null) {
			Paint.iconChip(ctx, icon, x + w / 2 - 7, my - 4, Colors.INK_FAINT);
			my += 16;
		}
		center(fit(message, w - 12), x + w / 2, my, Colors.INK_DIM);
		if (hint != null) {
			center(fit(hint, w - 12), x + w / 2, my + 11, Colors.INK_FAINT);
		}
	}

	/** In place of private figures while Streamer Mode is on. Nothing private is drawn. */
	void locked(int x, int y, int w, int h, String what) {
		card(x, y, w, h);
		int mx = x + w / 2;
		int my = y + h / 2 - 16;
		Paint.round(ctx, mx - 10, my - 4, 20, 20, 5, Colors.alpha(Colors.RED, 0x22));
		Icons.LOCK.draw(ctx, mx - 4, my + 2, Colors.mix(Colors.RED, 0xFFFFFFFF, 0.3f));
		center(what + " hidden in Streamer Mode", mx, my + 22, Colors.INK_DIM);
		String l = "Streamer settings →";
		link(l, mx - width(l) / 2, my + 35, Colors.accent(), () -> show(Section.STREAMER));
	}

	/** Two-line wrapped paragraph; returns the y below it. */
	int paragraph(String s, int x, int y, int w, int maxY, int color) {
		Paint.record(s);
		for (var line : textRenderer.wrapLines(Text.literal(s), w)) {
			if (y > maxY - 9) {
				break;
			}
			ctx.drawText(textRenderer, line, x, y, color, false);
			y += 10;
		}
		return y;
	}

	static String time(long millis) {
		return format(CLOCK, millis);
	}

	static String shortTime(long millis) {
		return format(SHORT, millis);
	}

	static String day(long millis) {
		return format(DAY, millis);
	}

	static String date(long millis) {
		return format(DATE, millis);
	}

	private static String format(SimpleDateFormat f, long millis) {
		synchronized (f) {
			return f.format(new Date(millis));
		}
	}

	/** "just now", "5m ago", "2h ago", or a date. */
	static String ago(long millis) {
		long d = System.currentTimeMillis() - millis;
		if (d < 60_000) {
			return "just now";
		}
		if (d < 3_600_000) {
			return d / 60_000 + "m ago";
		}
		if (d < 86_400_000) {
			return d / 3_600_000 + "h ago";
		}
		return date(millis);
	}

	static String onOff(boolean b) {
		return b ? "On" : "Off";
	}

	// ================= durations =================

	/** "5m", "90s", "1h", "2:30" or plain seconds. Returns {@code fallback} if it can't be read. */
	static int parseDuration(String s, int fallback) {
		String t = s.trim().toLowerCase(Locale.ROOT);
		try {
			if (t.contains(":")) {
				String[] p = t.split(":");
				return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
			}
			if (t.endsWith("h")) {
				return (int) Math.round(Double.parseDouble(t.substring(0, t.length() - 1)) * 3600);
			}
			if (t.endsWith("m")) {
				return (int) Math.round(Double.parseDouble(t.substring(0, t.length() - 1)) * 60);
			}
			if (t.endsWith("s")) {
				return Integer.parseInt(t.substring(0, t.length() - 1));
			}
			return Integer.parseInt(t);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			return fallback;
		}
	}

	static String formatDuration(int sec) {
		return sec % 60 == 0 && sec > 0 ? (sec / 60) + "m" : sec + "s";
	}
}
