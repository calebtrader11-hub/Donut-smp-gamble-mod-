package com.donutgamble.client;

import com.donutgamble.client.ui.Button;
import com.donutgamble.client.ui.Icons;
import com.donutgamble.client.ui.Paint;
import com.donutgamble.core.AmountParser;
import com.donutgamble.core.Auction;
import com.donutgamble.core.Contribution;
import com.donutgamble.core.PrivacySettings.Surface;
import com.donutgamble.core.Standing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Runs the live auction (timer, controls, ranking, bid history, HUD switches) and shows results after. */
final class AuctionPage extends ManagerPage {
	static final List<String> BID_NOTIFY = List.of("ALL", "LEADER", "OFF");
	static final List<String> BID_NOTIFY_LABELS = List.of("Every bid", "New leader", "No alerts");
	private static final String KEY = "auction";
	private static int tab;
	private final RankingView ranking = new RankingView();

	AuctionPage(ManagerScreen m) {
		super(m);
	}

	@Override
	ManagerScreen.Section section() {
		return ManagerScreen.Section.AUCTION;
	}

	@Override
	String scrollKey(double mx, double my) {
		return my > listTop() ? KEY + tab : null;
	}

	private int listTop() {
		return m.cy + 120;
	}

	@Override
	void init() {
		Auction a = state.auctions.current;
		int cx = m.cx, cy = m.cy, cw = m.cw;
		if (a == null) {
			Button create = m.buttonRight(cx + cw - 8, cy + 17, "Create auction", Button.Variant.PRIMARY, this::openSetup);
			create.icon(Icons.PLUS);
			create.setX(cx + cw - 8 - create.getWidth());
			return;
		}
		int y = cy + 80;
		int x = cx;
		boolean paused = a.status == Auction.Status.PAUSED;
		Button pause = m.button(x, y, paused ? "Resume" : "Pause", paused ? Button.Variant.PRIMARY : Button.Variant.SECONDARY, state::togglePauseAuction);
		pause.icon(paused ? Icons.PLAY : Icons.PAUSE);
		x = pause.getRight() + 4;
		x = m.button(x, y, "+30s", Button.Variant.SECONDARY, () -> state.extendAuction(30)).getRight() + 4;
		x = m.button(x, y, "-30s", Button.Variant.SECONDARY, () -> state.extendAuction(-30)).getRight() + 4;
		m.button(x, y, "+5m", Button.Variant.SECONDARY, () -> state.extendAuction(300));
		Button end = m.buttonRight(cx + cw, y, "End now", Button.Variant.PRIMARY, this::confirmEnd);
		end.icon(Icons.CHECK);
		end.setX(cx + cw - end.getWidth());
		m.buttonRight(end.getX() - 4, y, "Cancel", Button.Variant.DANGER, this::confirmCancel);

		int ty = cy + 100;
		m.segmented(cx, ty, 128, List.of("Ranking", "Bid history"), () -> tab, i -> tab = i);
		m.dropdown(cx + cw - 76, ty, 76, BID_NOTIFY_LABELS, () -> Math.max(0, BID_NOTIFY.indexOf(a.bidNotify)),
			i -> a.bidNotify = BID_NOTIFY.get(i));
		m.toggle(boardRight(), cy + 26, "Leaderboard on HUD", () -> a.hudLeaderboard, () -> a.hudLeaderboard = !a.hudLeaderboard);
		m.toggle(cardRight(), cy + 26, "Auction card on HUD", () -> a.hudCard, () -> a.hudCard = !a.hudCard);

		int fy = m.cy + m.ch - 16;
		m.box("auction.fixPlayer", cx + 56, fy, 92, "Player");
		m.box("auction.fixAmount", cx + 152, fy, 76, "50m or -50m");
		m.button(cx + 232, fy, "Apply", Button.Variant.SECONDARY, () -> {
			String raw = ManagerScreen.draft("auction.fixAmount");
			boolean negative = raw.startsWith("-");
			long v = AmountParser.parseOrZero(negative ? raw.substring(1) : raw);
			String player = ManagerScreen.draft("auction.fixPlayer");
			if (player.isEmpty() || v == 0) {
				return;
			}
			state.correctAuction(player, negative ? -v : v);
			ManagerScreen.DRAFTS.remove("auction.fixAmount");
		});
	}

	/** Right edges of the two HUD switches, left of the bid-alert dropdown. */
	private int boardRight() {
		return m.cx + m.cw - 8;
	}

