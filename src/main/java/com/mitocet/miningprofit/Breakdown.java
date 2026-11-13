package com.mitocet.miningprofit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What was earned from what, for the big box: categories (Gemstone, Dwarven Metal…), and in each
 * one a row per material (Ruby, Tungsten…) with counts per kind (Rough, Flawed, Enchanted…).
 * Kept with Gson between game restarts. No Minecraft classes, so it can be tested on its own.
 */
public final class Breakdown {
	public static final String GEMSTONE = "Gemstone";
	public static final String METAL = "Dwarven Metal";
	public static final String OTHER = "Diğer";
	public static final String FIESTA = "Fiesta";
	public static final String CORPSE = "Corpse Loot";
	/** Classic ores (Coal, Iron, Gold, Diamond, Lapis, Redstone, Emerald, Quartz, Obsidian). */
	public static final String ORE = "Ore";
	/**
	 * Loot of corpses that need a key (Tungsten/Umber/Vanguard), kept apart from Lapis loot: it
	 * only counts while key costs are on (/kazanc anahtar), and then shows under Corpse Loot.
	 */
	public static final String KEYED = "Corpse Loot (anahtarlı)";

	/** Order of the kinds inside a row: raw first, then Enchanted; gem tiers low to high. */
	private static final List<String> UNIT_ORDER = List.of("", "Block", "Enchanted", "Enchanted Block", "Rough", "Flawed", "Fine", "Flawless");

	public static final class Row {
		public double coins;
		public Map<String, Double> counts = new HashMap<>();
	}

	public Map<String, Map<String, Row>> cats = new HashMap<>();
	/** Corpse keys used (name -> count) and what they were worth when used. */
	public Map<String, Double> keys = new HashMap<>();
	public double keyCoins;

	public void clear() {
		cats.clear();
		keys = new HashMap<>();
		keyCoins = 0;
	}

	public void addKey(String name, int n, double coins) {
		if (keys == null) keys = new HashMap<>();
		keys.merge(name, (double) n, Double::sum);
		if (keys.get(name) <= 0) keys.remove(name);
		keyCoins = Math.max(0, keyCoins + coins);
	}

	/** "Anahtar: 2 Tungsten Key, 1 Umber Key · -8,4M" or null when no key was used. */
	public String keyText() {
		if (keys == null || keys.isEmpty()) return null;
		List<String> names = new ArrayList<>(keys.keySet());
		names.sort(null);
		StringBuilder sb = new StringBuilder("Anahtar:");
		boolean first = true;
		for (String n : names) {
			sb.append(first ? " " : ", ").append(Format.count(keys.get(n))).append(' ').append(n);
			first = false;
		}
		return sb.append(" · -").append(Format.coins(keyCoins)).toString();
	}

	private Row row(String cat, String row) {
		return cats.computeIfAbsent(cat, (k) -> new HashMap<>()).computeIfAbsent(row, (k) -> new Row());
	}

	public void addCount(String cat, String row, String unit, double count) {
		row(cat, row).counts.merge(unit, count, Double::sum);
	}

	public void addCoins(String cat, String row, double coins) {
		row(cat, row).coins += coins;
	}

	/**
	 * What the box shows: with key costs on, keyed corpse loot joins Corpse Loot; with them off
	 * it is left out (only Lapis corpses count then).
	 */
	public Breakdown view(boolean keys) {
		Breakdown v = new Breakdown();
		v.keys = keys ? keys() : new HashMap<>();
		v.keyCoins = keys ? keyCoins : 0;
		for (Map.Entry<String, Map<String, Row>> c : cats.entrySet()) {
			if (c.getKey().equals(KEYED) && !keys) continue;
			String cat = c.getKey().equals(KEYED) ? CORPSE : c.getKey();
			for (Map.Entry<String, Row> r : c.getValue().entrySet()) {
				Row to = v.row(cat, r.getKey());
				to.coins += r.getValue().coins;
				r.getValue().counts.forEach((u, n) -> to.counts.merge(u, n, Double::sum));
			}
		}
		return v;
	}

	private Map<String, Double> keys() {
		return keys == null ? new HashMap<>() : new HashMap<>(keys);
	}

	public double total(String cat) {
		Map<String, Row> rows = cats.get(cat);
		double t = 0;
		if (rows != null) for (Row r : rows.values()) t += r.coins;
		return t;
	}

	/** Categories that earned something, most coins first. */
	public List<String> categories() {
		List<String> out = new ArrayList<>();
		for (String c : cats.keySet()) if (total(c) > 0) out.add(c);
		out.sort(Comparator.comparingDouble(this::total).reversed());
		return out;
	}

	/** Rows of a category that earned something, most coins first. */
	public List<Map.Entry<String, Row>> rows(String cat) {
		List<Map.Entry<String, Row>> out = new ArrayList<>();
		Map<String, Row> rows = cats.get(cat);
		if (rows != null) for (Map.Entry<String, Row> e : rows.entrySet()) if (e.getValue().coins > 0) out.add(e);
		out.sort(Comparator.comparingDouble((Map.Entry<String, Row> e) -> e.getValue().coins).reversed());
		return out;
	}

	/** "Ruby: 1.200 Rough, 15 Flawed · 45,0k" or "Tungsten: 3.200, 12 Enchanted · 250,0k". */
	public static String rowText(String name, Row r) {
		List<String> units = new ArrayList<>(r.counts.keySet());
		units.sort(Comparator.comparingInt((String u) -> {
			int i = UNIT_ORDER.indexOf(u);
			return i < 0 ? UNIT_ORDER.size() : i;
		}));
		StringBuilder sb = new StringBuilder(name).append(':');
		boolean first = true;
		for (String u : units) {
			double n = r.counts.get(u);
			if (n <= 0) continue;
			sb.append(first ? " " : ", ").append(Format.count(n));
			if (!u.isEmpty()) sb.append(' ').append(u);
			first = false;
		}
		return sb.append(" · ").append(Format.coins(r.coins)).toString();
	}

	// ---------- where a mining item goes ----------
	/** Category of a mining family (fam in mod-values.json). */
	public static String category(String family) {
		if (family.startsWith("GEM_")) return GEMSTONE;
		if (family.startsWith("ORE_")) return ORE;
		return switch (family) {
			case "MITHRIL", "TITANIUM", "TUNGSTEN", "UMBER" -> METAL;
			case "GLOSSY_GEMSTONE", "REFINED_MINERAL" -> FIESTA;
			default -> OTHER; // Glacite, Hard Stone, Suspicious Scrap
		};
	}

	/** Row name of a family: GEM_RUBY → Ruby, HARD_STONE → Hard Stone. */
	public static String rowName(String family) {
		return title(family.startsWith("GEM_") || family.startsWith("ORE_") ? family.substring(4) : family);
	}

	/** Kind of an item inside its row: Rough/Flawed/Fine/Flawless for gems, Enchanted or raw ("") otherwise. */
	public static String unit(String id, String family) {
		if (family.startsWith("GEM_")) return title(id.substring(0, id.indexOf('_')));
		if (family.startsWith("ORE_")) {
			boolean ench = id.startsWith("ENCHANTED_");
			if (id.endsWith("_BLOCK")) return ench ? "Enchanted Block" : "Block";
			return ench ? "Enchanted" : "";
		}
		return id.startsWith("ENCHANTED_") ? "Enchanted" : "";
	}

	static String title(String id) {
		StringBuilder sb = new StringBuilder();
		for (String w : id.toLowerCase(Locale.ROOT).split("_")) {
			if (w.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}
}
