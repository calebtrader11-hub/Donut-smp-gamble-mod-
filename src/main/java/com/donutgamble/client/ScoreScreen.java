package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.TextBox;
import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Outcome;
import com.donutgamble.core.ScoreJudge;
import com.donutgamble.core.ScoreOptions;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The small window after W or L: pick the round's numbers (Paper 1-9, Blackjack 17-21 or Bust).
 * The result is already recorded; the numbers are optional. For an Ely hit it asks for the
 * elytra price instead, and records the win on confirm.
 */
final class ScoreScreen extends Screen {
	private static final int CHIP_GAP = 3;

	private final GambleState state;
	private final Bet bet;
	private final boolean elytra;
	private final List<ScoreOptions.Option> options;
	private final Anim open = new Anim(0);
	private Integer them, me;
	private TextBox price;
	private int wx, wy, ww, wh, chipW;

	private ScoreScreen(GambleState state, Bet bet, boolean elytra) {
		super(Text.literal(bet.player));
		this.state = state;
		this.bet = bet;
		this.elytra = elytra;
		this.options = ScoreOptions.forGame(bet.game);
	}

	/** After W or L on Paper / Blackjack. */
	static ScoreScreen score(GambleState state, Bet bet) {
		return new ScoreScreen(state, bet, false);
	}

	/** W on Ely: confirm the elytra price, then the win is recorded. */
	static ScoreScreen elytraPrice(GambleState state, Bet bet) {
		return new ScoreScreen(state, bet, true);
	}

	@Override
	protected void init() {
		chipW = options.size() > 6 ? 18 : 28;
		int rowW = options.isEmpty() ? 0 : options.size() * (chipW + CHIP_GAP) - CHIP_GAP;
		ww = Math.max(210, 44 + rowW + Paint.S6);
		wh = elytra ? 104 : 126;
		wx = (width - ww) / 2;
		wy = (height - wh) / 2;

		int footY = wy + wh - Paint.S6 - Button.HEIGHT;
		if (elytra) {
			price = new TextBox(textRenderer, wx + Paint.S6, wy + 52, ww - 2 * Paint.S6, "Elytra price, e.g. 180m");
			price.setMaxLength(14);
			price.setTextPredicate(s -> s.matches("[0-9.,kKmMbBtT$]*"));
			if (state.config.elytraPrice > 0) {
				price.setText(AmountParser.format(state.config.elytraPrice));
			}
			addDrawableChild(price);
			setInitialFocus(price);
			Button ok = footer(footY, "Back", "Confirm win", AmountParser.parseOrZero(price.getText()) > 0);
			price.setChangedListener(s -> ok.active = AmountParser.parseOrZero(s) > 0);
			return;
		}
		chipRow(wy + 44, true);
		chipRow(wy + 64, false);
		footer(footY, "Skip", "Save", them != null && me != null);
	}

	private void chipRow(int y, boolean theirs) {
		int x = wx + 44;
		for (ScoreOptions.Option o : options) {
			Integer current = theirs ? them : me;
			Button chip = new Button(x, y, chipW, o.label(), Button.Variant.SECONDARY, () -> {
				if (theirs) {
					them = o.value();
				} else {
					me = o.value();
				}
				clearAndInit();
			}).selected(current != null && current == o.value());
			addDrawableChild(chip);
			x += chipW + CHIP_GAP;
		}
	}

	private Button footer(int y, String cancel, String confirm, boolean canConfirm) {
		Button ok = Button.fit(0, y, confirm, Button.Variant.PRIMARY, this::save);
		ok.setX(wx + ww - Paint.S6 - ok.getWidth());
		ok.active = canConfirm;
		Button back = Button.fit(0, y, cancel, Button.Variant.GHOST, this::close);
		back.setX(ok.getX() - 4 - back.getWidth());
		addDrawableChild(back);
		addDrawableChild(ok);
		return ok;
	}

	private void save() {
		if (elytra) {
			long p = AmountParser.parseOrZero(price.getText());
			if (p <= 0) {
				return;
			}
			state.confirmElytraWin(bet, p);
		} else if (them != null && me != null) {
			state.setScore(bet, them, me);
		} else {
			return;
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
		// Paper: type the two digits (theirs, then yours).
		if (!elytra && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && options.size() == 9) {
			int v = key - GLFW.GLFW_KEY_0;
			if (them == null) {
				them = v;
			} else {
				me = v;
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

		boolean won = elytra || bet.outcome == Outcome.PLAYER_WIN;
		String badge = elytra ? "HIT" : won ? "WON" : "LOST";
		int badgeColor = won ? Colors.RED : Colors.GREEN;
		int bw = textRenderer.getWidth(badge) + 10;
		Paint.pill(ctx, wx + ww - Paint.S6 - bw, wy + Paint.S6 - 1, bw, 12, Colors.alpha(badgeColor, 0x40));
		Paint.center(ctx, textRenderer, badge, wx + ww - Paint.S6 - bw / 2, wy + Paint.S6 + 1, badgeColor);

		Paint.text(ctx, textRenderer, Paint.fit(textRenderer, bet.player, ww - 2 * Paint.S6 - bw - 6), wx + Paint.S6, wy + Paint.S6, Colors.INK);
		Paint.text(ctx, textRenderer, bet.game.displayName + " · " + Fmt.money(bet.amount) + " bet", wx + Paint.S6, wy + Paint.S6 + 12, Colors.INK_DIM);
		Paint.hline(ctx, wx + 1, wx + ww - 1, wy + 34, Colors.LINE);

		if (elytra) {
			Paint.label(ctx, textRenderer, "What does the elytra cost?", wx + Paint.S6, wy + 41);
			return;
		}
		Paint.text(ctx, textRenderer, "Them", wx + Paint.S6, wy + 48, Colors.INK_DIM);
		Paint.text(ctx, textRenderer, "Me", wx + Paint.S6, wy + 68, Colors.INK_DIM);
		String hint;
		int hintColor = Colors.INK_FAINT;
		if (them != null && me != null) {
			Outcome scored = ScoreJudge.judge(bet.game, them, me);
			String score = ScoreOptions.label(bet.game, them) + " – " + ScoreOptions.label(bet.game, me);
			if (scored == bet.outcome) {
				hint = score + " · press Enter to save";
				hintColor = Colors.INK_DIM;
			} else {
				hint = score + " would be a " + (scored == Outcome.PLAYER_WIN ? "win" : "loss") + " for them";
				hintColor = Colors.GOLD;
			}
		} else {
			hint = options.size() == 9 ? "Pick both, or type the two numbers" : "Pick both numbers (optional)";
		}
		Paint.text(ctx, textRenderer, hint, wx + Paint.S6, wy + 86, hintColor);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
