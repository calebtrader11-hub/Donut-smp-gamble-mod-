package com.donutgamble.client;

import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Regions;
import com.donutgamble.core.Stats;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Session profit at a glance (off by default; turn it on in the HUD editor). */
final class ProfitHud extends HudElement {
	private final GambleState state;

	ProfitHud(GambleState state) {
		super("profit", "Session profit");
		this.state = state;
	}

	@Override
	boolean visibleByDefault() {
		return false;
	}

	@Override
	int[] size(TextRenderer tr, boolean editing) {
		Stats s = state.ledger.sessionStats();
		int w = Math.max(tr.getWidth(Fmt.signed(s.net())) * 2, tr.getWidth(detail(s))) + 12;
		return new int[]{Math.max(w, 90), 40};
	}

	private static String detail(Stats s) {
		return "W " + s.playerWins() + " · L " + s.playerLosses() + " · T " + s.ties() + " · " + Fmt.money(s.wagered()) + " bet";
	}

	@Override
	int[] defaultPosition(int screenW, int screenH, int w, int h) {
		return new int[]{screenW - w - 6, 100};
	}

	@Override
	void draw(DrawContext ctx, TextRenderer tr, Regions regions, double mouseX, double mouseY, boolean editing) {
		int[] size = size(tr, editing);
		Stats s = state.ledger.sessionStats();
		Paint.hud(ctx, 0, 0, size[0], size[1]);
		Paint.hudText(ctx, tr, "SESSION", 6, 5, Colors.muted());
		Paint.big(ctx, tr, Fmt.signed(s.net()), 6, 14, 2, Colors.profit(s.net()), true);
		Paint.hudText(ctx, tr, detail(s), 6, 30, Colors.text());
	}
}
