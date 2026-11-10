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
		 "FLAWED_ONYX_GEM":{"v":400,"fam":"GEM_ONYX","name":"Flawed Onyx Gemstone"},
		 "GLOSSY_GEMSTONE":{"v":1000000,"fam":"GLOSSY_GEMSTONE","name":"Glossy Gemstone"},
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
	void perHour() {
		assertEquals(2_000_000, Tracker.perHour(1_000_000, 1_800_000), 1e-6);
	}
}
