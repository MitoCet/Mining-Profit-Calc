package com.mitocet.miningprofit;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
		/** Value of the differences (API - mod); can be negative (never taken off). */
		public double diffCoins;
		public List<Item> items = new ArrayList<>();
		/** Items to add with Düzelt (positive differences, item id -> count). Empty after adding. */
		public Map<String, Long> missing = new HashMap<>();
		/** Düzelt added this much (0 = not fixed). */
		public double fixedCoins;

		/** Coins Düzelt would add now. */
		public double missingCoins() {
			double c = 0;
			if (missing == null) return 0;
			for (Item i : items) if (missing.containsKey(i.id)) c += missing.get(i.id) * i.value;
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
			c.items.add(i);
			c.diffCoins += d.missing() * i.value;
			if (d.missing() > 0) c.missing.put(d.id(), d.missing());
		}
		return c;
	}

	/** Short state for the list: "✔ sack ile aynı", "⚠ sack farkı +45,2k", "kontrol yok"… */
	public static String status(Check c) {
		if (c == null) return "kontrol yok";
		if (c.fixedCoins > 0 && c.missingCoins() <= 0) return "✔ düzeltildi (+" + Format.coins(c.fixedCoins) + ")";
		if (c.items.isEmpty()) return "✔ sack ile aynı";
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

	/** One line of the detail pane: text on the left, a value on the right (may be empty). */
	public record Line(String text, String right, int color, int rightColor) {
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
			for (Item i : c.items) {
				long d = i.api - i.read;
				out.add(Line.of("  " + i.name + ": gerçek " + signed(i.api) + ", mod " + signed(i.read) + " · "
					+ (d > 0 ? "+" + Format.coins(d * i.value) : "(düşürülmez)"), MUTED));
			}
		}
		if (c.fixedCoins > 0) out.add(Line.of("  ✔ Düzelt ile eklendi: +" + Format.count(c.fixedCoins), GOOD));
		double m = c.missingCoins();
		if (m > 0) out.add(Line.of("  Düzelt ile +" + Format.count(m) + " eklenebilir.", HEAD));
		return out;
	}

	static String signed(long n) {
		return (n > 0 ? "+" : "") + Format.count(n);
	}
}
