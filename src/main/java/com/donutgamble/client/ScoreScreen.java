package com.donutgamble.client;

import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Bet;
import com.donutgamble.core.Outcome;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Small box that pops up after a letter is clicked: the round's numbers, or the elytra price. */
final class ScoreScreen extends Screen {
	private static final int W = 150;
	private static final int H = 62;

	private final GambleState state;
	private final Bet bet;
	private final String heading;
	private final boolean elytra;
	private TextFieldWidget them, me, price;

	private ScoreScreen(GambleState state, Bet bet, String heading, boolean elytra) {
		super(Text.literal(heading));
		this.state = state;
		this.bet = bet;
		this.heading = heading;
		this.elytra = elytra;
	}

	/** Result already recorded; this just asks for the numbers (optional). */
	static ScoreScreen score(GambleState state, Bet bet, String result) {
		return new ScoreScreen(state, bet, bet.player + " " + result, false);
	}

	/** An Ely win: confirm what the elytra costs before it's counted. */
	static ScoreScreen elytraPrice(GambleState state, Bet bet) {
		return new ScoreScreen(state, bet, bet.player + " hit", true);
	}

	@Override
	protected void init() {
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		if (elytra) {
			price = new TextFieldWidget(textRenderer, x + 70, y + 20, 72, 14, Text.literal("Elytra price"));
			price.setMaxLength(12);
			price.setTextPredicate(s -> s.matches("[0-9.,kKmMbB$]*"));
			if (state.config.elytraPrice > 0) {
				price.setText(AmountParser.format(state.config.elytraPrice));
			}
			addDrawableChild(price);
			setInitialFocus(price);
		} else {
			them = scoreField(x + 34, y + 20, "Their score");
			me = scoreField(x + 96, y + 20, "My score");
			setInitialFocus(them);
		}
		addDrawableChild(new FlatButton(x + W - 70, y + H - 18, 30, 12, "OK", Colors.GREEN, this::save));
		addDrawableChild(new FlatButton(x + W - 36, y + H - 18, 30, 12, elytra ? "Back" : "Skip", Colors.GREY, this::close));
	}

	private TextFieldWidget scoreField(int x, int y, String label) {
		TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 22, 14, Text.literal(label));
		f.setMaxLength(2);
		f.setTextPredicate(s -> s.matches("\\d{0,2}"));
		addDrawableChild(f);
		return f;
	}

	private void save() {
		if (elytra) {
			long p = AmountParser.parseOrZero(price.getText());
			if (p <= 0) {
				return;
			}
			state.setElytraPrice(p);
			state.resolve(bet, Outcome.PLAYER_WIN, null, null);
		} else if (!them.getText().isEmpty() && !me.getText().isEmpty()) {
			state.setScore(bet, Integer.parseInt(them.getText()), Integer.parseInt(me.getText()));
		}
		close();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
			save();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		Draw.box(ctx, x, y, x + W, y + H, Colors.BOX_BG);
		ctx.drawTextWithShadow(textRenderer, heading, x + 6, y + 6, Colors.PINK);
		if (elytra) {
			ctx.drawTextWithShadow(textRenderer, "Elytra price", x + 6, y + 23, Colors.MUTED);
		} else {
			ctx.drawTextWithShadow(textRenderer, "Them", x + 6, y + 23, Colors.MUTED);
			ctx.drawTextWithShadow(textRenderer, "Me", x + 78, y + 23, Colors.MUTED);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
