package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ores (ORE_* families): counted when they come into the inventory, mostly with full sacks. */
class OreTest {
	private static final Prices PRICES = Prices.parse("""
		{"ts":1,"items":{
		 "COAL":{"v":6,"fam":"ORE_COAL","name":"Coal"},
		 "COAL_BLOCK":{"v":54,"fam":"ORE_COAL","name":"Block of Coal"},
		 "ENCHANTED_COAL":{"v":960,"fam":"ORE_COAL","name":"Enchanted Coal"},
		 "ENCHANTED_COAL_BLOCK":{"v":153600,"fam":"ORE_COAL","name":"Enchanted Coal Block"},
		 "INK_SACK:4":{"v":5,"fam":"ORE_LAPIS","name":"Lapis Lazuli"},
		 "TUNGSTEN":{"v":100,"fam":"TUNGSTEN","name":"Tungsten"}
		}}""");

	private Tracker running() {
		Tracker t = new Tracker(() -> PRICES);
		t.start(0);
		return t;
	}

	private static List<Map.Entry<String, Integer>> inv(Object... nameCount) {
		java.util.ArrayList<Map.Entry<String, Integer>> out = new java.util.ArrayList<>();
		for (int i = 0; i < nameCount.length; i += 2) out.add(Map.entry((String) nameCount[i], (Integer) nameCount[i + 1]));
		return out;
	}

	@Test
	void sackOresCount() {
		Tracker t = running();
		assertEquals(1000 * 6 + 10 * 5, t.onSackMessage(List.of("  +1,000 Coal (Mining Sack)\n  +10 Lapis Lazuli (Mining Sack)"), 30_000), 1e-9);
		assertEquals(1000 * 6 + 10 * 5, t.sessionBreakdown.total(Breakdown.ORE), 1e-9);
	}

	@Test
	void fullSacksOresInInventoryCount() {
		Tracker t = running();
		t.onInventory(inv("Diamond Pickaxe", 1), 1000);
		// Sacks full: what is mined lands in the inventory.
		assertEquals(200 * 6, t.onInventory(inv("Diamond Pickaxe", 1, "Coal", 200), 1500), 1e-9);
		assertEquals(150 * 6, t.onInventory(inv("Diamond Pickaxe", 1, "Coal", 350), 2000), 1e-9);
		assertEquals(350 * 6, t.sessionCoins, 1e-9);
		Breakdown.Row row = t.sessionBreakdown.cats.get(Breakdown.ORE).get("Coal");
		assertEquals(350, row.counts.get(""), 1e-9);
	}

	@Test
	void craftingInTheInventoryAddsNothingMore() {
		Tracker t = running();
		t.onInventory(inv(), 1000);
		t.onInventory(inv("Coal", 320), 1500);
		// Two Enchanted Coal made in a crafting menu: nothing changes.
		t.onMenu(true, 2000);
		t.onInventory(inv("Enchanted Coal", 2), 2500);
		t.onMenu(false, 3000);
		t.onInventory(inv("Enchanted Coal", 2), 5000);
		assertEquals(320 * 6, t.sessionCoins, 1e-9);
		// Mining goes on: only the new Coal counts.
		assertEquals(100 * 6, t.onInventory(inv("Enchanted Coal", 2, "Coal", 100), 6000), 1e-9);
		assertEquals((320 + 100) * 6, t.sessionCoins, 1e-9);
	}

	@Test
	void compactorSameLookCountsOnlyNew() {
		Tracker t = running();
		t.onInventory(inv("Coal", 150), 1000);
		// 50 more mined; 160 of the 200 compacted into one Enchanted Coal at once.
		assertEquals(50 * 6, t.onInventory(inv("Coal", 40, "Enchanted Coal", 1), 1500), 1e-9);
	}

	@Test
	void puttingCountedOresIntoSacksDoesNotCountAgain() {
		Tracker t = running();
		t.onInventory(inv(), 1000);
		t.onInventory(inv("Enchanted Coal", 5), 1500);
		assertEquals(5 * 960, t.sessionCoins, 1e-9);
		// Space in the sacks again: put in by hand.
		t.onInventory(inv(), 10_000);
		assertEquals(300 * 6, t.onSackMessage(List.of("  +5 Enchanted Coal (Mining Sack)\n  +300 Coal (Mining Sack)"), 30_000), 1e-9);
		assertEquals(5 * 960 + 300 * 6, t.sessionCoins, 1e-9);
	}

