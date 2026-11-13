package com.mitocet.miningprofit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Checks on joining the game and on entering SkyBlock whether the saved Hypixel API key still works.
 *
 * Hypixel removed its /key endpoint, so a small endpoint that needs a key is asked instead
 * (/v2/status of the player): 200 = works, 403 "Invalid API key" = expired or wrong. A Development
 * Key lasts 3 days; the mod knows when the key was saved, so it can also say when it will run out.
 * SkyHanni does not use an API key, so this is our own.
 */
public final class KeyCheck {
	public static final String URL = "https://api.hypixel.net/v2/status?uuid=";
	/** A Development Key lasts this long after it is made. */
	public static final long DEV_KEY_MS = 72L * 3600_000L;
	/** Warn this long before the 3 days are up (as the site does). */
	public static final long WARN_BEFORE_MS = 12L * 3600_000L;

	/** Entering SkyBlock tests the key again only if the last test is at least this old. */
	public static final long RECHECK_MS = 5L * 60_000L;

	public enum Result { OK, INVALID, UNKNOWN }

	private KeyCheck() {}

	/** What the answer says about the key; UNKNOWN (no message) for throttling, outages and the like. */
	public static Result result(int status, String body) {
		String cause = "";
		Boolean success = null;
		try {
			JsonObject o = JsonParser.parseString(body).getAsJsonObject();
			if (o.has("success")) success = o.get("success").getAsBoolean();
			if (o.has("cause") && !o.get("cause").isJsonNull()) cause = o.get("cause").getAsString();
		} catch (Exception ignored) {
			// not JSON (e.g. a Cloudflare page): say nothing about the key
		}
		if (status == 403 || cause.toLowerCase(java.util.Locale.ROOT).contains("invalid api key")) return Result.INVALID;
		if (status == 200 && Boolean.TRUE.equals(success)) return Result.OK;
		return Result.UNKNOWN;
	}

	/**
	 * Hours left of a Development Key saved at {@code savedAt} (rounded up), or -1 when there is
	 * nothing to warn about yet (unknown save time, or more than 12 hours left).
	 */
	public static long hoursLeftToWarn(long savedAt, long now) {
		if (savedAt <= 0) return -1;
		long left = savedAt + DEV_KEY_MS - now;
		if (left > WARN_BEFORE_MS || left <= 0) return -1;
		return (left + 3600_000L - 1) / 3600_000L;
	}

	/**
	 * Whether the sidebar title is SkyBlock's ("SKYBLOCK", also with colour codes or as the
	 * April-fools "SKIBLOCK", as SkyHanni checks it).
	 */
	public static boolean isSkyBlockTitle(String title) {
		if (title == null) return false;
		String t = title.replaceAll("§.", "").toUpperCase(java.util.Locale.ROOT);
		return t.contains("SKYBLOCK") || t.contains("SKIBLOCK");
	}
}
