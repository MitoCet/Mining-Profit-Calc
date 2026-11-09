package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackerTest {
	private static final Prices PRICES = Prices.parse("""
		{"ts":1,"items":{
		 "TUNGSTEN":{"v":100,"fam":"TUNGSTEN","name":"Tungsten"},
		 "ENCHANTED_TUNGSTEN":{"v":16000,"fam":"TUNGSTEN","name":"Enchanted Tungsten"},
		 "ROUGH_ONYX_GEM":{"v":5,"fam":"GEM_ONYX","name":"Rough Onyx Gemstone"},
		 "UMBER_KEY":{"v":50000,"name":"Umber Key"},
		 "ENCHANTED_BREAD":{"v":300,"name":"Enchanted Bread"}
		}}""");

	private Tracker running() {
		Tracker t = new Tracker(() -> PRICES);
		t.start(0);
		return t;
	}

	@Test
	void sackAddsMiningItems() {
		Tracker t = running();
		double c = t.onSackMessage(List.of("Added items:\n  +1,000 Tungsten (Mining Sack)\n  +20 Rough Onyx Gemstone (Gemstone Sack)\n  +5 Enchanted Bread (Agronomy Sack)"), 1000);
		assertEquals(1000 * 100 + 20 * 5, c, 1e-9);
		assertEquals(c, t.sessionCoins, 1e-9);
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

	@Test
	void nothingCountedWhenStopped() {
		Tracker t = new Tracker(() -> PRICES);
		assertEquals(0, t.onSackMessage(List.of("  +1,000 Tungsten (Mining Sack)"), 0), 1e-9);
	}

	@Test
	void perHour() {
		assertEquals(2_000_000, Tracker.perHour(1_000_000, 1_800_000), 1e-6);
	}
}
