package com.donutgamble.client;

import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.AmountParser;
import com.donutgamble.core.GambleConfig;
import com.donutgamble.core.HudLayout;
import com.donutgamble.core.NotificationSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Settings in categories. Every switch, slider and list saves as soon as it changes; typed values
 * apply on Enter, when the box loses focus, or when leaving the page.
 */
final class SettingsPage extends ManagerPage {
	enum Category {
		APPEARANCE("Appearance", Icons.STAR),
		HUD("HUD", Icons.LAYOUT),
		AUCTIONS("Auctions", Icons.AUCTION),
		GAMES("Games", Icons.GAME),
		PAYMENTS("Payments", Icons.PAYMENTS),
		NOTIFICATIONS("Notifications", Icons.BELL),
		STREAMER("Streamer Mode", Icons.EYE),
		DATA("Data", Icons.HISTORY),
		ADVANCED("Advanced", Icons.SETTINGS);

		final String label;
		final Icons icon;

		Category(String label, Icons icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	/** {@code ctrl}: width the control takes at the right, so the text stops before it. */
	private record Row(String label, String hint, int y, int h, int ctrl) {
	}

	private static final int NAV_W = 92;
	private static final int ROW_H = 24;
	private static final String KEY = "settings.";
	static Category category = Category.APPEARANCE;

	private final List<Row> rows = new ArrayList<>();
	private int rowY, panelTop, panelBottom, contentH;

	SettingsPage(ManagerScreen m) {
		super(m);
	}

	@Override
	ManagerScreen.Section section() {
		return ManagerScreen.Section.SETTINGS;
	}

	private int px() {
		return m.cx + NAV_W + ManagerScreen.GAP;
	}

	private int pw() {
		return m.cx + m.cw - px();
	}

	private int ctrlRight() {
		return px() + pw() - 10;
	}

	@Override
	boolean scroll(double mx, double my, double vertical) {
		if (mx < px()) {
			return true;
		}
		int max = Math.max(0, contentH - (panelBottom - panelTop));
		int before = ManagerScreen.SCROLL.getOrDefault(KEY + category, 0);
		int v = Math.max(0, Math.min(max, before - (int) Math.signum(vertical) * ROW_H));
		if (v != before) {
			ManagerScreen.SCROLL.put(KEY + category, v);
			m.rebuild();
		}
		return true;
	}

	// ---- row builders: each returns the row's top, or -1 when it's scrolled out of view ----

	private int next(String label, String hint, int ctrl) {
		int h = hint == null ? 16 : ROW_H;
		int y = rowY;
		rows.add(new Row(label, hint, y, h, ctrl));
		rowY += h;
		return y >= panelTop && y + h <= panelBottom ? y : -1;
	}

	private void heading(String text) {
		rows.add(new Row(null, text, rowY, 18, 0));
		rowY += 18;
	}

	private void toggle(String label, String hint, BooleanSupplier value, Runnable flip) {
		int y = next(label, hint, 30);
		if (y >= 0) {
			m.toggle(ctrlRight(), y + (hint == null ? 3 : 7), label, value, flip);
		}
	}

	private void field(String label, String hint, int w, String current, Consumer<String> apply) {
		int y = next(label, hint, w + 8);
		if (y >= 0) {
			m.setting(ctrlRight() - w, y + 4, w, current, apply);
		}
	}

	private void slider(String label, String hint, int min, int max, int step, int value, IntFunction<String> format, IntConsumer set) {
		int w = Math.min(130, pw() / 2 - 10);
		int y = next(label, hint, w + 8);
		if (y >= 0) {
			m.slider(ctrlRight() - w, y + 5, w, min, max, step, value, format, set);
		}
	}

	private void dropdown(String label, String hint, int w, List<String> options, IntSupplier selected, IntConsumer set) {
		int y = next(label, hint, w + 8);
		if (y >= 0) {
			m.dropdown(ctrlRight() - w, y + 4, w, options, selected, set);
		}
	}

	private void buttons(String label, String hint, Object... labelsAndActions) {
		int total = 4;
		for (int i = 0; i < labelsAndActions.length; i += 2) {
			total += Button.widthFor(((String) labelsAndActions[i]).replaceFirst("^[!*]", "")) + 4;
		}
		int y = next(label, hint, total);
		if (y < 0) {
			return;
		}
		int right = ctrlRight();
		for (int i = labelsAndActions.length - 2; i >= 0; i -= 2) {
			String text = (String) labelsAndActions[i];
			Runnable run = (Runnable) labelsAndActions[i + 1];
			Button.Variant v = text.startsWith("!") ? Button.Variant.DANGER : text.startsWith("*") ? Button.Variant.PRIMARY : Button.Variant.SECONDARY;
			Button b = m.buttonRight(right, y + 4, text.replaceFirst("^[!*]", ""), v, run);
			right = b.getX() - 4;
		}
	}

	private void info(String label, String hint) {
		next(label, hint, label.equals("Palette") ? Colors.Theme.values().length * 14 + 6 : 0);
	}

	private static String money(long v) {
		return v > 0 ? AmountParser.format(v) : "";
	}

	private static long parseMoney(String s, long fallback) {
		long v = AmountParser.parseOrZero(s);
		return v > 0 ? v : fallback;
	}

	@Override
	void init() {
		rows.clear();
		panelTop = m.cy + 24;
		panelBottom = m.cy + m.ch - 14;
		int scroll = ManagerScreen.SCROLL.getOrDefault(KEY + category, 0);
		rowY = panelTop - scroll;
		GambleConfig c = state.config;
		switch (category) {
			case APPEARANCE -> appearance(c);
			case HUD -> hud(c);
			case AUCTIONS -> auctions(c);
			case GAMES -> games(c);
			case PAYMENTS -> payments(c);
			case NOTIFICATIONS -> notifications(c.notices, c);
			case STREAMER -> streamer();
			case DATA -> data();
			case ADVANCED -> advanced();
		}
		contentH = rowY + scroll - panelTop;
		if (scroll > 0 && contentH <= panelBottom - panelTop) {
			ManagerScreen.SCROLL.put(KEY + category, 0);
		}
	}

	private void appearance(GambleConfig c) {
		List<String> themes = new ArrayList<>();
		for (Colors.Theme t : Colors.Theme.values()) {
			themes.add(t.displayName);
		}
		dropdown("Accent color", "Highlights, HUD, buttons", 110, themes,
			() -> Colors.theme().ordinal(), i -> state.setTheme(Colors.Theme.values()[i]));
		info("Palette", "Click to switch");
		info("Per-component colors", "Set each HUD component's accent in the HUD editor");
	}

	private void hud(GambleConfig c) {
		buttons("Layout", "Drag, resize, style and save layouts (End key)", "*Edit HUD layout",
			(Runnable) () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen(DonutGambleClient.hud, m)));
		for (HudElement e : DonutGambleClient.hud.elements) {
			HudLayout.Slot s = c.hud.slots.get(e.id);
			toggle(e.title, "Show the " + e.title.toLowerCase(java.util.Locale.ROOT) + " component", () -> s == null ? e.visibleByDefault() : s.visible, () -> {
				HudLayout.Slot slot = c.hud.slot(e.id, 6, 6, e.visibleByDefault());
				slot.visible = !slot.visible;
			});
		}
		buttons("Reset layout", "Every component back to its default place and size", "!Reset", (Runnable) () ->
			m.confirm("Reset the HUD layout?", "Every component goes back to its default position, size and style. Saved layouts are kept.",
				"Reset", true, DonutGambleClient.hud::resetLayout));
	}