	@Test
	void sellingOrDroppingDoesNotLowerEarnings() {
		Tracker t = running();
		t.onInventory(inv(), 1000);
		t.onInventory(inv("Coal", 500), 1500);
		t.onMenu(true, 2000);
		t.onInventory(inv(), 2500);
		t.onMenu(false, 3000);
		t.onInventory(inv(), 5000);
		assertEquals(500 * 6, t.sessionCoins, 1e-9);
	}

	@Test
	void boughtOrTakenInAMenuIsNotCounted() {
		Tracker t = running();
		t.onInventory(inv(), 1000);
		t.onMenu(true, 2000); // Bazaar, storage, Sack menu…
		assertEquals(0, t.onInventory(inv("Enchanted Coal", 64), 2500), 1e-9);
		t.onMenu(false, 3000);
		// Just after closing (cursor item dropped back) still not.
		assertEquals(0, t.onInventory(inv("Enchanted Coal", 64, "Coal", 10), 3500), 1e-9);
		assertEquals(10 * 6, t.onInventory(inv("Enchanted Coal", 64, "Coal", 20), 3000 + Tracker.MENU_MS + 500), 1e-9);
	}

	@Test
	void movedFromSacksIsNotCounted() {
		Tracker t = running();
		t.onInventory(inv(), 1000);
		t.onChatLine("§aMoved §r§e64 Coal§r§a from your Sacks to your inventory.", 1200);
		assertEquals(0, t.onInventory(inv("Coal", 64), 1500), 1e-9);
		// Its sack message (-64) changes nothing either.
		assertEquals(0, t.onSackMessage(List.of("  -64 Coal (Mining Sack)"), 20_000), 1e-9);
		assertEquals(0, t.sessionCoins, 1e-9);
	}

	@Test
	void worldChangeIsNotCounted() {
		Tracker t = running();
		t.onInventory(inv("Coal", 500), 1000);
		t.onWorldChange(2000);
		t.onInventory(inv(), 2100);
		assertEquals(0, t.onInventory(inv("Coal", 500), 2600), 1e-9);
		assertEquals(0, t.sessionCoins, 1e-9);
	}

	@Test
	void stoppedOrBeforeStartIsNotCounted() {
		Tracker t = new Tracker(() -> PRICES);
		t.onInventory(inv(), 1000);
		assertEquals(0, t.onInventory(inv("Coal", 100), 1500), 1e-9);
	}

	@Test
	void otherMiningItemsInTheInventoryStillDoNotCount() {
		Tracker t = running();
		t.onInventory(inv(), 1000);
		assertEquals(0, t.onInventory(inv("Tungsten", 100), 1500), 1e-9);
	}

	@Test
	void stashOresCountOnceWhenTheyComeIn() {
		Tracker t = running();
		t.onInventory(inv(), 1000);
		t.onStashPickup(1100);
		assertEquals(400 * 6, t.onInventory(inv("Coal", 400), 1500), 1e-9);
		t.onInventory(inv(), 5000);
		assertEquals(0, t.onSackMessage(List.of("  +400 Coal (Mining Sack)"), 20_000), 1e-9);
		assertEquals(400 * 6, t.sessionCoins, 1e-9);
	}

	@Test
	void oreUnitsInTheBox() {
		assertEquals(Breakdown.ORE, Breakdown.category("ORE_LAPIS"));
		assertEquals("Lapis", Breakdown.rowName("ORE_LAPIS"));
		assertEquals("", Breakdown.unit("COAL", "ORE_COAL"));
		assertEquals("Block", Breakdown.unit("COAL_BLOCK", "ORE_COAL"));
		assertEquals("Enchanted", Breakdown.unit("ENCHANTED_COAL", "ORE_COAL"));
		assertEquals("Enchanted Block", Breakdown.unit("ENCHANTED_COAL_BLOCK", "ORE_COAL"));
	}
}
