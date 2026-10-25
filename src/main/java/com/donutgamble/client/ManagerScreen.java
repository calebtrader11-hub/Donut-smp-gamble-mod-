package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.client.ui.ScaledScreen;
import com.donutgamble.client.ui.TextBox;
import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Auction;
import com.donutgamble.core.FinanceReport;
import com.donutgamble.core.Game;
import com.donutgamble.core.PlayerStats;
import com.donutgamble.core.PlayerTotal;
import com.donutgamble.core.Standing;
import com.donutgamble.core.Transaction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The manager window (Home): dashboard, players, auctions, payments, finance and settings.
 * Built from the shared design system in {@code client.ui}.
 */
final class ManagerScreen extends ScaledScreen {
	enum Section {
		DASHBOARD("Dashboard", "Overview", "Today at a glance"),
		PLAYERS("Players", "Overview", "Everyone who has paid, bet or bid"),
		AUCTION("Auction", "Auctions", "Run the live auction"),
		LEADERBOARD("Leaderboard", "Auctions", "Biggest contributors"),
		HISTORY("History", "Auctions", "Every finished auction"),
		PAYMENTS("Payments", "Money", "Every payment in and out"),
		FINANCE("Finance", "Money", "Profit and loss"),
		SETTINGS("Settings", "System", "Rules, look and data");

		final String label;
		final String group;
		final String subtitle;

		Section(String label, String group, String subtitle) {
			this.label = label;
			this.group = group;
			this.subtitle = subtitle;
		}
	}

	@FunctionalInterface
	private interface RowPainter {
		void paint(int index, int textY);
	}

	private static final int SIDEBAR = 92;
	private static final int HEADER = 28;
	private static final int PAD = Paint.S6;
	private static final int ROW = 13;
	private static final int TOGGLE_W = 20;
	private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
	private static final SimpleDateFormat DAY = new SimpleDateFormat("MMM d, HH:mm", Locale.ROOT);

	/** Remembered while the game runs. */
	private static Section lastSection = Section.DASHBOARD;
	private static final Map<String, String> DRAFTS = new HashMap<>();
	private static final Map<Section, Integer> SCROLL = new HashMap<>();
	private static long selectedAuction;
	private static String selectedPlayer;

	private final GambleState state;
	private Section section;
	private final Regions regions = new Regions();
	private final Anim navY = new Anim(-1);
	private final Anim fade = new Anim(0);
	/** Settings fields, applied on Enter, focus loss or leaving the section. */
	private final Map<TextBox, Consumer<String>> commits = new LinkedHashMap<>();

	private int wx, wy, ww, wh, cx, cy, cw, ch;
	private int mouseX, mouseY;
	private DrawContext ctx;

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

	// ================= layout & widgets =================

	@Override
	protected void layout() {
		commits.clear();
		ww = Math.min(vw - 16, 500);
		wh = Math.min(vh - 12, 296);
		wx = (vw - ww) / 2;
		wy = (vh - wh) / 2;
		cx = wx + SIDEBAR + PAD;
		cy = wy + HEADER + PAD;
		cw = wx + ww - PAD - cx;
		ch = wy + wh - PAD - cy;
		lastSection = section;

		Button close = Button.fit(0, wy + 6, "Close", Button.Variant.GHOST, this::close);
		close.setX(wx + ww - 6 - close.getWidth());
		addDrawableChild(close);

		switch (section) {
			case PLAYERS -> initPlayers();
			case AUCTION -> initAuction();
			case HISTORY -> initHistory(close.getX() - 4);
			case PAYMENTS -> initPayments();
			case FINANCE -> initFinance(close.getX() - 4);
			case SETTINGS -> initSettings();
			default -> {
			}
		}
	}

	private void show(Section s) {
		commitAll();
		section = s;
		clearAndInit();
	}

	/** A button that rebuilds the screen after its action, so labels and states refresh. */
	private Button button(int x, int y, String label, Button.Variant variant, Runnable action) {
		Button b = Button.fit(x, y, label, variant, () -> {
			action.run();
			if (MinecraftClient.getInstance().currentScreen == this) {
				clearAndInit();
			}
		});
		addDrawableChild(b);
		return b;
	}

	private Button buttonRight(int rightX, int y, String label, Button.Variant variant, Runnable action) {
		Button b = button(0, y, label, variant, action);
		b.setX(rightX - b.getWidth());
		return b;
	}

	/** A text box whose contents survive rebuilds, remembered under {@code id}. */
	private TextBox box(String id, int x, int y, int w, String placeholder) {
		TextBox t = new TextBox(textRenderer, x, y, w, placeholder);
		t.setText(DRAFTS.getOrDefault(id, ""));
		t.setChangedListener(s -> DRAFTS.put(id, s));
		addDrawableChild(t);
		return t;
	}

	private static String draft(String id) {
		return DRAFTS.getOrDefault(id, "").trim();
	}

	/** A settings field showing the live value; applied on commit. Invalid input is ignored. */
	private void setting(int x, int y, int w, String current, Consumer<String> apply) {
		TextBox t = new TextBox(textRenderer, x, y, w, "");
		t.setText(current);
		commits.put(t, apply);
		addDrawableChild(t);
	}

	private void commitAll() {
		if (commits.isEmpty()) {
			return;
		}
		commits.forEach((t, apply) -> apply.accept(t.getText().trim()));
		state.save();
	}

	// ================= input =================

	@Override
	protected boolean onMouseClicked(Click click, boolean doubled) {
		TextBox before = getFocused() instanceof TextBox t && commits.containsKey(t) ? t : null;
		boolean handled = super.onMouseClicked(click, doubled);
		if (before != null && getFocused() != before) {
			commitAll();
		}
		return handled || regions.click(click.x(), click.y(), click.button());
	}

