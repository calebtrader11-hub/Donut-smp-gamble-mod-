package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.Standing;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An auction ranking whose rows glide to their new place when someone overtakes, new bidders fade
 * in, and share bars grow. Used by the manager and the leaderboard HUD.
 */
final class RankingView {
	private static final class Row {
		final Anim y;
		final Anim alpha = new Anim(0);
		final Anim share;
		int lastRank;
		int frame;
		long flashAt = Long.MIN_VALUE / 2;

		Row(float y, float share) {
			this.y = new Anim(y);
			this.share = new Anim(share);
		}
	}

	private final Map<String, Row> rows = new HashMap<>();
	/** HUD mode: text with a shadow, readable over the world. */
	private final boolean hud;
	private Object source;
	private int frame;

	RankingView() {
		this(false);
	}

	RankingView(boolean hud) {
		this.hud = hud;
	}

	/** Forget positions when switching to another auction, so rows don't fly in from the old one. */
	void reset(Object auction) {
		if (auction != source) {
			rows.clear();
			source = auction;
		}
	}

	/**
	 * Draws rows {@code first..first+visible} at {@code rowH} spacing.
	 * @param amounts false hides money (Streamer Mode); shares and names stay
	 */
	void draw(DrawContext ctx, TextRenderer tr, List<Standing> r, int x, int y, int w, int rowH, int first, int visible,
			boolean amounts, boolean shares) {
		long now = System.currentTimeMillis();
		frame++;
		int end = Math.min(r.size(), first + visible);
		for (int i = 0; i < r.size(); i++) {
			Standing s = r.get(i);
			String key = s.player().toLowerCase(Locale.ROOT);
			float targetY = (i - first) * rowH;
			Row row = rows.get(key);
			if (row == null) {
				row = new Row(targetY, (float) s.share());
				row.lastRank = i;
				rows.put(key, row);
			} else if (i < row.lastRank) {
				row.flashAt = now; // moved up
			}
			row.lastRank = i;
			row.frame = frame;
			float ry = row.y.to(targetY, 14f);
			if (i < first || i >= end) {
				continue;
			}
			float a = row.alpha.to(1f, 10f);
			float share = row.share.to((float) s.share(), 10f);
			drawRow(ctx, tr, i, s, x, y + Math.round(ry), w, rowH, a, share, now - row.flashAt, amounts, shares);
		}
		int current = frame;
		rows.values().removeIf(row -> row.frame != current);
	}

	private void drawRow(DrawContext ctx, TextRenderer tr, int i, Standing s, int x, int y, int w, int h, float a,
			float share, long sinceFlash, boolean amounts, boolean shares) {
		int medal = i == 0 ? Colors.GOLD : i == 1 ? Colors.GREY : i == 2 ? 0xFFD08A5B : Colors.INK_FAINT;
		if (sinceFlash < 900) {
			float f = 1f - sinceFlash / 900f;
			Paint.round(ctx, x, y, w, h - 1, 2, Colors.fade(Colors.alpha(Colors.GREEN, 0x40), f * a));
		}
		int barW = Math.round((w - 4) * Math.max(0f, Math.min(1f, share)));
		if (barW > 1) {
			Paint.round(ctx, x + 2, y + 1, barW, h - 3, 2, Colors.fade(Colors.alpha(Colors.accent(), i == 0 ? 0x30 : 0x1A), a));
		}
		int ty = y + (h - 8) / 2;
		if (i < 3) {
			Paint.round(ctx, x + 5, ty - 1, 13, 10, 3, Colors.fade(Colors.alpha(medal, 0x38), a));
		}
		text(ctx, tr, Integer.toString(i + 1), x + 12 - tr.getWidth(Integer.toString(i + 1)) / 2, ty, Colors.fade(medal, a));
		int nameColor = i == 0 ? Colors.GOLD : Colors.INK;
		int right = x + w - 6;
		if (shares) {
			String pct = Math.round(s.share() * 100) + "%";
			text(ctx, tr, pct, right - tr.getWidth(pct), ty, Colors.fade(Colors.INK_DIM, a));
			right -= 32;
		}
		if (amounts) {
			String money = Fmt.money(s.total());
			text(ctx, tr, money, right - tr.getWidth(money), ty, Colors.fade(Colors.INK, a));
			right -= tr.getWidth(money) + 8;
		}
		text(ctx, tr, Paint.fit(tr, s.player(), right - (x + 24)), x + 24, ty, Colors.fade(nameColor, a));
	}

	private void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		if (hud) {
			Paint.hudText(ctx, tr, s, x, y, color);
		} else {
			Paint.text(ctx, tr, s, x, y, color);
		}
	}
}
