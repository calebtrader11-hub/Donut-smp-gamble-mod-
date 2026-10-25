package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.ScaledScreen;
import com.donutgamble.client.ui.TextBox;
import com.donutgamble.client.ui.Toggle;
import com.donutgamble.core.AmountParser;
import com.donutgamble.core.AuctionSetup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Configure everything before an auction starts: the prize (any item, picked from the item
 * browser or your inventory), quantity, title, description, length, prize cost, timer rules and
 * what viewers see about bidders.
 */
final class AuctionSetupScreen extends ScaledScreen {
	/** What's been filled in; kept while the game runs so a trip to the item picker loses nothing. */
	static final class Draft {
		ItemStack prize = ItemStack.EMPTY;
		/** Picked from the inventory: keep its components (enchantments, custom name…). */
		boolean exact;
		String quantity = "1";
		String title = "";
		String description = "";
		String duration = "";
		String cost = "";
		Boolean autoEnd;
		String antiSnipe = "";
		int showTop = 5;
		boolean showAmounts = true;
		boolean showShare = true;
	}

	static final Draft DRAFT = new Draft();
	private static final int[] TOP_OPTIONS = {3, 5, 10};
	private static final int LEFT_W = 150;

	private final GambleState state;
	private final Screen parent;
	private final Anim fade = new Anim(0);
	private int wx, wy, ww, wh, rx, rw;

	AuctionSetupScreen(GambleState state, Screen parent) {
		super(Text.literal("New auction"), 480, 270);
		this.state = state;
		this.parent = parent;
		if (DRAFT.duration.isEmpty()) {
			DRAFT.duration = ManagerScreen.formatDuration(state.config.auctionDefaultDurationSec);
		}
		if (DRAFT.autoEnd == null) {
			DRAFT.autoEnd = state.config.auctionAutoEnd;
		}
		if (DRAFT.antiSnipe.isEmpty()) {
			DRAFT.antiSnipe = Integer.toString(state.config.auctionAntiSnipeSec);
		}
	}

	/** Opens the setup with this item already chosen (e.g. a recent prize). */
	static AuctionSetupScreen withItem(GambleState state, Screen parent, ItemStack stack) {
		DRAFT.prize = stack;
		DRAFT.exact = false;
		return new AuctionSetupScreen(state, parent);
	}

	@Override
	protected void layout() {
		ww = Math.min(vw - 16, 500);
		wh = Math.min(vh - 12, 296);
		wx = (vw - ww) / 2;
		wy = (vh - wh) / 2;
		rx = wx + 12 + LEFT_W + 12;
		rw = wx + ww - 12 - rx;
		int lx = wx + 12;
		int top = wy + 34;

		// Prize
		int by = top + 92;
		Button choose = Button.fit(lx + 8, by, DRAFT.prize.isEmpty() ? "Choose item" : "Change", Button.Variant.PRIMARY,
			() -> openPicker());
		addDrawableChild(choose);
		Button clear = Button.fit(choose.getRight() + 4, by, "No item", Button.Variant.GHOST, () -> {
			DRAFT.prize = ItemStack.EMPTY;
			DRAFT.exact = false;
			clearAndInit();
		});
		clear.active = !DRAFT.prize.isEmpty();
		addDrawableChild(clear);
		field(lx + LEFT_W - 8 - 44, by + 24, 44, "1", DRAFT.quantity, s -> DRAFT.quantity = s);

		// Details
		int y = top + 12;
		field(rx + 70, y, rw - 70, DRAFT.prize.isEmpty() ? "e.g. Spawner giveaway" : ItemIcons.name(DRAFT.prize), DRAFT.title, s -> DRAFT.title = s);
		y += 20;
		field(rx + 70, y, rw - 70, "Optional, shown under the title", DRAFT.description, s -> DRAFT.description = s);
		y += 20;
		field(rx + 70, y, 56, "5m", DRAFT.duration, s -> DRAFT.duration = s);
		field(rx + rw - 64, y, 64, "optional", DRAFT.cost, s -> DRAFT.cost = s);

		// Timer
		y += 36;
		addDrawableChild(new Toggle(rx + 70, y + 3, "End automatically", () -> DRAFT.autoEnd, () -> DRAFT.autoEnd = !DRAFT.autoEnd));
		field(rx + rw - 40, y, 40, "0", DRAFT.antiSnipe, s -> DRAFT.antiSnipe = s);

		// Viewers see
		y += 36;
		int cx = rx + 70;
		for (int top3 : TOP_OPTIONS) {
			Button chip = new Button(cx, y, 26, Integer.toString(top3), Button.Variant.SECONDARY, () -> {
				DRAFT.showTop = top3;
				clearAndInit();
			}).selected(DRAFT.showTop == top3);
			addDrawableChild(chip);
			cx += 29;
		}
		y += 20;
		addDrawableChild(new Toggle(rx + 70, y + 3, "Amounts", () -> DRAFT.showAmounts, () -> DRAFT.showAmounts = !DRAFT.showAmounts));
		addDrawableChild(new Toggle(rx + rw - Toggle.W, y + 3, "Share", () -> DRAFT.showShare, () -> DRAFT.showShare = !DRAFT.showShare));

		// Footer
		int fy = wy + wh - 26;
		Button start = Button.fit(0, fy, "Start auction", Button.Variant.PRIMARY, this::start);
		start.setX(wx + ww - 12 - start.getWidth());
		start.active = !state.auctions.isLive();
		addDrawableChild(start);
		Button cancel = Button.fit(0, fy, "Cancel", Button.Variant.GHOST, this::close);
		cancel.setX(start.getX() - 4 - cancel.getWidth());
		addDrawableChild(cancel);
	}

