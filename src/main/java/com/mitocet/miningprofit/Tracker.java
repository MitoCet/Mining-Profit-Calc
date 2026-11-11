package com.mitocet.miningprofit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
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
 *   Only Mining tab materials and gemstones count. Items put in from the inventory do not count;
 *   items from the stash do. Taking items out never counts negative, and crafting what was taken
 *   out (e.g. Glacite into Enchanted Glacite) and putting it back adds nothing (see onSackMessage).
 * - Frozen corpse loot ("... CORPSE LOOT!" then "REWARDS"): every priced item counts, also those
 *   that land in the inventory. Mining items from the loot also show up in the next sack message,
 *   so they are remembered for a while and taken off that sack message.
 */
public final class Tracker {
	public static final long CORPSE_CREDIT_MS = 90_000;
	static final Pattern SACK_LINE = Pattern.compile("^\\s*([+-][\\d,.]+)\\s+(.+?)\\s*\\(([^)]*)\\)\\s*$");
	private static final Pattern STASH_PICKUP = Pattern.compile("(?i)picked up .*stash");
	private static final Pattern CORPSE_HEADER = Pattern.compile("CORPSE LOOT!");
	private static final Pattern AMOUNT_FIRST = Pattern.compile("^\\+?([\\d,.]+)x?\\s+(.+)$");
	private static final Pattern AMOUNT_LAST = Pattern.compile("^(.+?)\\s+x([\\d,.]+)$");
	// Message formats below are as SkyHanni reads them (github.com/hannibal002/SkyHanni, LGPL-2.1),
	// with colour codes stripped: SuperCraftFeatures.craftedPattern and CorpseLoot.
	/** "You Supercrafted Enchanted Mithril x3!": made with /craft (from the sacks), not mined. */
	private static final Pattern SUPERCRAFT = Pattern.compile("^You Supercrafted (.+?)(?: x[\\d,]+)?!$");
	/** "TUNGSTEN CORPSE LOOT!": the word before is the corpse type, which says the key it used. */
	private static final Pattern CORPSE_TYPE = Pattern.compile("(\\w+) CORPSE LOOT!");
	/** The Resourceful perk kept the key. */
	private static final Pattern KEY_SAVED = Pattern.compile("^LUCKY! Your Resourceful perk saved your key from being consumed!$");

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
	private int corpseRead;
	private long corpseUntil;
	private final Map<String, Credit> credits = new HashMap<>();

	private record Credit(double count, long until) {}

	private record Part(Prices.Item item, double amount) {}

	// ---------- warnings ----------
	/** Things the mod could not read or that look wrong, this session (newest last). */
	public final List<String> warnings = new ArrayList<>();
	/** Counts every warning ever given, so the caller can see that a call added one. */
	public long warningCount;
	private static final int MAX_WARNINGS = 20;
	/** A mining-looking word in an item name the values don't know. */
	private static final Pattern MINING_WORD = Pattern.compile(
		"(?i)gemstone|tungsten|umber|glacite|mithril|titanium|hard stone|suspicious scrap|refined mineral");
	/** The same sack message coming again this soon is reported (it was the cause of double counts). */
	public static final long DUPLICATE_MS = 5_000;
	private String lastSackKey;
	private long lastSackAt = -1;

	void warn(String text) {
		warningCount++;
		if (warnings.size() >= MAX_WARNINGS) warnings.remove(0);
		warnings.add(text);
	}

	public Tracker(Supplier<Prices> prices) {
		this.prices = prices;
	}

	// ---------- session control ----------
	public boolean running() {
		return runningSince >= 0;
	}

	public void start(long now) {
		lastActive = now;
		supercrafted.clear();
		warnings.clear();
		lastSackKey = null;
		sessionCoins = 0;
		sessionMs = 0;
		sessionBreakdown.clear();
		runningSince = now;
		credits.clear();
		gained.clear();
		lost.clear();
		usedInSacks.clear();
	}

	public void stop(long now) {
		if (!running()) return;
		long d = segmentMs(now);
		sessionMs += d;
		totalMs += d;
		runningSince = -1;
	}

	/** Ends the session: stops it and clears it to zero. The all-time total keeps what it earned. */
	public void end(long now) {
		stop(now);
		warnings.clear();
		lastSackKey = null;
		sessionCoins = 0;
		sessionMs = 0;
		sessionBreakdown.clear();
		credits.clear();
		gained.clear();
		lost.clear();
		usedInSacks.clear();
		supercrafted.clear();
	}