	@Override
	protected boolean onMouseScrolled(double mx, double my, double horizontal, double vertical) {
		SCROLL.merge(section, -(int) Math.signum(vertical) * 2, (a, b) -> Math.max(0, a + b));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if ((input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)
			&& getFocused() instanceof TextBox t && commits.containsKey(t)) {
			commitAll();
			clearAndInit();
			return true;
		}
		if (DonutGambleClient.OPEN_MANAGER.matchesKey(input) && !(getFocused() instanceof TextBox)) {
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
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		regions.clear();
		float t = Anim.ease(fade.to(1f, 14f));
		ctx.fill(0, 0, vw, vh, Colors.alpha(Colors.SCRIM, Math.round(0x8C * t)));
		Paint.window(ctx, wx, wy, ww, wh);

		Paint.round(ctx, wx + PAD, wy + 10, 7, 7, 2, Colors.accent());
		Paint.text(ctx, textRenderer, "Donut Gamble", wx + PAD + 12, wy + 10, Colors.INK);
		Paint.text(ctx, textRenderer, section.label, cx, wy + 10, Colors.INK);
		Paint.text(ctx, textRenderer, section.subtitle, cx + textRenderer.getWidth(section.label) + 8, wy + 10, Colors.INK_FAINT);
		Paint.hline(ctx, wx + 1, wx + ww - 1, wy + HEADER, Colors.LINE);

		drawSidebar();
		ctx.enableScissor(cx - 2, cy - 2, cx + cw + 2, cy + ch + 2);
		switch (section) {
			case DASHBOARD -> drawDashboard();
			case PLAYERS -> drawPlayers();
			case AUCTION -> drawAuction();
			case LEADERBOARD -> drawLeaderboard();
			case HISTORY -> drawHistory();
			case PAYMENTS -> drawPayments();
			case FINANCE -> drawFinance();
			case SETTINGS -> drawSettings();
		}
		ctx.disableScissor();
	}

	private void drawSidebar() {
		int x = wx + 1;
		int top = wy + HEADER + 1;
		ctx.fill(x, top, wx + SIDEBAR, wy + wh - 4, Colors.alpha(Colors.INPUT, 0x90));
		ctx.fill(wx + SIDEBAR, top, wx + SIDEBAR + 1, wy + wh - 1, Colors.LINE);

		int y = top + PAD - 2;
		String group = "";
		int selectedY = 0;
		List<int[]> items = new ArrayList<>();
		for (Section s : Section.values()) {
			if (!s.group.equals(group)) {
				y += group.isEmpty() ? 0 : 4;
				group = s.group;
				Paint.label(ctx, textRenderer, group, x + PAD - 2, y);
				y += 11;
			}
			if (s == section) {
				selectedY = y;
			}
			items.add(new int[]{s.ordinal(), y});
			y += 14;
		}
		if (navY.get() < 0) {
			navY.snap(selectedY);
		}
		int sy = Math.round(navY.to(selectedY, 18f));
		Paint.round(ctx, x + 4, sy - 3, SIDEBAR - 10, 14, 3, Colors.alpha(Colors.accent(), 0x2C));
		Paint.round(ctx, x + 4, sy - 1, 2, 10, 1, Colors.accent());
		for (int[] item : items) {
			Section s = Section.values()[item[0]];
			int iy = item[1];
			boolean hover = s != section && regions.hovered(mouseX, mouseY, x + 4, iy - 3, SIDEBAR - 10, 14);
			if (hover) {
				Paint.round(ctx, x + 4, iy - 3, SIDEBAR - 10, 14, 3, Colors.alpha(Colors.CARD_HI, 0xC0));
			}
			Paint.text(ctx, textRenderer, s.label, x + PAD + 2, iy, s == section || hover ? Colors.INK : Colors.INK_DIM);
			if (s == Section.AUCTION && state.auctions.isLive()) {
				Paint.right(ctx, textRenderer, Fmt.clock(state.auctions.current.remaining(System.currentTimeMillis())),
					x + SIDEBAR - 10, iy, Colors.GREEN);
			}
			regions.add(x + 4, iy - 3, SIDEBAR - 10, 14, () -> show(s));
		}
		if (y + 8 < wy + wh - 16) {
			Paint.text(ctx, textRenderer, "Home to close", x + PAD - 2, wy + wh - 16, Colors.INK_FAINT);
		}
	}

	// ================= building blocks =================

	private void card(int x, int y, int w, int h) {
		Paint.card(ctx, x, y, w, h, 4, Colors.CARD, Colors.LINE);
	}

	/** A stat tile: small label, value underneath. */
	private void stat(int x, int y, int w, String label, String value, int color) {
		card(x, y, w, 30);
		Paint.label(ctx, textRenderer, label, x + 7, y + 6);
		text(Paint.fit(textRenderer, value, w - 14), x + 7, y + 17, color);
	}

	private void cardTitle(int x, int y, String title) {
		Paint.label(ctx, textRenderer, title, x + 7, y + 6);
	}

	private void text(String s, int x, int y, int color) {
		Paint.text(ctx, textRenderer, s, x, y, color);
	}

	private void right(String s, int rightX, int y, int color) {
		Paint.right(ctx, textRenderer, s, rightX, y, color);
	}

	/** Clickable inline text with a hover highlight; returns where the next one goes. */
	private int link(String s, int x, int y, int color, Runnable action) {
		int w = textRenderer.getWidth(s);
		boolean hover = regions.hovered(mouseX, mouseY, x - 2, y - 2, w + 4, 12);
		if (hover) {
			Paint.round(ctx, x - 2, y - 2, w + 4, 12, 2, Colors.alpha(color, 0x26));
		}
		text(s, x, y, hover ? Colors.mix(color, 0xFFFFFFFF, 0.25f) : color);
		regions.add(x - 2, y - 2, w + 4, 12, () -> {
			action.run();
			if (MinecraftClient.getInstance().currentScreen == this) {
				clearAndInit();
			}
		});
		return x + w + 8;
	}

	/** On/off switch, right-aligned to {@code rightX}, vertically centered on a text line at {@code y}. */
	private void toggle(int rightX, int y, boolean on, Runnable flip) {
		int x = rightX - TOGGLE_W;
		int h = 10;
		Paint.pill(ctx, x, y - 1, TOGGLE_W, h, on ? Colors.alpha(Colors.accent(), 0xC0) : Colors.LINE_HI);
		Paint.round(ctx, on ? x + TOGGLE_W - h + 1 : x + 1, y, h - 2, h - 2, (h - 2) / 2, on ? Colors.ON_ACCENT : Colors.INK_DIM);
		regions.add(x - 2, y - 3, TOGGLE_W + 4, h + 4, () -> {
			flip.run();
			state.save();
		});
	}

	/** Scrollable rows inside a card, with hover highlight and a scrollbar. */
	private void list(int x, int y, int w, int h, int count, Section key, RowPainter painter) {
		int rows = Math.max(1, (h - 4) / ROW);
		int max = Math.max(0, count - rows);
		int scroll = Math.min(SCROLL.getOrDefault(key, 0), max);
		SCROLL.put(key, scroll);
		int ry = y + 2;
		for (int i = scroll; i < Math.min(count, scroll + rows); i++) {
			if (regions.hovered(mouseX, mouseY, x + 2, ry, w - 4, ROW)) {
				Paint.round(ctx, x + 2, ry, w - 4, ROW, 2, Colors.CARD_HI);
			}
			painter.paint(i, ry + 3);
			ry += ROW;
		}
		if (max > 0) {
			int trackH = h - 8;
			int thumbH = Math.max(10, trackH * rows / count);
			int thumbY = y + 4 + (trackH - thumbH) * scroll / max;
			Paint.round(ctx, x + w - 4, thumbY, 2, thumbH, 1, Colors.LINE_HI);
		}
	}

	private void empty(String message, int x, int y, int w, int h) {
		Paint.center(ctx, textRenderer, message, x + w / 2, y + h / 2 - 4, Colors.INK_FAINT);
	}

	private static String time(SimpleDateFormat f, long millis) {
		synchronized (f) {
			return f.format(new Date(millis));
		}
	}

	private static String onOff(boolean b) {
		return b ? "On" : "Off";
	}

	// ================= Dashboard =================

	private void drawDashboard() {
		FinanceReport session = state.finance.report(state.ledger.sessionStart);
		FinanceReport life = state.finance.report(0);
		int gap = 6;
		int tw = (cw - 3 * gap) / 4;
		stat(cx, cy, tw, "Session", Fmt.signed(session.net()), Colors.profit(session.net()));
		stat(cx + (tw + gap), cy, tw, "Received", Fmt.money(session.received()), Colors.INK);
		stat(cx + 2 * (tw + gap), cy, tw, "Paid out", Fmt.money(session.paidOut()), Colors.INK);
		stat(cx + 3 * (tw + gap), cy, tw, "Lifetime", Fmt.signed(life.net()), Colors.profit(life.net()));

		int y = cy + 30 + gap;
		int half = (cw - gap) / 2;
		int boxH = 56;
		card(cx, y, half, boxH);
		cardTitle(cx, y, "Auction");
		Auction a = state.auctions.current;
		if (a != null) {
			long left = a.remaining(System.currentTimeMillis());
			text(Paint.fit(textRenderer, a.item, half - 50), cx + 7, y + 18, Colors.INK);
			right(Fmt.clock(left), cx + half - 7, y + 18, a.status == Auction.Status.PAUSED ? Colors.GOLD : Colors.GREEN);
			Standing leader = a.leader();
			text("Total " + Fmt.money(a.total()) + " · " + a.rankings().size() + " bidders", cx + 7, y + 30, Colors.INK_DIM);
			text(leader == null ? "No bids yet" : "Leader " + Paint.fit(textRenderer, leader.player(), half - 90) + " " + Fmt.money(leader.total()),
				cx + 7, y + 42, Colors.GOLD);
		} else {
			text("No auction running", cx + 7, y + 18, Colors.INK_DIM);
			link("Start one →", cx + 7, y + 34, Colors.accent(), () -> show(Section.AUCTION));
		}

		int qx = cx + half + gap;
		card(qx, y, half, boxH);
		cardTitle(qx, y, "Queue");
		int owed = state.ledger.owed().size() + state.auctions.openRefunds().size();
		right(owed + " owed", qx + half - 7, y + 6, owed > 0 ? Colors.GOLD : Colors.INK_FAINT);
		int ly = y + 18;
		for (Game g : Game.values()) {
			text(g.displayName, qx + 7, ly, Colors.INK_DIM);
			right(state.ledger.pending(g).size() + " waiting", qx + half - 7, ly, Colors.INK);
			ly += 11;
		}

		y += boxH + gap;
		int listH = cy + ch - y;
		card(cx, y, cw, listH);
		cardTitle(cx, y, "Latest payments");
		List<Transaction> recent = state.finance.search("");
		if (recent.isEmpty()) {
			empty("Payments show up here as they come in", cx, y, cw, listH);
			return;
		}
		int rows = Math.max(0, (listH - 20) / ROW);
		for (int i = 0; i < Math.min(rows, recent.size()); i++) {
			txRow(recent.get(i), cx + 7, y + 19 + i * ROW, cw - 14, false);
		}
	}

	/** time · type · player · amount · source/note · [Void] */
	private void txRow(Transaction t, int x, int y, int w, boolean interactive) {
		int ink = t.voided ? Colors.INK_FAINT : Colors.INK;
		text(time(CLOCK, t.timestamp), x, y, Colors.INK_FAINT);
		String type = switch (t.type) {
			case INCOMING -> "IN";
			case PAYOUT -> "OUT";
			case EXPENSE -> "EXP";
		};
		int typeColor = t.voided ? Colors.INK_FAINT : t.type == Transaction.Type.INCOMING ? Colors.GREEN : Colors.RED;
		Paint.pill(ctx, x + 50, y - 2, 24, 11, Colors.alpha(typeColor, 0x30));
		Paint.center(ctx, textRenderer, type, x + 62, y, typeColor);
		String name = Paint.fit(textRenderer, t.player, 86);
		if (interactive) {
			link(name, x + 80, y, ink, () -> {
				DRAFTS.put("payments.search", t.player);
				SCROLL.put(Section.PAYMENTS, 0);
			});
		} else {
			text(name, x + 80, y, ink);
		}
		int amountColor = t.voided ? Colors.INK_FAINT : t.type == Transaction.Type.INCOMING ? Colors.GREEN : Colors.INK;
		right(Fmt.money(t.amount), x + 214, y, amountColor);
		String detail = t.source.name().toLowerCase(Locale.ROOT) + (t.note.isEmpty() ? "" : " · " + t.note);
		int detailRight = interactive ? x + w - 34 : x + w;
		text(Paint.fit(textRenderer, detail, detailRight - (x + 222)), x + 222, y, Colors.INK_FAINT);
		if (interactive) {
			String act = t.voided ? "Undo" : "Void";
			link(act, x + w - textRenderer.getWidth(act) - 4, y, t.voided ? Colors.GREEN : Colors.RED, () -> state.setVoided(t, !t.voided));
		}
	}

	// ================= Players =================

	private void initPlayers() {
		TextBox search = box("players.search", cx, cy, Math.min(130, cw * 2 / 5), "Search players");
		search.setChangedListener(s -> {
			DRAFTS.put("players.search", s);
			SCROLL.put(Section.PLAYERS, 0);
		});
	}

	private void drawPlayers() {
		List<PlayerStats> all = PlayerStats.all(state.ledger, state.finance, state.auctions);
		List<PlayerStats> list = PlayerStats.filter(all, draft("players.search"));
		int listW = Math.min(130, cw * 2 / 5);
		int top = cy + TextBox.HEIGHT + 6;
		int listH = cy + ch - top;
		card(cx, top, listW, listH);
		if (list.isEmpty()) {
			empty(all.isEmpty() ? "No players yet" : "No match", cx, top, listW, listH);
		} else {
			if (selectedPlayer == null || PlayerStats.find(all, selectedPlayer) == null) {
				selectedPlayer = list.get(0).player();
			}
			list(cx, top, listW, listH, list.size(), Section.PLAYERS, (i, y) -> {
				PlayerStats p = list.get(i);
				boolean sel = p.player().equalsIgnoreCase(selectedPlayer);
				if (sel) {
					Paint.round(ctx, cx + 2, y - 3, listW - 4, ROW, 2, Colors.alpha(Colors.accent(), 0x30));
				}
				text(Paint.fit(textRenderer, p.player(), listW - 64), cx + 7, y, sel ? Colors.INK : Colors.INK_DIM);
				right(Fmt.signed(p.net()), cx + listW - 8, y, Colors.profit(p.net()));
				regions.add(cx + 2, y - 3, listW - 4, ROW, () -> selectedPlayer = p.player());
			});
		}

		int dx = cx + listW + 6;
		int dw = cx + cw - dx;
		card(dx, cy, dw, ch);
		PlayerStats p = selectedPlayer == null ? null : PlayerStats.find(all, selectedPlayer);
		if (p == null) {
			empty("Pick a player", dx, cy, dw, ch);
			return;
		}
		text(Paint.fit(textRenderer, p.player(), dw - 16), dx + 8, cy + 8, Colors.INK);
		Paint.hline(ctx, dx + 1, dx + dw - 1, cy + 22, Colors.LINE);
		String[][] cells = {
			{"Paid you", Fmt.money(p.paidYou())},
			{"You paid", Fmt.money(p.youPaid())},
			{"Net", Fmt.signed(p.net())},
			{"W-L-T", p.wins() + "-" + p.losses() + "-" + p.ties()},
			{"Wagered", Fmt.money(p.wagered())},
			{"Bets net", Fmt.signed(p.houseNetFromBets())},
			{"Bids", Fmt.money(p.auctionTotal())},
			{"Won", p.auctionsWon() + (p.auctionsWon() == 1 ? " auction" : " auctions")},
		};
		int colW = (dw - 16) / 2;
		for (int i = 0; i < cells.length; i++) {
			int x = dx + 8 + (i % 2) * colW;
			int y = cy + 28 + (i / 2) * 22;
			Paint.label(ctx, textRenderer, cells[i][0], x, y);
			String v = cells[i][1];
			text(v, x, y + 10, v.startsWith("+") ? Colors.GREEN : v.startsWith("-") ? Colors.RED : Colors.INK);
		}
		int hy = cy + 28 + 4 * 22 + 2;
		Paint.hline(ctx, dx + 1, dx + dw - 1, hy - 4, Colors.LINE);
		Paint.label(ctx, textRenderer, "Recent", dx + 8, hy);
		right("seen " + time(DAY, p.lastSeen()), dx + dw - 8, hy, Colors.INK_FAINT);
		List<Transaction> hist = state.finance.history(p.player());
		int rows = Math.max(0, (cy + ch - hy - 28) / 11);
		for (int i = 0; i < Math.min(rows, hist.size()); i++) {
			Transaction t = hist.get(i);
			int y = hy + 12 + i * 11;
			text(time(CLOCK, t.timestamp).substring(0, 5), dx + 8, y, Colors.INK_FAINT);
			String what = (t.type == Transaction.Type.INCOMING ? "in · " : "out · ") + t.source.name().toLowerCase(Locale.ROOT);
			text(what, dx + 40, y, t.voided ? Colors.INK_FAINT : Colors.INK_DIM);
			int c = t.voided ? Colors.INK_FAINT : t.type == Transaction.Type.INCOMING ? Colors.GREEN : Colors.RED;
			right(Fmt.money(t.amount), dx + dw - 8, y, c);
		}
		link("All their payments →", dx + 8, cy + ch - 14, Colors.accent(), () -> {
			DRAFTS.put("payments.search", p.player());
			show(Section.PAYMENTS);
		});
	}

	// ================= Live Auction =================

	private static final int FORM_X = 90;

	private void initAuction() {
		Auction a = state.auctions.current;
		if (a == null) {
			int fx = cx + FORM_X;
			box("auction.item", fx, cy + 20, cw - FORM_X - 8, "e.g. Skeleton Spawner");
			TextBox len = box("auction.duration", fx, cy + 40, 70, "5m");
			if (draft("auction.duration").isEmpty()) {
				len.setText(formatDuration(state.config.auctionDefaultDurationSec));
			}
			box("auction.cost", fx, cy + 60, 70, "optional");
			button(fx, cy + 82, "Start auction", Button.Variant.PRIMARY, () -> {
				int sec = parseDuration(draft("auction.duration"), state.config.auctionDefaultDurationSec);
				if (state.startAuction(draft("auction.item"), sec, AmountParser.parseOrZero(draft("auction.cost")))) {
					DRAFTS.remove("auction.item");
					DRAFTS.remove("auction.cost");
					DRAFTS.remove("auction.duration");
				}
			});
			return;
		}
		int y = cy + 72;
		int x = cx + 8;
		x = button(x, y, a.status == Auction.Status.PAUSED ? "Resume" : "Pause", Button.Variant.SECONDARY, state::togglePauseAuction).getRight() + 4;
		x = button(x, y, "+30s", Button.Variant.SECONDARY, () -> state.extendAuction(30)).getRight() + 4;
		x = button(x, y, "-30s", Button.Variant.SECONDARY, () -> state.extendAuction(-30)).getRight() + 4;
		button(x, y, "+5m", Button.Variant.SECONDARY, () -> state.extendAuction(300));
		Button end = buttonRight(cx + cw - 8, y, "End now", Button.Variant.PRIMARY, state::endAuction);
		buttonRight(end.getX() - 4, y, "Cancel", Button.Variant.DANGER, state::cancelAuction);

		int fy = cy + ch - TextBox.HEIGHT;
		box("auction.fixPlayer", cx + 64, fy, 96, "Player");
		box("auction.fixAmount", cx + 164, fy, 76, "50m or -50m");
		button(cx + 244, fy, "Apply", Button.Variant.SECONDARY, () -> {
			String raw = draft("auction.fixAmount");
			boolean negative = raw.startsWith("-");
			long v = AmountParser.parseOrZero(negative ? raw.substring(1) : raw);
			state.correctAuction(draft("auction.fixPlayer"), negative ? -v : v);
			DRAFTS.remove("auction.fixAmount");
		});
	}

	private void drawAuction() {
		Auction a = state.auctions.current;
		if (a == null) {
			card(cx, cy, cw, 106);
			cardTitle(cx, cy, "New auction");
			right("While it runs, every /pay counts toward it", cx + cw - 8, cy + 6, Colors.INK_FAINT);
			text("Item", cx + 8, cy + 24, Colors.INK_DIM);
			text("Length", cx + 8, cy + 44, Colors.INK_DIM);
			text("Prize cost", cx + 8, cy + 64, Colors.INK_DIM);
			text("5m · 90s · 2:30", cx + FORM_X + 78, cy + 44, Colors.INK_FAINT);
			text("counted as an expense", cx + FORM_X + 78, cy + 64, Colors.INK_FAINT);

			int ry = cy + 112;
			card(cx, ry, cw, cy + ch - ry);
			cardTitle(cx, ry, "Rules");
			String[][] rules = {
				{"Payments go to the auction", onOff(state.config.routePaymentsToAuction)},
				{"End when the timer hits 0", onOff(state.config.auctionAutoEnd)},
				{"Refund everyone but the winner", onOff(state.config.auctionRefundLosers)},
				{"Anti-snipe", state.config.auctionAntiSnipeSec > 0 ? state.config.auctionAntiSnipeSec + "s" : "Off"},
				{"Minimum payment", state.config.auctionMinContribution > 0 ? Fmt.money(state.config.auctionMinContribution) : "None"},
			};
			int y = ry + 19;
			for (String[] r : rules) {
				text(r[0], cx + 8, y, Colors.INK_DIM);
				right(r[1], cx + cw - 8, y, Colors.INK);
				y += 12;
			}
			link("Change in Settings →", cx + 8, Math.min(y + 2, cy + ch - 12), Colors.accent(), () -> show(Section.SETTINGS));
			return;
		}

		long left = a.remaining(System.currentTimeMillis());
		boolean paused = a.status == Auction.Status.PAUSED;
		card(cx, cy, cw, 94);
		text(Paint.fit(textRenderer, a.item, cw - 70), cx + 8, cy + 8, Colors.INK);
		String status = paused ? "PAUSED" : "LIVE";
		int sc = paused ? Colors.GOLD : Colors.GREEN;
		int sw = textRenderer.getWidth(status) + 10;
		Paint.pill(ctx, cx + cw - 8 - sw, cy + 6, sw, 12, Colors.alpha(sc, 0x38));
		Paint.center(ctx, textRenderer, status, cx + cw - 8 - sw / 2, cy + 8, sc);
		int clockColor = paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.INK;
		Paint.big(ctx, textRenderer, Fmt.clock(left), cx + 8, cy + 22, 3, clockColor, false);
		float frac = (float) left / Math.max(1, Math.max(a.durationMs, left));
		Paint.bar(ctx, cx + 8, cy + 52, cw - 16, 4, frac, Colors.LINE, paused ? Colors.GOLD : Colors.accent());
		Standing leader = a.leader();
		int sx = cx + cw / 2;
		Paint.label(ctx, textRenderer, "Collected", sx, cy + 24);
		text(Fmt.money(a.total()), sx, cy + 34, Colors.GREEN);
		Paint.label(ctx, textRenderer, "Leader", sx + 70, cy + 24);
		text(leader == null ? "-" : Paint.fit(textRenderer, leader.player(), cw / 2 - 78), sx + 70, cy + 34, Colors.GOLD);

		int ly = cy + 100;
		int lh = cy + ch - TextBox.HEIGHT - 6 - ly;
		card(cx, ly, cw, lh);
		List<Standing> r = a.rankings();
		if (r.isEmpty()) {
			empty("Waiting for the first /pay", cx, ly, cw, lh);
		} else {
			list(cx, ly, cw, lh, r.size(), Section.AUCTION, (i, y) -> standingRow(i, r.get(i), cx, y, cw));
		}
		text("Correction", cx, cy + ch - 12, Colors.INK_DIM);
	}

	private void standingRow(int i, Standing s, int x, int y, int w) {
		int barW = Math.round((w - 16) * (float) s.share());
		Paint.round(ctx, x + 6, y - 2, Math.max(2, barW), ROW - 2, 2, Colors.alpha(Colors.accent(), 0x1C));
		text(Integer.toString(i + 1), x + 10, y, i == 0 ? Colors.GOLD : Colors.INK_FAINT);
		text(Paint.fit(textRenderer, s.player(), w / 2 - 34), x + 26, y, i == 0 ? Colors.GOLD : Colors.INK);
		right(Fmt.money(s.total()), x + w / 2 + 40, y, Colors.INK);
		text(s.count() + (s.count() == 1 ? " payment" : " payments"), x + w / 2 + 52, y, Colors.INK_FAINT);
		right(Math.round(s.share() * 100) + "%", x + w - 10, y, Colors.INK_DIM);
	}

	// ================= Leaderboard =================

	private void drawLeaderboard() {
		Auction a = state.auctions.latest();
		card(cx, cy, cw, ch);
		if (a == null) {
			empty("No auctions yet", cx, cy, cw, ch);
			return;
		}
		text(Paint.fit(textRenderer, a.item, cw - 120), cx + 8, cy + 8, Colors.INK);
		right((a.isLive() ? "live" : a.status.name().toLowerCase(Locale.ROOT)) + " · " + Fmt.money(a.total()), cx + cw - 8, cy + 8, Colors.INK_DIM);
		Paint.hline(ctx, cx + 1, cx + cw - 1, cy + 22, Colors.LINE);
		List<Standing> r = a.rankings();
		if (r.isEmpty()) {
			empty("No bids", cx, cy + 22, cw, ch - 22);
			return;
		}
		list(cx, cy + 24, cw, ch - 26, r.size(), Section.LEADERBOARD, (i, y) -> standingRow(i, r.get(i), cx, y, cw));
	}

	// ================= History =================

	private void initHistory(int rightX) {
		Button json = buttonRight(rightX, wy + 6, "JSON", Button.Variant.SECONDARY, () -> state.exportAuctions(true));
		buttonRight(json.getX() - 4, wy + 6, "CSV", Button.Variant.SECONDARY, () -> state.exportAuctions(false));
	}

	private void drawHistory() {
		List<Auction> list = new ArrayList<>(state.auctions.history);
		Collections.reverse(list);
		int listW = Math.min(170, cw * 2 / 5);
		if (list.isEmpty()) {
			card(cx, cy, cw, ch);
			empty("Finished auctions show up here", cx, cy, cw, ch);
			return;
		}
		card(cx, cy, listW, ch);
		Auction found = state.auctions.find(selectedAuction);
		Auction sel = found == null || found.isLive() ? list.get(0) : found;
		selectedAuction = sel.id;
		int rows = Math.max(1, (ch - 4) / 24);
		int scroll = Math.min(SCROLL.getOrDefault(Section.HISTORY, 0), Math.max(0, list.size() - rows));
		SCROLL.put(Section.HISTORY, scroll);
		int y = cy + 2;
		for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
			Auction a = list.get(i);
			boolean on = a.id == sel.id;
			if (on || regions.hovered(mouseX, mouseY, cx + 2, y, listW - 4, 23)) {
				Paint.round(ctx, cx + 2, y, listW - 4, 23, 3, on ? Colors.alpha(Colors.accent(), 0x30) : Colors.CARD_HI);
			}
			text(Paint.fit(textRenderer, a.item, listW - 60), cx + 8, y + 3, on ? Colors.INK : Colors.INK_DIM);
			right(Fmt.money(a.total()), cx + listW - 8, y + 3, Colors.INK);
			String sub = time(DAY, a.startedAt) + " · " + (a.winner != null ? a.winner : a.status.name().toLowerCase(Locale.ROOT));
			text(Paint.fit(textRenderer, sub, listW - 16), cx + 8, y + 13, Colors.INK_FAINT);
			long id = a.id;
			regions.add(cx + 2, y, listW - 4, 23, () -> selectedAuction = id);
			y += 24;
		}

		int dx = cx + listW + 6;
		int dw = cx + cw - dx;
		card(dx, cy, dw, ch);
		int sy = cy + 8;
		List<String> lines = sel.summary();
		int limit = cy + ch - 24;
		for (int i = 0; i < lines.size() && sy < limit; i++) {
			String line = lines.get(i);
			int color = line.startsWith("Winner") ? Colors.GOLD : line.startsWith("#") ? Colors.INK_DIM : Colors.INK;
			for (var wrapped : textRenderer.wrapLines(Text.literal(line), dw - 16)) {
				if (sy >= limit) {
					break;
				}
				ctx.drawText(textRenderer, wrapped, dx + 8, sy, color, false);
				sy += 11;
			}
			if (i == 0) {
				Paint.hline(ctx, dx + 1, dx + dw - 1, sy + 1, Colors.LINE);
				sy += 6;
			}
		}
		long open = state.auctions.openRefunds().stream().filter(rf -> rf.auctionId == sel.id).count();
		if (open > 0) {
			link(open + (open == 1 ? " refund" : " refunds") + " still owed →", dx + 8, cy + ch - 14, Colors.GOLD, () -> {
				state.config.activeTab = QueueHud.Tab.OWE.name();
				state.save();
				close();
			});
		}
	}

