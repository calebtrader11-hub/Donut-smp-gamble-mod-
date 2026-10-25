package com.donutgamble.client;

import com.donutgamble.core.AuctionManager;
import com.donutgamble.core.FinanceBook;
import com.donutgamble.core.GambleConfig;
import com.donutgamble.core.Ledger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Reads and writes config.json, ledger.json (gamble bets), finance.json (every payment) and
 * auctions.json in .minecraft/config/donutgamble/; exports go to its exports/ folder.
 */
final class Storage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("donutgamble");

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

	void save(GambleConfig cfg, Ledger ledger, FinanceBook finance, AuctionManager auctions) {
		write("config.json", cfg);
		write("ledger.json", ledger);
		write("finance.json", finance);
		write("auctions.json", auctions);
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

	private <T> T read(String name, Class<T> type) {
		Path file = dir.resolve(name);
		if (!Files.exists(file)) {
			return null;
		}
		try (Reader r = Files.newBufferedReader(file)) {
			return GSON.fromJson(r, type);
		} catch (IOException | RuntimeException e) {
			DonutGambleClient.LOGGER.error("Could not read {}, starting fresh", file, e);
			return null;
		}
	}

	private void write(String name, Object value) {
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve(name + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp)) {
				GSON.toJson(value, w);
			}
			Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			DonutGambleClient.LOGGER.error("Could not save {}", name, e);
		}
	}
}