	private int cardRight() {
		return boardRight() - 24 - m.width("Board") - 8;
	}

	private void openSetup() {
		MinecraftClient.getInstance().setScreen(new AuctionSetupScreen(state, m));
	}

	private void confirmEnd() {
		Auction a = state.auctions.current;
		if (a == null) {
			return;
		}
		Standing leader = a.leader();
		String msg = leader == null ? "Nobody has paid, so there's no winner."
			: leader.player() + " wins with " + Fmt.money(leader.total()) + "."
				+ (state.config.auctionRefundLosers && a.rankings().size() > 1 ? " Everyone else is owed a refund." : "");
		m.confirm("End the auction now?", msg, "End now", false, state::endAuction);
	}

	private void confirmCancel() {
		Auction a = state.auctions.current;
		if (a == null) {
			return;
		}
		int n = a.rankings().size();
		m.confirm("Cancel the auction?", "There's no winner. " + (n == 0 ? "Nobody has paid yet."
			: n + (n == 1 ? " player is" : " players are") + " owed a refund of what they paid."), "Cancel auction", true, state::cancelAuction);
	}

	@Override
	void draw() {
		Auction a = state.auctions.current;
		if (a == null) {
			drawIdle();
		} else {
			drawLive(a);
		}
	}

	private void drawLive(Auction a) {
		int cx = m.cx, cy = m.cy, cw = m.cw, ch = m.ch;
		var ctx = m.ctx;
		long now = System.currentTimeMillis();
		boolean paused = a.status == Auction.Status.PAUSED;
		boolean amounts = !m.hides(Surface.AUCTION_HUD);
		long left = a.remaining(now);

		m.card(cx, cy, cw, 74);
		ItemStack prize = ItemIcons.of(a);
		Paint.round(ctx, cx + 8, cy + 8, 26, 26, 5, Colors.alpha(Colors.accent(), 0x24));
		m.icon(prize, cx + 11, cy + 11, 1.25f);
		int tx = cx + 40;
		m.text(m.fit(a.item, cw - 110), tx, cy + 10, Colors.INK);
		String desc = a.description == null || a.description.isBlank() ? (a.quantity > 1 ? "Quantity " + a.quantity : "No description")
			: a.description;
		m.text(m.fit(desc, cardRight() - 24 - m.width("Card") - 40 - tx), tx, cy + 22, Colors.INK_FAINT);
		String status = paused ? "PAUSED" : left < 30_000 ? "ENDING" : "LIVE";
		int sc = paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.GREEN;
		int sw = m.width(status) + 16;
		Paint.pill(ctx, cx + cw - 8 - sw, cy + 8, sw, 12, Colors.alpha(sc, 0x33));
		float pulse = paused ? 1f : 0.5f + 0.5f * (float) Math.sin(now / 250.0);
		Paint.round(ctx, cx + cw - 8 - sw + 4, cy + 12, 4, 4, 2, Colors.fade(sc, 0.4f + 0.6f * pulse));
		m.text(status, cx + cw - 8 - sw + 11, cy + 10, sc);

		int clockColor = paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.INK;
		Paint.big(ctx, m.textRenderer(), Fmt.clock(left), cx + 8, cy + 39, 3, clockColor, false);
		float frac = (float) left / Math.max(1, Math.max(a.durationMs, left));
		Paint.bar(ctx, cx + 8, cy + 66, cw - 16, 3, frac, Colors.LINE, paused ? Colors.GOLD : left < 30_000 ? Colors.RED : Colors.accent());

		int sx = cx + Math.max(110, cw / 2 - 30);
		int cols = cw >= 400 ? 4 : 3;
		int colW = (cx + cw - 8 - sx) / cols;
		Standing leader = a.leader();
		List<Standing> r = a.rankings();
		m.stat(sx, cy + 41, colW - 4, "Collected", Fmt.money(a.total()), Colors.GREEN, !amounts);
		m.stat(sx + colW, cy + 41, colW - 4, "Leader", leader == null ? "-" : leader.player(), Colors.GOLD, false);
		m.stat(sx + 2 * colW, cy + 41, colW - 4, "Bidders", Integer.toString(r.size()), Colors.INK, false);
		if (cols == 4) {
			m.stat(sx + 3 * colW, cy + 41, colW - 4, "Ends", paused ? "paused" : ManagerScreen.shortTime(now + left), Colors.INK_DIM, false);
		}

		m.text("Fix", cx, cy + ch - 12, Colors.INK_DIM);
		int hudX = cardRight() - 24 - m.width("Card");
		m.text("Board", boardRight() - 24 - m.width("Board"), cy + 27, Colors.INK_DIM);
		m.text("Card", hudX, cy + 27, Colors.INK_DIM);
		m.right("HUD", hudX - 6, cy + 27, Colors.INK_FAINT);

		int top = listTop();
		int lh = cy + ch - 22 - top;
		m.card(cx, top, cw, lh);
		if (tab == 0) {
			ranking.reset(a);
			if (r.isEmpty()) {
				m.empty(Icons.AUCTION, "Waiting for the first /pay", "Every payment counts toward the auction", cx, top, cw, lh);
				return;
			}
			int rowH = 14;
			int visible = Math.max(1, (lh - 6) / rowH);
			int max = Math.max(0, r.size() - visible);
			int first = Math.min(ManagerScreen.SCROLL.getOrDefault(KEY + 0, 0), max);
			ManagerScreen.SCROLL.put(KEY + 0, first);
			ctx.enableScissor(cx + 1, top + 1, cx + cw - 1, top + lh - 1);
			ranking.draw(ctx, m.textRenderer(), r, cx + 4, top + 3, cw - 8, rowH, first, visible, amounts, true);
			ctx.disableScissor();
		} else {
			List<Contribution> bids = new ArrayList<>(a.contributions);
			java.util.Collections.reverse(bids);
			if (bids.isEmpty()) {
				m.empty(Icons.HISTORY, "No bids yet", null, cx, top, cw, lh);
				return;
			}
			m.list(cx, top, cw, lh, bids.size(), KEY + 1, 13, (i, y) -> {
				Contribution c = bids.get(i);
				m.text(ManagerScreen.time(c.timestamp), cx + 8, y + 3, Colors.INK_FAINT);
				m.text(m.fit(c.player, cw - 190), cx + 60, y + 3, Colors.INK);
				if (c.manual) {
					Paint.pill(ctx, cx + cw - 130, y + 1, 36, 11, Colors.alpha(Colors.GOLD, 0x26));
					m.text("fixed", cx + cw - 126, y + 3, Colors.GOLD);
				}
				if (amounts) {
					m.right((c.amount < 0 ? "-" : "+") + Fmt.money(Math.abs(c.amount)), cx + cw - 10, y + 3, c.amount < 0 ? Colors.RED : Colors.GREEN);
				}
			});
		}
	}

