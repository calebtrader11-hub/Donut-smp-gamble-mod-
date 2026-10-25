package com.donutgamble.client;

import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.PrivacySettings;
import com.donutgamble.core.PrivacySettings.Surface;

import java.util.List;

/** Streamer Mode: the switch, presets, what each surface hides, a preview and what always stays public. */
final class StreamerPage extends ManagerPage {
	private static final Object[][] SURFACES = {
		{Surface.DASHBOARD, "Dashboard", "Session and lifetime net, received, paid out"},
		{Surface.PAPER_HUD, "Queue HUD", "Game profit, Stats tab, total owed"},
		{Surface.AUCTION_HUD, "Auction bids", "Bid amounts and totals (public by default)"},
		{Surface.TOASTS, "Pop-ups", "Profit, exports, backups; public ones stay"},
		{Surface.PLAYER_STATS, "Player stats", "What each player paid and was paid, notes"},
		{Surface.FINANCE, "Finance", "Finance and Payments pages, profit HUD"},
	};
	private static final List<String> PRESETS = List.of("BALANCED", "STRICT", "CUSTOM");
	private static final int ROW = 15;

	StreamerPage(ManagerScreen m) {
		super(m);
	}

	@Override
	ManagerScreen.Section section() {
		return ManagerScreen.Section.STREAMER;
	}

	private int leftW() {
		return Math.round(m.cw * 0.6f);
	}

	private int listTop() {
		return m.cy + 48;
	}

	@Override
	void init() {
		PrivacySettings p = state.config.privacy;
		int cx = m.cx, cy = m.cy, cw = m.cw;
		Button main = m.buttonRight(cx + cw - 8, cy + 12, p.streamerMode ? "Turn off" : "Turn on",
			p.streamerMode ? Button.Variant.SECONDARY : Button.Variant.PRIMARY, state::toggleStreamerMode);
		Button prev = m.buttonRight(main.getX() - 4, cy + 12, state.preview ? "Stop preview" : "Preview",
			Button.Variant.SECONDARY, () -> state.preview = !state.preview);
		prev.icon(Icons.EYE);
		prev.setX(main.getX() - 4 - prev.getWidth());

		int lw = leftW();
		int top = listTop();
		m.dropdown(cx + lw - 8 - 74, top + 4, 74, List.of("Balanced", "Strict", "Custom"),
			() -> PRESETS.indexOf(state.privacyPreset()), i -> {
				if (i < 2) {
					state.applyPrivacyPreset(PRESETS.get(i));
				}
			});
		int ty = top + 25;
		for (Object[] row : SURFACES) {
			Surface surface = (Surface) row[0];
			m.toggle(cx + lw - 8, ty + 1, (String) row[1], () -> p.get(surface), () -> p.set(surface, !p.get(surface)));
			ty += ROW;
		}
	}

	@Override
	void draw() {
		int cx = m.cx, cy = m.cy, cw = m.cw, ch = m.ch;
		var ctx = m.ctx;
		PrivacySettings p = state.config.privacy;
		boolean on = p.streamerMode;
		boolean preview = state.preview && !on;

		// status
		int sc = on ? Colors.RED : preview ? Colors.GOLD : Colors.INK_FAINT;
		Paint.round(ctx, cx, cy, cw, 40, 4, Colors.LINE);
		Paint.roundGradient(ctx, cx + 1, cy + 1, cw - 2, 38, 3, Colors.mix(Colors.CARD_TOP, sc, on ? 0.16f : 0.05f), Colors.CARD);
		Paint.round(ctx, cx + 8, cy + 9, 22, 22, 6, Colors.alpha(sc, 0x30));
		(on ? Icons.EYE : Icons.LOCK).draw(ctx, cx + 15, cy + 16, on ? Colors.mix(Colors.RED, 0xFFFFFFFF, 0.3f) : sc);
		int textW = cw - 36 - 8 - (m.width("Stop preview") + m.width("Turn off") + 2 * 2 * Paint.S5 + 11 + 12);
		m.text(m.fit(on ? "Streamer Mode is on" : preview ? "Previewing" : "Streamer Mode is off", textW), cx + 36, cy + 10,
			on || preview ? Colors.INK : Colors.INK_DIM);
		String sub = on ? "Private figures hidden"
			: preview ? "What viewers would see"
			: "Bind a hotkey in Controls";
		boolean bound = DonutGambleClient.TOGGLE_STREAMER != null && !DonutGambleClient.TOGGLE_STREAMER.isUnbound();
		if (bound && !preview) {
			sub = "Hotkey " + DonutGambleClient.TOGGLE_STREAMER.getBoundKeyLocalizedText().getString() + " · " + sub;
		}
		m.text(m.fit(sub, textW), cx + 36, cy + 22, Colors.INK_FAINT);

		// switches
		int lw = leftW();
		int top = listTop();
		int h = 25 + SURFACES.length * ROW + 3;
		m.card(cx, top, lw, h);
		m.cardTitle(cx, top, "Hide on stream", Icons.LOCK);
		int y = top + 25;
		for (Object[] row : SURFACES) {
			float hv = m.hover("surface." + row[1], m.in(cx + 3, y - 2, lw - 6, ROW));
			if (hv > 0.01f) {
				Paint.round(ctx, cx + 3, y - 2, lw - 6, ROW, 2, Colors.fade(Colors.CARD_HI, hv));
			}
			m.text((String) row[1], cx + 8, y + 2, Colors.INK);
			m.text(m.fit((String) row[2], lw - 80 - 36), cx + 80, y + 2, Colors.INK_FAINT);
			y += ROW;
		}

		// what stays public
		int rx = cx + lw + ManagerScreen.GAP;
		int rw = cx + cw - rx;
		int rh = cy + ch - top;
		m.card(rx, top, rw, rh);
		m.cardTitle(rx, top, "Always public", Icons.EYE);
		String[] pub = {"Auction timer", "Prize and title", "Leaderboard", p.get(Surface.AUCTION_HUD) ? "Bids (hidden)" : "Bid amounts",
			"Game queues", "Winners", "Public pop-ups"};
		int py = top + 22;
		for (String line : pub) {
			if (py > top + rh - 10) {
				break;
			}
			boolean off = line.endsWith("(hidden)");
			(off ? Icons.CLOSE : Icons.CHECK).draw(m.ctx, rx + 8, py, off ? Colors.INK_FAINT : Colors.GREEN);
			m.text(m.fit(line, rw - 28), rx + 20, py, off ? Colors.INK_FAINT : Colors.INK_DIM);
			py += 12;
		}

		int ny = top + h + 6;
		m.paragraph("Only hides what this mod draws in Minecraft. It can't hide other windows, overlays or files "
			+ "your stream captures.", cx, ny, lw, cy + ch + 2, Colors.INK_FAINT);
	}
}
