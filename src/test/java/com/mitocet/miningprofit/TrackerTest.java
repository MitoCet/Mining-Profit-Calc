package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackerTest {
	private static final Prices PRICES = Prices.parse("""
		{"ts":1,"items":{
		 "TUNGSTEN":{"v":100,"fam":"TUNGSTEN","name":"Tungsten"},
		 "ENCHANTED_TUNGSTEN":{"v":16000,"fam":"TUNGSTEN","name":"Enchanted Tungsten"},
		 "ROUGH_ONYX_GEM":{"v":5,"fam":"GEM_ONYX","name":"Rough Onyx Gemstone"},
		 "FLAWED_ONYX_GEM":{"v":400,"fam":"GEM_ONYX","name":"Flawed Onyx Gemstone"},
		 "GLOSSY_GEMSTONE":{"v":1000000,"fam":"GLOSSY_GEMSTONE","name":"Glossy Gemstone"},
		 "GLACITE":{"v":10,"fam":"GLACITE","name":"Glacite"},
		 "ENCHANTED_GLACITE":{"v":1600,"fam":"GLACITE","name":"Enchanted Glacite"},
		 "UMBER_KEY":{"v":50000,"name":"Umber Key"},
		 "ENCHANTED_BREAD":{"v":300,"name":"Enchanted Bread"}
		}}""");

	private Tracker running() {
		Tracker t = new Tracker(() -> PRICES);
		t.start(0);
		return t;
	}

	@Test
	void endClearsSessionKeepsTotal() {
		Tracker t = running();
		t.onSackMessage(List.of("  +1,000 Tungsten (Mining Sack)"), 1000);
		t.end(60_000);
		assertFalse(t.running());
		assertEquals(0, t.sessionCoins, 1e-9);
		assertEquals(0, t.sessionMs(120_000));
		assertEquals(1000 * 100, t.totalCoins, 1e-9);
		assertEquals(60_000, t.totalMs(120_000));
	}

	@Test
	void sackAddsMiningItems() {
		Tracker t = running();
		double c = t.onSackMessage(List.of("Added items:\n  +1,000 Tungsten (Mining Sack)\n  +20 Rough Onyx Gemstone (Gemstone Sack)\n  +5 Enchanted Bread (Agronomy Sack)"), 1000);
		assertEquals(1000 * 100 + 20 * 5, c, 1e-9);
		assertEquals(c, t.sessionCoins, 1e-9);
	}

	@Test
	void sameHoverOnTwoPiecesCountsOnce() {
		Tracker t = running();
		String added = "Added items:\n  +1,000 Tungsten (Mining Sack)\n  +3 Enchanted Tungsten (Mining Sack)";
		String removed = "Removed items:\n  -10 Rough Onyx Gemstone (Gemstone Sack)";
		double c = t.onSackMessage(List.of(added, added, removed, removed), 1000);
		assertEquals(1000 * 100 + 3 * 16000, c, 1e-9);
	}

	@Test
	void compactingIsNotCounted() {
		Tracker t = running();
		double c = t.onSackMessage(List.of("  +1 Enchanted Tungsten (Mining Sack)", "  -160 Tungsten (Mining Sack)"), 1000);
		assertEquals(0, c, 1e-9);
	}

	@Test
	void takingOutIsNotNegative() {
		Tracker t = running();
		double c = t.onSackMessage(List.of("  +10 Rough Onyx Gemstone (Gemstone Sack)", "  -5 Enchanted Tungsten (Mining Sack)"), 1000);
		assertEquals(50, c, 1e-9);
	}

	@Test
	void corpseLootCountsOnceWithSack() {
		Tracker t = running();
		String loot = """
			▬▬▬▬▬▬▬▬▬▬▬▬▬▬
			  UMBER CORPSE LOOT!\s
			  REWARDS
			    Umber Key x2
			    Tungsten x300
			    +2,000 Glacite Powder
			▬▬▬▬▬▬▬▬▬▬▬▬▬▬""";
		double c = 0;
		for (String line : Tracker.lines(loot)) c += t.onChatLine(line, 1000);
		assertEquals(2 * 50000 + 300 * 100, c, 1e-9);
		// The Tungsten from the corpse arrives in the sacks with 200 mined ones.
		double s = t.onSackMessage(List.of("  +500 Tungsten (Mining Sack)"), 20_000);
		assertEquals(200 * 100, s, 1e-9);
		assertEquals(c + s, t.sessionCoins, 1e-9);
	}

	private static List<Map.Entry<String, Integer>> inv(Object... nameCount) {
		java.util.ArrayList<Map.Entry<String, Integer>> out = new java.util.ArrayList<>();
		for (int i = 0; i < nameCount.length; i += 2) out.add(Map.entry((String) nameCount[i], (Integer) nameCount[i + 1]));
		return out;
	}

	@Test
	void putFromInventoryIsNotCounted() {
		Tracker t = running();
		t.onInventory(inv("§aTungsten", 500, "Diamond Pickaxe", 1), 1000);
		t.onInventory(inv("Diamond Pickaxe", 1), 2000);
		// 500 came from the inventory, 200 were mined.
		double c = t.onSackMessage(List.of("  +700 Tungsten (Mining Sack)"), 20_000);
		assertEquals(200 * 100, c, 1e-9);
		// Used up: the next message counts in full.
		assertEquals(300 * 100, t.onSackMessage(List.of("  +300 Tungsten (Mining Sack)"), 40_000), 1e-9);
	}

	@Test
	void fromStashIsCounted() {
		Tracker t = running();
		t.onInventory(inv("Diamond Pickaxe", 1), 1000);
		t.onInventory(inv("Diamond Pickaxe", 1), 2000);
		assertEquals(2000 * 10, t.onSackMessage(List.of("  +2,000 Glacite (Glacite Sack)"), 20_000), 1e-9);
	}

	@Test
	void oldInventoryChangeIsForgotten() {
		Tracker t = running();
		t.onInventory(inv("Tungsten", 500), 1000);
		t.onInventory(inv(), 2000);
		assertEquals(500 * 100, t.onSackMessage(List.of("  +500 Tungsten (Mining Sack)"), 2000 + Tracker.INVENTORY_MS + 1), 1e-9);
	}

	@Test
	void craftingGlaciteFromSacksChangesNothing() {
		Tracker t = running();
		t.onInventory(inv(), 0);
		// 1,600 Glacite crafted out of the sacks into 10 Enchanted Glacite (lands in the inventory),
		// while 1,000 Glacite were mined.
		t.onInventory(inv("Enchanted Glacite", 10), 5000);
		double a = t.onSackMessage(List.of("  +1,000 Glacite (Glacite Sack)\n  -1,600 Glacite (Glacite Sack)"), 10_000);
		assertEquals(1000 * 10, a, 1e-9);
		// The Enchanted Glacite is put back into the sacks while 500 more are mined.
		t.onInventory(inv(), 15_000);
		double b = t.onSackMessage(List.of("  +500 Glacite (Glacite Sack)\n  +10 Enchanted Glacite (Glacite Sack)"), 30_000);
		assertEquals(500 * 10, b, 1e-9);
		assertEquals("Glacite: 1.500 · 15,0k", Breakdown.rowText("Glacite", t.sessionBreakdown.rows(Breakdown.OTHER).get(0).getValue()));
	}

	@Test
	void craftAndPutBackInOneSackMessage() {
		Tracker t = running();
		t.onInventory(inv(), 0);
		t.onInventory(inv("Enchanted Glacite", 10), 5000);
		t.onInventory(inv(), 8000);
		double c = t.onSackMessage(List.of("  +1,000 Glacite (Glacite Sack)\n  -1,600 Glacite (Glacite Sack)\n  +10 Enchanted Glacite (Glacite Sack)"), 20_000);
		assertEquals(1000 * 10, c, 1e-9);
	}

	@Test
	void craftPutBackComesBeforeTakeOutMessage() {
		Tracker t = running();
		t.onInventory(inv(), 0);
		t.onInventory(inv("Enchanted Glacite", 10), 5000);
		t.onInventory(inv(), 8000);
		// Put back message first, then the taking out one.
		assertEquals(0, t.onSackMessage(List.of("  +10 Enchanted Glacite (Glacite Sack)"), 10_000), 1e-9);
		assertEquals(300 * 10, t.onSackMessage(List.of("  +300 Glacite (Glacite Sack)\n  -1,600 Glacite (Glacite Sack)"), 20_000), 1e-9);
	}

	@Test
	void craftInSacksOverTwoMessages() {
		Tracker t = running();
		// /craft takes 1,600 Glacite from the sacks and puts 10 Enchanted Glacite back in.
		assertEquals(1000 * 10, t.onSackMessage(List.of("  +1,000 Glacite (Glacite Sack)\n  -1,600 Glacite (Glacite Sack)"), 10_000), 1e-9);
		assertEquals(500 * 10, t.onSackMessage(List.of("  +500 Glacite (Glacite Sack)\n  +10 Enchanted Glacite (Glacite Sack)"), 30_000), 1e-9);
	}

	@Test
	void usingAHigherTierDoesNotCostMinedItems() {
		Tracker t = running();
		// A forge takes Enchanted Glacite from the sacks while Glacite is mined.
		assertEquals(1000 * 10, t.onSackMessage(List.of("  -256 Enchanted Glacite (Glacite Sack)\n  +1,000 Glacite (Glacite Sack)"), 10_000), 1e-9);
	}

	@Test
	void stashPutIntoSacksIsCounted() {
		Tracker t = running();
		t.onInventory(inv(), 0);
		t.onChatLine("You picked up 2,000 items from your material stash!", 1000);
		t.onInventory(inv("Glacite", 2000), 1500);
		t.onInventory(inv(), 8000);
		assertEquals(2000 * 10, t.onSackMessage(List.of("  +2,000 Glacite (Glacite Sack)"), 20_000), 1e-9);
	}

	@Test
	void stashCraftedInInventoryIsCounted() {
		Tracker t = running();
		t.onInventory(inv(), 0);
		t.onStashPickup(1000);
		t.onInventory(inv("Glacite", 1600), 1500);
		t.onInventory(inv("Enchanted Glacite", 10), 30_000);
		t.onInventory(inv(), 40_000);
		assertEquals(10 * 1600, t.onSackMessage(List.of("  +10 Enchanted Glacite (Glacite Sack)"), 50_000), 1e-9);
	}

	@Test
	void stashCraftedInAGridIsCounted() {
		Tracker t = running();
		t.onInventory(inv(), 0);
		t.onStashPickup(1000);
		t.onInventory(inv("Glacite", 1600), 1500);
		// Glacite goes into a crafting grid, later the Enchanted Glacite comes out.
		t.onInventory(inv(), 20_000);
		t.onInventory(inv("Enchanted Glacite", 10), 25_000);
		t.onInventory(inv(), 30_000);
		assertEquals(10 * 1600, t.onSackMessage(List.of("  +10 Enchanted Glacite (Glacite Sack)"), 40_000), 1e-9);
		// Used up: Glacite put in by hand afterwards does not count.
		t.onInventory(inv("Glacite", 100), 41_000);
		t.onInventory(inv(), 42_000);
		assertEquals(0, t.onSackMessage(List.of("  +100 Glacite (Glacite Sack)"), 50_000), 1e-9);
	}

	@Test
	void pickupAfterStashWindowIsNotStash() {
		Tracker t = running();
		t.onInventory(inv(), 0);
		t.onStashPickup(1000);
		t.onInventory(inv("Glacite", 500), 1000 + Tracker.STASH_PICKUP_MS + 1);
		t.onInventory(inv(), 10_000);
		assertEquals(0, t.onSackMessage(List.of("  +500 Glacite (Glacite Sack)"), 20_000), 1e-9);
	}

	@Test
	void nothingCountedWhenStopped() {
		Tracker t = new Tracker(() -> PRICES);
		assertEquals(0, t.onSackMessage(List.of("  +1,000 Tungsten (Mining Sack)"), 0), 1e-9);
	}

	@Test
	void breakdownByCategory() {
		Tracker t = running();
		// Pristine drops Flawed gems, Compact drops Enchanted items: both counted with their own value.
		t.onSackMessage(List.of("  +1,200 Rough Onyx Gemstone (Gemstone Sack)\n  +15 Flawed Onyx Gemstone (Gemstone Sack)",
			"  +3,200 Tungsten (Mining Sack)\n  +12 Enchanted Tungsten (Mining Sack)\n  +1 Glossy Gemstone (Mining Sack)"), 1000);
		Breakdown b = t.sessionBreakdown;
		assertEquals(List.of(Breakdown.FIESTA, Breakdown.METAL, Breakdown.GEMSTONE), b.categories());
		assertEquals(1200 * 5 + 15 * 400, b.total(Breakdown.GEMSTONE), 1e-9);
		assertEquals("Onyx: 1.200 Rough, 15 Flawed · 12,0k", Breakdown.rowText("Onyx", b.rows(Breakdown.GEMSTONE).get(0).getValue()));
		assertEquals("Tungsten: 3.200, 12 Enchanted · 512,0k", Breakdown.rowText("Tungsten", b.rows(Breakdown.METAL).get(0).getValue()));
		assertEquals(t.totalCoins, t.totalBreakdown.total(Breakdown.METAL) + t.totalBreakdown.total(Breakdown.FIESTA) + t.totalBreakdown.total(Breakdown.GEMSTONE), 1e-9);
		// A new session starts an empty breakdown; the total keeps it.
		t.start(2000);
		assertEquals(List.of(), t.sessionBreakdown.categories());
		assertEquals(3, t.totalBreakdown.categories().size());
	}

	@Test
	void corpseLootHasItsOwnCategory() {
		Tracker t = running();
		for (String line : Tracker.lines("  UMBER CORPSE LOOT!\n  REWARDS\n    Umber Key x2\n    Tungsten x300")) t.onChatLine(line, 1000);
		t.onSackMessage(List.of("  +500 Tungsten (Mining Sack)"), 20_000);
		Breakdown b = t.sessionBreakdown;
		assertEquals(List.of(Breakdown.CORPSE, Breakdown.METAL), b.categories());
		assertEquals(2 * 50000 + 300 * 100, b.total(Breakdown.CORPSE), 1e-9);
		assertEquals("Tungsten: 200 · 20,0k", Breakdown.rowText("Tungsten", b.rows(Breakdown.METAL).get(0).getValue()));
	}

	@Test
	void categories() {
		assertEquals(Breakdown.METAL, Breakdown.category("UMBER"));
		assertEquals(Breakdown.OTHER, Breakdown.category("GLACITE"));
		assertEquals(Breakdown.OTHER, Breakdown.category("SUSPICIOUS_SCRAP"));
		assertEquals(Breakdown.FIESTA, Breakdown.category("REFINED_MINERAL"));
		assertEquals("Hard Stone", Breakdown.rowName("HARD_STONE"));
		assertEquals("Flawless", Breakdown.unit("FLAWLESS_RUBY_GEM", "GEM_RUBY"));
	}

	@Test
	void timeHasSeconds() {
		assertEquals("42sn", Format.time(42_500));
		assertEquals("5dk 07sn", Format.time(307_000));
		assertEquals("1sa 05dk 07sn", Format.time(3_907_000));
	}

	@Test
	void perHour() {
		assertEquals(2_000_000, Tracker.perHour(1_000_000, 1_800_000), 1e-6);
	}

	@Test
	void unreadableSackMessageWarns() {
		Tracker t = running();
		t.onSackMessage(List.of("Something new: 1,000 Tungsten"), 1000);
		assertEquals(List.of("Sack mesajı okunamadı (biçim farklı)"), t.warnings);
	}

	@Test
	void sameSackMessageTwiceWarns() {
		Tracker t = running();
		t.onSackMessage(List.of("  +10 Tungsten (Mining Sack)"), 1000);
		t.onSackMessage(List.of("  +10 Tungsten (Mining Sack)"), 2000);
		assertEquals(List.of("Aynı sack mesajı iki kez geldi"), t.warnings);
		t.onSackMessage(List.of("  +10 Tungsten (Mining Sack)"), 30_000);
		assertEquals(1, t.warnings.size());
	}

	@Test
	void unknownMiningItemAndCutListWarn() {
		Tracker t = running();
		t.onSackMessage(List.of("Added items:\n  +5 Flawless Onyx Gemstone (Gemstone Sack)\n  +3 Wheat (Agronomy Sack)\n  and 12 other items"), 1000);
		assertEquals(List.of("Sack listesi kısa kesilmiş, bazı itemler sayılamadı", "Tanınmayan item: Flawless Onyx Gemstone"), t.warnings);
	}

	@Test
	void corpseWithoutRewardsWarns() {
		Tracker t = running();
		for (String line : Tracker.lines("  UMBER CORPSE LOOT!\n  Something else\n")) t.onChatLine(line, 1000);
		t.onChatLine("", 1100);
		assertEquals(List.of("Corpse ödülleri okunamadı"), t.warnings);
		Tracker ok = running();
		for (String line : Tracker.lines("  UMBER CORPSE LOOT!\n  REWARDS\n    Umber Key x2\n")) ok.onChatLine(line, 1000);
		ok.onChatLine("", 1100);
		assertEquals(List.of(), ok.warnings);
	}

	@Test
	void startClearsWarnings() {
		Tracker t = running();
		t.onSackMessage(List.of("nothing"), 1000);
		t.start(2000);
		assertEquals(List.of(), t.warnings);
	}

	@Test
	void timeStopsWhileIdleAndGoesOnWithTheNextEarning() {
		Tracker t = running();
		t.onSackMessage(List.of("  +10 Tungsten (Mining Sack)"), 30_000);
		// Nothing for a long while: the time stops 90 s after the last earning.
		assertFalse(t.idle(100_000));
		assertTrue(t.idle(200_000));
		assertEquals(120_000, t.sessionMs(200_000));
		assertEquals(120_000, t.sessionMs(500_000));
		// Earning again starts the time from there.
		t.onSackMessage(List.of("  +10 Tungsten (Mining Sack)"), 500_000);
		assertFalse(t.idle(500_000));
		assertEquals(130_000, t.sessionMs(510_000));
		assertEquals(130_000, t.totalMs(510_000));
		t.stop(520_000);
		assertEquals(140_000, t.sessionMs(900_000));
	}

	@Test
	void idlePauseCanBeOff() {
		Tracker t = running();
		t.idleMs = 0;
		assertFalse(t.idle(1_000_000));
		assertEquals(1_000_000, t.sessionMs(1_000_000));
	}

	@Test
	void supercraftedItemsAreNotCounted() {
		Tracker t = running();
		t.onSackMessage(List.of("  -1,600 Glacite (Glacite Sack)"), 1000);
		t.onChatLine("You Supercrafted Enchanted Glacite x10!", 2000);
		double c = t.onSackMessage(List.of("  +10 Enchanted Glacite (Glacite Sack)\n  +50 Glacite (Glacite Sack)"), 20_000);
		assertEquals(50 * 10, c, 1e-9);
		// Only that one sack message: later Enchanted Glacite (e.g. from Compact) counts again.
		assertEquals(1600, t.onSackMessage(List.of("  +1 Enchanted Glacite (Glacite Sack)"), 50_000), 1e-9);
	}

	@Test
	void oldSupercraftIsForgotten() {
		Tracker t = running();
		t.onChatLine("You Supercrafted Enchanted Glacite!", 1000);
		assertEquals(1600, t.onSackMessage(List.of("  +1 Enchanted Glacite (Glacite Sack)"), 40_000), 1e-9);
	}

	@Test
	void nothingCountedThroughTheSackMenu() {
		Tracker t = running();
		t.onSackMenu(true, 1000);
		assertEquals(0, t.onSackMessage(List.of("  +100 Tungsten (Mining Sack)"), 2000), 1e-9);
		t.onSackMenu(false, 3000);
		assertEquals(0, t.onSackMessage(List.of("  +100 Tungsten (Mining Sack)"), 10_000), 1e-9);
		assertEquals(100 * 100, t.onSackMessage(List.of("  +100 Tungsten (Mining Sack)"), 20_000), 1e-9);
		assertTrue(Tracker.isSackMenu("Mining Sack"));
		assertTrue(Tracker.isSackMenu("Sack of Sacks"));
		assertFalse(Tracker.isSackMenu("Chest"));
	}

	@Test
	void stashStillCountsThroughTheSackMenu() {
		Tracker t = running();
		t.onInventory(List.of(), 0);
		t.onStashPickup(1000);
		t.onInventory(List.of(Map.entry("Tungsten", 100)), 1500);
		t.onSackMenu(true, 2000);
		t.onInventory(List.of(), 2500);
		assertEquals(100 * 100, t.onSackMessage(List.of("  +100 Tungsten (Mining Sack)"), 3000), 1e-9);
	}

	private static void corpse(Tracker t, String type, long now) {
		for (String line : Tracker.lines("  " + type + " CORPSE LOOT!\n  REWARDS\n    Tungsten x10")) t.onChatLine(line, now);
		t.onChatLine("", now + 100);
	}

	@Test
	void corpseKeysAreKeptApart() {
		Tracker t = running();
		corpse(t, "UMBER", 1000);
		corpse(t, "LAPIS", 20_000);
		corpse(t, "UMBER", 40_000);
		Breakdown b = t.sessionBreakdown;
		// Earnings stay as they were; the keys are only taken off when asked.
		assertEquals(3 * 10 * 100, t.sessionCoins, 1e-9);
		assertEquals(2 * 50000, b.keyCoins, 1e-9);
		assertEquals("Anahtar: 2 Umber Key · -100,0k", b.keyText());
		assertEquals(3000, Tracker.net(t.sessionCoins, b, false), 1e-9);
		assertEquals(3000 - 100_000, Tracker.net(t.sessionCoins, b, true), 1e-9);
		assertEquals(2 * 50000, t.totalBreakdown.keyCoins, 1e-9);
		t.end(60_000);
		assertEquals(0, t.sessionBreakdown.keyCoins, 1e-9);
		assertEquals(null, t.sessionBreakdown.keyText());
		assertEquals(2 * 50000, t.totalBreakdown.keyCoins, 1e-9);
	}

	@Test
	void resourcefulKeepsTheKey() {
		Tracker t = running();
		// Before the loot...
		t.onChatLine("LUCKY! Your Resourceful perk saved your key from being consumed!", 1000);
		corpse(t, "UMBER", 1500);
		assertEquals(0, t.sessionBreakdown.keyCoins, 1e-9);
		// ...or among its lines.
		t.onChatLine("  UMBER CORPSE LOOT!", 30_000);
		t.onChatLine("LUCKY! Your Resourceful perk saved your key from being consumed!", 30_100);
		t.onChatLine("  REWARDS", 30_200);
		t.onChatLine("    Tungsten x10", 30_300);
		t.onChatLine("", 30_400);
		assertEquals(0, t.sessionBreakdown.keyCoins, 1e-9);
		// The next corpse uses its key again.
		corpse(t, "UMBER", 32_000);
		assertEquals(50000, t.sessionBreakdown.keyCoins, 1e-9);
	}
}