	private void field(int x, int y, int w, String placeholder, String value, java.util.function.Consumer<String> onChange) {
		TextBox t = new TextBox(textRenderer, x, y, w, placeholder);
		t.setText(value);
		t.setChangedListener(onChange);
		addDrawableChild(t);
	}

	private void openPicker() {
		MinecraftClient.getInstance().setScreen(new ItemPickerScreen(state, this, picked -> {
			DRAFT.prize = picked.stack();
			DRAFT.exact = picked.exact();
			if (picked.exact() && picked.stack().getCount() > 1) {
				DRAFT.quantity = Integer.toString(picked.stack().getCount());
			}
		}));
	}

	private AuctionSetup setup() {
		int qty;
		try {
			qty = Math.max(1, Integer.parseInt(DRAFT.quantity.trim()));
		} catch (NumberFormatException e) {
			qty = 1;
		}
		Integer snipe;
		try {
			snipe = Math.max(0, Integer.parseInt(DRAFT.antiSnipe.trim()));
		} catch (NumberFormatException e) {
			snipe = null;
		}
		boolean hasItem = !DRAFT.prize.isEmpty();
		String stackData = hasItem && DRAFT.exact ? ItemIcons.encode(DRAFT.prize.copyWithCount(qty)) : "";
		return new AuctionSetup(DRAFT.title, hasItem ? ItemIcons.id(DRAFT.prize) : "", hasItem ? ItemIcons.name(DRAFT.prize) : "", qty,
			DRAFT.description, stackData == null ? "" : stackData,
			ManagerScreen.parseDuration(DRAFT.duration, state.config.auctionDefaultDurationSec),
			AmountParser.parseOrZero(DRAFT.cost), DRAFT.autoEnd, snipe, DRAFT.showTop, DRAFT.showAmounts, DRAFT.showShare);
	}

