package com.mitocet.miningprofit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** What is kept between game restarts: totals, the current session, box position and tab. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public double totalCoins;
	public long totalMs;
	public double sessionCoins;
	public long sessionMs;
	public int x = 4;
	public int y = 40;
	public float scale = 1f;
	public boolean sessionTab = true;
	/** Show the box also while no session runs (/kazanc goster, /kazanc gizle). */
	public boolean showStopped;
	/** Big box: earnings per category above the totals. Small box: only earned and per hour. */
	public boolean big;
	/** Categories opened in the big box. */
	public Set<String> open = new HashSet<>();
	public Breakdown sessionBreakdown;
	public Breakdown totalBreakdown;

	public static Config load(Path file) {
		try {
			if (Files.exists(file)) {
				Config c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Config.class);
				if (c != null) return c;
			}
		} catch (Exception e) {
			MiningProfitClient.LOG.warn("Could not read {}", file, e);
		}
		return new Config();
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (Exception e) {
			MiningProfitClient.LOG.warn("Could not write {}", file, e);
		}
	}
}
