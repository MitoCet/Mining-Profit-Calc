package com.mitocet.miningprofit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;

/**
 * Checks the sack messages the mod read against the real sack counts from the Hypixel API.
 *
 * At /kazanc baslat the sack counts are taken from the API (the base). From then on every sack
 * message line of a mining item is added up as it is (+ and -, whatever the earning rules say),
 * also while stopped. Later (/kazanc bitir or /kazanc kontrol) the API counts minus the base
 * should equal that sum; a difference means a sack message was missed or read twice.
 *
 * Hypixel saves the profile only every few minutes, so the API can be behind: if nothing changed
 * since the base the check says so instead of reporting differences.
 */
public final class SackCheck {
	public static final String URL = "https://api.hypixel.net/v2/skyblock/profiles?uuid=";

	/** Sack counts from the API when the check started (item id -> count); null = no base. */
	public Map<String, Long> base;
	/** When the base was taken (ms). */
	public long baseAt;
	/** Sum of the sack message lines since the base (item id -> amount). */
	public Map<String, Long> read = new HashMap<>();

	public boolean hasBase() {
		return base != null;
	}

	public void setBase(Map<String, Long> counts, long now) {
		base = new HashMap<>(counts);
		baseAt = now;
		read = new HashMap<>();
	}

	/** Adds one sack message (all its hover texts) to what was read. Only mining items. */
	public void onSackMessage(List<String> hoverTexts, Prices p) {
		if (base == null) return;
		for (String hover : new LinkedHashSet<>(hoverTexts)) {
			for (String line : hover.split("\n")) {
				Matcher m = Tracker.SACK_LINE.matcher(Tracker.strip(line));
				if (!m.matches()) continue;
				Prices.Item item = p.byName(m.group(2));
				if (item == null || !item.mining()) continue;
				long amount = Math.round(Tracker.number(m.group(1)));
				if (amount != 0) read.merge(item.id(), amount, Long::sum);
			}
		}
	}

	/** One item whose API change is not what the messages said. */
	public record Diff(String id, long api, long read) {
		public long missing() {
			return api - read;
		}
	}

	public record Result(boolean apiUnchanged, List<Diff> diffs, long matched) {}

	/** Compares the API counts now with the base and what was read; only mining items. */
	public Result compare(Map<String, Long> now, Prices p) {
		TreeSet<String> ids = new TreeSet<>(read.keySet());
		boolean unchanged = true;
		TreeSet<String> all = new TreeSet<>(base.keySet());
		all.addAll(now.keySet());
		for (String id : all) {
			Prices.Item item = p.byId(id);
			if (item == null || !item.mining()) continue;
			long d = now.getOrDefault(id, 0L) - base.getOrDefault(id, 0L);
			if (d != 0) {
				unchanged = false;
				ids.add(id);
			}
		}
		List<Diff> diffs = new ArrayList<>();
		long matched = 0;
		for (String id : ids) {
			long api = now.getOrDefault(id, 0L) - base.getOrDefault(id, 0L);
			long r = read.getOrDefault(id, 0L);
			if (api == r) matched++;
			else diffs.add(new Diff(id, api, r));
		}
		// Nothing changed in the API although sack messages came: Hypixel has not saved yet.
		boolean behind = unchanged && !read.isEmpty();
		return new Result(behind, behind ? List.of() : diffs, matched);
	}

	/**
	 * Sack counts of the player's selected profile from a /v2/skyblock/profiles answer.
	 * Throws with a Turkish reason the player can act on.
	 */
	public static Map<String, Long> parseSacks(String json, String uuid) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (root.has("success") && !root.get("success").getAsBoolean()) {
			String cause = root.has("cause") ? root.get("cause").getAsString() : "bilinmeyen hata";
			throw new IllegalStateException(reason(cause));
		}
		JsonElement list = root.get("profiles");
		if (list == null || !list.isJsonArray() || list.getAsJsonArray().isEmpty()) {
			throw new IllegalStateException("Hypixel'de SkyBlock profili bulunamadı.");
		}
		JsonArray profiles = list.getAsJsonArray();
		JsonObject chosen = null;
		for (JsonElement e : profiles) {
			JsonObject o = e.getAsJsonObject();
			if (o.has("selected") && o.get("selected").getAsBoolean()) chosen = o;
		}
		if (chosen == null) chosen = profiles.get(0).getAsJsonObject();
		String key = uuid.replace("-", "");
		JsonObject members = chosen.getAsJsonObject("members");
		JsonObject member = members == null ? null : members.getAsJsonObject(key);
		if (member == null) throw new IllegalStateException("Profilde oyuncu bulunamadı.");
		JsonObject sacks = null;
		JsonObject inv = member.getAsJsonObject("inventory");
		if (inv != null && inv.has("sack_counts")) sacks = inv.getAsJsonObject("sack_counts");
		if (sacks == null && member.has("sacks_counts")) sacks = member.getAsJsonObject("sacks_counts");
		if (sacks == null) {
			throw new IllegalStateException("API sack bilgisini vermedi. Oyunda SkyBlock Menu → Settings → API Settings'te Inventory/Sacks API açık olmalı.");
		}
		Map<String, Long> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : sacks.entrySet()) {
			if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsLong());
		}
		return out;
	}

	/** Hypixel's error text in Turkish. */
	public static String reason(String cause) {
		String c = cause.toLowerCase(java.util.Locale.ROOT);
		if (c.contains("invalid api key")) return "API anahtarı geçersiz ya da süresi bitmiş. Yenisini /kazanc api <anahtar> ile gir.";
		if (c.contains("throttle") || c.contains("limit")) return "Hypixel çok fazla istek dedi, biraz sonra tekrar dene.";
		return "Hypixel hata verdi: " + cause;
	}
}