	private void start() {
		AuctionSetup setup = setup();
		if (state.startAuction(setup)) {
			if (!DRAFT.prize.isEmpty()) {
				state.config.rememberItem(ItemIcons.id(DRAFT.prize));
			}
			DRAFT.title = "";
			DRAFT.description = "";
			DRAFT.cost = "";
			close();
		}
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if ((input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) && !state.auctions.isLive()) {
			start();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void drawBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		float t = Anim.ease(fade.to(1f, 14f));
		ctx.fill(0, 0, vw, vh, Colors.alpha(Colors.SCRIM, Math.round(0x8C * t)));
		Paint.window(ctx, wx, wy, ww, wh);
		Paint.round(ctx, wx + 12, wy + 10, 7, 7, 2, Colors.accent());
		Paint.text(ctx, textRenderer, "New auction", wx + 24, wy + 10, Colors.INK);
		Paint.text(ctx, textRenderer, "Set it up, then start", wx + 24 + textRenderer.getWidth("New auction") + 8, wy + 10, Colors.INK_FAINT);
		Paint.hline(ctx, wx + 1, wx + ww - 1, wy + 24, Colors.LINE);
		int top = wy + 34;

		// Prize card
		int lx = wx + 12;
		Paint.card(ctx, lx, top, LEFT_W, 140, 4, Colors.CARD, Colors.LINE);
		Paint.label(ctx, textRenderer, "Prize", lx + 8, top + 6);
		int box = 56;
		int bx = lx + (LEFT_W - box) / 2;
		int bY = top + 18;
		Paint.card(ctx, bx, bY, box, box, 6, Colors.INPUT, Colors.alpha(Colors.accent(), DRAFT.prize.isEmpty() ? 0x40 : 0xA0));
		if (DRAFT.prize.isEmpty()) {
			Paint.center(ctx, textRenderer, "?", bx + box / 2, bY + box / 2 - 4, Colors.INK_FAINT);
		} else {
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(bx + (box - 48) / 2f, bY + (box - 48) / 2f);
			ctx.getMatrices().scale(3f, 3f);
			ctx.drawItem(DRAFT.prize, 0, 0);
			ctx.getMatrices().popMatrix();
		}
		String name = DRAFT.prize.isEmpty() ? "No item (text only)" : ItemIcons.name(DRAFT.prize);
		Paint.center(ctx, textRenderer, Paint.fit(textRenderer, name, LEFT_W - 12), lx + LEFT_W / 2, bY + box + 4,
			DRAFT.prize.isEmpty() ? Colors.INK_FAINT : Colors.INK);
		if (DRAFT.exact) {
			Paint.center(ctx, textRenderer, "exact item from inventory", lx + LEFT_W / 2, bY + box + 14, Colors.GOLD);
		}
		Paint.text(ctx, textRenderer, "Quantity", lx + 8, top + 92 + 24 + 4, Colors.INK_DIM);

		// Details
		int y = top;
		Paint.label(ctx, textRenderer, "Details", rx, y);
		y += 12;
		Paint.text(ctx, textRenderer, "Title", rx, y + 4, Colors.INK_DIM);
		y += 20;
		Paint.text(ctx, textRenderer, "Description", rx, y + 4, Colors.INK_DIM);
		y += 20;
		Paint.text(ctx, textRenderer, "Length", rx, y + 4, Colors.INK_DIM);
		Paint.right(ctx, textRenderer, "Prize cost", rx + rw - 70, y + 4, Colors.INK_DIM);

		y += 24;
		Paint.label(ctx, textRenderer, "Timer", rx, y);
		y += 12;
		Paint.text(ctx, textRenderer, "Auto-end", rx, y + 4, Colors.INK_DIM);
		Paint.right(ctx, textRenderer, "Late bid adds (sec)", rx + rw - 46, y + 4, Colors.INK_DIM);

		y += 24;
		Paint.label(ctx, textRenderer, "Viewers see", rx, y);
		y += 12;
		Paint.text(ctx, textRenderer, "Top bidders", rx, y + 4, Colors.INK_DIM);
		y += 20;
		Paint.text(ctx, textRenderer, "Bid amounts", rx, y + 2, Colors.INK_DIM);
		Paint.right(ctx, textRenderer, "Share %", rx + rw - Toggle.W - 6, y + 2, Colors.INK_DIM);

		if (state.auctions.isLive()) {
			Paint.text(ctx, textRenderer, "An auction is already running. End it first.", wx + 12, wy + wh - 22, Colors.GOLD);
		} else {
			String length = Fmt.clock(ManagerScreen.parseDuration(DRAFT.duration, state.config.auctionDefaultDurationSec) * 1000L);
			Paint.text(ctx, textRenderer, "Runs for " + length + " · Enter to start", wx + 12, wy + wh - 22, Colors.INK_FAINT);
		}
	}
}