	private void drawIdle() {
		int cx = m.cx, cy = m.cy, cw = m.cw, ch = m.ch;
		m.card(cx, cy, cw, 50);
		m.cardTitle(cx, cy, "Start an auction", Icons.AUCTION);
		m.text(m.fit("Pick any item, set the timer and what viewers see.", cw - 130), cx + 8, cy + 22, Colors.INK_DIM);
		m.text(m.fit("While it runs, every /pay counts toward it.", cw - 130), cx + 8, cy + 33, Colors.INK_FAINT);

		int y = cy + 56;
		int h = cy + ch - y;
		int rw = Math.min(150, cw * 2 / 5);
		int lw = cw - rw - ManagerScreen.GAP;
		results(cx, y, lw, h);

		int rx = cx + lw + ManagerScreen.GAP;
		m.card(rx, y, rw, h);
		m.cardTitle(rx, y, "Recent prizes", Icons.STAR);
		List<String> recent = state.config.recentItems;
		int ix = rx + 8;
		int iy = y + 21;
		if (recent.isEmpty()) {
			m.text(m.fit("Items you auction show here", rw - 16), rx + 8, iy + 2, Colors.INK_FAINT);
			iy += 14;
		}
		for (String id : recent) {
			ItemStack stack = ItemIcons.fromId(id, 1);
			if (stack.isEmpty()) {
				continue;
			}
			if (ix + 20 > rx + rw - 6) {
				ix = rx + 8;
				iy += 23;
				if (iy + 20 > y + h - 60) {
					break;
				}
			}
			float hv = m.hover("prize." + id, m.in(ix, iy, 20, 20));
			Paint.round(m.ctx, ix, iy, 20, 20, 4, Colors.mix(Colors.CARD_HI, Colors.alpha(Colors.accent(), 0x50), hv));
			m.icon(stack, ix + 2, iy + 2, 1f);
			m.region(ix, iy, 20, 20, () -> MinecraftClient.getInstance().setScreen(AuctionSetupScreen.withItem(state, m, stack)));
			ix += 23;
		}
		int dy = Math.max(iy + 26, y + h - 54);
		Paint.hline(m.ctx, rx + 1, rx + rw - 1, dy - 4, Colors.LINE);
		Paint.label(m.ctx, m.textRenderer(), "Defaults", rx + 8, dy);
		String[][] rules = {
			{"Length", ManagerScreen.formatDuration(state.config.auctionDefaultDurationSec)},
			{"Auto end", ManagerScreen.onOff(state.config.auctionAutoEnd)},
			{"Anti-snipe", state.config.auctionAntiSnipeSec > 0 ? state.config.auctionAntiSnipeSec + "s" : "Off"},
		};
		for (int i = 0; i < rules.length; i++) {
			int ly = dy + 11 + i * 10;
			if (ly > y + h - 9) {
				break;
			}
			m.text(rules[i][0], rx + 8, ly, Colors.INK_DIM);
			m.right(rules[i][1], rx + rw - 8, ly, Colors.INK);
		}
	}