	// ================= Payments =================

	private void initPayments() {
		TextBox search = box("payments.search", cx, cy, 150, "Search: player, 200m, payout");
		search.setChangedListener(s -> {
			DRAFTS.put("payments.search", s);
			SCROLL.put(Section.PAYMENTS, 0);
		});
		Button json = buttonRight(cx + cw, cy, "JSON", Button.Variant.SECONDARY, () -> state.exportTransactions(true));
		buttonRight(json.getX() - 4, cy, "Export CSV", Button.Variant.SECONDARY, () -> state.exportTransactions(false));

		int y = cy + ch - TextBox.HEIGHT;
		box("payments.player", cx + 30, y, 80, "Player");
		box("payments.amount", cx + 114, y, 56, "Amount");
		Button in = button(cx + 174, y, "+ Received", Button.Variant.SECONDARY, () -> manual(true));
		button(in.getRight() + 4, y, "+ Paid out", Button.Variant.SECONDARY, () -> manual(false));
	}

	private void manual(boolean incoming) {
		String player = draft("payments.player");
		long amount = AmountParser.parseOrZero(draft("payments.amount"));
		if (player.isEmpty() || amount <= 0) {
			return;
		}
		if (incoming) {
			state.addManualPayment(player, amount);
		} else {
			state.addManualPayout(player, amount);
		}
		DRAFTS.remove("payments.amount");
	}

