package com.donutgamble.client;

import com.donutgamble.core.AuctionManager;
import com.donutgamble.core.FinanceBook;
import com.donutgamble.core.GambleConfig;
import com.donutgamble.core.Ledger;
import com.donutgamble.core.PlayerDirectory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Reads and writes config.json, ledger.json (gamble bets), finance.json (every payment) and
 * auctions.json in .minecraft/config/donutgamble/.
 *
 * <p>Saving is debounced onto a background thread so a busy stream never stalls the game: the
 * JSON is built on the calling thread (a consistent snapshot) and written shortly after.
 * {@link #flush()} writes immediately and is called when the game closes. Once a day the files
 * are copied to backups/yyyy-MM-dd/ (last {@value #KEEP_BACKUPS} days kept); if a file can't be
 * read, the newest backup that can be read is used instead of starting empty.
 */
final class Storage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String[] FILES = {"config.json", "ledger.json", "finance.json", "auctions.json", "players.json"};
	private static final int KEEP_BACKUPS = 7;
	private static final long DEBOUNCE_MS = 400;

	private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("donutgamble");
	private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "DonutGamble-save");
		t.setDaemon(true);
		return t;
	});
	/** Latest JSON per file waiting to be written. Guarded by {@code this}. */
	private final Map<String, String> pending = new LinkedHashMap<>();
	private ScheduledFuture<?> scheduled;
	private final Object writeLock = new Object();

	GambleConfig loadConfig() {
		GambleConfig cfg = read("config.json", GambleConfig.class);
		return cfg != null ? cfg : new GambleConfig();
	}

	Ledger loadLedger() {
		Ledger ledger = read("ledger.json", Ledger.class);
		if (ledger == null) {
			ledger = new Ledger();
			ledger.startSession(System.currentTimeMillis());
		}
		return ledger;
	}

	FinanceBook loadFinance() {
		FinanceBook f = read("finance.json", FinanceBook.class);
		return f != null ? f : new FinanceBook();
	}

	AuctionManager loadAuctions() {
		AuctionManager a = read("auctions.json", AuctionManager.class);
		return a != null ? a : new AuctionManager();
	}

	PlayerDirectory loadPlayers() {
		PlayerDirectory d = read("players.json", PlayerDirectory.class);
		return d != null ? d : new PlayerDirectory();
	}

	/** Queues a save of everything; written ~0.4 s later on the save thread. */
	void save(GambleConfig cfg, Ledger ledger, FinanceBook finance, AuctionManager auctions, PlayerDirectory players) {
		Map<String, String> snapshot = new LinkedHashMap<>();
		snapshot.put("players.json", GSON.toJson(players));
		snapshot.put("config.json", GSON.toJson(cfg));
		snapshot.put("ledger.json", GSON.toJson(ledger));
		snapshot.put("finance.json", GSON.toJson(finance));
		snapshot.put("auctions.json", GSON.toJson(auctions));
		synchronized (this) {
			pending.putAll(snapshot);
			if (scheduled == null || scheduled.isDone()) {
				scheduled = writer.schedule(this::writePending, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
			}
		}
	}

	/** Writes anything still waiting, on the calling thread. */
	void flush() {
		synchronized (this) {
			if (scheduled != null) {
				scheduled.cancel(false);
			}
		}
		writePending();
	}

	/**
	 * Only one batch is written at a time (so an older snapshot can never land after a newer one),
	 * under its own lock so {@link #save} on the game thread never waits for the disk.
	 */
	private void writePending() {
		synchronized (writeLock) {
			Map<String, String> batch;
			synchronized (this) {
				batch = new LinkedHashMap<>(pending);
				pending.clear();
			}
			batch.forEach(this::write);
		}
	}

	private void write(String name, String json) {
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve(name + ".tmp");
			Files.writeString(tmp, json);
			Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			DonutGambleClient.LOGGER.error("Could not save {}", name, e);
		}
	}

	private <T> T read(String name, Class<T> type) {
		Path file = dir.resolve(name);
		if (!Files.exists(file)) {
			return null;
		}
		T value = parse(file, type);
		if (value != null) {
			return value;
		}
		for (Path backup : backups()) {
			T restored = parse(backup.resolve(name), type);
			if (restored != null) {
				DonutGambleClient.LOGGER.warn("{} was unreadable; restored it from backup {}", name, backup.getFileName());
				keepBroken(file);
				return restored;
			}
		}
		DonutGambleClient.LOGGER.error("{} was unreadable and no backup could be read; starting fresh", name);
		keepBroken(file);
		return null;
	}

	/** Keeps the unreadable file next to the new one so nothing is silently thrown away. */
	private void keepBroken(Path file) {
		try {
			Files.copy(file, dir.resolve(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
		}
	}

	private static <T> T parse(Path file, Class<T> type) {
		if (!Files.exists(file)) {
			return null;
		}
		try (Reader r = Files.newBufferedReader(file)) {
			return GSON.fromJson(r, type);
		} catch (IOException | RuntimeException e) {
			DonutGambleClient.LOGGER.error("Could not read {}", file, e);
			return null;
		}
	}

	// ---- backups ----

	/** Today's backup, if there isn't one yet. Daily backups beyond {@value #KEEP_BACKUPS} are deleted. */
	void dailyBackup() {
		Path today = dir.resolve("backups").resolve(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()));
		if (!Files.exists(today)) {
			backupTo(today);
		}
		List<Path> all = backups();
		for (int i = KEEP_BACKUPS; i < all.size(); i++) {
			deleteTree(all.get(i));
		}
	}

	/** A backup right now, in backups/manual-yyyyMMdd-HHmmss/. */
	Path backupNow() {
		flush();
		Path target = dir.resolve("backups").resolve("manual-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
		return backupTo(target) ? target : null;
	}

	private boolean backupTo(Path target) {
		try {
			Files.createDirectories(target);
			for (String name : FILES) {
				Path src = dir.resolve(name);
				if (Files.exists(src)) {
					Files.copy(src, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			return true;
		} catch (IOException e) {
			DonutGambleClient.LOGGER.error("Backup to {} failed", target, e);
			return false;
		}
	}

	/** Daily backup folders, newest first. */
	private List<Path> backups() {
		Path root = dir.resolve("backups");
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(root)) {
			List<Path> list = new ArrayList<>(s.filter(p -> p.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}")).toList());
			list.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
			return list;
		} catch (IOException e) {
			return List.of();
		}
	}

	private static void deleteTree(Path p) {
		try (Stream<Path> s = Files.walk(p)) {
			s.sorted(Comparator.reverseOrder()).forEach(f -> {
				try {
					Files.delete(f);
				} catch (IOException ignored) {
				}
			});
		} catch (IOException ignored) {
		}
	}

	// ---- exports ----

	Path dir() {
		return dir;
	}

	/** Writes exports/<name>-<time>.<ext> and returns the file, or null if it couldn't be written. */
	Path export(String name, String ext, String content) {
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
		Path file = dir.resolve("exports").resolve(name + "-" + stamp + "." + ext);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
			return file;
		} catch (IOException e) {
			DonutGambleClient.LOGGER.error("Could not export {}", file, e);
			return null;
		}
	}

	static String toJson(Object value) {
		return GSON.toJson(value);
	}

	/** Where settings are imported from. */
	Path importFile() {
		return dir.resolve("import").resolve("settings.json");
	}

	/** Reads settings exported earlier (dropped into import/settings.json); null if missing or unreadable. */
	GambleConfig readImport() {
		Path file = importFile();
		return Files.exists(file) ? parse(file, GambleConfig.class) : null;
	}
}
