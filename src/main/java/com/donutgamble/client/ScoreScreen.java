package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.TextBox;
import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Game;
import com.donutgamble.core.Outcome;
import com.donutgamble.core.ScoreJudge;
import com.donutgamble.core.ScoreOptions;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * The result window after W or L on Paper / Blackjack: pick the host's number and the viewer's,
 * and the numbers decide (higher wins, a tie goes to the house, blackjack bust loses). Nothing is
 * recorded until Confirm. Without numbers, Confirm records the letter that was clicked.
 * For an Ely hit it asks for the elytra price instead.
 */
final class ScoreScreen extends Screen {
	private static final int CHIP_GAP = 4;
	private static final int CHIP_H = 18;

	private final GambleState state;
	private final Bet bet;
	private final boolean elytra;
	/** W or L, used when no numbers are picked. */
	private final Outcome called;
	private final List<ScoreOptions.Option> options;
	private final Anim open = new Anim(0);
	private Integer host, viewer;
	private TextBox price;
	private int wx, wy, ww, wh, chipW, hostY, viewerY;

	private ScoreScreen(GambleState state, Bet bet, boolean elytra, Outcome called) {
		super(Text.literal(bet.player));
		this.state = state;
		this.bet = bet;
		this.elytra = elytra;
		this.called = called;
		this.options = ScoreOptions.forGame(bet.game);
	}

	/** W or L on Paper / Blackjack. */
	static ScoreScreen result(GambleState state, Bet bet, Outcome called) {
		return new ScoreScreen(state, bet, false, called);
	}

	/** W on Ely: confirm the elytra price, then the win is recorded. */
	static ScoreScreen elytraPrice(GambleState state, Bet bet) {
		return new ScoreScreen(state, bet, true, Outcome.PLAYER_WIN);
	}

	private String noun() {
		return bet.game == Game.BLACKJACK ? "HAND" : "PAPER";
	}

	@Override
	protected void init() {
		if (elytra) {
			ww = 210;
			wh = 104;
			wx = (width - ww) / 2;
			wy = (height - wh) / 2;
			price = new TextBox(textRenderer, wx + Paint.S6, wy + 52, ww - 2 * Paint.S6, "Elytra price, e.g. 180m");
			price.setMaxLength(14);
			price.setTextPredicate(s -> s.matches("[0-9.,kKmMbBtT$]*"));
			if (state.config.elytraPrice > 0) {
				price.setText(AmountParser.format(state.config.elytraPrice));
			}
			addDrawableChild(price);
			setInitialFocus(price);
			Button ok = footer(wy + wh - Paint.S6 - Button.HEIGHT, "Back", "Confirm win");
			ok.active = AmountParser.parseOrZero(price.getText()) > 0;
			price.setChangedListener(s -> ok.active = AmountParser.parseOrZero(s) > 0);
			return;
		}
		chipW = options.size() > 6 ? 22 : 34;
		int rowW = options.size() * (chipW + CHIP_GAP) - CHIP_GAP;
		ww = Math.max(220, rowW + 2 * Paint.S6);
		wh = 180;
		wx = (width - ww) / 2;
		wy = (height - wh) / 2;
		hostY = wy + 46;
		viewerY = wy + 84;
		chipRow(hostY + 12, true);
		chipRow(viewerY + 12, false);
		footer(wy + wh - Paint.S6 - Button.HEIGHT, "Cancel", "Confirm");
	}

	private void chipRow(int y, boolean isHost) {
		int rowW = options.size() * (chipW + CHIP_GAP) - CHIP_GAP;
		int x = wx + (ww - rowW) / 2;
		for (ScoreOptions.Option o : options) {
			Integer current = isHost ? host : viewer;
			Button chip = new Button(x, y, chipW, o.label(), Button.Variant.SECONDARY, () -> {
				if (isHost) {
					host = o.value();
				} else {
					viewer = o.value();
				}
				clearAndInit();
			}).selected(current != null && current == o.value());
			chip.setHeight(CHIP_H);
			addDrawableChild(chip);
			x += chipW + CHIP_GAP;
		}
	}