	private void drawPayments() {
		List<Transaction> list = state.finance.search(draft("payments.search"));
		int top = cy + TextBox.HEIGHT + 6;
		int h = ch - 2 * (TextBox.HEIGHT + 6);
		card(cx, top, cw, h);
		if (list.isEmpty()) {
			empty(draft("payments.search").isEmpty() ? "No payments yet" : "No matches", cx, top, cw, h);
		} else {
			list(cx, top, cw, h, list.size(), Section.PAYMENTS, (i, y) -> txRow(list.get(i), cx + 7, y, cw - 14, true));
		}
		text(list.size() + (list.size() == 1 ? " entry" : " entries"), cx + 156, cy + 4, Colors.INK_FAINT);
		text("Add", cx, cy + ch - 12, Colors.INK_DIM);
	}

	// ================= Finance =================

	private void initFinance(int rightX) {
		buttonRight(rightX, wy + 6, "Export report", Button.Variant.SECONDARY, state::exportReport);
		int y = cy + ch - TextBox.HEIGHT;
		box("finance.note", cx + 48, y, 120, "What for");
		box("finance.amount", cx + 172, y, 60, "Amount");
		button(cx + 236, y, "Add expense", Button.Variant.SECONDARY, () -> {
			long amount = AmountParser.parseOrZero(draft("finance.amount"));
			if (amount > 0) {
				state.addExpense(draft("finance.note").isEmpty() ? "expense" : draft("finance.note"), amount);
				DRAFTS.remove("finance.amount");
				DRAFTS.remove("finance.note");
			}
		});
	}

