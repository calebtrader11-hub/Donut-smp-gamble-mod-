package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.PrivacySettings;
import com.donutgamble.core.Stats;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Session at a glance: how long it's been running, games played and the result count, plus the
 * house net. Off by default. While Streamer Mode hides finance, money isn't drawn; the public part
 * (time, games, W/L/T) still is.
 */
final class ProfitHud extends HudElement {
	private final GambleState state;

	ProfitHud(GambleState state) {
		super("profit", "Session");
		this.state = state;
	}

	@Override
	boolean visibleByDefault() {
		return false;
	}

	private boolean hidden() {
		return state.hides(PrivacySettings.Surface.FINANCE);
	}

	private static String counts(Stats s) {
		int games = s.playerWins() + s.playerLosses() + s.ties();
		return games + (games == 1 ? " game" : " games") + " · W " + s.playerWins() + " · L " + s.playerLosses() + " · T " + s.ties();
	}

	private String clock() {
		return Fmt.duration(System.currentTimeMillis() - state.ledger.sessionStart);
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		Stats s = state.ledger.sessionStats();
		int head = tr.getWidth("SESSION  " + clock());
		if (hidden()) {
			return new int[]{Math.max(head, tr.getWidth(counts(s))) + 12, compact ? 28 : 30};
		}
		String detail = counts(s) + " · " + Fmt.money(s.wagered()) + " bet";
		int w = Math.max(head, Math.max(tr.getWidth(Fmt.signed(s.net())) * 2, compact ? 0 : tr.getWidth(detail))) + 12;
		return new int[]{Math.max(w, 96), compact ? 32 : 42};
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{screenW - w - 6, Math.max(6, screenH / 2 - h / 2)};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		int[] size = size(tr, editing);
		Stats s = state.ledger.sessionStats();
		Paint.hud(ctx, 0, 0, size[0], size[1]);
		Paint.hudText(ctx, tr, "SESSION", 6, 5, Colors.muted());
		String clock = clock();
		Paint.hudText(ctx, tr, clock, size[0] - 6 - tr.getWidth(clock), 5, Colors.muted());
		if (hidden()) {
			Paint.hudText(ctx, tr, counts(s), 6, 17, Colors.text());
			return;
		}
		Paint.big(ctx, tr, Fmt.signed(s.net()), 6, 15, 2, Colors.profit(s.net()), true);
		if (!compact) {
			Paint.hudText(ctx, tr, counts(s) + " · " + Fmt.money(s.wagered()) + " bet", 6, 32, Colors.text());
		}
	}
}
