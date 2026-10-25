package com.donutgamble.client;

import com.donutgamble.client.ui.Anim;
import com.donutgamble.client.ui.AppScreen;
import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Dropdown;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.client.ui.Segmented;
import com.donutgamble.client.ui.Slider;
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

import java.util.List;

/**
 * Configure everything before an auction starts: the prize (any item, picked from the item
 * browser or your inventory), quantity, title, description, length, prize cost, timer rules, what
 * viewers see, bid alerts and which HUD parts show. A preview shows the result as you go.
 */
final class AuctionSetupScreen extends AppScreen {
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
		String bidNotify;
		boolean hudCard = true;
		boolean hudLeaderboard = true;
	}

	static final Draft DRAFT = new Draft();
	private static final List<Integer> TOP_OPTIONS = List.of(3, 5, 10);
	private static final int LEFT_W = 150;
	private static final int ROW = 20;
	private static final int SECTION = 13;

	private final GambleState state;
	private final Screen parent;
	private final Anim fade = new Anim(0);
	private int wx, wy, ww, wh, rx, rw, top;
	private int detailsY, timerY, viewersY, broadcastY;

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
		if (DRAFT.bidNotify == null) {
			DRAFT.bidNotify = state.config.auctionBidNotify;
		}
	}

	/** Opens the setup with this item already chosen (e.g. a recent prize). */
	static AuctionSetupScreen withItem(GambleState state, Screen parent, ItemStack stack) {
		DRAFT.prize = stack;
		DRAFT.exact = false;
		return new AuctionSetupScreen(state, parent);
	}

	private int durationSec() {
		return ManagerScreen.parseDuration(DRAFT.duration, state.config.auctionDefaultDurationSec);
	}

	private int antiSnipe() {
		try {
			return Math.max(0, Integer.parseInt(DRAFT.antiSnipe.trim()));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	@Override
	protected void layout() {
		ww = Math.min(vw - 16, 520);
		wh = Math.min(vh - 12, 300);
		wx = (vw - ww) / 2;
		wy = (vh - wh) / 2;
		rx = wx + 12 + LEFT_W + 12;
		rw = wx + ww - 12 - rx;
		top = wy + 34;
		int lx = wx + 12;
		int fieldX = rx + 66;
		int fieldW = rw - 66;

		// Prize
		int by = top + 88;
		Button choose = Button.fit(lx + 8, by, DRAFT.prize.isEmpty() ? "Choose item" : "Change", Button.Variant.PRIMARY, this::openPicker);
		addDrawableChild(choose);
		Button clear = Button.fit(choose.getRight() + 4, by, "No item", Button.Variant.GHOST, () -> {
			DRAFT.prize = ItemStack.EMPTY;
			DRAFT.exact = false;
			clearAndInit();
		});
		clear.active = !DRAFT.prize.isEmpty();
		addDrawableChild(clear);
		field(lx + LEFT_W - 8 - 44, by + 21, 44, "1", DRAFT.quantity, s -> DRAFT.quantity = s);

		// Details
		detailsY = top;
		int y = detailsY + SECTION;
		field(fieldX, y, fieldW, DRAFT.prize.isEmpty() ? "e.g. Spawner giveaway" : ItemIcons.name(DRAFT.prize), DRAFT.title, s -> DRAFT.title = s);
		y += ROW;
		field(fieldX, y, fieldW, "Optional, shown under the title", DRAFT.description, s -> DRAFT.description = s);
		y += ROW;
		int costW = 58;
		int sliderW = fieldW - costW - 8 - 34;
		addDrawableChild(new Slider(fieldX, y + 1, sliderW, 30, 3600, 30, durationSec(), ManagerScreen::formatDuration,
			v -> DRAFT.duration = ManagerScreen.formatDuration(v)));
		field(rx + rw - costW, y, costW, "optional", DRAFT.cost, s -> DRAFT.cost = s);

		// Timer
		timerY = y + ROW + 4;
		y = timerY + SECTION;
		addDrawableChild(new Segmented(fieldX, y, 100, List.of("Auto end", "Manual"), () -> DRAFT.autoEnd ? 0 : 1,
			i -> DRAFT.autoEnd = i == 0));
		int snipeW = rx + rw - (fieldX + 108);
		addDrawableChild(new Slider(rx + rw - snipeW, y + 1, snipeW, 0, 120, 5, antiSnipe(), v -> v == 0 ? "Off" : "+" + v + "s",
			v -> DRAFT.antiSnipe = Integer.toString(v)));

		// Viewers see
		viewersY = y + ROW + 4;
		y = viewersY + SECTION;
		addDrawableChild(new Dropdown(this, fieldX, y, 54, List.of("Top 3", "Top 5", "Top 10"),
			() -> Math.max(0, TOP_OPTIONS.indexOf(DRAFT.showTop)), i -> DRAFT.showTop = TOP_OPTIONS.get(i)));
		addDrawableChild(new Toggle(fieldX + 54 + 12 + textRenderer.getWidth("Amounts") + 4, y + 3, "Amounts",
			() -> DRAFT.showAmounts, () -> DRAFT.showAmounts = !DRAFT.showAmounts));
		addDrawableChild(new Toggle(rx + rw - Toggle.W, y + 3, "Share", () -> DRAFT.showShare, () -> DRAFT.showShare = !DRAFT.showShare));

		// Broadcast
		broadcastY = y + ROW + 4;
		y = broadcastY + SECTION;
		addDrawableChild(new Dropdown(this, fieldX, y, 82, AuctionPage.BID_NOTIFY_LABELS,
			() -> Math.max(0, AuctionPage.BID_NOTIFY.indexOf(DRAFT.bidNotify)), i -> DRAFT.bidNotify = AuctionPage.BID_NOTIFY.get(i)));
		addDrawableChild(new Toggle(cardToggleX(), y + 3, "Card on HUD", () -> DRAFT.hudCard, () -> DRAFT.hudCard = !DRAFT.hudCard));
		addDrawableChild(new Toggle(rx + rw - Toggle.W, y + 3, "Leaderboard on HUD", () -> DRAFT.hudLeaderboard,
			() -> DRAFT.hudLeaderboard = !DRAFT.hudLeaderboard));

		// Footer
		int fy = wy + wh - 26;
		Button start = Button.fit(0, fy, "Start auction", Button.Variant.PRIMARY, this::start).icon(Icons.PLAY);
		start.setX(wx + ww - 12 - start.getWidth());
		start.active = !state.auctions.isLive();
		addDrawableChild(start);
		Button cancel = Button.fit(0, fy, "Cancel", Button.Variant.GHOST, this::close);
		cancel.setX(start.getX() - 4 - cancel.getWidth());
		addDrawableChild(cancel);
	}

	private int cardToggleX() {
		return rx + rw - Toggle.W - 10 - textRenderer.getWidth("Board") - 10 - Toggle.W;
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

	private int quantity() {
		try {
			return Math.max(1, Integer.parseInt(DRAFT.quantity.trim()));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	private AuctionSetup setup() {
		int qty = quantity();
		boolean hasItem = !DRAFT.prize.isEmpty();
		String stackData = hasItem && DRAFT.exact ? ItemIcons.encode(DRAFT.prize.copyWithCount(qty)) : "";
		return new AuctionSetup(DRAFT.title, hasItem ? ItemIcons.id(DRAFT.prize) : "", hasItem ? ItemIcons.name(DRAFT.prize) : "", qty,
			DRAFT.description, stackData == null ? "" : stackData, durationSec(), AmountParser.parseOrZero(DRAFT.cost),
			DRAFT.autoEnd, antiSnipe(), DRAFT.showTop, DRAFT.showAmounts, DRAFT.showShare, DRAFT.bidNotify, DRAFT.hudCard, DRAFT.hudLeaderboard);
	}

	/** The title viewers will see: what was typed, or the item. */
	private String previewTitle() {
		if (!DRAFT.title.isBlank()) {
			return DRAFT.title.trim();
		}
		if (DRAFT.prize.isEmpty()) {
			return "Auction";
		}
		int q = quantity();
		return (q > 1 ? q + "× " : "") + ItemIcons.name(DRAFT.prize);
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
		if (!dialogOpen() && popup() == null && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)
			&& !state.auctions.isLive()) {
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

	private void section(DrawContext ctx, String title, Icons icon, int y) {
		icon.draw(ctx, rx, y, Colors.accent());
		Paint.label(ctx, textRenderer, title, rx + 12, y);
		Paint.hline(ctx, rx + 16 + textRenderer.getWidth(title.toUpperCase(java.util.Locale.ROOT)), rx + rw, y + 4, Colors.LINE);
	}

	private void label(DrawContext ctx, String s, int y) {
		Paint.text(ctx, textRenderer, s, rx, y + 4, Colors.INK_DIM);
	}

	@Override
	protected void drawBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		float t = Anim.ease(fade.to(1f, 14f));
		ctx.fill(0, 0, vw, vh, Colors.fade(Colors.SCRIM, t));
		Paint.window(ctx, wx, wy, ww, wh);
		Paint.iconChip(ctx, Icons.AUCTION, wx + 10, wy + 6, Colors.accent());
		Paint.text(ctx, textRenderer, "New auction", wx + 29, wy + 9, Colors.INK);
		Paint.text(ctx, textRenderer, "Set it up, then start", wx + 29 + textRenderer.getWidth("New auction") + 8, wy + 9, Colors.INK_FAINT);
		Paint.hline(ctx, wx + 1, wx + ww - 1, wy + 26, Colors.LINE);

		// Prize card
		int lx = wx + 12;
		Paint.panel(ctx, lx, top, LEFT_W, 128);
		Paint.label(ctx, textRenderer, "Prize", lx + 8, top + 6);
		int box = 52;
		int bx = lx + (LEFT_W - box) / 2;
		int bY = top + 17;
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
			DRAFT.prize.isEmpty() ? Colors.INK_FAINT : DRAFT.exact ? Colors.GOLD : Colors.INK);
		Paint.text(ctx, textRenderer, "Quantity", lx + 8, top + 88 + 21 + 4, Colors.INK_DIM);

		drawPreview(ctx, lx, top + 134, LEFT_W, wy + wh - 32 - (top + 134));

		section(ctx, "Details", Icons.STAR, detailsY);
		label(ctx, "Title", detailsY + SECTION);
		label(ctx, "Description", detailsY + SECTION + ROW);
		label(ctx, "Length", detailsY + SECTION + 2 * ROW);
		Paint.right(ctx, textRenderer, "Cost", rx + rw - 58 - 5, detailsY + SECTION + 2 * ROW + 4, Colors.INK_DIM);

		section(ctx, "Timer · late bids add time", Icons.HISTORY, timerY);
		label(ctx, "At 0:00", timerY + SECTION);

		section(ctx, "Viewers see", Icons.EYE, viewersY);
		label(ctx, "Ranking", viewersY + SECTION);
		Paint.text(ctx, textRenderer, "Amounts", rx + 66 + 54 + 8, viewersY + SECTION + 4, Colors.INK_DIM);
		Paint.right(ctx, textRenderer, "Share %", rx + rw - Toggle.W - 5, viewersY + SECTION + 4, Colors.INK_DIM);

		section(ctx, "Broadcast", Icons.BELL, broadcastY);
		label(ctx, "Bid alerts", broadcastY + SECTION);
		Paint.right(ctx, textRenderer, "Card", cardToggleX() - 5, broadcastY + SECTION + 4, Colors.INK_DIM);
		Paint.right(ctx, textRenderer, "Board", rx + rw - Toggle.W - 5, broadcastY + SECTION + 4, Colors.INK_DIM);

		if (state.auctions.isLive()) {
			Paint.text(ctx, textRenderer, "An auction is already running. End it first.", wx + 12, wy + wh - 22, Colors.GOLD);
		} else {
			String length = Fmt.clock(durationSec() * 1000L);
			Paint.text(ctx, textRenderer, "Runs for " + length + " · Enter to start", wx + 12, wy + wh - 22, Colors.INK_FAINT);
		}
	}

	/** A small version of the HUD card as viewers will see it. */
	private void drawPreview(DrawContext ctx, int x, int y, int w, int h) {
		if (h < 30) {
			return;
		}
		Paint.label(ctx, textRenderer, "Preview", x + 2, y);
		int cy = y + 11;
		int ch = Math.min(h - 11, 44);
		Paint.card(ctx, x, cy, w, ch, Paint.RADIUS, Colors.HUD, DRAFT.hudCard ? Colors.alpha(Colors.accent(), 0xC0) : Colors.LINE);
		int tx = x + 6;
		if (!DRAFT.prize.isEmpty()) {
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(x + 4, cy + 3);
			ctx.getMatrices().scale(0.75f, 0.75f);
			ctx.drawItem(DRAFT.prize, 0, 0);
			ctx.getMatrices().popMatrix();
			tx = x + 20;
		}
		Paint.hudText(ctx, textRenderer, Paint.fit(textRenderer, previewTitle(), x + w - 6 - tx), tx, cy + 5, Colors.INK);
		if (!DRAFT.hudCard) {
			Paint.center(ctx, textRenderer, "Card hidden on HUD", x + w / 2, cy + ch - 11, Colors.GOLD);
		} else if (ch >= 36) {
			Paint.big(ctx, textRenderer, Fmt.clock(durationSec() * 1000L), x + 6, cy + 17, 2, Colors.INK, true);
			String topLine = "Top " + DRAFT.showTop + (DRAFT.showAmounts ? " · $" : "") + (DRAFT.showShare ? " · %" : "");
			Paint.right(ctx, textRenderer, topLine, x + w - 6, cy + 21, Colors.INK_DIM);
		}
	}
}