	private void auctions(GambleConfig c) {
		toggle("Payments go to the auction", "While one runs, /pay counts as a bid instead of a bet",
			() -> c.routePaymentsToAuction, () -> c.routePaymentsToAuction = !c.routePaymentsToAuction);
		toggle("End when the timer hits 0", "Off: it waits for you to press End", () -> c.auctionAutoEnd, () -> c.auctionAutoEnd = !c.auctionAutoEnd);
		toggle("Refund everyone but the winner", "Adds refunds to the Owe tab when it ends",
			() -> c.auctionRefundLosers, () -> c.auctionRefundLosers = !c.auctionRefundLosers);
		slider("Default length", "For new auctions", 30, 3600, 30, c.auctionDefaultDurationSec, ManagerScreen::formatDuration,
			v -> c.auctionDefaultDurationSec = v);
		slider("Anti-snipe", "A late bid pushes the end back to this", 0, 120, 5, c.auctionAntiSnipeSec,
			v -> v == 0 ? "Off" : v + "s", v -> c.auctionAntiSnipeSec = v);
		slider("Ending-soon notice", "Pop-up when this much time is left", 0, 120, 5, c.auctionEndingSoonSec,
			v -> v == 0 ? "Off" : v + "s", v -> c.auctionEndingSoonSec = v);
		dropdown("Bid alerts", "Default for new auctions", 90, AuctionPage.BID_NOTIFY_LABELS,
			() -> Math.max(0, AuctionPage.BID_NOTIFY.indexOf(c.auctionBidNotify)), i -> c.auctionBidNotify = AuctionPage.BID_NOTIFY.get(i));
		field("Minimum payment", "Smaller payments don't count (0 = any)", 64, c.auctionMinContribution > 0 ? AmountParser.format(c.auctionMinContribution) : "0",
			s -> c.auctionMinContribution = AmountParser.parseOrZero(s));
	}

