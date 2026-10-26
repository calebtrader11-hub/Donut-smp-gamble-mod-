package com.donutgamble.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** All bets ever taken plus the current session start. Saved as config/donutgamble/ledger.json. */
public class Ledger {
	private static final int RECENT = 5;

	public List<Bet> bets = new ArrayList<>();
	public long sessionStart;
	public long nextId = 1;

	public void startSession(long now) {
		sessionStart = now;
	}

	public Bet addPayment(String player, long amount, Game game, long now) {
		Bet b = new Bet();
		b.id = nextId++;
		b.player = player;
		b.amount = amount;
		b.game = game;
		b.timestamp = now;
		bets.add(b);
		return b;
	}

	public Bet find(long id) {
		for (Bet b : bets) {
			if (b.id == id) {
				return b;
			}
		}
		return null;
	}

	public void setGame(long id, Game game) {
		Bet b = find(id);
		if (b != null && b.isPending()) {
			b.game = game;
		}
	}

	/** Ely Arrows: {@code won} elytras out of the shots bought (0 = a miss). */
	public void resolveEly(long id, int won, GambleConfig cfg) {
		Bet b = find(id);
		if (b == null || !b.isPending()) {
			return;
		}
		b.shots = cfg.elyShots(b.amount);
		b.elytrasWon = Math.max(0, won);
		resolve(id, won > 0 ? Outcome.PLAYER_WIN : Outcome.PLAYER_LOSE, null, null, cfg);
	}

	public void resolve(long id, Outcome outcome, Integer them, Integer me, GambleConfig cfg) {
		Bet b = find(id);
		if (b == null || !b.isPending()) {
			return;
		}
		b.outcome = outcome;
		b.resolvedAt = System.currentTimeMillis();
		b.scoreThem = them;
		b.scoreMe = me;
		if (outcome == Outcome.PLAYER_WIN) {
			b.payout = b.game == Game.ELY ? cfg.elytraPrice * b.elytras() : Math.round(b.amount * cfg.winMultiplier);
			b.houseNet = b.amount - b.payout;
		} else {
			b.payout = 0;
			b.houseNet = b.amount;
			b.settled = true;
		}
	}

	/** A tie: the house keeps the bet. */
	public void resolveTie(long id, Integer them, Integer me, GambleConfig cfg) {
		resolve(id, Outcome.PLAYER_LOSE, them, me, cfg);
		Bet b = find(id);
		if (b != null) {
			b.tie = true;
		}
	}

	/** Records the score after the result was already called. */
	public void setScore(long id, Integer them, Integer me) {
		Bet b = find(id);
		if (b != null && !b.isPending()) {
			b.scoreThem = them;
			b.scoreMe = me;
		}
	}

	public void settle(long id) {
		Bet b = find(id);
		if (b != null && !b.isPending()) {
			b.settled = true;
		}
	}

	/** You paid someone: clear the oldest money payout owed to them for exactly that amount. */
	public boolean settleByPayment(String player, long amount) {
		return settleMatching(player, amount) != null;
	}

	/** Like {@link #settleByPayment} but returns the bet that was paid, or null if none matched exactly. */
	public Bet settleMatching(String player, long amount) {
		for (Bet b : owed()) {
			if (!b.payoutIsElytra() && b.player.equalsIgnoreCase(player) && b.payout == amount) {
				b.settled = true;
				return b;
			}
		}
		return null;
	}

	public void remove(long id) {
		bets.removeIf(b -> b.id == id && b.isPending());
	}

	/** The queue: oldest first, so #1 is the bet being played now. */
	public List<Bet> pending() {
		List<Bet> list = filterNewestFirst(Bet::isPending);
		Collections.reverse(list);
		return list;
	}

	/** Oldest first, so the first person to win is paid first. */
	public List<Bet> owed() {
		List<Bet> list = filterNewestFirst(b -> !b.isPending() && !b.settled);
		list.sort(Comparator.comparingLong(b -> b.timestamp));
		return list;
	}

	public Stats sessionStats() {
		return stats(b -> b.timestamp >= sessionStart);
	}

	public Stats sessionStats(Game game) {
		return stats(b -> b.timestamp >= sessionStart && b.game == game);
	}

	/** Queue for one game, oldest first. */
	public List<Bet> pending(Game game) {
		List<Bet> list = pending();
		list.removeIf(b -> b.game != game);
		return list;
	}

	/** Results for one game (null = all games) for bets placed in {@code from <= time < to}. */
	public Stats stats(Game game, long from, long to) {
		return stats(b -> b.timestamp >= from && b.timestamp < to && (game == null || b.game == game));
	}

	/** Results called since {@code since}, most recent first (by when they were called). */
	public List<Bet> recentResults(long since, int limit) {
		List<Bet> list = filterNewestFirst(b -> !b.isPending() && Math.max(b.resolvedAt, b.timestamp) >= since);
		list.sort((a, b) -> Long.compare(Math.max(b.resolvedAt, b.timestamp), Math.max(a.resolvedAt, a.timestamp)));
		return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
	}

	public Stats lifetimeStats() {
		return stats(b -> true);
	}

	private Stats stats(Predicate<Bet> include) {
		long net = 0;
		long wagered = 0;
		int wins = 0;
		int losses = 0;
		int ties = 0;
		List<Bet> resolved = filterNewestFirst(b -> !b.isPending() && include.test(b));
		for (Bet b : resolved) {
			net += b.houseNet;
			wagered += b.amount;
			if (b.outcome == Outcome.PLAYER_WIN) {
				wins++;
			} else if (b.tie) {
				ties++;
			} else {
				losses++;
			}
		}
		List<Bet> recent = new ArrayList<>(resolved.subList(0, Math.min(RECENT, resolved.size())));
		return new Stats(net, wagered, wins, losses, ties, recent);
	}

	private List<Bet> filterNewestFirst(Predicate<Bet> p) {
		List<Bet> out = new ArrayList<>();
		for (int i = bets.size() - 1; i >= 0; i--) {
			if (p.test(bets.get(i))) {
				out.add(bets.get(i));
			}
		}
		return out;
	}
}