	private Button footer(int y, String cancel, String confirm) {
		int bw = (ww - 2 * Paint.S6 - 8) / 2;
		Button back = new Button(wx + Paint.S6, y, bw, cancel, Button.Variant.SECONDARY, this::close);
		Button ok = new Button(wx + ww - Paint.S6 - bw, y, bw, confirm, Button.Variant.PRIMARY, this::save);
		addDrawableChild(back);
		addDrawableChild(ok);
		return ok;
	}

	private boolean picked() {
		return host != null && viewer != null;
	}

	/** What Confirm will record: from the numbers if both are picked, else the letter clicked. */
	private Outcome verdict() {
		return picked() ? ScoreJudge.judge(bet.game, viewer, host) : called;
	}

	private void save() {
		if (elytra) {
			long p = AmountParser.parseOrZero(price.getText());
			if (p <= 0) {
				return;
			}
			state.confirmElytraWin(bet, p);
		} else {
			state.recordResult(bet, viewer, host, called);
		}
		close();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.key();
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			save();
			return true;
		}
		// Paper: type the two numbers, host first, then the viewer.
		if (!elytra && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && options.size() == 9) {
			int v = key - GLFW.GLFW_KEY_0;
			if (host == null || viewer != null) {
				host = v;
				viewer = null;
			} else {
				viewer = v;
			}
			clearAndInit();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		float t = Anim.ease(open.to(1f, 14f));
		ctx.fill(0, 0, width, height, Colors.alpha(Colors.SCRIM, Math.round(0x8C * t)));
		Paint.window(ctx, wx, wy, ww, wh);

		if (elytra) {
			Paint.text(ctx, textRenderer, Paint.fit(textRenderer, bet.player, ww - 2 * Paint.S6), wx + Paint.S6, wy + Paint.S6, Colors.INK);
			Paint.text(ctx, textRenderer, "Ely Arrows hit · " + Fmt.money(bet.amount) + " bet", wx + Paint.S6, wy + Paint.S6 + 12, Colors.INK_DIM);
			Paint.hline(ctx, wx + 1, wx + ww - 1, wy + 34, Colors.LINE);
			Paint.label(ctx, textRenderer, "What does the elytra cost?", wx + Paint.S6, wy + 41);
			return;
		}
		int cx = wx + ww / 2;
		Paint.center(ctx, textRenderer, bet.game.displayName.toUpperCase(Locale.ROOT) + " RESULT", cx, wy + 9, Colors.accent());
		Paint.center(ctx, textRenderer, Paint.fit(textRenderer, bet.player + " · " + Fmt.money(bet.amount), ww - 20), cx, wy + 22, Colors.INK_DIM);

		Paint.hline(ctx, wx + Paint.S6, wx + ww - Paint.S6, hostY - 6, Colors.LINE);
		Paint.text(ctx, textRenderer, "HOST " + noun() + " · TIE = LOSE", wx + Paint.S6, hostY, Colors.INK);
		Paint.hline(ctx, wx + Paint.S6, wx + ww - Paint.S6, viewerY - 6, Colors.LINE);
		Paint.text(ctx, textRenderer, "VIEWER " + noun(), wx + Paint.S6, viewerY, Colors.INK);

		int sy = viewerY + 38;
		if (picked()) {
			String nums = "Host " + ScoreOptions.label(bet.game, host) + " · Viewer " + ScoreOptions.label(bet.game, viewer);
			Paint.center(ctx, textRenderer, nums, cx, sy, Colors.INK_DIM);
			boolean tie = ScoreJudge.isTie(bet.game, viewer, host);
			boolean won = verdict() == Outcome.PLAYER_WIN;
			String v = tie ? "TIE · PLAYER LOST" : won ? "PLAYER WON" : "PLAYER LOST";
			Paint.center(ctx, textRenderer, v, cx, sy + 12, won ? Colors.GOLD : Colors.GREEN);
		} else {
			Paint.center(ctx, textRenderer, "Choose one number in each row", cx, sy, Colors.INK_DIM);
			String v = "RESULT NOT SELECTED · Confirm = " + (called == Outcome.PLAYER_WIN ? "win" : "loss");
			Paint.center(ctx, textRenderer, Paint.fit(textRenderer, v, ww - 16), cx, sy + 12, Colors.INK_FAINT);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