	private void games(GambleConfig c) {
		field("Paper minimum", "Payments from here to the maximum are Paper bets", 64, money(c.paperMin), s -> c.paperMin = parseMoney(s, c.paperMin));
		field("Paper maximum", "", 64, money(c.paperMax), s -> c.paperMax = parseMoney(s, c.paperMax));
		field("Blackjack minimum", "Payments from here to the maximum are Blackjack", 64, money(c.blackjackMin), s -> c.blackjackMin = parseMoney(s, c.blackjackMin));
		field("Blackjack maximum", "", 64, money(c.blackjackMax), s -> c.blackjackMax = parseMoney(s, c.blackjackMax));
		field("Win payout", "Paper and Blackjack winners get the bet times this", 64, trim(c.winMultiplier) + "x", s -> {
			try {
				double v = Double.parseDouble(s.replace("x", "").trim());
				if (v >= 1 && v <= 100) {
					c.winMultiplier = v;
				}
			} catch (NumberFormatException ignored) {
			}
		});
		field("Elytra price", "What one elytra costs you (Ely Arrows wins)", 64, money(c.elytraPrice), s -> {
			long v = AmountParser.parseOrZero(s);
			if (v > 0 || s.isEmpty()) {
				c.elytraPrice = v;
			}
		});
		field("Ely Arrows shot price", "One shot, e.g. 270m: a 810m payment shows as 3x", 64, money(c.elyShotPrice), s -> {
			long v = AmountParser.parseOrZero(s);
			if (v > 0 || s.isEmpty()) {
				c.elyShotPrice = v;
			}
		});
		buttons("Session", "Starts the session totals from zero; nothing is deleted", "New session", (Runnable) () ->
			m.confirm("Start a new session?", "Session totals start from zero. Lifetime totals and all history are kept.", "New session", false, state::newSession));
	}

	private static String trim(double d) {
		return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
	}

	private void payments(GambleConfig c) {
		info("Detected messages", "\"Name paid you $ 75M\" and \"You paid Name $ 1B\"");
		info("Matching payouts", "A /pay you send settles a bet or refund only on an exact name + amount match");
		field("Duplicate window", "Same payment again within this many ms is ignored", 64, Long.toString(c.dedupeWindowMs), s -> {
			try {
				c.dedupeWindowMs = Math.max(0, Math.min(60_000, Long.parseLong(s)));
			} catch (NumberFormatException ignored) {
			}
		});
	}

	private void notifications(NotificationSettings n, GambleConfig c) {
		toggle("Pop-ups", "Master switch for every notification", () -> c.notifications, () -> c.notifications = !c.notifications);
		slider("Duration", "How long each stays on screen", 2, 15, 1, n.durationSec, v -> v + "s", n::setDuration);
		slider("On screen at once", "Older ones leave early when it's full", 1, GambleState.MAX_TOASTS, 1, n.maxVisible, v -> Integer.toString(v),
			v -> n.maxVisible = v);
		toggle("Sounds", "Quiet UI sounds for payments, leaders and endings", () -> n.sounds, () -> n.sounds = !n.sounds);
		buttons("Preview", "Shows one of each style", "*Preview", (Runnable) state::previewNotifications);
		heading("Types");
		for (NotificationSettings.Kind k : NotificationSettings.Kind.values()) {
			toggle(k.label, null, () -> n.isEnabled(k), () -> n.setEnabled(k, !n.isEnabled(k)));
		}
	}

	private void streamer() {
		toggle("Streamer Mode", "Hide private figures on stream", () -> state.config.privacy.streamerMode, state::toggleStreamerMode);
		List<String> presets = List.of("Balanced", "Strict", "Custom");
		List<String> ids = List.of("BALANCED", "STRICT", "CUSTOM");
		dropdown("Preset", "Strict also hides bid amounts", 90, presets, () -> ids.indexOf(state.privacyPreset()), i -> {
			if (i < 2) {
				state.applyPrivacyPreset(ids.get(i));
			}
		});
		buttons("Everything else", "Each surface, preview and what stays public", "Open Streamer page",
			(Runnable) () -> m.show(ManagerScreen.Section.STREAMER));
	}

	private void data() {
		info("Saving", "Automatic, in the background · daily backups, 7 kept");
		buttons("Backups", "A copy of every data file right now", "Back up now", (Runnable) state::backupNow);
		buttons("Folder", "config/donutgamble", "Open folder", (Runnable) () -> Util.getOperatingSystem().open(state.dataDir().toFile()));
		buttons("Payments", "Spreadsheet or JSON in exports/", "CSV", (Runnable) () -> state.exportTransactions(false),
			"JSON", (Runnable) () -> state.exportTransactions(true));
		buttons("Auctions", "Every auction with its ranking", "CSV", (Runnable) () -> state.exportAuctions(false),
			"JSON", (Runnable) () -> state.exportAuctions(true));
		buttons("Settings file", "Export to exports/, import from import/settings.json", "Export", (Runnable) state::exportSettings,
			"Import", (Runnable) () -> m.confirm("Import settings?", "Replaces every setting with config/donutgamble/import/settings.json. "
				+ "Payments, bets, auctions and players aren't touched.", "Import", false, state::importSettings));
	}

