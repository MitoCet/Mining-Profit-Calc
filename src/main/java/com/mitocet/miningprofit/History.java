package com.mitocet.miningprofit;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every ended session (Mito, 2026-11-13): what it earned, from what, and what the Hypixel API
 * sack check said about it. Toplam is the sum of these plus the session running now, so deleting
 * one takes it out of Toplam too. Kept in the config file with Gson; no Minecraft classes, so it
 * can be tested on its own.
 */
public final class History {
	/** Newest last. */
	public List<Session> sessions = new ArrayList<>();

	public static final class Session {
		/** When it started (ms); also its id. */
		public long start;
		public long end;
		/** Counted time (idle time left out, as in the box). */
		public long ms;
		/** Raw earnings like Tracker.sessionCoins (keys are taken off only when shown, see Tracker.net). */
		public double coins;
		public Breakdown breakdown = new Breakdown();
		/** The last API sack check of this session; null = none. */
		public Check check;
	}

	/** What an API sack check found. */
	public static final class Check {
		public long at;
		/** Items whose API change matched what the mod read. */
		public long matched;
		/** Earnings the mod counted when checked. */
		public double modCoins;
		/** Value of the differences (API - mod), netted per family; only families that went up (never taken off). */
		public double diffCoins;
		public List<Item> items = new ArrayList<>();
		/** Items to add with Düzelt (differences of the families that went up, item id -> count). Empty after adding. */
		public Map<String, Long> missing = new HashMap<>();
		/** Düzelt added this much (0 = not fixed). */
		public double fixedCoins;
		/** missing and diffCoins are netted per family (checks saved before v1.0.34 were not). */
		public boolean netted;
		/** Put into the sacks by hand and no sack message showed it (see SackCheck.hand); count = amount. */
		public List<Item> hand = new ArrayList<>();
		public long handFrom;
		public long handTo;
		/** Last material stash pickup before the check (0 = none): went into the sacks, amount unknown. */
		public long stashAt;
		/** Families whose hand-put items are taken off Düzelt (clicked in /kazanc gecmis). */
		public java.util.Set<String> handOff = new java.util.HashSet<>();

		/** Family key of an item as the Fark list groups it. */
		static String famKey(Item i) {
			return i.family != null ? i.family : "?" + i.id;
		}

		/** Hand-put items of a family (empty if none). */
		public List<Item> handOf(String fam) {
			List<Item> out = new ArrayList<>();
			if (hand != null) for (Item h : hand) if (famKey(h).equals(fam)) out.add(h);
			return out;
		}

		public boolean handOff(String fam) {
			return handOff != null && handOff.contains(fam);
		}

		/** Click in /kazanc gecmis: take the family's hand-put items off Düzelt, or count them again. */
		public void toggleHand(String fam) {
			if (handOff == null) handOff = new java.util.HashSet<>();
			if (!handOff.remove(fam)) handOff.add(fam);
		}

		/**
		 * {@code m} (item id -> count to add) less the hand-put items of the families clicked off.
		 * A family that is then not up any more is left out (earnings never go down).
		 */
		public Map<String, Long> withoutHand(Map<String, Long> m) {
			if (m == null) return new HashMap<>();
			if (hand == null || hand.isEmpty() || handOff == null || handOff.isEmpty()) return m;
			Map<String, String> fam = new HashMap<>();
			Map<String, Double> val = new HashMap<>();
			for (Item i : items) {
				fam.put(i.id, famKey(i));
				val.put(i.id, i.value);
			}
			java.util.Set<String> up = new java.util.HashSet<>();
			for (String id : m.keySet()) up.add(fam.getOrDefault(id, "?" + id));
			Map<String, Long> out = new HashMap<>(m);
			for (Item h : hand) {
				String f = famKey(h);
				if (!handOff.contains(f) || !up.contains(f)) continue;
				fam.putIfAbsent(h.id, f);
				val.putIfAbsent(h.id, h.value);
				out.merge(h.id, -h.api, Long::sum);
			}
			Map<String, Double> net = new HashMap<>();
			out.forEach((id, n) -> net.merge(fam.getOrDefault(id, "?" + id), n * val.getOrDefault(id, 0.0), Double::sum));
			out.entrySet().removeIf(e -> e.getValue() == 0 || net.get(fam.getOrDefault(e.getKey(), "?" + e.getKey())) < 1);
			return out;
		}

