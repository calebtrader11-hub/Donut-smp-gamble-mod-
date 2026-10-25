package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Auction;
import com.donutgamble.core.FinanceReport;
import com.donutgamble.core.Game;
import com.donutgamble.core.PlayerTotal;
import com.donutgamble.core.Standing;
import com.donutgamble.core.Transaction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The full manager (Home key, or "Manager" on the queue): auctions, payments, finance and settings.
 * Same look as the queue: see-through dark box, theme outline, plain text, clickable words.
 */
final class ManagerScreen extends Screen {
	enum Section {
		DASHBOARD("Dashboard"),
		AUCTION("Active Auction"),
		LEADERBOARD("Leaderboard"),
		PAYMENTS("Payments"),
		FINANCE("Finance"),
		HISTORY("Auction History"),
		SETTINGS("Settings");

		final String label;

		Section(String label) {
			this.label = label;
		}
	}

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final int PAD = 8;
	private static final int ROW = 11;
	private static final int SIDEBAR = 92;
	private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
	private static final SimpleDateFormat DAY = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT);

	/** Survive closing and reopening the manager. */
	private static Section lastSection = Section.DASHBOARD;
	private static final Map<String, String> DRAFTS = new HashMap<>();
	private static long selectedAuction;

	private final GambleState state;
	private Section section;
	private final List<Hit> hits = new ArrayList<>();
	private int px, py, pw, ph, cx, cy, cw, ch;
	private int scroll;

	private ManagerScreen(GambleState state, Section section) {
		super(Text.literal("Donut Gamble Manager"));
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

	// ---- layout & widgets ----

	@Override
	protected void init() {
		pw = Math.min(width - 16, 440);
		ph = Math.min(height - 16, 270);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		cx = px + SIDEBAR + PAD;
		cy = py + 24;
		cw = px + pw - PAD - cx;
		ch = py + ph - PAD - cy;
		lastSection = section;

		switch (section) {
			case AUCTION -> initAuction();
			case PAYMENTS -> initPayments();
			case FINANCE -> initFinance();
			case HISTORY -> initHistory();
			case SETTINGS -> initSettings();
			default -> {
			}
		}
	}

	private void show(Section s) {
		section = s;
		scroll = 0;
		clearAndInit();
	}

	/** A small outlined button sized to its label; returns the x after it. */
	private int button(int x, int y, String label, int color, Runnable action) {
		int w = textRenderer.getWidth(label) + 10;
		addDrawableChild(new FlatButton(x, y, w, 13, label, color, () -> {
			action.run();
			clearAndInit();
		}));
		return x + w + 4;
	}

	/** A text box whose contents are remembered under {@code id}. */
	private TextFieldWidget field(String id, int x, int y, int w, String placeholder, String initial) {
		TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 13, Text.literal(placeholder));
		f.setMaxLength(48);
		f.setText(DRAFTS.getOrDefault(id, initial));
		f.setPlaceholder(Text.literal(placeholder).withColor(Colors.muted()));
		f.setChangedListener(s -> DRAFTS.put(id, s));
		addDrawableChild(f);
		return f;
	}

	private static String draft(String id) {
		return DRAFTS.getOrDefault(id, "").trim();
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == 0) {
			for (Hit h : List.copyOf(hits)) {
				if (h.contains(click.x(), click.y())) {
					h.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Math.max(0, scroll - (int) Math.signum(vertical) * 3);
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (DonutGambleClient.OPEN_MANAGER.matchesKey(input) && !(getFocused() instanceof TextFieldWidget)) {
			close();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ---- drawing ----

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x66000000);
		Draw.box(ctx, px, py, px + pw, py + ph, Colors.BOX_BG);
		hits.clear();

		ctx.drawTextWithShadow(textRenderer, "Donut Gamble", px + PAD, py + 8, Colors.text());
		ctx.drawTextWithShadow(textRenderer, section.label, cx, py + 8, Colors.text());
		String close = "Close";
		int closeX = px + pw - PAD - textRenderer.getWidth(close);
		ctx.drawTextWithShadow(textRenderer, close, closeX, py + 8, Colors.muted());
		hits.add(new Hit(closeX - 2, py + 6, textRenderer.getWidth(close) + 4, ROW, this::close));
		ctx.fill(px + 1, py + 20, px + pw - 1, py + 21, Colors.outline());
		ctx.fill(px + SIDEBAR, py + 21, px + SIDEBAR + 1, py + ph - 1, Colors.outline());

		int y = py + 28;
		for (Section s : Section.values()) {
			boolean active = s == section;
			if (active) {
				ctx.fill(px + 2, y - 2, px + SIDEBAR - 1, y + 9, (Colors.outline() & 0x00FFFFFF) | 0x55000000);
			}
			String label = s.label;
			if (s == Section.AUCTION && state.auctions.isLive()) {
				label = "Auction " + QueueOverlay.clock(state.auctions.current.remaining(System.currentTimeMillis()));
			}
			ctx.drawTextWithShadow(textRenderer, label, px + PAD, y, active ? Colors.text() : Colors.muted());
			hits.add(new Hit(px + 2, y - 2, SIDEBAR - 3, ROW + 1, () -> show(s)));
			y += ROW + 4;
		}
		String hint = "Home to close";
		ctx.drawTextWithShadow(textRenderer, hint, px + PAD, py + ph - PAD - 8, Colors.muted());

		switch (section) {
			case DASHBOARD -> drawDashboard(ctx);
			case AUCTION -> drawAuction(ctx);
			case LEADERBOARD -> drawLeaderboard(ctx);
			case PAYMENTS -> drawPayments(ctx);
			case FINANCE -> drawFinance(ctx);
			case HISTORY -> drawHistory(ctx);
			case SETTINGS -> drawSettings(ctx);
		}
	}

	private void text(DrawContext ctx, String s, int x, int y, int color) {
		ctx.drawTextWithShadow(textRenderer, s, x, y, color);
	}

	private void right(DrawContext ctx, String s, int rightX, int y, int color) {
		ctx.drawTextWithShadow(textRenderer, s, rightX - textRenderer.getWidth(s), y, color);
	}

	private void heading(DrawContext ctx, String s, int y) {
		text(ctx, s, cx, y, Colors.muted());
		ctx.fill(cx + textRenderer.getWidth(s) + 4, y + 4, cx + cw, y + 5, (Colors.outline() & 0x00FFFFFF) | 0x44000000);
	}

	/** A clickable word drawn in render (no rebuild needed). */
	private int link(DrawContext ctx, String s, int x, int y, int color, Runnable action) {
		text(ctx, s, x, y, color);
		hits.add(new Hit(x - 1, y - 1, textRenderer.getWidth(s) + 2, ROW, action));
		return x + textRenderer.getWidth(s) + 8;
	}

	private static String money(long v) {
		return QueueOverlay.money(v);
	}

	private static String signed(long v) {
		return (v > 0 ? "+" : v < 0 ? "-" : "") + money(Math.abs(v));
	}

	private static String time(SimpleDateFormat f, long millis) {
		synchronized (f) {
			return f.format(new Date(millis));
		}
	}

	/** Draws a "label  value" pair in a column. */
	private void stat(DrawContext ctx, String label, String value, int x, int y, int color) {
		text(ctx, label, x, y, Colors.muted());
		text(ctx, value, x, y + 10, color);
	}

	// ---- Dashboard ----

	private void drawDashboard(DrawContext ctx) {
		FinanceReport session = state.finance.report(state.ledger.sessionStart);
		FinanceReport life = state.finance.report(0);
		int col = cw / 4;
		int y = cy;
		stat(ctx, "Session net", signed(session.net()), cx, y, Colors.profit(session.net()));
		stat(ctx, "Received", money(session.received()), cx + col, y, Colors.text());
		stat(ctx, "Paid out", money(session.paidOut()), cx + col * 2, y, Colors.text());
		stat(ctx, "Expenses", money(session.expenses()), cx + col * 3, y, Colors.text());
		y += 26;
		stat(ctx, "Lifetime net", signed(life.net()), cx, y, Colors.profit(life.net()));
		stat(ctx, "Lifetime received", money(life.received()), cx + col, y, Colors.text());
		stat(ctx, "Session", QueueOverlay.duration(System.currentTimeMillis() - state.ledger.sessionStart), cx + col * 3, y, Colors.text());
		y += 28;

		heading(ctx, "Auction", y);
		y += ROW + 1;
		Auction a = state.auctions.current;
		if (a != null) {
			Standing leader = a.leader();
			text(ctx, a.item + "  " + QueueOverlay.clock(a.remaining(System.currentTimeMillis()))
				+ (a.status == Auction.Status.PAUSED ? " (paused)" : ""), cx, y, Colors.text());
			y += ROW;
			text(ctx, "Total " + money(a.total()) + " · " + a.rankings().size() + " players"
				+ (leader != null ? " · leader " + leader.player() + " " + money(leader.total()) : ""), cx, y, Colors.muted());
		} else {
			link(ctx, "No auction running - start one", cx, y, Colors.GREEN, () -> show(Section.AUCTION));
			y += ROW;
			Auction last = state.auctions.latest();
			text(ctx, last == null ? "" : "Last: " + String.join(" · ", last.summary().subList(0, Math.min(3, last.summary().size()))),
				cx, y, Colors.muted());
		}
		y += ROW + 4;

		heading(ctx, "Gambling", y);
		y += ROW + 1;
		StringBuilder q = new StringBuilder("Waiting:");
		for (Game g : Game.values()) {
			q.append(' ').append(g.displayName).append(' ').append(state.ledger.pending(g).size()).append(" ·");
		}
		q.append(" Owed ").append(state.ledger.owed().size() + state.auctions.openRefunds().size());
		text(ctx, q.toString(), cx, y, Colors.text());
		y += ROW;
		long gambleNet = state.ledger.sessionStats().net();
		text(ctx, "Game results this session " + signed(gambleNet), cx, y, Colors.profit(gambleNet));
		y += ROW + 4;

		heading(ctx, "Latest payments", y);
		y += ROW + 1;
		List<Transaction> recent = state.finance.search("");
		int rows = Math.max(0, (cy + ch - y) / ROW);
		for (int i = 0; i < Math.min(rows, recent.size()); i++) {
			txRow(ctx, recent.get(i), y, false);
			y += ROW;
		}
		if (recent.isEmpty()) {
			text(ctx, "Nothing yet. Payments appear here as they come in.", cx, y, Colors.muted());
		}
	}

	// ---- Active Auction ----

	private void initAuction() {
		Auction a = state.auctions.current;
		if (a == null) {
			int y = cy + 14;
			field("auction.item", cx + 70, y, cw - 70, "e.g. Spawner, 64 Totems", "");
			y += 18;
			field("auction.duration", cx + 70, y, 60, "5m", formatDuration(state.config.auctionDefaultDurationSec));
			y += 18;
			field("auction.cost", cx + 70, y, 60, "optional", "");
			y += 22;
			button(cx + 70, y, "Start auction", Colors.GREEN, () -> {
				int sec = parseDuration(draft("auction.duration"), state.config.auctionDefaultDurationSec);
				if (state.startAuction(draft("auction.item"), sec, AmountParser.parseOrZero(draft("auction.cost")))) {
					DRAFTS.remove("auction.item");
					DRAFTS.remove("auction.cost");
				}
			});
			return;
		}
		int y = cy + 62;
		int x = button(cx, y, a.status == Auction.Status.PAUSED ? "Resume" : "Pause", Colors.GOLD, state::togglePauseAuction);
		x = button(x, y, "+30s", Colors.text(), () -> state.extendAuction(30));
		x = button(x, y, "-30s", Colors.text(), () -> state.extendAuction(-30));
		x = button(x, y, "+5m", Colors.text(), () -> state.extendAuction(300));
		x = button(x + 6, y, "End now", Colors.RED, state::endAuction);
		button(x, y, "Cancel", Colors.muted(), state::cancelAuction);

		y = cy + ch - 30;
		field("auction.fixPlayer", cx + 44, y, 90, "Player", "");
		field("auction.fixAmount", cx + 178, y, 60, "50m / -50m", "");
		button(cx + 244, y, "Apply", Colors.text(), () -> {
			String raw = draft("auction.fixAmount");
			boolean negative = raw.startsWith("-");
			long v = AmountParser.parseOrZero(negative ? raw.substring(1) : raw);
			state.correctAuction(draft("auction.fixPlayer"), negative ? -v : v);
			DRAFTS.remove("auction.fixAmount");
		});
	}

	private void drawAuction(DrawContext ctx) {
		Auction a = state.auctions.current;
		if (a == null) {
			text(ctx, "Start a new auction. While it runs, every /pay counts toward it.", cx, cy, Colors.muted());
			int y = cy + 17;
			text(ctx, "Item", cx, y, Colors.text());
			text(ctx, "Length", cx, y + 18, Colors.text());
			text(ctx, "Prize cost", cx, y + 36, Colors.text());
			text(ctx, "(5m, 90s, 2:30)", cx + 136, y + 21, Colors.muted());
			text(ctx, "counted as an expense", cx + 136, y + 39, Colors.muted());
			y += 80;
			heading(ctx, "Rules (change in Settings)", y);
			y += ROW + 1;
			text(ctx, "Payments go to auction: " + onOff(state.config.routePaymentsToAuction)
				+ " · Auto-end: " + onOff(state.config.auctionAutoEnd), cx, y, Colors.text());
			y += ROW;
			text(ctx, "Refund non-winners: " + onOff(state.config.auctionRefundLosers)
				+ " · Anti-snipe: " + (state.config.auctionAntiSnipeSec > 0 ? state.config.auctionAntiSnipeSec + "s" : "off"), cx, y, Colors.text());
			y += ROW;
			text(ctx, "Minimum payment: " + (state.config.auctionMinContribution > 0 ? money(state.config.auctionMinContribution) : "none"),
				cx, y, Colors.text());
			return;
		}

		long left = a.remaining(System.currentTimeMillis());
		text(ctx, a.item, cx, cy, Colors.text());
		right(ctx, a.status == Auction.Status.PAUSED ? "PAUSED" : "LIVE", cx + cw, cy,
			a.status == Auction.Status.PAUSED ? Colors.GOLD : Colors.GREEN);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy + 14);
		ctx.getMatrices().scale(3f, 3f);
		ctx.drawTextWithShadow(textRenderer, QueueOverlay.clock(left), 0, 0,
			a.status == Auction.Status.PAUSED ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.text());
		ctx.getMatrices().popMatrix();
		Standing leader = a.leader();
		int sx = cx + 120;
		stat(ctx, "Total collected", money(a.total()), sx, cy + 14, Colors.GREEN);
		stat(ctx, "Leader", leader == null ? "-" : leader.player() + " " + money(leader.total()), sx, cy + 38, Colors.GOLD);
		right(ctx, a.rankings().size() + " players", cx + cw, cy + 24, Colors.muted());

		int y = cy + 82;
		heading(ctx, "Top contributors", y);
		y += ROW + 1;
		List<Standing> r = a.rankings();
		int room = Math.max(0, (cy + ch - 36 - y) / ROW);
		for (int i = 0; i < Math.min(room, r.size()); i++) {
			standingRow(ctx, i, r.get(i), y);
			y += ROW;
		}
		if (r.isEmpty()) {
			text(ctx, "Waiting for the first /pay...", cx, y, Colors.muted());
		} else if (r.size() > room) {
			link(ctx, "+" + (r.size() - room) + " more - see Leaderboard", cx, y, Colors.muted(), () -> show(Section.LEADERBOARD));
		}
		int fy = cy + ch - 30;
		heading(ctx, "Manual correction (auction only)", fy - 12);
		text(ctx, "Player", cx, fy + 3, Colors.text());
		text(ctx, "Amount", cx + 140, fy + 3, Colors.text());
	}

	private void standingRow(DrawContext ctx, int i, Standing s, int y) {
		int barW = (int) Math.round((cw - 2) * s.share());
		ctx.fill(cx, y - 1, cx + barW, y + 9, (Colors.outline() & 0x00FFFFFF) | 0x33000000);
		text(ctx, "#" + (i + 1), cx + 2, y, i == 0 ? Colors.GOLD : Colors.muted());
		text(ctx, s.player(), cx + 26, y, i == 0 ? Colors.GOLD : Colors.text());
		text(ctx, money(s.total()), cx + cw / 2, y, Colors.text());
		text(ctx, s.count() + (s.count() == 1 ? " payment" : " payments"), cx + cw / 2 + 56, y, Colors.muted());
		right(ctx, Math.round(s.share() * 100) + "%", cx + cw - 2, y, Colors.text());
	}

	// ---- Leaderboard ----

	private void drawLeaderboard(DrawContext ctx) {
		Auction a = state.auctions.latest();
		if (a == null) {
			text(ctx, "No auctions yet.", cx, cy, Colors.muted());
			return;
		}
		text(ctx, a.item + " · " + (a.isLive() ? "live" : a.status.name().toLowerCase(Locale.ROOT))
			+ " · total " + money(a.total()), cx, cy, Colors.text());
		int y = cy + ROW + 4;
		text(ctx, "#", cx + 2, y, Colors.muted());
		text(ctx, "Player", cx + 26, y, Colors.muted());
		text(ctx, "Total", cx + cw / 2, y, Colors.muted());
		right(ctx, "Share", cx + cw - 2, y, Colors.muted());
		y += ROW + 2;
		List<Standing> r = a.rankings();
		int rows = Math.max(1, (cy + ch - y) / ROW);
		scroll = Math.min(scroll, Math.max(0, r.size() - rows));
		for (int i = scroll; i < Math.min(r.size(), scroll + rows); i++) {
			standingRow(ctx, i, r.get(i), y);
			y += ROW;
		}
		if (r.isEmpty()) {
			text(ctx, "No contributions yet.", cx, y, Colors.muted());
		}
	}

	// ---- Payments ----

	private void initPayments() {
		TextFieldWidget search = field("payments.search", cx + 40, cy - 2, 120, "player, amount, type...", "");
		search.setChangedListener(s -> {
			DRAFTS.put("payments.search", s);
			scroll = 0;
		});
		int x = button(cx + cw - 116, cy - 2, "CSV", Colors.text(), () -> state.exportTransactions(false));
		button(x, cy - 2, "JSON", Colors.text(), () -> state.exportTransactions(true));

		int y = cy + ch - 14;
		field("payments.player", cx + 24, y, 62, "Player", "");
		field("payments.amount", cx + 98, y, 44, "Amount", "");
		x = button(cx + 148, y, "+ Received", Colors.GREEN, () -> manual(true));
		button(x, y, "+ Paid out", Colors.RED, () -> manual(false));
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

	private void drawPayments(DrawContext ctx) {
		text(ctx, "Search", cx, cy + 1, Colors.text());
		List<Transaction> list = state.finance.search(draft("payments.search"));
		int y = cy + 16;
		text(ctx, list.size() + " entries · click a name for their history", cx, y, Colors.muted());
		y += ROW + 2;
		int rows = Math.max(1, (cy + ch - 20 - y) / ROW);
		scroll = Math.min(scroll, Math.max(0, list.size() - rows));
		for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
			txRow(ctx, list.get(i), y, true);
			y += ROW;
		}
		if (list.isEmpty()) {
			text(ctx, "No matching payments.", cx, y, Colors.muted());
		}
		text(ctx, "Add", cx, cy + ch - 11, Colors.text());
		text(ctx, "$", cx + 90, cy + ch - 11, Colors.muted());
	}

	/** time · IN/OUT/EXP · player · amount · source/note · [void] */
	private void txRow(DrawContext ctx, Transaction t, int y, boolean interactive) {
		int color = t.voided ? Colors.muted() : Colors.text();
		text(ctx, time(CLOCK, t.timestamp), cx, y, Colors.muted());
		String type = switch (t.type) {
			case INCOMING -> "IN";
			case PAYOUT -> "OUT";
			case EXPENSE -> "EXP";
		};
		text(ctx, type, cx + 48, y, t.voided ? Colors.muted() : t.type == Transaction.Type.INCOMING ? Colors.GREEN : Colors.RED);
		int nameX = cx + 72;
		if (interactive) {
			link(ctx, t.player, nameX, y, color, () -> {
				DRAFTS.put("payments.search", t.player);
				scroll = 0;
				clearAndInit();
			});
		} else {
			text(ctx, t.player, nameX, y, color);
		}
		right(ctx, money(t.amount), cx + 196, y, color);
		String detail = t.source.name().toLowerCase(Locale.ROOT) + (t.note.isEmpty() ? "" : " · " + t.note);
		int detailRight = interactive ? cx + cw - 28 : cx + cw;
		text(ctx, textRenderer.trimToWidth(detail, detailRight - (cx + 202)), cx + 202, y, Colors.muted());
		if (interactive) {
			String act = t.voided ? "undo" : "void";
			int ax = cx + cw - textRenderer.getWidth(act);
			link(ctx, act, ax, y, t.voided ? Colors.GREEN : Colors.RED, () -> state.setVoided(t, !t.voided));
		}
	}

	// ---- Finance ----

	private void initFinance() {
		int y = cy + ch - 14;
		field("finance.note", cx + 46, y, 110, "What for", "");
		field("finance.amount", cx + 170, y, 50, "Amount", "");
		button(cx + 226, y, "Add expense", Colors.RED, () -> {
			long amount = AmountParser.parseOrZero(draft("finance.amount"));
			if (amount > 0) {
				state.addExpense(draft("finance.note").isEmpty() ? "expense" : draft("finance.note"), amount);
				DRAFTS.remove("finance.amount");
				DRAFTS.remove("finance.note");
			}
		});
		button(cx + cw - 76, cy - 2, "Export report", Colors.text(), state::exportReport);
	}

	private void drawFinance(DrawContext ctx) {
		FinanceReport s = state.finance.report(state.ledger.sessionStart);
		FinanceReport l = state.finance.report(0);
		int c1 = cx + 140;
		int c2 = cx + 210;
		int y = cy + 1;
		right(ctx, "Session", c1, y, Colors.muted());
		right(ctx, "Lifetime", c2, y, Colors.muted());
		y += ROW + 2;
		y = financeRow(ctx, "Received", money(s.received()), money(l.received()), c1, c2, y, Colors.GREEN);
		for (Transaction.Source src : Transaction.Source.values()) {
			y = financeRow(ctx, "  from " + src.name().toLowerCase(Locale.ROOT), money(s.receivedFrom(src)), money(l.receivedFrom(src)), c1, c2, y, Colors.muted());
		}
		y = financeRow(ctx, "Paid out", money(s.paidOut()), money(l.paidOut()), c1, c2, y, Colors.RED);
		y = financeRow(ctx, "Expenses", money(s.expenses()), money(l.expenses()), c1, c2, y, Colors.RED);
		ctx.fill(cx, y - 1, c2, y, (Colors.outline() & 0x00FFFFFF) | 0x66000000);
		y += 2;
		text(ctx, "Net profit", cx, y, Colors.text());
		right(ctx, signed(s.net()), c1, y, Colors.profit(s.net()));
		right(ctx, signed(l.net()), c2, y, Colors.profit(l.net()));
		y += ROW;
		y = financeRow(ctx, "Payments in/out", s.incomingCount() + " / " + s.payoutCount(), l.incomingCount() + " / " + l.payoutCount(), c1, c2, y, Colors.muted());
		long gamble = state.ledger.sessionStats().net();
		long gambleLife = state.ledger.lifetimeStats().net();
		y = financeRow(ctx, "Game results", signed(gamble), signed(gambleLife), c1, c2, y, Colors.muted());

		y += 4;
		heading(ctx, "Top payers (lifetime)", y);
		y += ROW + 1;
		List<PlayerTotal> top = state.finance.topContributors(0, 6);
		int half = cw / 2;
		for (int i = 0; i < top.size(); i++) {
			PlayerTotal p = top.get(i);
			int colX = cx + (i % 2) * half;
			int rowY = y + (i / 2) * ROW;
			text(ctx, textRenderer.trimToWidth((i + 1) + " " + p.player(), half - 60), colX, rowY, i == 0 ? Colors.GOLD : Colors.text());
			right(ctx, money(p.total()), colX + half - 10, rowY, Colors.text());
		}
		if (top.isEmpty()) {
			text(ctx, "No payments yet", cx, y, Colors.muted());
		}
		text(ctx, "Expense", cx, cy + ch - 11, Colors.text());
		text(ctx, "Net = money in - /pay out - expenses", cx, cy + ch - 26, Colors.muted());
	}

	private int financeRow(DrawContext ctx, String label, String session, String life, int c1, int c2, int y, int color) {
		text(ctx, label, cx, y, label.startsWith("  ") ? Colors.muted() : Colors.text());
		right(ctx, session, c1, y, color);
		right(ctx, life, c2, y, color);
		return y + ROW;
	}

	// ---- Auction History ----

	private void initHistory() {
		int x = button(cx + cw - 76, cy - 2, "CSV", Colors.text(), () -> state.exportAuctions(false));
		button(x, cy - 2, "JSON", Colors.text(), () -> state.exportAuctions(true));
	}

	private void drawHistory(DrawContext ctx) {
		List<Auction> list = new ArrayList<>(state.auctions.history);
		java.util.Collections.reverse(list);
		text(ctx, list.size() + (list.size() == 1 ? " auction" : " auctions") + " · click one for its summary", cx, cy + 1, Colors.muted());
		if (list.isEmpty()) {
			text(ctx, "Finished auctions show up here.", cx, cy + 16, Colors.muted());
			return;
		}
		if (state.auctions.find(selectedAuction) == null || state.auctions.find(selectedAuction).isLive()) {
			selectedAuction = list.get(0).id;
		}
		int listW = cw * 2 / 5;
		int y = cy + 16;
		int rows = Math.max(1, (cy + ch - y) / (ROW * 2));
		scroll = Math.min(scroll, Math.max(0, list.size() - rows));
		for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
			Auction a = list.get(i);
			boolean sel = a.id == selectedAuction;
			if (sel) {
				ctx.fill(cx - 2, y - 2, cx + listW, y + 19, (Colors.outline() & 0x00FFFFFF) | 0x44000000);
			}
			text(ctx, textRenderer.trimToWidth(a.item, listW - 50), cx, y, sel ? Colors.text() : Colors.muted());
			right(ctx, money(a.total()), cx + listW - 2, y, Colors.text());
			text(ctx, textRenderer.trimToWidth(time(DAY, a.startedAt) + " · " + (a.winner != null ? a.winner : a.status.name().toLowerCase(Locale.ROOT)),
				listW - 2), cx, y + 9, Colors.muted());
			long id = a.id;
			hits.add(new Hit(cx - 2, y - 2, listW + 2, 21, () -> selectedAuction = id));
			y += ROW * 2;
		}
		Auction sel = state.auctions.find(selectedAuction);
		int sx = cx + listW + 10;
		int sy = cy + 16;
		for (String line : sel.summary()) {
			int color = line.startsWith("Winner") ? Colors.GOLD : Colors.text();
			for (var wrapped : textRenderer.wrapLines(Text.literal(line), cx + cw - sx)) {
				ctx.drawTextWithShadow(textRenderer, wrapped, sx, sy, color);
				sy += ROW;
			}
		}
		long refundsOpen = state.auctions.openRefunds().stream().filter(r -> r.auctionId == sel.id).count();
		if (refundsOpen > 0) {
			link(ctx, refundsOpen + " refunds still owed", sx, sy + 4, Colors.GOLD, () -> {
				state.config.activeTab = QueueOverlay.Tab.OWE.name();
				state.save();
				close();
			});
		}
	}

	// ---- Settings ----

	private void initSettings() {
		int fx = cx + 190;
		int y = cy + 6 * (ROW + 3);
		numberField("set.duration", fx, y, formatDuration(state.config.auctionDefaultDurationSec), s -> {
			int sec = parseDuration(s, -1);
			if (sec > 0) {
				state.config.auctionDefaultDurationSec = sec;
			}
		});
		y += 16;
		numberField("set.antisnipe", fx, y, Integer.toString(state.config.auctionAntiSnipeSec), s -> {
			try {
				state.config.auctionAntiSnipeSec = Math.max(0, Integer.parseInt(s));
			} catch (NumberFormatException ignored) {
			}
		});
		y += 16;
		numberField("set.min", fx, y, state.config.auctionMinContribution > 0 ? AmountParser.format(state.config.auctionMinContribution) : "0",
			s -> state.config.auctionMinContribution = AmountParser.parseOrZero(s));
		y += 16;
		numberField("set.dedupe", fx, y, Long.toString(state.config.dedupeWindowMs), s -> {
			try {
				state.config.dedupeWindowMs = Math.max(0, Long.parseLong(s));
			} catch (NumberFormatException ignored) {
			}
		});
		y += 16;
		numberField("set.elytra", fx, y, state.config.elytraPrice > 0 ? AmountParser.format(state.config.elytraPrice) : "",
			s -> state.config.elytraPrice = AmountParser.parseOrZero(s));
	}

	private void numberField(String id, int x, int y, String current, java.util.function.Consumer<String> apply) {
		DRAFTS.remove(id);
		TextFieldWidget f = field(id, x, y, 60, "", current);
		f.setChangedListener(s -> {
			apply.accept(s.trim());
			state.save();
		});
	}

	private void drawSettings(DrawContext ctx) {
		int y = cy;
		int vx = cx + 190;
		y = toggle(ctx, "Color", Colors.theme.displayName, y, vx, state::nextTheme);
		y = toggle(ctx, "Payment pop-ups", onOff(state.config.notifications), y, vx,
			() -> state.config.notifications = !state.config.notifications);
		y = toggle(ctx, "Payments go to a live auction", onOff(state.config.routePaymentsToAuction), y, vx,
			() -> state.config.routePaymentsToAuction = !state.config.routePaymentsToAuction);
		y = toggle(ctx, "End auction when timer hits 0", onOff(state.config.auctionAutoEnd), y, vx,
			() -> state.config.auctionAutoEnd = !state.config.auctionAutoEnd);
		y = toggle(ctx, "Refund everyone but the winner", onOff(state.config.auctionRefundLosers), y, vx,
			() -> state.config.auctionRefundLosers = !state.config.auctionRefundLosers);
		y = toggle(ctx, "HUD size", String.format(Locale.ROOT, "%.2fx", state.config.hudScale), y, vx, null);
		int hx = vx + 40;
		hx = link(ctx, "-", hx, y - ROW - 3, Colors.text(), () -> scaleHud(-0.25f));
		hx = link(ctx, "+", hx, y - ROW - 3, Colors.text(), () -> scaleHud(0.25f));
		link(ctx, "Reset", hx + 4, y - ROW - 3, Colors.muted(), () -> {
			state.config.hudX = 6;
			state.config.hudY = 6;
			state.save();
		});

		String[] labels = {"Default auction length", "Anti-snipe (seconds, 0 = off)", "Minimum auction payment",
			"Duplicate window (ms, 0 = off)", "Elytra price"};
		for (int i = 0; i < labels.length; i++) {
			text(ctx, labels[i], cx, y + 3, Colors.text());
			y += 16;
		}
		y += 4;
		text(ctx, "Drag the queue anywhere while chat or Insert is open.", cx, y, Colors.muted());
		text(ctx, "Saved to config/donutgamble/ as you change things.", cx, y + ROW, Colors.muted());
	}

	private int toggle(DrawContext ctx, String label, String value, int y, int vx, Runnable action) {
		text(ctx, label, cx, y, Colors.text());
		if (action != null) {
			link(ctx, value, vx, y, Colors.GREEN, () -> {
				action.run();
				state.save();
			});
		} else {
			text(ctx, value, vx, y, Colors.GREEN);
		}
		return y + ROW + 3;
	}

	private void scaleHud(float delta) {
		state.config.hudScale = Math.max(0.5f, Math.min(3f, state.config.hudScale + delta));
		state.save();
	}

	private static String onOff(boolean b) {
		return b ? "On" : "Off";
	}

	// ---- durations ----

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