	private void drawFinance() {
		FinanceReport s = state.finance.report(state.ledger.sessionStart);
		FinanceReport l = state.finance.report(0);
		int tableW = Math.min(240, cw * 3 / 5);
		int tableH = 158;
		card(cx, cy, tableW, tableH);
		int c1 = cx + tableW - 76;
		int c2 = cx + tableW - 8;
		int y = cy + 6;
		Paint.label(ctx, textRenderer, "Cash", cx + 8, y);
		Paint.label(ctx, textRenderer, "Session", c1 - textRenderer.getWidth("SESSION"), y);
		Paint.label(ctx, textRenderer, "Lifetime", c2 - textRenderer.getWidth("LIFETIME"), y);
		y += 15;
		y = financeRow("Received", Fmt.money(s.received()), Fmt.money(l.received()), c1, c2, y, Colors.GREEN, false);
		for (Transaction.Source src : Transaction.Source.values()) {
			String name = "  from " + src.name().toLowerCase(Locale.ROOT);
			y = financeRow(name, Fmt.money(s.receivedFrom(src)), Fmt.money(l.receivedFrom(src)), c1, c2, y, Colors.INK_DIM, true);
		}
		y = financeRow("Paid out", Fmt.money(s.paidOut()), Fmt.money(l.paidOut()), c1, c2, y, Colors.RED, false);
		y = financeRow("Expenses", Fmt.money(s.expenses()), Fmt.money(l.expenses()), c1, c2, y, Colors.RED, false);
		Paint.hline(ctx, cx + 8, cx + tableW - 8, y - 2, Colors.LINE_HI);
		y += 2;
		text("Net profit", cx + 8, y, Colors.INK);
		right(Fmt.signed(s.net()), c1, y, Colors.profit(s.net()));
		right(Fmt.signed(l.net()), c2, y, Colors.profit(l.net()));
		y += 15;
		y = financeRow("In / out", s.incomingCount() + " / " + s.payoutCount(), l.incomingCount() + " / " + l.payoutCount(), c1, c2, y, Colors.INK_DIM, true);
		long g = state.ledger.sessionStats().net();
		long gl = state.ledger.lifetimeStats().net();
		financeRow("Game results", Fmt.signed(g), Fmt.signed(gl), c1, c2, y, Colors.INK_DIM, true);

		int px = cx + tableW + 6;
		int pw = cx + cw - px;
		card(px, cy, pw, tableH);
		cardTitle(px, cy, "Top payers");
		List<PlayerTotal> top = state.finance.topContributors(0, 10);
		if (top.isEmpty()) {
			empty("No payments yet", px, cy, pw, tableH);
		}
		for (int i = 0; i < top.size(); i++) {
			PlayerTotal p = top.get(i);
			int ty = cy + 20 + i * 13;
			text(Integer.toString(i + 1), px + 8, ty, i == 0 ? Colors.GOLD : Colors.INK_FAINT);
			text(Paint.fit(textRenderer, p.player(), pw - 78), px + 26, ty, i == 0 ? Colors.GOLD : Colors.INK);
			right(Fmt.money(p.total()), px + pw - 8, ty, Colors.INK);
		}

		text("Net = money in - /pay out - expenses", cx, cy + tableH + 6, Colors.INK_FAINT);
		text("Expense", cx, cy + ch - 12, Colors.INK_DIM);
	}

