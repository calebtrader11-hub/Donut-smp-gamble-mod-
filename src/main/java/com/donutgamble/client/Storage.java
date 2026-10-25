package com.donutgamble.client;

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

/** Reads and writes config.json and ledger.json in .minecraft/config/donutgamble/. */
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

	void save(GambleConfig cfg, Ledger ledger) {
		write("config.json", cfg);
		write("ledger.json", ledger);
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