	/** The last finished auction: winner, podium and totals. */
	private void results(int x, int y, int w, int h) {
		m.card(x, y, w, h);
		Auction a = state.auctions.latest();
		if (a == null || a.isLive()) {
			m.empty(Icons.TROPHY, "No auctions yet", "Results show here when one ends", x, y, w, h);
			return;
		}
		boolean amounts = !m.hides(Surface.AUCTION_HUD);
		m.cardTitle(x, y, "Last result", Icons.TROPHY);
		m.right(ManagerScreen.ago(a.endedAt), x + w - 8, y + 7, Colors.INK_FAINT);
		m.itemTile(ItemIcons.of(a), x + 8, y + 21);
		m.text(m.fit(a.item, w - 44), x + 34, y + 22, Colors.INK);
		List<Standing> r = a.rankings();
		String sub = a.status == Auction.Status.CANCELLED ? "Cancelled · everyone refunded"
			: r.size() + (r.size() == 1 ? " bidder" : " bidders") + (amounts ? " · " + Fmt.money(a.total()) + " total" : "");
		m.text(m.fit(sub, w - 44), x + 34, y + 33, Colors.INK_DIM);

		if (a.winner != null) {
			int wy = y + 48;
			Paint.roundGradient(m.ctx, x + 8, wy, w - 16, 24, 4, Colors.alpha(Colors.GOLD, 0x30), Colors.alpha(Colors.GOLD, 0x10));
			Icons.TROPHY.draw(m.ctx, x + 14, wy + 8, Colors.GOLD);
			m.text("Winner", x + 26, wy + 4, Colors.mix(Colors.GOLD, 0xFF000000, 0.2f));
			m.text(m.fit(a.winner, w - 110), x + 26, wy + 14, Colors.GOLD);
			if (amounts && !r.isEmpty()) {
				Standing ws = a.standing(a.winner);
				if (ws != null) {
					m.right(Fmt.money(ws.total()), x + w - 14, wy + 9, Colors.INK);
				}
			}
		}
		int py = y + 78;
		for (int i = 1; i < Math.min(r.size(), 5); i++) {
			if (py > y + h - 22) {
				break;
			}
			Standing s = r.get(i);
			m.text("#" + (i + 1), x + 10, py, Colors.INK_FAINT);
			m.text(m.fit(s.player(), w - 100), x + 28, py, Colors.INK);
			if (amounts) {
				m.right(Fmt.money(s.total()), x + w - 10, py, Colors.INK_DIM);
			}
			py += 11;
		}
		if (!m.hides(Surface.FINANCE) && a.prizeCost > 0) {
			long profit = a.total() - a.prizeCost;
			m.text("Prize cost " + Fmt.money(a.prizeCost) + " · net " + Fmt.signed(profit), x + 8, y + h - 12, Colors.INK_FAINT);
		}
		long open = state.auctions.openRefunds().stream().filter(rf -> rf.auctionId == a.id).count();
		if (open > 0 && (m.hides(Surface.FINANCE) || a.prizeCost <= 0)) {
			m.link(open + (open == 1 ? " refund" : " refunds") + " still owed →", x + 8, y + h - 12, Colors.GOLD,
				() -> m.show(ManagerScreen.Section.HISTORY));
		}
	}
}