	private int financeRow(String label, String session, String life, int c1, int c2, int y, int color, boolean minor) {
		text(label, cx + 8, y, minor ? Colors.INK_FAINT : Colors.INK);
		right(session, c1, y, color);
		right(life, c2, y, color);
		return y + (minor ? 11 : 13);
	}

	// ================= Settings =================

	// Layout shared by initSettings (widgets) and drawSettings (labels and cards).
	private static final int FIELD_W = 56;
	private static final int SET_ROW = 15;
	private static final int FIELD_ROW = 20;

	private int col2() {
		return cx + cw / 2 + 3;
	}

	private int col2W() {
		return cx + cw - col2();
	}

	private int halfW() {
		return cw / 2 - 3;
	}

	/** Top of the first field row in the Auctions card (after its three toggles). */
	private int auctionFieldsY() {
		return cy + 19 + 3 * SET_ROW;
	}

	private int lookH() {
		return 19 + 2 * SET_ROW + 20;
	}

	private int paymentsY() {
		return cy + lookH() + 6;
	}

	private int dataY() {
		return paymentsY() + 17 + 2 * FIELD_ROW + 6;
	}

	private void initSettings() {
		int fx = cx + halfW() - 8 - FIELD_W;
		int y = auctionFieldsY();
		setting(fx, y, FIELD_W, formatDuration(state.config.auctionDefaultDurationSec), s -> {
			int sec = parseDuration(s, -1);
			if (sec > 0) {
				state.config.auctionDefaultDurationSec = sec;
			}
		});
		setting(fx, y + FIELD_ROW, FIELD_W, Integer.toString(state.config.auctionAntiSnipeSec), s -> {
			try {
				state.config.auctionAntiSnipeSec = Math.max(0, Integer.parseInt(s));
			} catch (NumberFormatException ignored) {
			}
		});
		setting(fx, y + 2 * FIELD_ROW, FIELD_W,
			state.config.auctionMinContribution > 0 ? AmountParser.format(state.config.auctionMinContribution) : "0",
			s -> state.config.auctionMinContribution = AmountParser.parseOrZero(s));

		int rx = col2() + col2W() - 8 - FIELD_W;
		int py = paymentsY() + 17;
		setting(rx, py, FIELD_W, Long.toString(state.config.dedupeWindowMs), s -> {
			try {
				state.config.dedupeWindowMs = Math.max(0, Long.parseLong(s));
			} catch (NumberFormatException ignored) {
			}
		});
		setting(rx, py + FIELD_ROW, FIELD_W, state.config.elytraPrice > 0 ? AmountParser.format(state.config.elytraPrice) : "",
			s -> {
				long v = AmountParser.parseOrZero(s);
				if (v > 0 || s.isEmpty()) {
					state.config.elytraPrice = v;
				}
			});

		button(col2() + 8, cy + 19 + 2 * SET_ROW, "Edit HUD layout", Button.Variant.PRIMARY,
			() -> MinecraftClient.getInstance().setScreen(new HudEditorScreen(DonutGambleClient.hud, this)));
		int dy = dataY() + 24;
		Button backup = button(col2() + 8, dy, "Back up", Button.Variant.SECONDARY, state::backupNow);
		button(backup.getRight() + 4, dy, "Open folder", Button.Variant.SECONDARY,
			() -> Util.getOperatingSystem().open(state.dataDir().toFile()));
	}

