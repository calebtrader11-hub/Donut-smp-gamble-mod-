package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Outcome;
import com.donutgamble.core.Stats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** The always-on profit card (top-left) and the toast banner (top-right). */
final class HudRenderer {
	private static final int WIDTH = 132;

	private final GambleState state;

	HudRenderer(GambleState state) {
		this.state = state;
	}

	void render(DrawContext ctx, RenderTickCounter tick) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden) {
			return;
		}
		TextRenderer tr = client.textRenderer;
		if (state.config.hudVisible) {
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(state.config.hudX, state.config.hudY);
			ctx.getMatrices().scale(state.config.hudScale, state.config.hudScale);
			renderCard(ctx, tr);
			ctx.getMatrices().popMatrix();
		}
		renderToast(ctx, tr, client.getWindow().getScaledWidth());
	}

	private void renderCard(DrawContext ctx, TextRenderer tr) {
		Stats s = state.ledger.sessionStats();
		int accent = state.config.accentColor;
		int owed = state.ledger.owed().size();
		int height = 62 + s.recent().size() * 10 + (owed > 0 ? 11 : 0);

		Draw.roundedBordered(ctx, 0, 0, WIDTH, height, Colors.HUD_BG, Colors.BORDER);
		ctx.drawText(tr, "SESSION PROFIT · " + duration(System.currentTimeMillis() - state.ledger.sessionStart), 6, 6, Colors.MUTED, false);

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(6, 17);
		ctx.getMatrices().scale(2f, 2f);
		ctx.drawText(tr, AmountParser.formatSigned(s.net()), 0, 0, Colors.profit(s.net(), accent), true);
		ctx.getMatrices().popMatrix();

		ctx.drawText(tr, "Wagered " + AmountParser.format(s.wagered()), 6, 38, Colors.TEXT, false);
		String wl = "W " + s.playerWins() + " · L " + s.playerLosses();
		ctx.drawText(tr, wl, WIDTH - 6 - tr.getWidth(wl), 38, Colors.TEXT, false);
		ctx.fill(6, 51, WIDTH - 6, 52, Colors.BORDER);

		int y = 56;
		for (Bet b : s.recent()) {
			ctx.drawText(tr, tr.trimToWidth(b.player + " " + b.scoreLabel(), WIDTH - 50), 6, y, Colors.TEXT, false);
			String net = AmountParser.formatSigned(b.houseNet);
			int color = b.outcome == Outcome.PLAYER_LOSE ? accent : Colors.profit(b.houseNet, accent);
			ctx.drawText(tr, net, WIDTH - 6 - tr.getWidth(net), y, color, false);
			y += 10;
		}
		if (owed > 0) {
			ctx.drawText(tr, owed + (owed == 1 ? " payout owed" : " payouts owed"), 6, y + 1, Colors.GOLD, false);
		}
	}

	private void renderToast(DrawContext ctx, TextRenderer tr, int screenWidth) {
		GambleState.Toast toast = state.activeToast();
		if (toast == null) {
			return;
		}
		int w = tr.getWidth(toast.text()) + 14;
		int x = screenWidth - w - 6;
		Draw.roundedBordered(ctx, x, 6, x + w, 24, Colors.HUD_BG, Colors.OWED_BORDER);
		ctx.drawText(tr, toast.text(), x + 7, 11, toast.color(), false);
	}

	static String duration(long millis) {
		long sec = Math.max(0, millis / 1000);
		return String.format("%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
	}
}
