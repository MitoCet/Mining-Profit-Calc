package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SackCheckTest {
	private static final Prices PRICES = Prices.parse("""
		{"ts":1,"items":{
		 "TUNGSTEN":{"v":100,"fam":"TUNGSTEN","name":"Tungsten"},
		 "GLACITE":{"v":10,"fam":"GLACITE","name":"Glacite"},
		 "ENCHANTED_GLACITE":{"v":1600,"fam":"GLACITE","name":"Enchanted Glacite"},
		 "ENCHANTED_BREAD":{"v":300,"name":"Enchanted Bread"}
		}}""");

	private static SackCheck started() {
		SackCheck s = new SackCheck();
		s.setBase(Map.of("TUNGSTEN", 1000L, "GLACITE", 50L, "ENCHANTED_BREAD", 3L), 0);
		return s;
	}

	@Test
	void matchesWhenMessagesExplainTheChange() {
		SackCheck s = started();
		// The same hover twice in one message is read once.
		s.onSackMessage(List.of("  +1,200 Tungsten (Mining Sack)\n  +30 Glacite (Mining Sack)",
			"  +1,200 Tungsten (Mining Sack)\n  +30 Glacite (Mining Sack)"), PRICES);
		s.onSackMessage(List.of("  -16 Glacite (Mining Sack)", "  +1 Enchanted Glacite (Mining Sack)"), PRICES);
		SackCheck.Result r = s.compare(Map.of("TUNGSTEN", 2200L, "GLACITE", 64L, "ENCHANTED_GLACITE", 1L,
			"ENCHANTED_BREAD", 10L), PRICES);
		assertFalse(r.apiUnchanged());
		assertEquals(List.of(), r.diffs());
		assertEquals(3, r.matched());
	}

	@Test
	void reportsMissedMessage() {
		SackCheck s = started();
		s.onSackMessage(List.of("  +500 Tungsten (Mining Sack)"), PRICES);
		SackCheck.Result r = s.compare(Map.of("TUNGSTEN", 2000L, "GLACITE", 50L), PRICES);
		assertEquals(List.of(new SackCheck.Diff("TUNGSTEN", 1000, 500)), r.diffs());
		assertEquals(500, r.diffs().get(0).missing());
	}

	@Test
	void apiNotSavedYet() {
		SackCheck s = started();
		s.onSackMessage(List.of("  +500 Tungsten (Mining Sack)"), PRICES);
		SackCheck.Result r = s.compare(Map.of("TUNGSTEN", 1000L, "GLACITE", 50L), PRICES);
		assertTrue(r.apiUnchanged());
	}

	@Test
	void nothingReadBeforeBase() {
		SackCheck s = new SackCheck();
		s.onSackMessage(List.of("  +500 Tungsten (Mining Sack)"), PRICES);
		assertTrue(s.read.isEmpty());
	}

	@Test
	void parsesSelectedProfile() {
		String json = """
			{"success":true,"profiles":[
			 {"selected":false,"members":{"abc":{"inventory":{"sacks_counts":{"TUNGSTEN":1}}}}},
			 {"selected":true,"members":{"abc":{"inventory":{"sacks_counts":{"TUNGSTEN":42,"GLACITE":7}}}}}
			]}""";
		assertEquals(Map.of("TUNGSTEN", 42L, "GLACITE", 7L), SackCheck.parseSacks(json, "a-b-c"));
	}

	@Test
	void badKeyGivesTurkishReason() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> SackCheck.parseSacks("{\"success\":false,\"cause\":\"Invalid API key\"}", "abc"));
		assertTrue(e.getMessage().contains("geçersiz"));
	}

	@Test
	void noSacksInApi() {
		assertThrows(IllegalStateException.class, () -> SackCheck.parseSacks(
			"{\"success\":true,\"profiles\":[{\"selected\":true,\"members\":{\"abc\":{}}}]}", "abc"));
	}

	@Test
	void oldSackPlace() {
		assertEquals(Map.of("MITHRIL_ORE", 5L), SackCheck.parseSacks(
			"{\"success\":true,\"profiles\":[{\"selected\":true,\"members\":{\"abc\":{\"sacks_counts\":{\"MITHRIL_ORE\":5}}}}]}", "abc"));
	}

	@Test
	void inventoryOnButSacksEmpty() {
		assertTrue(SackCheck.parseSacks(
			"{\"success\":true,\"profiles\":[{\"selected\":true,\"members\":{\"abc\":{\"inventory\":{\"inv_contents\":{}}}}}]}", "abc").isEmpty());
	}

	@Test
	void corpseLootWithoutSackMessageIsNotMissing() {
		SackCheck s = started();
		s.onSackMessage(List.of("  +500 Tungsten (Mining Sack)"), PRICES);
		// Loot of a corpse: 20 Tungsten, its sack message never comes.
		s.onCorpseItem("TUNGSTEN", 20);
		SackCheck.Result r = s.compare(Map.of("TUNGSTEN", 1520L, "GLACITE", 50L), PRICES);
		assertEquals(List.of(), r.diffs());
	}

	@Test
	void corpseLootShownInSackMessageIsNotCountedTwice() {
		SackCheck s = started();
		s.onCorpseItem("TUNGSTEN", 20);
		s.onSackMessage(List.of("  +520 Tungsten (Mining Sack)"), PRICES);
		SackCheck.Result r = s.compare(Map.of("TUNGSTEN", 1520L, "GLACITE", 50L), PRICES);
		assertEquals(List.of(), r.diffs());
		assertTrue(s.corpse.isEmpty());
	}

	@Test
	void sameDifferencesGiveSameKey() {
		SackCheck s = started();
		s.onSackMessage(List.of("  +500 Tungsten (Mining Sack)"), PRICES);
		String a = s.compare(Map.of("TUNGSTEN", 2000L, "GLACITE", 50L), PRICES).key();
		String b = s.compare(Map.of("TUNGSTEN", 2000L, "GLACITE", 50L), PRICES).key();
		String c = s.compare(Map.of("TUNGSTEN", 2100L, "GLACITE", 50L), PRICES).key();
		assertEquals(a, b);
		assertFalse(a.equals(c));
	}

	@Test
	void missingItemsAreAddedOnce() {
		SackCheck s = started();
		s.onSackMessage(List.of("  +500 Tungsten (Mining Sack)"), PRICES);
		Map<String, Long> api = Map.of("TUNGSTEN", 2000L, "GLACITE", 40L);
		s.setMissing(s.compare(api, PRICES).diffs());
		// Glacite went down: never taken off the earnings.
		assertEquals(Map.of("TUNGSTEN", 500L), s.missing);
		assertEquals(Map.of("TUNGSTEN", 500L), s.takeMissing());
		assertTrue(s.missing.isEmpty());
		// Added items count as read: the next check finds Tungsten right.
		assertEquals(List.of(new SackCheck.Diff("GLACITE", -10, 0)), s.compare(api, PRICES).diffs());
	}
}