	public void resume(long now) {
		if (running()) return;
		runningSince = now;
		lastActive = now;
	}

	public void resetTotal(long now) {
		if (running()) {
			sessionMs += segmentMs(now);
			runningSince = now;
		}
		totalCoins = 0;
		totalMs = 0;
		totalBreakdown.clear();
	}

	public long sessionMs(long now) {
		return sessionMs + segmentMs(now);
	}

	public long totalMs(long now) {
		return totalMs + segmentMs(now);
	}

	// ---------- pausing while idle ----------
	/**
	 * With nothing earned for this long the time stops (like SkyHanni's trackers), so a break
	 * does not lower the per hour figure; the next earning starts it again. 0 = never.
	 * Sack messages come every 30 seconds, so this has to be well above that.
	 */
	public long idleMs = 90_000;
	private long lastActive;

	/** Time counted since the running time last started: up to idleMs after the last earning. */
	private long segmentMs(long now) {
		if (!running()) return 0;
		long end = idleMs > 0 ? Math.min(now, lastActive + idleMs) : now;
		return Math.max(0, end - runningSince);
	}

	/** Running, but nothing earned for idleMs: the time is not counting. */
	public boolean idle(long now) {
		return running() && idleMs > 0 && now > lastActive + idleMs;
	}

	/** Something was earned: the time counts again from here if it had stopped. */
	private void active(long now) {
		if (!running()) return;
		if (idle(now)) {
			long d = segmentMs(now);
			sessionMs += d;
			totalMs += d;
			runningSince = now;
		}
		lastActive = now;
	}

	public static double perHour(double coins, long ms) {
		return ms < 60_000 ? 0 : coins / (ms / 3_600_000.0);
	}