		/** Value of one item, from the check (hand-put items included). */
		double valueOf(String id) {
			for (Item i : items) if (i.id.equals(id)) return i.value;
			if (hand != null) for (Item h : hand) if (h.id.equals(id)) return h.value;
			return 0;
		}

		/** Coins the clicked-off hand-put items take off Düzelt. */
		public double handOffCoins() {
			return rawMissingCoins() - missingCoins();
		}

		private double rawMissingCoins() {
			double c = 0;
			if (missing == null) return 0;
			for (Map.Entry<String, Long> e : missing.entrySet()) c += e.getValue() * valueOf(e.getKey());
			return c;
		}

		/**
		 * A check saved by an older version kept every positive difference for Düzelt and summed all
		 * differences for the Fark, so the two did not agree. Nets them per family again, with the
		 * values stored at check time. Only while nothing was added from it.
		 */
		public boolean renet(Prices p) {
			if (netted || !p.loaded()) return false;
			netted = true;
			if (fixedCoins > 0 || items.isEmpty()) return true;
			List<SackCheck.Diff> diffs = new ArrayList<>();
			Map<String, Double> values = new HashMap<>();
			for (Item i : items) {
				diffs.add(new SackCheck.Diff(i.id, i.api, i.read));
				values.put(i.id, i.value);
			}
			missing = SackCheck.familyMissing(diffs, p, id -> values.getOrDefault(id, 0.0));
			diffCoins = missingCoins();
			return true;
		}

		/** Checks saved before v1.0.35 have no family on their items: fills it from the prices. */
		public void fillFamilies(Prices p) {
			if (!p.loaded()) return;
			for (Item i : items) {
				if (i.family != null) continue;
				Prices.Item item = p.byId(i.id);
				if (item != null) i.family = item.family();
			}
		}

		/** Coins Düzelt would add now (hand-put items clicked off are left out). */
		public double missingCoins() {
			double c = 0;
			for (Map.Entry<String, Long> e : withoutHand(missing).entrySet()) c += e.getValue() * valueOf(e.getKey());
			return c;
		}
	}

	public static final class Item {
		public String id;
		public String name;
		public long api;
		public long read;
		/** Value of one at check time. */
		public double value;
		/** Family (fam in mod-values.json) for grouping in the menu; null in checks saved before v1.0.35. */
		public String family;
	}

	public Session find(long start) {
		for (Session s : sessions) if (s.start == start) return s;
		return null;
	}

	/** Newest first, for the menu. */
	public List<Session> newestFirst() {
		List<Session> out = new ArrayList<>(sessions);
		out.sort((a, b) -> Long.compare(b.start, a.start));
		return out;
	}

	/** Builds a check result from an API comparison. Prices give names and values. */
	public static Check check(SackCheck.Result r, double modCoins, Prices p, long now) {
		Check c = new Check();
		c.at = now;
		c.matched = r.matched();
		c.modCoins = modCoins;
		for (SackCheck.Diff d : r.diffs()) {
			Prices.Item item = p.byId(d.id());
			Item i = new Item();
			i.id = d.id();
			i.name = item != null && item.name() != null ? item.name() : d.id();
			i.api = d.api();
			i.read = d.read();
			i.value = item != null ? item.value() : 0;
			i.family = item != null ? item.family() : null;
			c.items.add(i);
		}
		c.missing = SackCheck.familyMissing(r.diffs(), p);
		c.diffCoins = SackCheck.missingCoins(c.missing, p);
		c.netted = true;
		return c;
	}

