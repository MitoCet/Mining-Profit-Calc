package com.mitocet.miningprofit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts what mining earns. No Minecraft classes here, so it can be tested on its own.
 *
 * Two sources:
 * - Sack messages ("[Sacks] +1,234 items"): their hover text lists every item that went in or out.
 *   Only Mining tab materials and gemstones count. Items of one family (e.g. Tungsten and Enchanted
 *   Tungsten) are netted, so compacting 160 Tungsten into 1 Enchanted Tungsten adds nothing.
 *   A family that went down in total (taken out of the sacks) counts 0, never negative.
 * - Frozen corpse loot ("... CORPSE LOOT!" then "REWARDS"): every priced item counts, also those
 *   that land in the inventory. Mining items from the loot also show up in the next sack message,
 *   so they are remembered for a while and taken off that sack message.
 */
public final class Tracker {
	public static final long CORPSE_CREDIT_MS = 90_000;
	private static final Pattern SACK_LINE = Pattern.compile("^\\s*([+-][\\d,.]+)\\s+(.+?)\\s*\\(([^)]*)\\)\\s*$");
	private static final Pattern CORPSE_HEADER = Pattern.compile("CORPSE LOOT!");
	private static final Pattern AMOUNT_FIRST = Pattern.compile("^\\+?([\\d,.]+)x?\\s+(.+)$");
	private static final Pattern AMOUNT_LAST = Pattern.compile("^(.+?)\\s+x([\\d,.]+)$");

	private final Supplier<Prices> prices;

	// Totals, kept between game restarts.
	public double totalCoins;
	public long totalMs;
	// The current session.
	public double sessionCoins;
	public long sessionMs;
	// What the coins came from, for the big box (session and all time).
	public Breakdown sessionBreakdown = new Breakdown();
	public Breakdown totalBreakdown = new Breakdown();
	private long runningSince = -1;

	// Corpse loot being read, and mining items from loot still expected in a sack message.
	private boolean corpseOpen;
	private boolean corpseRewards;
	private long corpseUntil;
	private final Map<String, Credit> credits = new HashMap<>();

	private record Credit(double count, long until) {}

	private record Part(Prices.Item item, double amount) {}

	public Tracker(Supplier<Prices> prices) {
		this.prices = prices;
	}

	// ---------- session control ----------
	public boolean running() {
		return runningSince >= 0;
	}

	public void start(long now) {
		sessionCoins = 0;
		sessionMs = 0;
		sessionBreakdown.clear();
		runningSince = now;
		credits.clear();
	}

	public void stop(long now) {
		if (!running()) return;
		long d = now - runningSince;
		sessionMs += d;
		totalMs += d;
		runningSince = -1;
	}

	public void resume(long now) {
		if (!running()) runningSince = now;
	}

	public void resetTotal(long now) {
		if (running()) {
			sessionMs += now - runningSince;
			runningSince = now;
		}
		totalCoins = 0;
		totalMs = 0;
		totalBreakdown.clear();
	}

	public long sessionMs(long now) {
		return sessionMs + (running() ? now - runningSince : 0);
	}

	public long totalMs(long now) {
		return totalMs + (running() ? now - runningSince : 0);
	}

	public static double perHour(double coins, long ms) {
		return ms < 60_000 ? 0 : coins / (ms / 3_600_000.0);
	}

	private void earn(double coins) {
		if (coins <= 0) return;
		sessionCoins += coins;
		totalCoins += coins;
	}

	private void note(String cat, String row, String unit, double count) {
		sessionBreakdown.addCount(cat, row, unit, count);
		totalBreakdown.addCount(cat, row, unit, count);
	}

	private void noteCoins(String cat, String row, double coins) {
		sessionBreakdown.addCoins(cat, row, coins);
		totalBreakdown.addCoins(cat, row, coins);
	}

