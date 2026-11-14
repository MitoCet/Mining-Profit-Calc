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
	/** When the current session started (ms). */
	public long sessionStart;
	/** Every ended session (/kazanc gecmis); Toplam is their sum plus the current session. */
	public History history;
	/** The last API sack check of the session running now; saved with it when it ends. */
	public History.Check liveCheck;
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
	/** Write what the mod reads to mining-profit-calc-debug.log (/kazanc kayit). */
	public boolean debugLog;
	/** Take the corpse keys used off the earnings (/kazanc anahtar). Off: keys are not counted. */
	public boolean keyCost;
	/** Seconds with nothing earned after which the time stops (/kazanc bosta); 0 = never. */
	public int idleSeconds = 90;
	/** Hypixel API key for the sack check (/kazanc api). Stays only in this file on this computer. */
	public String apiKey;
	/** When the API key was saved (ms; 0 = unknown, saved before this was kept). A Development Key lasts 3 days. */
	public long apiKeySetAt;
	/** Sack check against the Hypixel API: base counts and what was read since. */
	public SackCheck sackCheck;
	/**
	 * Sack items the API shows but no sack message did (e.g. leaving a Mineshaft before the
	 * message came): false = added only with Düzelt in /kazanc gecmis, true = added on their own, and the
	 * API is checked ~3 min after a server change (Oto düzelt button in /kazanc gecmis).
	 */
	public boolean autoFix;
	/** On the first SkyBlock entry of a game, ask whether to download a newer jar (/kazanc update bildirim). */
	public boolean updateNotify = true;
	/**
	 * The session the game ended when it was closed (or found unfinished after a crash), to tell
	 * about it and start a new session on the next join. null = nothing to tell.
	 */
	public Previous previous;
	public Breakdown sessionBreakdown;
	public Breakdown totalBreakdown;

	/** A session ended because the game was closed. */
	public static final class Previous {
		public double coins;
		public long ms;
		public long endedAt;
		/** Closed by a crash: found unfinished when the game started again. */
		public boolean crashed;
	}

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