	/**
	 * Adds what was put into the sacks by hand (and no sack message showed) to a check. The families
	 * clicked off in the check it replaces stay off; with {@code allOff} (Oto düzelt) all are off.
	 */
	public static void addHand(Check c, SackCheck sc, Check before, boolean allOff, Prices p) {
		if (sc.hand != null) {
			for (Map.Entry<String, Long> e : sc.hand.entrySet()) {
				Prices.Item item = p.byId(e.getKey());
				Item h = new Item();
				h.id = e.getKey();
				h.name = item != null && item.name() != null ? item.name() : e.getKey();
				h.api = e.getValue();
				h.value = item != null ? item.value() : 0;
				h.family = item != null ? item.family() : null;
				c.hand.add(h);
			}
		}
		c.hand.sort((a, b) -> Double.compare(b.api * b.value, a.api * a.value));
		c.handFrom = sc.handFrom;
		c.handTo = sc.handTo;
		c.stashAt = sc.stashAt;
		if (before != null && before.handOff != null) c.handOff.addAll(before.handOff);
		if (allOff) for (Item h : c.hand) c.handOff.add(Check.famKey(h));
	}

	/** Short state for the list: "✔ sack ile aynı", "⚠ sack farkı +45,2k", "kontrol yok"… */
	public static String status(Check c) {
		if (c == null) return "kontrol yok";
		if (c.fixedCoins > 0 && c.missingCoins() <= 0) return "✔ düzeltildi (+" + Format.coins(c.fixedCoins) + ")";
		if (c.items.isEmpty()) return "✔ sack ile aynı";
		if (c.diffCoins <= 0 && c.fixedCoins <= 0) return "✔ eksik yok";
		return "⚠ sack farkı " + (c.diffCoins >= 0 ? "+" : "") + Format.coins(c.diffCoins);
	}

	private static final String[] MONTHS = {"Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"};

	/** "13 Kas 21:05" in this computer's time zone. */
	public static String date(long ms) {
		ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
		return t.getDayOfMonth() + " " + MONTHS[t.getMonthValue() - 1] + " " + clock(ms);
	}

