package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Outcome;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Recent game results for viewers: who played what and how it went. Outcomes and scores are
 * public on stream anyway; no profit is shown.
 */
final class ActivityHud extends HudElement {
	private static final int W = 170;
	private static final int ROW = 11;

	private final GambleState state;

	ActivityHud(GambleState state) {
		super("activity", "Game activity");
		this.state = state;
	}

	@Override
	boolean visibleByDefault() {
		return false;
	}

	private int limit() {
		return compact ? 3 : 6;
	}

	private List<Bet> rows() {
		return state.ledger.recentResults(state.ledger.sessionStart, limit());
	}

	@Override
	boolean hasContent() {
		return !rows().isEmpty();
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		int n = Math.max(1, rows().size());
		return new int[]{W, 18 + n * ROW + 3};
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{6, Math.max(6, screenH / 2 + 10)};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		int[] size = size(tr, editing);
		Paint.hud(ctx, 0, 0, size[0], size[1]);
		Paint.hudText(ctx, tr, "RECENT GAMES", 6, 6, Colors.muted());
		List<Bet> rows = rows();
		int y = 18;
		if (rows.isEmpty()) {
			Paint.hudText(ctx, tr, "Results show here", 6, y, Colors.muted());
			return;
		}
		long now = System.currentTimeMillis();
		for (Bet b : rows) {
			boolean win = b.outcome == Outcome.PLAYER_WIN;
			String mark = win ? "W" : b.tie ? "T" : "L";
			int color = win ? Colors.GREEN : b.tie ? Colors.GOLD : Colors.RED;
			// The newest result glows briefly.
			long age = now - Math.max(b.resolvedAt, b.timestamp);
			if (age < 1500 && b.resolvedAt > 0) {
				Paint.round(ctx, 3, y - 2, W - 6, ROW, 2, Colors.fade(Colors.alpha(color, 0x40), 1f - age / 1500f));
			}
			Paint.round(ctx, 5, y - 1, 9, 9, 2, Colors.alpha(color, 0x40));
			Paint.hudText(ctx, tr, mark, 7, y, color);
			String game = QueueHud.Tab.of(b.game).label;
			String score = b.scoreLabel();
			if (score.length() <= 1) {
				score = "";
			}
			String right = score.isEmpty() ? game : game + " " + score;
			int rw = tr.getWidth(right);
			Paint.hudText(ctx, tr, Paint.fit(tr, b.player, W - 30 - rw), 18, y, Colors.text());
			Paint.hudText(ctx, tr, right, W - 6 - rw, y, Colors.muted());
			y += ROW;
		}
	}
}
