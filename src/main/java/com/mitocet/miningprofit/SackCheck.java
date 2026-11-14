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
 * also while stopped. Mining items from corpse loot count as read too until a sack message shows
 * them, because the loot message already counted them. Later (/kazanc bitir or /kazanc kontrol) the API counts minus the base
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
	/**
	 * Mining items from corpse loot that no sack message has shown yet (item id -> count). The
	 * earnings already counted them from the loot message; when the sack message for them never
	 * comes (it does not always), they are still not missing.
	 */
	public Map<String, Long> corpse = new HashMap<>();
	/** Earnings of the session that /kazanc bitir ended, for /kazanc kontrol after it (null = none). */
	public Double endedCoins;
	/** Start (id in History) of the ended session the check data belongs to; 0 = none or not saved. */
	public long endedId;
	/** The last result reported, so the same one is not warned about again. */
	public String lastReport;
	/** Items the last check found missing (item id -> count), for Düzelt in /kazanc gecmis. */
	public Map<String, Long> missing = new HashMap<>();

	/** The missing items belong to a session that has ended: Düzelt adds them to that session and Toplam. */
	public boolean missingEnded;
	/**
	 * The game was closed while a session ran: that session was ended and the base and what was
	 * read stay until the next game checks them against the API (then the new session gets a fresh
	 * base). Until then the sack messages of the new session are added to the same read.
	 */
	public boolean previousPending;
	/** The missing items are from before the base (the previous game's session, after rebase). */
	public boolean missingBeforeBase;

	// ---------- items put into the sacks by hand (Mito, 2026-11-14) ----------
	// Items bought (or brought) and put into the sacks are in the API counts. Their sack message is
	// normally read, so they make no difference; when it is lost (server change before it comes),
	// the check would see them as missed mining and Düzelt would add them. So the mod keeps what it
	// saw leave the inventory into a Sack menu until a sack message shows it; what no message
	// showed is listed with the check, and in /kazanc gecmis a click takes it off Düzelt.
	/** A sack message comes ~30 s after the change; what no message showed by then is at risk. */
	public static final long HAND_MS = 75_000;
	/** Put into the sacks by hand, no sack message showed it yet (item id -> count). */
	public Map<String, Long> handPending = new HashMap<>();
	/** When something was last put into handPending (ms). */
	public long handPendingAt;
	/** Put in by hand and no sack message showed it (lost): may be in the API difference. Item id -> count. */
	public Map<String, Long> hand = new HashMap<>();
	/** First and last time something went into {@link #hand} (ms, 0 = none). */
	public long handFrom;
	public long handTo;

	/**
	 * Material stash pickups (Mito, 2026-11-14: only a note, they count): their items go into the
	 * sacks without a sack message, the API check sees them and Düzelt adds them; Hypixel does not
	 * say which items. Count of items, and the first and last pickup (ms, 0 = none).
	 */
	public long stashItems;
	public long stashFrom;
	public long stashTo;
	/** "You picked up 2,000 items from your material stash!" (as SkyHanni's stash messages). */
	public static final java.util.regex.Pattern STASH_LINE =
		java.util.regex.Pattern.compile("^You picked up ([\\d,]+) items? from your material stash!?$");

	/** A chat line (colors stripped): a material stash pickup is noted. */
	public void onChatLine(String line, long now) {
		if (base == null) return;
		Matcher m = STASH_LINE.matcher(line.trim());
		if (!m.matches()) return;
		stashItems += Math.round(Tracker.number(m.group(1)));
		if (stashFrom == 0) stashFrom = now;
		stashTo = now;
	}

	/** Mining items that left the inventory into a Sack menu (item id -> count). */
	public void onHandDeposit(String id, long count, long now) {
		if (base == null || count <= 0) return;
		handPending.merge(id, count, Long::sum);
		handPendingAt = now;
	}

	/** Server change: the sack message still to come is lost, what it would have shown is at risk. */
	public void onWorldChange(long now) {
		if (!handPending.isEmpty() && now - handPendingAt <= HAND_MS) movePending();
	}

	/** Called now and then: what no sack message showed for a while is at risk too. */
	public void tick(long now) {
		if (!handPending.isEmpty() && now - handPendingAt > HAND_MS) movePending();
	}

	private void movePending() {
		handPending.forEach((id, n) -> hand.merge(id, n, Long::sum));
		if (handFrom == 0) handFrom = handPendingAt;
		handTo = handPendingAt;
		handPending = new HashMap<>();
	}

	/** Missing items to add now: families whose value went up (earnings never go down), see {@link #familyMissing}. */
	public void setMissing(List<Diff> diffs, Prices p) {
		setMissing(diffs, p, false);
	}

	public void setMissing(List<Diff> diffs, Prices p, boolean ended) {
		missing = familyMissing(diffs, p);
		missingEnded = ended;
		missingBeforeBase = false;
	}

	/**
	 * The differences netted per family (Glacite + Enchanted Glacite, Rough/Flawed/Fine Jade…), since
	 * a craft moves value inside a family: a lost Supercraft message shows as "too much Glacite, too
	 * few Enchanted Glacite" although nothing is wrong with the earnings. Only families whose value
	 * went up are kept (earnings never go down); inside such a family every difference is kept, also
	 * the negative ones, so the sum is the family's net. Item id -> count.
	 */
	public static Map<String, Long> familyMissing(List<Diff> diffs, Prices p) {
		return familyMissing(diffs, p, id -> value(id, p));
	}

	/** Same, with the value of one item from {@code valueOf} (stored at check time for saved sessions). */
	public static Map<String, Long> familyMissing(List<Diff> diffs, Prices p, java.util.function.ToDoubleFunction<String> valueOf) {
		Map<String, Double> net = new HashMap<>();
		for (Diff d : diffs) net.merge(family(d.id(), p), d.missing() * valueOf.applyAsDouble(d.id()), Double::sum);
		Map<String, Long> out = new HashMap<>();
		for (Diff d : diffs) {
			if (d.missing() != 0 && net.get(family(d.id(), p)) >= 1) out.put(d.id(), d.missing());
		}
		return out;
	}

	/** Coins the missing items are worth (the families' net). */
	public static double missingCoins(Map<String, Long> missing, Prices p) {
		double c = 0;
		for (Map.Entry<String, Long> e : missing.entrySet()) c += e.getValue() * value(e.getKey(), p);
		return c;
	}

	/** What Düzelt adds for one family: its net value, as a count of its item that went up the most. */
	public record Fix(Prices.Item item, double count, double coins) {}

	/**
	 * Turns the missing items into what Düzelt adds: one line per family that went up, so the box
	 * never shows negative counts. {@code valueOf} gives the value of one item (stored at check time
	 * for saved sessions).
	 */
	public static List<Fix> fixes(Map<String, Long> missing, Prices p, java.util.function.ToDoubleFunction<String> valueOf) {
		Map<String, Double> net = new java.util.TreeMap<>();
		Map<String, String> top = new HashMap<>();
		Map<String, Double> topCoins = new HashMap<>();
		for (Map.Entry<String, Long> e : missing.entrySet()) {
			String id = e.getKey();
			if (p.byId(id) == null) continue;
			double coins = e.getValue() * valueOf.applyAsDouble(id);
			String fam = family(id, p);
			net.merge(fam, coins, Double::sum);
			if (coins > topCoins.getOrDefault(fam, 0.0)) {
				topCoins.put(fam, coins);
				top.put(fam, id);
			}
		}
		List<Fix> out = new ArrayList<>();
		for (Map.Entry<String, Double> e : net.entrySet()) {
			String id = top.get(e.getKey());
			if (e.getValue() <= 0 || id == null) continue;
			double value = valueOf.applyAsDouble(id);
			if (value <= 0) continue;
			out.add(new Fix(p.byId(id), e.getValue() / value, e.getValue()));
		}
		return out;
	}

	private static String family(String id, Prices p) {
		Prices.Item item = p.byId(id);
		return item != null && item.family() != null ? item.family() : id;
	}

	private static double value(String id, Prices p) {
		Prices.Item item = p.byId(id);
		return item != null ? item.value() : 0;
	}

	/**
	 * After the previous game's session was checked: the API counts now become the base of the
	 * new session. What the check found missing stays for Düzelt in /kazanc gecmis (it belongs to the ended
	 * session).
	 */
	public void rebase(Map<String, Long> counts, long now) {
		Map<String, Long> keepMissing = missing;
		boolean keepEnded = missingEnded;
		Double keepCoins = endedCoins;
		long keepId = endedId;
		String keepReport = lastReport;
		setBase(counts, now);
		missing = keepMissing == null ? new HashMap<>() : keepMissing;
		missingEnded = keepEnded;
		missingBeforeBase = true;
		endedCoins = keepCoins;
		endedId = keepId;
		lastReport = keepReport;
	}

	/** Takes the missing items to add; they count as read from now on, so they are added once. */
	public Map<String, Long> takeMissing() {
		Map<String, Long> out = missing == null ? Map.of() : missing;
		// Missing items of the previous game are already in the new base: not read again.
		if (!missingBeforeBase) out.forEach((id, n) -> read.merge(id, n, Long::sum));
		missing = new HashMap<>();
		lastReport = null;
		// What was put in by hand is in what Düzelt took (or left out): not listed again.
		hand = new HashMap<>();
		handFrom = 0;
		handTo = 0;
		return out;
	}

	public boolean hasBase() {
		return base != null;
	}

	public void setBase(Map<String, Long> counts, long now) {
		base = new HashMap<>(counts);
		baseAt = now;
		read = new HashMap<>();
		corpse = new HashMap<>();
		missing = new HashMap<>();
		missingEnded = false;
		missingBeforeBase = false;
		previousPending = false;
		endedCoins = null;
		endedId = 0;
		lastReport = null;
		clearHand();
	}

	/** Forgets the items put in by hand (new base, or Düzelt took the difference). */
	public void clearHand() {
		handPending = new HashMap<>();
		handPendingAt = 0;
		hand = new HashMap<>();
		handFrom = 0;
		handTo = 0;
		stashItems = 0;
		stashFrom = 0;
		stashTo = 0;
	}

	/** A mining item from corpse loot (it goes into the sacks; a sack message may or may not follow). */
	public void onCorpseItem(String id, long count) {
		if (base == null || count <= 0) return;
		corpse.merge(id, count, Long::sum);
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
				if (amount == 0) continue;
				read.merge(item.id(), amount, Long::sum);
				// Put in by hand and shown here: the message was not lost.
				Long hp = handPending.get(item.id());
				if (amount > 0 && hp != null) {
					if (hp > amount) handPending.put(item.id(), hp - amount);
					else handPending.remove(item.id());
				}
				// A sack message showing corpse items: they are in what was read now.
				Long pending = corpse.get(item.id());
				if (amount > 0 && pending != null) {
					long left = pending - Math.min(pending, amount);
					if (left > 0) corpse.put(item.id(), left);
					else corpse.remove(item.id());
				}
			}
		}
	}

	/** One item whose API change is not what the messages said. */
	public record Diff(String id, long api, long read) {
		public long missing() {
			return api - read;
		}
	}

	public record Result(boolean apiUnchanged, List<Diff> diffs, long matched) {
		/** Same text for the same differences, to see that a check found nothing new. */
		public String key() {
			StringBuilder b = new StringBuilder();
			for (Diff d : diffs) b.append(d.id()).append(':').append(d.api()).append('/').append(d.read()).append(';');
			return b.toString();
		}
	}

	/** Compares the API counts now with the base and what was read; only mining items. */
	public Result compare(Map<String, Long> now, Prices p) {
		Map<String, Long> seen = new HashMap<>(read);
		corpse.forEach((id, n) -> seen.merge(id, n, Long::sum));
		TreeSet<String> ids = new TreeSet<>(seen.keySet());
		ids.addAll(corpse.keySet());
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
			long lo = read.getOrDefault(id, 0L);
			long hi = seen.getOrDefault(id, 0L);
			// Corpse loot may have gone into the inventory (sacks full, or items the sacks do not take,
			// like Flawless gems): anything between "without" and "with" the corpse items is right.
			if (api >= Math.min(lo, hi) && api <= Math.max(lo, hi)) matched++;
			else diffs.add(new Diff(id, api, api > hi ? hi : lo));
		}
		// Nothing changed in the API although sack messages came: Hypixel has not saved yet.
		boolean behind = unchanged && !seen.isEmpty();
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
		// v2 puts sack counts at members[uuid].inventory.sacks_counts (older answers: members[uuid].sacks_counts),
		// the same place the site reads them from.
		JsonObject sacks = null;
		JsonObject inv = obj(member, "inventory");
		if (inv != null) sacks = obj(inv, "sacks_counts");
		if (sacks == null) sacks = obj(member, "sacks_counts");
		if (sacks == null) {
			if (inv == null) {
				String profile = chosen.has("cute_name") ? chosen.get("cute_name").getAsString() + " profilinin" : "Profilin";
				throw new IllegalStateException("API " + profile + " envanterini vermedi. Oyunda SkyBlock Menu → Settings → API Settings'te Inventory API açık olmalı (yeni açtıysan birkaç dakika bekle).");
			}
			// Inventory API is on but no sack counts at all: the sacks are empty.
			return new HashMap<>();
		}
		Map<String, Long> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : sacks.entrySet()) {
			if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsLong());
		}
		return out;
	}

	private static JsonObject obj(JsonObject o, String name) {
		JsonElement e = o.get(name);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	/** Hypixel's error text in Turkish. */
	public static String reason(String cause) {
		String c = cause.toLowerCase(java.util.Locale.ROOT);
		if (c.contains("invalid api key")) return "API anahtarı geçersiz ya da süresi bitmiş. Yenisini /kazanc api <anahtar> ile gir.";
		if (c.contains("throttle") || c.contains("limit")) return "Hypixel çok fazla istek dedi, biraz sonra tekrar dene.";
		return "Hypixel hata verdi: " + cause;
	}
}