	private void advanced() {
		buttons("Dashboard cards", "Show every card in the default order", "Reset", (Runnable) () -> {
			state.config.dashboardCards = new ArrayList<>(new GambleConfig().dashboardCards);
			state.save();
		});
		buttons("Recent items", "Forget recently auctioned items (favorites stay)", "Clear", (Runnable) () -> {
			state.config.recentItems.clear();
			state.save();
		});
		buttons("All settings", "Back to defaults. Your data stays.", "!Reset settings", (Runnable) () ->
			m.confirm("Reset every setting?", "All settings, the HUD layout and Streamer Mode switches go back to defaults. "
				+ "Payments, bets, auctions and players are kept.", "Reset", true, state::resetSettings));
	}

	@Override
	void draw() {
		int cx = m.cx, cy = m.cy, ch = m.ch;
		var ctx = m.ctx;
		// category list
		m.card(cx, cy, NAV_W, ch);
		int y = cy + 5;
		for (Category k : Category.values()) {
			boolean sel = k == category;
			float h = m.hover("settings." + k, !sel && m.in(cx + 3, y, NAV_W - 6, 15));
			if (sel) {
				Paint.round(ctx, cx + 3, y, NAV_W - 6, 15, 3, Colors.alpha(Colors.accent(), 0x2C));
			} else if (h > 0.01f) {
				Paint.round(ctx, cx + 3, y, NAV_W - 6, 15, 3, Colors.fade(Colors.CARD_HI, h));
			}
			k.icon.draw(ctx, cx + 8, y + 4, sel ? Colors.accent() : Colors.INK_FAINT);
			m.text(m.fit(k.label, NAV_W - 28), cx + 20, y + 4, sel ? Colors.INK : Colors.mix(Colors.INK_DIM, Colors.INK, h));
			m.region(cx + 3, y, NAV_W - 6, 15, () -> {
				m.commitAll();
				category = k;
			});
			y += 16;
		}

		int px = px(), pw = pw();
		m.card(px, cy, pw, ch);
		category.icon.draw(ctx, px + 8, cy + 8, Colors.accent());
		m.text(category.label, px + 20, cy + 8, Colors.INK);
		m.right("Saved automatically", px + pw - 8, cy + 8, Colors.INK_FAINT);
		Paint.hline(ctx, px + 1, px + pw - 1, cy + 20, Colors.LINE);

		ctx.enableScissor(px + 1, panelTop - 2, px + pw - 1, panelBottom + 2);
		for (Row r : rows) {
			if (r.y() + r.h() < panelTop - 2 || r.y() > panelBottom) {
				continue;
			}
			if (r.label() == null) {
				Paint.label(ctx, m.textRenderer(), r.hint(), px + 10, r.y() + 6);
				continue;
			}
			boolean hasHint = r.hint() != null && !r.hint().isEmpty();
			int textW = pw - 20 - r.ctrl();
			m.text(m.fit(r.label(), textW), px + 10, r.y() + (hasHint ? 3 : (r.h() - 8) / 2), Colors.INK);
			if (hasHint) {
				m.text(m.fit(r.hint(), textW), px + 10, r.y() + 13, Colors.INK_FAINT);
			}
			if (r.label().equals("Palette")) {
				swatches(r.y());
			}
		}
		ctx.disableScissor();
		if (contentH > panelBottom - panelTop) {
			int track = panelBottom - panelTop;
			int off = ManagerScreen.SCROLL.getOrDefault(KEY + category, 0);
			int thumb = Math.max(12, track * track / contentH);
			int ty = panelTop + (track - thumb) * off / Math.max(1, contentH - track);
			Paint.round(ctx, px + pw - 4, ty, 2, thumb, 1, Colors.LINE_HI);
		}
	}

	private void swatches(int y) {
		int x = ctrlRight() - Colors.Theme.values().length * 14 + 2;
		for (Colors.Theme t : Colors.Theme.values()) {
			boolean sel = t == Colors.theme();
			boolean over = m.in(x, y + 6, 12, 12);
			if (sel || over) {
				Paint.round(m.ctx, x - 1, y + 5, 14, 14, 4, sel ? Colors.INK : Colors.LINE_HI);
			}
			Paint.roundGradient(m.ctx, x, y + 6, 12, 12, 3, Colors.mix(t.outline, 0xFFFFFFFF, 0.2f), t.outline);
			m.region(x, y + 6, 12, 12, () -> state.setTheme(t));
			x += 14;
		}
	}
}