	/** "21:05". */
	public static String clock(long ms) {
		ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
		return String.format(java.util.Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
	}

	// ---------- what the menu shows ----------
	public static final int HEAD = 0xFFFFD24A;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int MUTED = 0xFFAAAAAA;
	public static final int GOOD = 0xFF6BE36B;
	public static final int BAD = 0xFFFF6B6B;
	/** Category headings of the Fark list. */
	public static final int CAT = 0xFF7FD4FF;
	/** Items put into the sacks by hand. */
	public static final int HAND = 0xFFFFA040;

	/** One line of the detail pane: text on the left, a value on the right (may be empty). */
	public record Line(String text, String right, int color, int rightColor, String click) {
		/** {@code click}: the family a click on this line toggles (hand-put items), null = none. */
		public Line(String text, String right, int color, int rightColor) {
			this(text, right, color, rightColor, null);
		}

		static Line of(String text, int color) {
			return new Line(text, "", color, color);
		}

		static Line kv(String text, String right, int rightColor) {
			return new Line(text, right, TEXT, rightColor);
		}
	}

	/** List color of a check state. */
	public static int statusColor(Check c) {
		if (c == null) return MUTED;
		if (c.fixedCoins > 0 && c.missingCoins() <= 0) return HEAD;
		return c.items.isEmpty() ? GOOD : BAD;
	}

	/**
	 * The detail pane of a session: times, earnings, what it was earned from (as in the big box)
	 * and the sack check. {@code title} is the first line; keys as /kazanc anahtar.
	 */
	public static List<Line> lines(Session s, String title, boolean keys) {
		List<Line> out = new ArrayList<>();
		double coins = Tracker.net(s.coins, s.breakdown, keys);
		Breakdown bd = s.breakdown.view(keys);
		out.add(Line.of(title, HEAD));
		out.add(Line.kv("Kazanç", Format.count(coins), GOOD));
		out.add(Line.kv("Saatlik", Format.count(Tracker.perHour(coins, s.ms)), TEXT));
		out.add(Line.of("", TEXT));
		out.add(Line.of("Ne kazandım", HEAD));
		List<String> cats = bd.categories();
		if (cats.isEmpty()) out.add(Line.of("  Kazanç yok", MUTED));
		for (String cat : cats) {
			out.add(Line.kv(cat, Format.count(bd.total(cat)), GOOD));
			for (Map.Entry<String, Breakdown.Row> r : bd.rows(cat)) out.add(Line.of("  " + Breakdown.rowText(r.getKey(), r.getValue()), MUTED));
		}
		String keyText = bd.keyText();
		if (keyText != null) out.add(Line.of(keyText, BAD));
		out.add(Line.of("", TEXT));
		out.add(Line.of("Sack kontrolü (API)", HEAD));
		Check c = s.check;
		if (c == null) {
			out.add(Line.of("  Kontrol yapılmadı (API anahtarı yoktu ya da Hypixel henüz güncellemedi).", MUTED));
			return out;
		}
		out.add(Line.kv("Kontrol zamanı", clock(c.at), MUTED));
		out.add(Line.kv("Mod saydı", Format.count(c.modCoins), TEXT));
		if (c.items.isEmpty()) {
			out.add(Line.of("  ✔ Sack ile aynı (" + c.matched + " item tutuyor)", GOOD));
		} else {
			out.add(Line.kv("API'ye göre gerçek", "~" + Format.count(c.modCoins + c.diffCoins), TEXT));
			out.add(new Line("Fark (" + c.items.size() + " item, " + c.matched + " item tutuyor)",
				(c.diffCoins >= 0 ? "+" : "") + Format.count(c.diffCoins), BAD, BAD));
			handWarning(out, c);
			fark(out, c);
		}
		if (c.fixedCoins > 0) out.add(Line.of("  ✔ Düzelt ile eklendi: +" + Format.count(c.fixedCoins), GOOD));
		double m = c.missingCoins();
		double off = c.handOffCoins();
		if (m > 0) out.add(Line.of("  Düzelt ile +" + Format.count(m) + " eklenebilir"
			+ (off >= 1 ? " (elle konan " + Format.coins(off) + " çıkarıldı)." : "."), HEAD));
		return out;
	}

	/** Tells what was put into the sacks by hand without a sack message (Mito: say what and how many). */
	static void handWarning(List<Line> out, Check c) {
		if (c.hand != null && !c.hand.isEmpty()) {
			String when = c.handFrom > 0 ? " (" + clock(c.handFrom) + (c.handTo > 0 && !clock(c.handTo).equals(clock(c.handFrom)) ? "–" + clock(c.handTo) : "") + ")" : "";
			out.add(Line.of("  ⚠ Sack'e elle koydukların" + when + ":", HAND));
			StringBuilder b = new StringBuilder();
			int n = 0;
			for (Item h : c.hand) {
				if (n > 0) b.append(" · ");
				b.append(h.name).append(": ").append(Format.count(h.api));
				if (++n == 2) {
					out.add(Line.of("    " + b, HAND));
					b.setLength(0);
					n = 0;
				}
			}
			if (n > 0) out.add(Line.of("    " + b, HAND));
			out.add(Line.of("    Sack mesajında görünmediler (sunucu değişti), Fark'a", HAND));
			out.add(Line.of("    girmiş olabilirler. Çıkarmak için \"elle konmuş\" yazan aileye tıkla.", HAND));
		}
		if (c.stashAt > 0) {
			out.add(Line.of("  ⚠ Stash'ten sack'e aktarıldı (" + clock(c.stashAt) + "), miktarı görülemiyor;", HAND));
			out.add(Line.of("    Fark'ın bir kısmı ondan olabilir.", HAND));
		}
	}

	/**
	 * The Fark list grouped like "Ne kazandım": category (Gemstone, Dwarven Metal, Diğer, Ore…) →
	 * family with its net (Glacite + Enchanted Glacite) → its items. Netting is per family, so the
	 * family line is what Düzelt adds; a family that went down shows "mod fazla · çıkarılmaz". Biggest first.
	 */
	static void fark(List<Line> out, Check c) {
		Map<String, List<Item>> fams = new LinkedHashMap<>();
		for (Item i : c.items) fams.computeIfAbsent(i.family != null ? i.family : "?" + i.id, k -> new ArrayList<>()).add(i);
		Map<String, Double> net = new HashMap<>();
		Map<String, Double> catTotal = new HashMap<>();
		Map<String, List<String>> cats = new LinkedHashMap<>();
		for (Map.Entry<String, List<Item>> e : fams.entrySet()) {
			double v = 0;
			for (Item i : e.getValue()) v += (i.api - i.read) * i.value;
			net.put(e.getKey(), v);
			String cat = e.getKey().startsWith("?") ? Breakdown.OTHER : Breakdown.category(e.getKey());
			cats.computeIfAbsent(cat, k -> new ArrayList<>()).add(e.getKey());
			if (counted(c, e.getValue())) catTotal.merge(cat, Math.max(0, v - handOffCoins(c, e.getKey())), Double::sum);
		}
		List<String> catOrder = new ArrayList<>(cats.keySet());
		catOrder.sort((a, b) -> Double.compare(catTotal.getOrDefault(b, 0.0), catTotal.getOrDefault(a, 0.0)));
		for (String cat : catOrder) {
			double t = catTotal.getOrDefault(cat, 0.0);
			out.add(new Line("  " + cat, t > 0 ? "+" + Format.count(t) : "", CAT, CAT));
			List<String> famOrder = cats.get(cat);
			famOrder.sort((a, b) -> {
				boolean ua = counted(c, fams.get(a)), ub = counted(c, fams.get(b));
				if (ua != ub) return ua ? -1 : 1;
				return Double.compare(net.get(b), net.get(a));
			});
			for (String fam : famOrder) {
				List<Item> items = fams.get(fam);
				items.sort((a, b) -> Double.compare(a.value, b.value));
				boolean up = counted(c, items);
				double v = net.get(fam);
				String name = fam.startsWith("?") ? items.get(0).name : Breakdown.rowName(fam);
				List<Item> hand = c.handOf(fam);
				boolean off = up && !hand.isEmpty() && c.handOff(fam);
				double handCoins = off ? handOffCoins(c, fam) : 0;
				String tag = !up || hand.isEmpty() ? "" : off ? " §6elle konan çıkarıldı · tıkla: geri al" : " §6elle konmuş · tıkla: çıkar";
				double shown = v - handCoins;
				String right = up ? (shown >= 0 ? "+" : "") + Format.coins(Math.max(0, shown)) : "mod fazla · çıkarılmaz";
				out.add(new Line("    " + name + tag, right, TEXT, up ? GOOD : MUTED, up && !hand.isEmpty() ? fam : null));
				for (Item i : items) {
					long d = i.api - i.read;
					String unit = fam.startsWith("?") ? "" : Breakdown.unit(i.id, fam);
					String label = unit.isEmpty() ? i.name : unit;
					String worth = up ? " · " + (d >= 0 ? "+" : "") + Format.coins(d * i.value) : "";
					out.add(Line.of("      " + label + ": gerçek " + signed(i.api) + ", mod " + signed(i.read) + worth, MUTED));
				}
				if (off) {
					StringBuilder b = new StringBuilder();
					for (Item h : hand) b.append(b.length() > 0 ? ", " : "").append(Format.count(h.api)).append(' ').append(h.name);
					out.add(new Line("      − Elle konan: " + b, "−" + Format.coins(handCoins), HAND, HAND, fam));
				}
			}
		}
	}

	/** Coins the family's clicked-off hand-put items take off what Düzelt adds (up to the family's net). */
	static double handOffCoins(Check c, String fam) {
		if (!c.handOff(fam)) return 0;
		Map<String, Long> fm = new HashMap<>();
		if (c.missing != null) c.missing.forEach((id, n) -> {
			for (Item i : c.items) if (i.id.equals(id) && Check.famKey(i).equals(fam)) fm.put(id, n);
		});
		double raw = 0;
		for (Map.Entry<String, Long> e : fm.entrySet()) raw += e.getValue() * c.valueOf(e.getKey());
		double left = 0;
		for (Map.Entry<String, Long> e : c.withoutHand(fm).entrySet()) left += e.getValue() * c.valueOf(e.getKey());
		return raw - left;
	}

	/** Whether Düzelt adds this family (it went up; netted, so every item of it is in missing). */
	private static boolean counted(Check c, List<Item> items) {
		if (c.missing == null) return false;
		for (Item i : items) if (c.missing.containsKey(i.id)) return true;
		return false;
	}

	static String signed(long n) {
		return (n > 0 ? "+" : "") + Format.count(n);
	}
}
