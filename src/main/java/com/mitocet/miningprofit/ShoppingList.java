package com.mitocet.miningprofit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The forge plan from the site (Mining → Forge planı → "Mod için kopyala", read from the clipboard by
 * /kazanc forge yukle) and how much of each item the player has now.
 *
 * Rows: c = craft (click opens /viewrecipe), b = buy (click opens /bz and copies the amount),
 * x = crystal (can't be bought, must be held), h = already enough. The same item can have a craft
 * and a buy row (craft what can be made, buy the rest). target = how many must be held for the row
 * to be done; a craft row of a lower form (Rough → Flawed for a Fine) is also done once its parent is.
 *
 * Held = sacks (Hypixel API at load, then every sack message) + ender chest, backpacks and personal
 * vault (API at load, replaced by what is read each time one of them is open) + the inventory (live).
 * Kept apart from earnings counting: it works whether a session runs or not.
 */
public final class ShoppingList {
	public static final String PREFIX = "MPC-PLAN1 ";
	private static final Pattern ENDER = Pattern.compile("^Ender Chest(?: \\((\\d+)/\\d+\\))?$");
	private static final Pattern BACKPACK = Pattern.compile("^.*Backpack \\(Slot #(\\d+)\\)$");
	private static final Pattern MOVED = Pattern.compile("^Moved ([\\d,]+) (.+?) from your Sacks to your inventory\\.?$");
	/** Ender chest slots per page in the API blob (the menu's first row is navigation). */
	static final int EC_PAGE = 45;

	public record Forge(String id, String name, int count) {}

	public record Row(char action, String id, String name, long n, long target, String parent, String market) {}

	public final List<Forge> forge = new ArrayList<>();
	public final List<Row> rows = new ArrayList<>();
	public long at;

	private final transient Map<String, String> idByKey = new HashMap<>();
	private final Map<String, Long> sacks = new HashMap<>();
	private final Map<String, Map<String, Long>> storage = new HashMap<>();
	private final Map<String, Long> inventory = new HashMap<>();
	/** True once the API's sacks and storage were read for this list. */
	public boolean baseLoaded;
	/** What was put into the forge since this plan was loaded; kept in the config (survives restarts). */
	public Placed placed = new Placed();

	/**
	 * Forge slots filled for the plan, read from The Forge menu (like NotEnoughUpdates' MiningOverlay, which reads
	 * the slots whose lore says "Time Remaining:"; LGPL-3.0). Jobs already in the forge the first time the menu is
	 * seen after loading are not counted; once such a slot is seen empty (claimed), a new job there counts.
	 */
	public static final class Placed {
		/** Menu slot -> item key of the jobs already there at the first look after loading; null = forge not seen yet. */
		public Map<String, String> base;
		/** Item key -> how many of the plan's slots are filled (never goes down, at most the plan's count). */
		public Map<String, Integer> done = new HashMap<>();
		/** Material rows that had enough once: they stay ✔ after the forge has used the items up. */
		public Set<Integer> rowsDone = new HashSet<>();
	}

	/** Reads the text the site copied. Throws IllegalArgumentException with a message for the player. */
	public static ShoppingList parse(String text) {
		if (text == null) throw new IllegalArgumentException("Pano boş.");
		String t = text.trim();
		if (!t.startsWith(PREFIX.trim())) {
			throw new IllegalArgumentException("Panoda forge planı yok. Sitede Forge planı sekmesi → 'Mod için kopyala'ya bas, sonra tekrar dene.");
		}
		JsonObject o;
		try {
			o = JsonParser.parseString(t.substring(PREFIX.trim().length()).trim()).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Panodaki plan okunamadı (eksik kopyalanmış olabilir). Sitede tekrar 'Mod için kopyala'ya bas.");
		}
		ShoppingList l = new ShoppingList();
		l.at = o.has("at") ? o.get("at").getAsLong() : 0;
		for (JsonElement e : arr(o, "forge")) {
			JsonArray a = e.getAsJsonArray();
			l.forge.add(new Forge(a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsInt()));
		}
		for (JsonElement e : arr(o, "rows")) {
			JsonArray a = e.getAsJsonArray();
			String act = a.get(0).getAsString();
			l.rows.add(new Row(act.isEmpty() ? 'h' : act.charAt(0), a.get(1).getAsString(), a.get(2).getAsString(),
				a.get(3).getAsLong(), a.get(4).getAsLong(), a.size() > 5 ? a.get(5).getAsString() : "", a.size() > 6 ? a.get(6).getAsString() : ""));
		}
		if (l.rows.isEmpty()) throw new IllegalArgumentException("Plan boş: sitede en az bir slota item seç.");
		l.index();
		return l;
	}

	private static JsonArray arr(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
	}

	/** Name lookups; called after parsing and after loading from the config. */
	public void index() {
		idByKey.clear();
		for (Row r : rows) idByKey.put(Prices.key(r.name), r.id);
		for (Forge f : forge) idByKey.put(Prices.key(f.name), f.id);
	}

	private String idOf(String name) {
		if (idByKey.isEmpty()) index();
		return idByKey.get(Prices.key(name));
	}

	private boolean wanted(String id) {
		for (Row r : rows) if (r.id.equals(id)) return true;
		return false;
	}

	// ---------- what is held ----------

	/** Sacks and storage from the Hypixel profile API; sack messages after this are added on top. */
	public void setBase(Map<String, Long> sackCounts, Map<String, Map<String, Long>> storageCounts) {
		sacks.clear();
		for (Row r : rows) sacks.put(r.id, sackCounts.getOrDefault(r.id, 0L));
		// A page read in game while the list was loaded is newer than the API.
		for (Map.Entry<String, Map<String, Long>> e : storageCounts.entrySet()) storage.putIfAbsent(e.getKey(), e.getValue());
		baseLoaded = true;
	}

	/** One sack message, with all its hover texts ("+5 Fine Peridot Gemstone (Gemstones Sack)"). */
	public void onSackMessage(List<String> hoverTexts) {
		for (String hover : new LinkedHashSet<>(hoverTexts)) {
			for (String line : hover.split("\n")) {
				Matcher m = Tracker.SACK_LINE.matcher(Tracker.strip(line));
				if (!m.matches()) continue;
				String id = idOf(m.group(2));
				if (id == null) continue;
				long n = Long.parseLong(m.group(1).replace(",", "").replace(".", "").replace("+", ""));
				sacks.merge(id, n, Long::sum);
			}
		}
	}

	/** A chat line: "Moved 64 X from your Sacks to your inventory." (/gfs) has no sack message. */
	public void onChatLine(String stripped) {
		Matcher m = MOVED.matcher(stripped.trim());
		if (!m.matches()) return;
		String id = idOf(m.group(2));
		if (id != null) sacks.merge(id, -Long.parseLong(m.group(1).replace(",", "")), Long::sum);
	}

	/** The inventory now: display name → count per stack. */
	public void setInventory(List<Map.Entry<String, Integer>> stacks) {
		inventory.clear();
		for (Map.Entry<String, Integer> s : stacks) {
			String id = idOf(s.getKey());
			if (id != null) inventory.merge(id, (long) s.getValue(), Long::sum);
		}
	}

	/** Which storage a menu title is ("ec:2", "bp:5", "vault"), null when it isn't one. */
	public static String storageKey(String title) {
		String t = Tracker.strip(title).trim();
		Matcher m = ENDER.matcher(t);
		if (m.matches()) return "ec:" + (m.group(1) == null ? "1" : m.group(1));
		m = BACKPACK.matcher(t);
		if (m.matches()) return "bp:" + m.group(1);
		if (t.equals("Personal Vault")) return "vault";
		return null;
	}

	/** What an open ender chest page, backpack or vault holds (the menu's own slots only). */
	public void setStoragePage(String key, List<Map.Entry<String, Integer>> stacks) {
		Map<String, Long> page = new HashMap<>();
		for (Map.Entry<String, Integer> s : stacks) {
			String id = idOf(s.getKey());
			if (id != null) page.merge(id, (long) s.getValue(), Long::sum);
		}
		storage.put(key, page);
	}

	public long owned(String id) {
		long n = Math.max(0, sacks.getOrDefault(id, 0L)) + inventory.getOrDefault(id, 0L);
		for (Map<String, Long> page : storage.values()) n += page.getOrDefault(id, 0L);
		return n;
	}

	/** What the parent's craft row needs held (the lower form is used up once that is made). */
	private long craftTargetOf(String id) {
		long t = 0;
		for (Row r : rows) if (r.id.equals(id) && r.action == 'c') t = Math.max(t, r.target);
		return t;
	}

	public boolean done(Row r) {
		if (owned(r.id) >= r.target) return true;
		// Putting items into the forge uses the materials up: a row that was enough stays enough.
		if (anyPlaced() && placed.rowsDone.contains(rows.indexOf(r))) return true;
		return !r.parent.isEmpty() && owned(r.parent) >= craftTargetOf(r.parent);
	}

	/** How much of this row is done: what is held beyond what the row starts from, at most n. */
	public long progress(Row r) {
		if (done(r)) return r.n;
		return Math.max(0, Math.min(r.n, owned(r.id) - (r.target - r.n)));
	}

	public boolean allDone() {
		for (Row r : rows) if (!done(r)) return false;
		return true;
	}

	// ---------- what is in the forge ----------

	private static final Pattern AMOUNT = Pattern.compile("^(?:\\d+x )?(.+?)(?: x\\d+)?$");

	private static String forgeKey(String name) {
		Matcher m = AMOUNT.matcher(Tracker.strip(name).trim());
		return Prices.key(m.matches() ? m.group(1) : name);
	}

	/** Slots the plan wants for this item (an item may be on two forge lines). */
	private int wanted(Forge f) {
		int n = 0;
		for (Forge o : forge) if (forgeKey(o.name).equals(forgeKey(f.name))) n += o.count;
		return n;
	}

	/**
	 * The Forge menu is open: menu slot number -> name of the item working there (lore "Time Remaining: …", done
	 * ones too). Returns true when the progress changed (to save it).
	 */
	public boolean onForgeMenu(Map<Integer, String> forging) {
		rememberRows();
		Map<String, String> now = new HashMap<>();
		for (Map.Entry<Integer, String> e : forging.entrySet()) now.put(String.valueOf(e.getKey()), forgeKey(e.getValue()));
		if (placed.base == null) {
			placed.base = new HashMap<>(now);
			return true;
		}
		// An old job that was claimed (slot empty or something else in it) is not old any more.
		boolean changed = placed.base.entrySet().removeIf((e) -> !e.getValue().equals(now.get(e.getKey())));
		Map<String, Integer> fresh = new HashMap<>();
		for (Map.Entry<String, String> e : now.entrySet()) {
			if (!placed.base.containsKey(e.getKey())) fresh.merge(e.getValue(), 1, Integer::sum);
		}
		for (Forge f : forge) {
			String k = forgeKey(f.name);
			int was = placed.done.getOrDefault(k, 0);
			int d = Math.min(wanted(f), Math.max(was, fresh.getOrDefault(k, 0)));
			if (d != was) {
				placed.done.put(k, d);
				changed = true;
			}
		}
		return changed;
	}

	/** Material rows that are enough now are remembered before the forge uses them up. */
	private void rememberRows() {
		for (int i = 0; i < rows.size(); i++) if (owned(rows.get(i).id) >= rows.get(i).target) placed.rowsDone.add(i);
	}

	/** How many of this forge line's slots are filled. Two lines of the same item share the count in order. */
	public int placed(Forge f) {
		String k = forgeKey(f.name);
		int left = placed.done.getOrDefault(k, 0);
		for (Forge o : forge) {
			if (!forgeKey(o.name).equals(k)) continue;
			int here = Math.min(o.count, left);
			if (o == f) return here;
			left -= here;
		}
		return 0;
	}

	public boolean anyPlaced() {
		for (int v : placed.done.values()) if (v > 0) return true;
		return false;
	}

	public boolean allPlaced() {
		if (forge.isEmpty()) return false;
		for (Forge f : forge) if (placed(f) < f.count) return false;
		return true;
	}

	/** Amount still to buy for a buy row (what is copied to the clipboard). */
	public long left(Row r) {
		return Math.max(0, r.n - progress(r));
	}

	// ---------- Hypixel profile API ----------

	/** Ender chest pages, backpacks and personal vault of the selected profile: key → id → amount. */
	public static Map<String, Map<String, Long>> parseStorage(String json, String uuid) {
		Map<String, Map<String, Long>> out = new HashMap<>();
		JsonObject member = member(json, uuid);
		JsonObject inv = member == null ? null : obj(member, "inventory");
		if (inv == null) return out;
		try {
			JsonObject ec = obj(inv, "ender_chest_contents");
			if (ec != null && ec.has("data")) {
				List<Map.Entry<String, Long>> slots = Nbt.slots(ec.get("data").getAsString());
				for (int i = 0; i < slots.size(); i++) {
					Map.Entry<String, Long> s = slots.get(i);
					if (s != null) out.computeIfAbsent("ec:" + (i / EC_PAGE + 1), (k) -> new HashMap<>()).merge(s.getKey(), s.getValue(), Long::sum);
				}
			}
			JsonObject bps = obj(inv, "backpack_contents");
			if (bps != null) {
				for (Map.Entry<String, JsonElement> e : bps.entrySet()) {
					if (!e.getValue().isJsonObject() || !e.getValue().getAsJsonObject().has("data")) continue;
					// API keys count from 0, the menu says "Slot #1".
					String key = "bp:" + (Integer.parseInt(e.getKey()) + 1);
					for (Map.Entry<String, Long> s : Nbt.slots(e.getValue().getAsJsonObject().get("data").getAsString())) {
						if (s != null) out.computeIfAbsent(key, (k) -> new HashMap<>()).merge(s.getKey(), s.getValue(), Long::sum);
					}
				}
			}
			JsonObject vault = obj(inv, "personal_vault_contents");
			if (vault != null && vault.has("data")) {
				for (Map.Entry<String, Long> s : Nbt.slots(vault.get("data").getAsString())) {
					if (s != null) out.computeIfAbsent("vault", (k) -> new HashMap<>()).merge(s.getKey(), s.getValue(), Long::sum);
				}
			}
		} catch (IOException | RuntimeException e) {
			// Storage is a bonus: sacks still count.
		}
		return out;
	}

	private static JsonObject member(String json, String uuid) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonElement list = root.get("profiles");
		if (list == null || !list.isJsonArray() || list.getAsJsonArray().isEmpty()) return null;
		JsonObject chosen = null;
		for (JsonElement e : list.getAsJsonArray()) {
			JsonObject o = e.getAsJsonObject();
			if (o.has("selected") && o.get("selected").getAsBoolean()) chosen = o;
		}
		if (chosen == null) chosen = list.getAsJsonArray().get(0).getAsJsonObject();
		JsonObject members = obj(chosen, "members");
		return members == null ? null : obj(members, uuid.replace("-", ""));
	}

	private static JsonObject obj(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	// ---------- what the box shows ----------

	/** One line of the list box; row is set on clickable lines. */
	public record Line(String text, String right, int color, Row row) {}

	public static final int HEAD = 0xFFFFAA00;
	public static final int SECTION = 0xFF55FFFF;
	public static final int MUTED = 0xFFAAAAAA;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int GOOD = 0xFF55FF55;
	public static final int TODO = 0xFFFFFF55;
	public static final int BAD = 0xFFFF5555;

	public List<Line> lines() {
		List<Line> out = new ArrayList<>();
		int slots = 0, in = 0;
		for (Forge f : forge) {
			slots += f.count;
			in += placed(f);
		}
		boolean all = allPlaced();
		out.add(new Line("Forge planı (" + slots + " slot)", "[x]", HEAD, null));
		if (all) out.add(new Line("✔ Hepsi forge'da", "", GOOD, null));
		else if (allDone()) out.add(new Line("✔ Hazır, forge'a koyabilirsin", "", GOOD, null));
		out.add(new Line("Forge'a koyulacak", in + "/" + slots, all ? GOOD : MUTED, null));
		for (Forge f : forge) {
			int p = placed(f);
			boolean ok = p >= f.count;
			out.add(new Line(f.count + "× " + f.name, p + "/" + f.count + (ok ? " ✔" : ""), ok ? GOOD : p > 0 ? TODO : TEXT, null));
		}
		// Everything is in the forge: the materials are not needed any more.
		if (all) return out;
		section(out, 'c', "Craftla (tıkla: tarif)");
		section(out, 'b', "Satın al (tıkla: Bazaar)");
		section(out, 'x', "Sende olmalı (satılmaz)");
		section(out, 'h', "Sende yeterli");
		if (!baseLoaded) out.add(new Line("Sack/storage API'den alınamadı: sadece envanter", "", BAD, null));
		return out;
	}

	private void section(List<Line> out, char action, String title) {
		boolean first = true;
		for (Row r : rows) {
			if (r.action != action) continue;
			if (first) out.add(new Line(title, "", SECTION, null));
			first = false;
			boolean ok = done(r);
			String right = action == 'h' || action == 'x'
				? Format.count(Math.min(owned(r.id), r.target)) + "/" + Format.count(r.target)
				: Format.count(progress(r)) + "/" + Format.count(r.n);
			long have = owned(r.id);
			int color = ok ? GOOD : action == 'h' && have < r.target ? BAD : TODO;
			out.add(new Line(r.name, right + (ok ? " ✔" : ""), color, action == 'c' || (action == 'b' && !r.market.equals("")) ? r : null));
		}
	}
}