	private void earn(double coins, long now) {
		if (coins <= 0) return;
		active(now);
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
	/**
	 * Coins counted from one sack message, given all of its hover texts.
	 *
	 * - Items put in that had just left the inventory were put in by hand: not counted. Items
	 *   from /pickupstash are the exception: they go through the inventory but still count.
	 * - Taking items out never counts negative. Items taken out that showed up in the inventory
	 *   change nothing. Others were used in the sacks themselves (e.g. /craft turns Glacite into
	 *   Enchanted Glacite in the sacks): their value is taken off what of the same family and a
	 *   higher tier goes in, in this message or one shortly after, so crafting adds nothing. Mined
	 *   items of the same or a lower tier are never taken off (e.g. a forge using Enchanted Glacite
	 *   does not cost the Glacite being mined).
	 */
	public double onSackMessage(List<String> hoverTexts, long now) {
		if (!running()) return 0;
		Prices p = prices.get();
		dropOldCredits(now);
		dropOldInventoryChanges(now);
		List<Part> in = new ArrayList<>();
		// Hypixel puts the same hover on several pieces of one message (e.g. "+1,234" and "items"),
		// so one list can come in more than once: read each distinct hover text once.
		LinkedHashSet<String> distinct = new LinkedHashSet<>(hoverTexts);
		String key = String.join("\u0000", distinct);
		if (key.equals(lastSackKey) && now - lastSackAt <= DUPLICATE_MS && !key.isEmpty()) {
			warn("Aynı sack mesajı iki kez geldi");
		}
		lastSackKey = key;
		lastSackAt = now;
		// Put in or taken out through a Sack menu (open, or closed just now): not mined.
		boolean menu = sackMenuOpen || (sackMenuClosedAt >= 0 && now - sackMenuClosedAt <= SACK_MENU_MS);
		// What /craft just made went straight into the sacks: not mined (only this message).
		dropOldSupercrafts(now);
		Map<String, Long> crafted = new HashMap<>(supercrafted);
		supercrafted.clear();
		int read = 0;
		for (String hover : distinct) {
			// A long list ends with "and N other items": those are not shown, so not counted.
			if (strip(hover).contains("other items")) warn("Sack listesi kısa kesilmiş, bazı itemler sayılamadı");
			for (String line : hover.split("\n")) {
				Matcher m = SACK_LINE.matcher(strip(line));
				if (!m.matches()) continue;
				read++;
				double amount = number(m.group(1));
				Prices.Item item = p.byName(m.group(2));
				if (item == null && p.loaded() && MINING_WORD.matcher(m.group(2)).find()) {
					warn("Tanınmayan item: " + m.group(2).trim());
				}
				if (item == null || !item.mining()) continue;
				if (amount > 0) {
					if (crafted.containsKey(item.id())) {
						// Its ingredients taken out of the sacks were used for this: forget them.
						usedInSacks.remove(item.family());
						continue;
					}
					amount -= useCredit(item.id(), amount);
					double byHand = fromInventory(item.id(), amount);
					amount -= byHand;
					double stashed = fromStash(item, byHand);
					if (menu) amount = 0;
					amount += stashed;
					if (amount > 0) in.add(new Part(item, amount));
				} else if (amount < 0) {
					double left = toInventory(item.family(), -amount * item.value(), p);
					if (left > 0) usedInSacks.computeIfAbsent(item.family(), (k) -> new ArrayList<>())
						.add(new Used(item.value(), left, now));
				}
			}
		}
		if (read == 0) warn("Sack mesajı okunamadı (biçim farklı)");
		Map<String, Double> byFamily = new HashMap<>();
		for (Part part : in) {
			Prices.Item item = part.item();
			double value = part.amount() * item.value();
			value -= takeUsed(item.family(), item.value(), value);
			if (value <= 0 || item.value() <= 0) continue;
			String fam = item.family();
			String cat = Breakdown.category(fam);
			String row = Breakdown.rowName(fam);
			note(cat, row, Breakdown.unit(item.id(), fam), value / item.value());
			noteCoins(cat, row, value);
			byFamily.merge(fam, value, Double::sum);
		}
		double coins = 0;
		for (double v : byFamily.values()) coins += v;
		earn(coins, now);
		return coins;
	}

	// ---------- sack menu and /craft ----------
	/** After a Sack menu closes, sack changes this soon still come from it (SkyHanni uses 10 s). */
	public static final long SACK_MENU_MS = 10_000;
	/** A supercraft is matched with a sack message this long (SkyHanni uses 30 s). */
	public static final long SUPERCRAFT_MS = 30_000;
	private boolean sackMenuOpen;
	private long sackMenuClosedAt = -1;
	// Item id -> when it was supercrafted.
	private final Map<String, Long> supercrafted = new HashMap<>();

	/** A menu with "Sack" in its title opened (true) or closed (false). */
	public void onSackMenu(boolean open, long now) {
		if (open) sackMenuOpen = true;
		else if (sackMenuOpen) {
			sackMenuOpen = false;
			sackMenuClosedAt = now;
		}
	}

	/** True for a menu title that belongs to the sacks ("Mining Sack", "Sack of Sacks"…). */
	public static boolean isSackMenu(String title) {
		return strip(title).contains("Sack");
	}

	private void dropOldSupercrafts(long now) {
		supercrafted.values().removeIf((t) -> now - t > SUPERCRAFT_MS);
	}

	// Items taken out of the sacks and used there (crafted), per family, not yet matched.
	private final Map<String, List<Used>> usedInSacks = new HashMap<>();

	private record Used(double unitValue, double value, long since) {}

	/** Of {@code value} coins of an item put in, how much is the crafted form of items taken out. */
	private double takeUsed(String fam, double unitValue, double value) {
		List<Used> list = usedInSacks.get(fam);
		if (list == null) return 0;
		double taken = 0;
		for (int i = 0; i < list.size() && taken < value; i++) {
			Used u = list.get(i);
			if (u.unitValue >= unitValue) continue;
			double t = Math.min(u.value, value - taken);
			taken += t;
			if (u.value - t < 1e-9) list.remove(i--);
			else list.set(i, new Used(u.unitValue, u.value - t, u.since));
		}
		return taken;
	}

	// ---------- inventory ----------
	/** How long an inventory change is matched with sack messages (they come up to ~30 s late). */
	public static final long INVENTORY_MS = 60_000;
	/** After /pickupstash, what comes into the inventory this soon is from the stash. */
	public static final long STASH_PICKUP_MS = 5_000;
	/** Stash items still in the inventory are remembered this long. */
	public static final long STASH_KEEP_MS = 30 * 60_000;
	// Mining items in the inventory at the last look (item id -> count), null = not known yet.
	private Map<String, Double> inventory;
	// Recent inventory changes per item id, kept apart: what came in, and what went out
	// (e.g. put into the sacks). Kept apart so taking out and putting back in can be matched
	// whatever order the sack messages come in.
	private final Map<String, Change> gained = new HashMap<>();
	private final Map<String, Change> lost = new HashMap<>();
	// Value per family of items from the stash not yet put into the sacks.
	private final Map<String, Double> stash = new HashMap<>();
	private long stashPickupUntil = -1;
	private long stashSince = -1;

	private record Change(double count, long since) {}

	/** /pickupstash was used: the items it brings into the inventory count when put into the sacks. */
	public void onStashPickup(long now) {
		stashPickupUntil = now + STASH_PICKUP_MS;
	}

	/**
	 * Feed the inventory now and then (item names with counts, the item on the cursor too).
	 * Pass null when there is no inventory (no player), so the next look starts over.
	 */
	public void onInventory(List<Map.Entry<String, Integer>> stacks, long now) {
		if (stacks == null) {
			inventory = null;
			return;
		}
		Prices p = prices.get();
		Map<String, Double> counts = new HashMap<>();
		for (Map.Entry<String, Integer> s : stacks) {
			Prices.Item item = p.byName(s.getKey());
			if (item != null && item.mining()) counts.merge(item.id(), (double) s.getValue(), Double::sum);
		}
		if (stashSince >= 0 && now - stashSince > STASH_KEEP_MS) {
			stash.clear();
			stashSince = -1;
		}
		if (inventory != null) {
			Map<String, Double> diff = new HashMap<>(counts);
			for (Map.Entry<String, Double> e : inventory.entrySet()) diff.merge(e.getKey(), -e.getValue(), Double::sum);
			// Per family, what came in and went out at the same look was crafted in the inventory
			// (e.g. Glacite compacted into Enchanted Glacite): that alone changes nothing.
			Map<String, double[]> famInOut = new HashMap<>();
			for (Map.Entry<String, Double> e : diff.entrySet()) {
				Prices.Item item = p.byId(e.getKey());
				if (item == null || e.getValue() == 0) continue;
				double[] io = famInOut.computeIfAbsent(item.family(), (k) -> new double[2]);
				io[e.getValue() > 0 ? 0 : 1] += Math.abs(e.getValue()) * item.value();
			}
			boolean fromStash = now <= stashPickupUntil;
			for (Map.Entry<String, Double> e : diff.entrySet()) {
				Prices.Item item = p.byId(e.getKey());
				double d = e.getValue();
				if (item == null || d == 0) continue;
				double[] io = famInOut.get(item.family());
				double crafted = Math.min(io[0], io[1]);
				double side = d > 0 ? io[0] : io[1];
				double count = Math.abs(d) * (side > 0 ? 1 - crafted / side : 1);
				if (count < 1e-9) continue;
				if (d > 0 && fromStash) {
					stash.merge(item.family(), count * item.value(), Double::sum);
					stashSince = now;
					continue;
				}
				Map<String, Change> pool = d > 0 ? gained : lost;
				Change c = pool.get(e.getKey());
				double sum = (c == null || c.since < now - INVENTORY_MS ? 0 : c.count) + count;
				pool.put(e.getKey(), new Change(sum, now));
			}
		}
		inventory = counts;
	}

	/**
	 * Of {@code count} items put in by hand, how many are worth stash items of the same family
	 * (as they are, or crafted however): those still count. The stash value is used up here.
	 */
	private double fromStash(Prices.Item item, double count) {
		double s = stash.getOrDefault(item.family(), 0.0);
		if (count <= 0 || s <= 0 || item.value() <= 0) return 0;
		double back = Math.min(count, s / item.value());
		stash.put(item.family(), s - back * item.value());
		return back;
	}

	/** Of {@code amount} put into the sacks, how many had just left the inventory (and are used up here). */
	private double fromInventory(String id, double amount) {
		Change c = lost.get(id);
		if (c == null || amount <= 0) return 0;
		double used = Math.min(c.count, amount);
		put(lost, id, c.count - used, c.since);
		return used;
	}

	/**
	 * Of {@code value} coins taken out of a family's sacks, what did not show up in the inventory
	 * (as the same items or what was crafted from them). The part that did is used up here.
	 */
	private double toInventory(String fam, double value, Prices p) {
		if (value <= 0) return 0;
		for (Map.Entry<String, Change> e : new ArrayList<>(gained.entrySet())) {
			Prices.Item item = p.byId(e.getKey());
			Change c = e.getValue();
			if (item == null || !fam.equals(item.family()) || item.value() <= 0) continue;
			double used = Math.min(value, c.count * item.value());
			value -= used;
			put(gained, e.getKey(), c.count - used / item.value(), c.since);
			if (value <= 0) return 0;
		}
		return value;
	}

	private static void put(Map<String, Change> pool, String id, double count, long since) {
		if (count < 1e-9) pool.remove(id);
		else pool.put(id, new Change(count, since));
	}

	private void dropOldInventoryChanges(long now) {
		gained.values().removeIf((c) -> c.since < now - INVENTORY_MS);
		lost.values().removeIf((c) -> c.since < now - INVENTORY_MS);
		for (List<Used> list : usedInSacks.values()) list.removeIf((u) -> u.since < now - INVENTORY_MS);
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
		if (STASH_PICKUP.matcher(line).find()) {
			onStashPickup(now);
			return 0;
		}
		Matcher sc = SUPERCRAFT.matcher(line);
		if (sc.matches()) {
			Prices.Item item = prices.get().byName(sc.group(1));
			if (item != null && item.mining()) supercrafted.put(item.id(), now);
			return 0;
		}
		// As SkyHanni reads it: the message keeps the key of the corpse being looted, or else of
		// the next one.
		if (KEY_SAVED.matcher(line).matches()) {
			if (corpseOpen && lastKey != null) {
				useKey(lastKey, -1);
				lastKey = null;
			} else keySaved = true;
			return 0;
		}
		if (CORPSE_HEADER.matcher(line).find()) {
			if (corpseOpen) closeCorpse();
			Matcher type = CORPSE_TYPE.matcher(line);
			String key = type.find() ? KEYS.get(type.group(1).toUpperCase(java.util.Locale.ROOT)) : null;
			lastKey = null;
			if (key != null && running()) {
				if (keySaved) keySaved = false;
				else {
					useKey(key, 1);
					lastKey = key;
				}
			}
			corpseOpen = true;
			corpseRewards = false;
			corpseRead = 0;
			corpseUntil = now + 3_000;
			return 0;
		}
		if (!corpseOpen) return 0;
		if (now > corpseUntil || line.isEmpty() || line.startsWith("▬")) {
			closeCorpse();
			return 0;
		}
		if (!corpseRewards) {
			if (line.equalsIgnoreCase("REWARDS")) corpseRewards = true;
			return 0;
		}
		return corpseReward(line, now);
	}

	// ---------- corpse keys ----------
	/** Key each corpse type uses (Lapis needs none), as in SkyHanni's CorpseType. */
	private static final Map<String, String> KEYS = Map.of(
		"TUNGSTEN", "TUNGSTEN_KEY", "UMBER", "UMBER_KEY", "VANGUARD", "SKELETON_KEY");
	/** The Resourceful perk kept the key of the next corpse. */
	private boolean keySaved;
	/** Key of the corpse whose loot is being read (null when none was used). */
	private String lastKey;

	/**
	 * A key was used (n = 1) or given back (n = -1). Kept apart from the earnings, valued at the
	 * price of now: the box takes it off only while key costs are on (/kazanc anahtar).
	 */
	private void useKey(String id, int n) {
		Prices.Item key = prices.get().byId(id);
		double value = key == null ? 0 : key.value();
		String name = key != null && key.name() != null ? key.name() : Breakdown.title(id);
		sessionBreakdown.addKey(name, n, n * value);
		totalBreakdown.addKey(name, n, n * value);
	}

	/** Earnings with the keys used taken off when {@code keys} is on. */
	public static double net(double coins, Breakdown bd, boolean keys) {
		return keys ? coins - bd.keyCoins : coins;
	}

	private void closeCorpse() {
		corpseOpen = false;
		if (running() && corpseRead == 0) warn("Corpse ödülleri okunamadı");
	}

	/** True while corpse loot lines are being read (for the debug log). */
	public boolean corpseOpen() {
		return corpseOpen;
	}

	private double corpseReward(String line, long now) {
		corpseRead++;
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
		earn(coins, now);
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
