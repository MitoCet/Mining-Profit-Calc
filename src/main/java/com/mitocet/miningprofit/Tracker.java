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
 *   Only Mining tab materials, gemstones and ores count. Items put in from the inventory do not
 *   count (they were counted when they came into the inventory, or were not mined). Taking items out never counts negative, and crafting what was taken
 *   out (e.g. Glacite into Enchanted Glacite) and putting it back adds nothing (see onSackMessage).
 * - Mining items that land in the inventory (sacks full, /pickupstash): they count when they come
 *   in (see onInventory), not again when crafted or put into the sacks. Mito, 2026-11-13.
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
	/** End of a long sack list: "+40 other items." (the rest is not shown). */
	static final Pattern OTHER_ITEMS = Pattern.compile("([+-]?[\\d,.]+)\\s+other items");
	// Message formats below are as SkyHanni reads them (github.com/hannibal002/SkyHanni, LGPL-2.1),
	// with colour codes stripped: SuperCraftFeatures.craftedPattern and CorpseLoot.
	/** "You Supercrafted Enchanted Mithril x3!": made with /craft (from the sacks), not mined. */
	private static final Pattern SUPERCRAFT = Pattern.compile("^You Supercrafted (.+?)(?: x[\\d,]+)?!$");
	/** "TUNGSTEN CORPSE LOOT!": the word before is the corpse type, which says the key it used. */
	private static final Pattern CORPSE_TYPE = Pattern.compile("(\\w+) CORPSE LOOT!");
	/** The Resourceful perk kept the key. */
	/** "Moved 64 Coal from your Sacks to your inventory." (/gfs): not mined. As SkyHanni's OwnInventoryData. */
	private static final Pattern MOVED_FROM_SACKS = Pattern.compile("^Moved ([\\d,]+) (.+?) from your Sacks to your inventory\\.?$");
	private static final Pattern KEY_SAVED = Pattern.compile("^LUCKY! Your Resourceful perk saved your key from being consumed!$");

	private final Supplier<Prices> prices;

	// Totals, kept between game restarts.
	public double totalCoins;
	public long totalMs;
	// The current session.
	public double sessionCoins;
	public long sessionMs;
	/** When the current session started (ms; 0 = unknown). */
	public long sessionStart;
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

	// Every item of recent corpse loot (name -> count), also non-mining ones: a sack list cut short
	// ("+40 other items") often only hides these, which were already read from the loot.
	private final Map<String, Credit> corpseItems = new HashMap<>();
	/** The last sack message was cut short and the hidden part is not all known corpse loot. */
	public boolean truncatedUnknown;

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

	/** Start of the warning an API sack check with differences gives. */
	public static final String API_WARNING = "API sack sayısıyla ";

	/** The API difference is settled (fixed, or a new check matches): its warning goes away. */
	public boolean clearApiWarnings() {
		return warnings.removeIf((w) -> w.startsWith(API_WARNING));
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
		sessionStart = now;
		runningSince = now;
		credits.clear();
		corpseItems.clear();
		gained.clear();
		lost.clear();
		usedInSacks.clear();
		movedFromSacks.clear();
		counted.clear();
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
		sessionStart = 0;
		warnings.clear();
		lastSackKey = null;
		sessionCoins = 0;
		sessionMs = 0;
		sessionBreakdown.clear();
		credits.clear();
		corpseItems.clear();
		gained.clear();
		lost.clear();
		usedInSacks.clear();
		supercrafted.clear();
		counted.clear();
	}

	public void resume(long now) {
		if (running()) return;
		runningSince = now;
		lastActive = now;
	}

	/**
	 * Deletes every saved session: Toplam becomes just the session running now (Toplam is the
	 * sum of the saved sessions and the current one).
	 */
	public void resetTotal(long now) {
		if (running()) {
			sessionMs += segmentMs(now);
			runningSince = now;
		}
		totalCoins = sessionCoins;
		totalMs = sessionMs;
		totalBreakdown = sessionBreakdown.copy();
	}

	/**
	 * The session as it is now, to save when it ends (before {@link #end}). The start is
	 * guessed from the counted time when it is unknown (a session from before this was kept).
	 */
	public History.Session record(long now) {
		History.Session r = new History.Session();
		r.ms = sessionMs(now);
		r.end = now;
		r.start = sessionStart > 0 ? sessionStart : now - r.ms;
		r.coins = sessionCoins;
		r.breakdown = sessionBreakdown.copy();
		return r;
	}

	/** A saved session was deleted: what it earned leaves Toplam. */
	public void remove(History.Session r) {
		totalCoins = Math.max(0, totalCoins - r.coins);
		totalMs = Math.max(0, totalMs - r.ms);
		totalBreakdown.subtract(r.breakdown);
	}

	/** Adds items the API check found missing to a saved (ended) session and to Toplam. */
	public double addFixTo(History.Session r, Prices.Item item, double count, double value) {
		double coins = count * value;
		if (coins <= 0) return 0;
		String fam = item.family();
		String cat = Breakdown.category(fam);
		String row = Breakdown.rowName(fam);
		String unit = Breakdown.unit(item.id(), fam);
		r.breakdown.addCount(cat, row, unit, count);
		r.breakdown.addCoins(cat, row, coins);
		r.coins += coins;
		totalBreakdown.addCount(cat, row, unit, count);
		totalBreakdown.addCoins(cat, row, coins);
		totalCoins += coins;
		return coins;
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

	/**
	 * Adds items the API sack check found missing (their sack message never came). Goes to the
	 * all-time total, and to the session too when {@code session} (not after it ended).
	 */
	public void addFix(Prices.Item item, double count, boolean session) {
		double coins = count * item.value();
		if (coins <= 0) return;
		String fam = item.family();
		String cat = Breakdown.category(fam);
		String row = Breakdown.rowName(fam);
		String unit = Breakdown.unit(item.id(), fam);
		if (session) {
			sessionBreakdown.addCount(cat, row, unit, count);
			sessionBreakdown.addCoins(cat, row, coins);
			sessionCoins += coins;
		}
		totalBreakdown.addCount(cat, row, unit, count);
		totalBreakdown.addCoins(cat, row, coins);
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
	 * - Items put in that had just left the inventory were put in by hand (or crafted there,
	 *   Hypixel puts the product straight into the sacks): not counted, they counted when they
	 *   came into the inventory.
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
		truncatedUnknown = false;
		double hidden = 0;
		// What corpse loot is still not seen in a sack list (name -> count), less what this one shows.
		Map<String, Double> corpseLeft = new HashMap<>();
		corpseItems.forEach((k, c) -> corpseLeft.put(k, c.count));
		for (String hover : distinct) {
			// A long list ends with "+N other items.": those N are not shown.
			Matcher other = OTHER_ITEMS.matcher(strip(hover));
			if (other.find()) {
				double n = number(other.group(1));
				if (n > 0) hidden += n;
			}
			for (String line : hover.split("\n")) {
				Matcher m = SACK_LINE.matcher(strip(line));
				if (!m.matches()) continue;
				read++;
				double amount = number(m.group(1));
				if (amount > 0) {
					double shown = amount;
					corpseLeft.computeIfPresent(key(m.group(2)), (k, c) -> c - shown > 0 ? c - shown : null);
				}
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
					double byHand = fromInventory(item, amount, p);
					amount -= byHand;
					if (menu) amount = 0;
					if (amount > 0) in.add(new Part(item, amount));
				} else if (amount < 0) {
					double left = toInventory(item.family(), -amount * item.value(), p);
					if (left > 0) usedInSacks.computeIfAbsent(item.family(), (k) -> new ArrayList<>())
						.add(new Used(item.value(), left, now));
				}
			}
		}
		if (read == 0) warn("Sack mesajı okunamadı (biçim farklı)");
		if (hidden > 0) {
			// The hidden part is first taken as corpse loot not shown yet: it was counted from the
			// loot already, so nothing is missing. Only what is left over is unknown.
			double known = 0;
			for (double c : corpseLeft.values()) known += c;
			double used = Math.min(known, hidden);
			takeCorpseItems(corpseLeft, used);
			double unknown = hidden - used;
			if (unknown > 0) {
				truncatedUnknown = true;
				warn("Sack listesi kısa kesilmiş: " + Format.count(unknown)
					+ " item görünmedi (mining itemi varsa /kazanc kontrol bulur)");
			}
		}
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

	/** A Sack menu is open (or closed this soon): what leaves the inventory now goes into the sacks. */
	public boolean sackMenuNow(long now) {
		return sackMenuOpen || (sackMenuClosedAt >= 0 && now - sackMenuClosedAt <= MENU_MS);
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
	// Mining items in the inventory at the last look (item id -> count), null = not known yet.
	private Map<String, Double> inventory;
	// Recent inventory changes per item id, kept apart: what came in, and what went out
	// (e.g. put into the sacks). Kept apart so taking out and putting back in can be matched
	// whatever order the sack messages come in.
	private final Map<String, Change> gained = new HashMap<>();
	private final Map<String, Change> lost = new HashMap<>();

	private record Change(double count, long since) {}

	// ---------- mining items picked up into the inventory ----------
	// Rules as SkyHanni's OwnInventoryData (github.com/hannibal002/SkyHanni, LGPL-2.1): what comes
	// into the inventory while a menu is open (or just after), just after a world change, or
	// right after "Moved … from your Sacks" is not picked up.
	/** After a menu closes, inventory changes this soon still come from it. */
	public static final long MENU_MS = 1_000;
	/** After a world change the inventory is filled again: not picked up. SkyHanni uses 3 s. */
	public static final long WORLD_MS = 3_000;
	/** After "Moved … from your Sacks", that item coming in this soon is from the sacks. */
	public static final long MOVED_MS = 2_000;
	private int menusOpen;
	private long menuClosedAt = -1;
	private long worldChangedAt = -1;
	// Item name key -> until when it is ignored.
	private final Map<String, Long> movedFromSacks = new HashMap<>();

	/** True for an ore family (Coal, Iron…): the "Ore" heading. */
	public static boolean isOre(String family) {
		return family != null && family.startsWith("ORE_");
	}

	/** Any menu (not chat) opened (true) or closed (false): the inventory changes are not mining. */
	public void onMenu(boolean open, long now) {
		if (open) menusOpen++;
		else if (menusOpen > 0) {
			menusOpen--;
			menuClosedAt = now;
		}
	}

	/** The player went to another world/server: the inventory is sent again, start over. */
	public void onWorldChange(long now) {
		worldChangedAt = now;
		inventory = null;
	}

	/** True while what comes into the inventory cannot be mining (menu, world change). */
	private boolean inventoryBusy(long now) {
		return menusOpen > 0
			|| (menuClosedAt >= 0 && now - menuClosedAt <= MENU_MS)
			|| (worldChangedAt >= 0 && now - worldChangedAt <= WORLD_MS);
	}

	private boolean movedFromSacks(Prices.Item item, long now) {
		movedFromSacks.values().removeIf((until) -> until < now);
		return item.name() != null && movedFromSacks.containsKey(key(item.name()));
	}

	/** Mining items that came into the inventory: counted like mined, less corpse loot already counted. */
	private double earnInventory(Prices.Item item, double count, long now) {
		count -= useCredit(item.id(), count);
		double coins = count * item.value();
		if (coins <= 0) return 0;
		String fam = item.family();
		String cat = Breakdown.category(fam);
		String row = Breakdown.rowName(fam);
		note(cat, row, Breakdown.unit(item.id(), fam), count);
		noteCoins(cat, row, coins);
		earn(coins, now);
		Change c = counted.get(item.id());
		counted.put(item.id(), new Change((c == null ? 0 : c.count) + count, now));
		return coins;
	}

	// Recently counted inventory gains per item id: a sack message later showing them taken out of
	// the sacks (a way out of the sacks without a menu or "Moved" message) takes them back off.
	private final Map<String, Change> counted = new HashMap<>();

	/** Takes back up to {@code count} of an item counted as it came into the inventory. */
	private void uncount(Prices.Item item, double count) {
		Change c = counted.get(item.id());
		if (c == null || count <= 0) return;
		double n = Math.min(c.count, count);
		put(counted, item.id(), c.count - n, c.since);
		double coins = n * item.value();
		String fam = item.family();
		String cat = Breakdown.category(fam);
		String row = Breakdown.rowName(fam);
		note(cat, row, Breakdown.unit(item.id(), fam), -n);
		noteCoins(cat, row, -coins);
		sessionCoins = Math.max(0, sessionCoins - coins);
		totalCoins = Math.max(0, totalCoins - coins);
	}

	/**
	 * /pickupstash was used. Nothing to do: what it brings into the inventory counts as it comes in,
	 * like everything picked up (kept for the debug log and old replays).
	 */
	public void onStashPickup(long now) {
	}

	/**
	 * Feed the inventory now and then (item names with counts, the item on the cursor too).
	 * Pass null when there is no inventory (no player), so the next look starts over.
	 * Returns the coins counted (mining items that came in, see the class comment).
	 */
	public double onInventory(List<Map.Entry<String, Integer>> stacks, long now) {
		if (stacks == null) {
			inventory = null;
			return 0;
		}
		double earned = 0;
		Prices p = prices.get();
		Map<String, Double> counts = new HashMap<>();
		for (Map.Entry<String, Integer> s : stacks) {
			Prices.Item item = p.byName(s.getKey());
			if (item != null && item.mining()) counts.merge(item.id(), (double) s.getValue(), Double::sum);
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
			boolean busy = inventoryBusy(now);
			for (Map.Entry<String, Double> e : diff.entrySet()) {
				Prices.Item item = p.byId(e.getKey());
				double d = e.getValue();
				if (item == null || d == 0) continue;
				double[] io = famInOut.get(item.family());
				double crafted = Math.min(io[0], io[1]);
				double side = d > 0 ? io[0] : io[1];
				double count = Math.abs(d) * (side > 0 ? 1 - crafted / side : 1);
				if (count < 1e-9) continue;
				// Mining items count as they come in (crafting between their forms nets out above),
				// and not again when they go into the sacks (the "lost" pool below). Stash ones too.
				if (d > 0 && running() && !busy && !movedFromSacks(item, now)) {
					earned += earnInventory(item, count, now);
				}
				Map<String, Change> pool = d > 0 ? gained : lost;
				Change c = pool.get(e.getKey());
				double sum = (c == null || c.since < now - INVENTORY_MS ? 0 : c.count) + count;
				pool.put(e.getKey(), new Change(sum, now));
			}
		}
		inventory = counts;
		return earned;
	}

	/**
	 * Of {@code amount} put into the sacks, how many had just left the inventory (and are used up
	 * here): the same items, or lower forms of the same family crafted into this one in the
	 * inventory, whose product Hypixel puts straight into the sacks (e.g. 160 Coal crafted in the
	 * inventory → "+1 Enchanted Coal" in the sacks). Higher forms that left are never taken off
	 * (e.g. Enchanted Glacite into a forge does not cost the Glacite being mined).
	 */
	private double fromInventory(Prices.Item item, double amount, Prices p) {
		if (amount <= 0) return 0;
		double used = 0;
		Change c = lost.get(item.id());
		if (c != null) {
			used = Math.min(c.count, amount);
			put(lost, item.id(), c.count - used, c.since);
		}
		if (used >= amount || item.value() <= 0) return used;
		double value = (amount - used) * item.value();
		for (Map.Entry<String, Change> e : new ArrayList<>(lost.entrySet())) {
			Prices.Item from = p.byId(e.getKey());
			Change l = e.getValue();
			if (from == null || from.id().equals(item.id()) || !item.family().equals(from.family())
				|| from.value() <= 0 || from.value() >= item.value()) continue;
			double v = Math.min(value, l.count * from.value());
			value -= v;
			put(lost, e.getKey(), l.count - v / from.value(), l.since);
			if (value <= 1e-9) break;
		}
		return amount - Math.max(0, value) / item.value();
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
			uncount(item, used / item.value());
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
		counted.values().removeIf((c) -> c.since < now - INVENTORY_MS);
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
		corpseItems.values().removeIf((c) -> c.until < now);
	}

	/** Corpse loot taken as the hidden part of a sack list: forgotten, and its credits too. */
	private void takeCorpseItems(Map<String, Double> left, double amount) {
		Prices p = prices.get();
		for (Map.Entry<String, Double> e : left.entrySet()) {
			if (amount <= 0) break;
			double t = Math.min(e.getValue(), amount);
			amount -= t;
			Credit c = corpseItems.get(e.getKey());
			if (c != null) {
				if (c.count - t > 0) corpseItems.put(e.getKey(), new Credit(c.count - t, c.until));
				else corpseItems.remove(e.getKey());
			}
			Prices.Item item = p.byName(e.getKey());
			if (item != null && item.mining()) useCredit(item.id(), t);
		}
	}

	private static String key(String name) {
		return Prices.key(name);
	}

	// ---------- corpse loot ----------
	/** Feed every chat line (a message with several lines is split first). Returns coins counted. */
	public double onChatLine(String raw, long now) {
		String line = strip(raw).trim();
		if (STASH_PICKUP.matcher(line).find()) {
			onStashPickup(now);
			return 0;
		}
		Matcher moved = MOVED_FROM_SACKS.matcher(line);
		if (moved.matches()) {
			movedFromSacks.put(key(moved.group(2)), now + MOVED_MS);
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
			corpseKeyed = key != null;
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
	/** The corpse whose loot is being read needs a key (all but Lapis), even if Resourceful kept it. */
	private boolean corpseKeyed;
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

	/**
	 * Earnings as shown. Key costs on: everything, less the keys used. Off: keyed corpse loot
	 * (Tungsten/Umber/Vanguard) left out, so only Lapis corpses count.
	 */
	public static double net(double coins, Breakdown bd, boolean keys) {
		return keys ? coins - bd.keyCoins : coins - bd.total(Breakdown.KEYED);
	}

	private void closeCorpse() {
		corpseOpen = false;
		if (running() && corpseRead == 0) warn("Corpse ödülleri okunamadı");
	}

	/** True while corpse loot lines are being read (for the debug log). */
	public boolean corpseOpen() {
		return corpseOpen;
	}

	/** Told of every mining item in corpse loot, also while stopped (for the API sack check). */
	public java.util.function.BiConsumer<String, Long> corpseMining;

	private double corpseReward(String line, long now) {
		corpseRead++;
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
		// Every loot item that goes to the sacks (powder does not), for a sack list cut short.
		if (running() && !name.toLowerCase(java.util.Locale.ROOT).contains("powder")) {
			Credit seen = corpseItems.get(key(name));
			corpseItems.put(key(name), new Credit((seen == null ? 0 : seen.count) + count, now + CORPSE_CREDIT_MS));
		}
		Prices.Item item = prices.get().byName(name);
		if (item == null) return 0;
		if (item.mining() && corpseMining != null) corpseMining.accept(item.id(), Math.round(count));
		if (!running()) return 0;
		if (item.mining()) {
			Credit c = credits.get(item.id());
			credits.put(item.id(), new Credit((c == null ? 0 : c.count) + count, now + CORPSE_CREDIT_MS));
		}
		double coins = count * item.value();
		if (coins > 0) {
			String row = item.name() != null ? item.name() : name;
			String cat = corpseKeyed ? Breakdown.KEYED : Breakdown.CORPSE;
			note(cat, row, "", count);
			noteCoins(cat, row, coins);
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