	// ---------- sacks ----------
	/** Coins counted from one sack message, given all of its hover texts. */
	public double onSackMessage(List<String> hoverTexts, long now) {
		if (!running()) return 0;
		Prices p = prices.get();
		dropOldCredits(now);
		Map<String, Double> byFamily = new HashMap<>();
		Map<String, List<Part>> parts = new HashMap<>();
		for (String hover : hoverTexts) {
			for (String line : hover.split("\n")) {
				Matcher m = SACK_LINE.matcher(strip(line));
				if (!m.matches()) continue;
				double amount = number(m.group(1));
				Prices.Item item = p.byName(m.group(2));
				if (item == null || !item.mining()) continue;
				if (amount > 0) amount -= useCredit(item.id(), amount);
				byFamily.merge(item.family(), amount * item.value(), Double::sum);
				parts.computeIfAbsent(item.family(), (k) -> new ArrayList<>()).add(new Part(item, amount));
			}
		}
		double coins = 0;
		for (Map.Entry<String, Double> e : byFamily.entrySet()) {
			double v = e.getValue();
			if (v <= 0) continue;
			coins += v;
			String fam = e.getKey();
			String cat = Breakdown.category(fam);
			String row = Breakdown.rowName(fam);
			for (Part part : parts.get(fam)) note(cat, row, Breakdown.unit(part.item().id(), fam), part.amount());
			noteCoins(cat, row, v);
		}
		earn(coins);
		return coins;
	}

	private double useCredit(String id, double amount) {
		Credit c = credits.get(id);
		if (c == null) return 0;
		double used = Math.min(c.count, amount);
		if (c.count - used <= 0) credits.remove(id);
		else credits.put(id, new Credit(c.count - used, c.until));
		return used;
	}

	private void dropOldCredits(long now) {
		for (Iterator<Credit> it = credits.values().iterator(); it.hasNext(); ) {
			if (it.next().until < now) it.remove();
		}
	}

	// ---------- corpse loot ----------
	/** Feed every chat line (a message with several lines is split first). Returns coins counted. */
	public double onChatLine(String raw, long now) {
		String line = strip(raw).trim();
		if (CORPSE_HEADER.matcher(line).find()) {
			corpseOpen = true;
			corpseRewards = false;
			corpseUntil = now + 3_000;
			return 0;
		}
		if (!corpseOpen) return 0;
		if (now > corpseUntil || line.isEmpty() || line.startsWith("▬")) {
			corpseOpen = false;
			return 0;
		}
		if (!corpseRewards) {
			if (line.equalsIgnoreCase("REWARDS")) corpseRewards = true;
			return 0;
		}
		return corpseReward(line, now);
	}

	private double corpseReward(String line, long now) {
		if (!running()) return 0;
		String name = line;
		double count = 1;
		Matcher last = AMOUNT_LAST.matcher(line);
		Matcher first = AMOUNT_FIRST.matcher(line);
		if (last.matches()) {
			name = last.group(1);
			count = number(last.group(2));
		} else if (first.matches()) {
			name = first.group(2);
			count = number(first.group(1));
		}
		Prices.Item item = prices.get().byName(name);
		if (item == null) return 0;
		if (item.mining()) {
			Credit c = credits.get(item.id());
			credits.put(item.id(), new Credit((c == null ? 0 : c.count) + count, now + CORPSE_CREDIT_MS));
		}
		double coins = count * item.value();
		if (coins > 0) {
			String row = item.name() != null ? item.name() : name;
			note(Breakdown.CORPSE, row, "", count);
			noteCoins(Breakdown.CORPSE, row, coins);
		}
		earn(coins);
		return coins;
	}

	// ---------- helpers ----------
	static String strip(String s) {
		return s.replaceAll("§.", "");
	}

	static double number(String s) {
		return Double.parseDouble(s.replace(",", "").replace(".", "").replace("+", ""));
	}

	/** Lines of a message, for {@link #onChatLine}. */
	public static List<String> lines(String text) {
		return new ArrayList<>(List.of(text.split("\n")));
	}
}