	private void drawSettings() {
		int half = halfW();
		int col2 = col2();
		int rw = col2W();

		int ah = auctionFieldsY() - cy + 3 * FIELD_ROW + 2;
		card(cx, cy, half, ah);
		cardTitle(cx, cy, "Auctions");
		int y = cy + 19;
		text("Payments go to it", cx + 8, y, Colors.INK_DIM);
		toggle(cx + half - 8, y, state.config.routePaymentsToAuction,
			() -> state.config.routePaymentsToAuction = !state.config.routePaymentsToAuction);
		y += SET_ROW;
		text("End when timer hits 0", cx + 8, y, Colors.INK_DIM);
		toggle(cx + half - 8, y, state.config.auctionAutoEnd, () -> state.config.auctionAutoEnd = !state.config.auctionAutoEnd);
		y += SET_ROW;
		text("Refund non-winners", cx + 8, y, Colors.INK_DIM);
		toggle(cx + half - 8, y, state.config.auctionRefundLosers, () -> state.config.auctionRefundLosers = !state.config.auctionRefundLosers);
		y = auctionFieldsY() + 4;
		for (String f : new String[]{"Default length", "Anti-snipe sec", "Min. payment"}) {
			text(f, cx + 8, y, Colors.INK_DIM);
			y += FIELD_ROW;
		}

		card(col2, cy, rw, lookH());
		cardTitle(col2, cy, "Look");
		int ly = cy + 19;
		text("Color", col2 + 8, ly, Colors.INK_DIM);
		String color = Colors.theme().displayName + " ›";
		link(color, col2 + rw - 8 - textRenderer.getWidth(color), ly, Colors.accent(), state::nextTheme);
		ly += SET_ROW;
		text("Pop-ups", col2 + 8, ly, Colors.INK_DIM);
		toggle(col2 + rw - 8, ly, state.config.notifications, () -> state.config.notifications = !state.config.notifications);

		int py = paymentsY();
		card(col2, py, rw, 17 + 2 * FIELD_ROW);
		cardTitle(col2, py, "Payments");
		text("Duplicate ms", col2 + 8, py + 21, Colors.INK_DIM);
		text("Elytra price", col2 + 8, py + 21 + FIELD_ROW, Colors.INK_DIM);

		int dy = dataY();
		card(col2, dy, rw, 46);
		cardTitle(col2, dy, "Data");
		right("daily, 7 kept", col2 + rw - 8, dy + 6, Colors.INK_FAINT);

		text("Press Enter to apply a value", cx + 2, cy + ah + 6, Colors.INK_FAINT);
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
		return sec % 60 == 0 ? (sec / 60) + "m" : sec + "s";
	}
}
