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
		s.setMissing(s.compare(api, PRICES).diffs(), PRICES);
		// Glacite went down (its family is down): never taken off the earnings.
		assertEquals(Map.of("TUNGSTEN", 500L), s.missing);
		assertEquals(Map.of("TUNGSTEN", 500L), s.takeMissing());
		assertTrue(s.missing.isEmpty());
		// Added items count as read: the next check finds Tungsten right.
		assertEquals(List.of(new SackCheck.Diff("GLACITE", -10, 0)), s.compare(api, PRICES).diffs());
	}

	@Test
	void previousGameSessionIsCheckedThenNewBaseKeepsMissing() throws Exception {
		SackCheck s = started();
		s.onSackMessage(List.of("  +200 Tungsten (Mining Sack)"), PRICES);
		// The game closed: the session ended, everything for the check goes to the config file.
		s.endedCoins = 20_000.0;
		s.previousPending = true;
		Config c = new Config();
		c.sackCheck = s;
		java.nio.file.Path f = java.nio.file.Files.createTempFile("mpc", ".json");
		c.save(f);
		SackCheck back = Config.load(f).sackCheck;
		assertTrue(back.previousPending);
		assertEquals(20_000.0, back.endedCoins);
		// Next game: the API has 300 more Tungsten, the mod read 200 (a sack message was lost).
		Map<String, Long> now = Map.of("TUNGSTEN", 1300L, "GLACITE", 50L, "ENCHANTED_BREAD", 3L);
		SackCheck.Result r = back.compare(now, PRICES);
		assertEquals(List.of(new SackCheck.Diff("TUNGSTEN", 300, 200)), r.diffs());
		back.setMissing(r.diffs(), PRICES, true);
		back.rebase(now, 5);
		// New base for the new session; the missing 100 stay for Düzelt (Toplam only).
		assertFalse(back.previousPending);
		assertTrue(back.missingEnded);
		assertEquals(Map.of("TUNGSTEN", 100L), back.missing);
		assertEquals(20_000.0, back.endedCoins);
		assertTrue(back.read.isEmpty());
		assertEquals(0, back.compare(now, PRICES).diffs().size());
		// Düzelt adds them, but they are in the new base already: the new session still matches.
		assertEquals(Map.of("TUNGSTEN", 100L), back.takeMissing());
		assertEquals(0, back.compare(now, PRICES).diffs().size());
	}

	@Test
	void lostSupercraftMessageIsNettedInsideTheFamily() {
		SackCheck s = started();
		// Mined 3.200 Glacite, then Supercrafted 10 Enchanted Glacite (1.600 Glacite) and changed server
		// before the sack message came; 5 more Enchanted Glacite came from a lost message too.
		s.onSackMessage(List.of("  +3,200 Glacite (Mining Sack)"), PRICES);
		Map<String, Long> api = Map.of("TUNGSTEN", 1000L, "GLACITE", 50L + 1600, "ENCHANTED_GLACITE", 15L,
			"ENCHANTED_BREAD", 3L);
		List<SackCheck.Diff> diffs = s.compare(api, PRICES).diffs();
		assertEquals(2, diffs.size());
		// Fark and Düzelt: 15 Enchanted (24.000) - 1.600 Glacite (16.000) = 8.000, not 24.000.
		Map<String, Long> m = SackCheck.familyMissing(diffs, PRICES);
		assertEquals(Map.of("GLACITE", -1600L, "ENCHANTED_GLACITE", 15L), m);
		assertEquals(8000, SackCheck.missingCoins(m, PRICES), 0.001);
		List<SackCheck.Fix> fixes = SackCheck.fixes(m, PRICES, id -> PRICES.byId(id).value());
		assertEquals(1, fixes.size());
		assertEquals("ENCHANTED_GLACITE", fixes.get(0).item().id());
		assertEquals(5, fixes.get(0).count(), 0.001);
		assertEquals(8000, fixes.get(0).coins(), 0.001);
		// Düzelt makes the read the API's: the next check matches.
		s.setMissing(diffs, PRICES);
		s.takeMissing();
		assertEquals(List.of(), s.compare(api, PRICES).diffs());
	}

	@Test
	void familyThatWentDownIsNotAdded() {
		SackCheck s = started();
		s.onSackMessage(List.of("  +3,200 Glacite (Mining Sack)"), PRICES);
		// 1 Enchanted (1.600) missing but 3.150 Glacite (31.500) gone: nothing to add.
		Map<String, Long> api = Map.of("TUNGSTEN", 1000L, "GLACITE", 100L, "ENCHANTED_GLACITE", 1L, "ENCHANTED_BREAD", 3L);
		List<SackCheck.Diff> diffs = s.compare(api, PRICES).diffs();
		assertEquals(2, diffs.size());
		assertTrue(SackCheck.familyMissing(diffs, PRICES).isEmpty());
	}

	@Test
	void corpseLootThatStayedInTheInventoryIsNotADifference() {
		SackCheck s = started();
		// 2 Tungsten from a corpse went into the inventory (never into the sacks).
		s.onCorpseItem("TUNGSTEN", 2);
		assertEquals(List.of(), s.compare(Map.of("TUNGSTEN", 1000L, "GLACITE", 50L, "ENCHANTED_BREAD", 3L), PRICES).diffs());
		// Or into the sacks without a sack message: also right.
		assertEquals(List.of(), s.compare(Map.of("TUNGSTEN", 1002L, "GLACITE", 50L, "ENCHANTED_BREAD", 3L), PRICES).diffs());
		// More than the loot: the rest is missing (read counts the loot).
		assertEquals(List.of(new SackCheck.Diff("TUNGSTEN", 5, 2)),
			s.compare(Map.of("TUNGSTEN", 1005L, "GLACITE", 50L, "ENCHANTED_BREAD", 3L), PRICES).diffs());
	}

	/** Mito, 2026-11-14: bought, put into the sacks by hand, sack message lost on a server change. */
	@Test
	void handPutItemsWithLostMessageCanBeTakenOffDuzelt() {
		SackCheck s = started();
		s.onSackMessage(List.of("  +10,000 Glacite (Mining Sack)"), PRICES);
		s.onHandDeposit("ENCHANTED_GLACITE", 500, 1_000);
		s.onWorldChange(5_000); // before the sack message: lost
		assertEquals(Map.of("ENCHANTED_GLACITE", 500L), s.hand);
		assertTrue(s.handPending.isEmpty());
		// The API also has 2,000 mined Glacite whose message was lost.
		SackCheck.Result r = s.compare(Map.of("TUNGSTEN", 1000L, "GLACITE", 12_050L, "ENCHANTED_GLACITE", 500L,
			"ENCHANTED_BREAD", 3L), PRICES);
		History.Check c = History.check(r, 0, PRICES, 10_000);
		History.addHand(c, s, null, false, PRICES);
		assertEquals(20_000 + 800_000, c.missingCoins(), 1e-6);
		c.toggleHand("GLACITE");
		// Only the hand-put 500 Enchanted Glacite goes; the lost mined Glacite is still added.
		assertEquals(20_000, c.missingCoins(), 1e-6);
		assertEquals(800_000, c.handOffCoins(), 1e-6);
		assertEquals(Map.of("GLACITE", 2_000L), c.withoutHand(c.missing));
		c.toggleHand("GLACITE");
		assertEquals(820_000, c.missingCoins(), 1e-6);
		// Oto düzelt: all hand-put items are off at once; a new check keeps what was clicked off.
		History.Check auto = History.check(r, 0, PRICES, 10_000);
		History.addHand(auto, s, null, true, PRICES);
		assertEquals(20_000, auto.missingCoins(), 1e-6);
		History.Check again = History.check(r, 0, PRICES, 20_000);
		History.addHand(again, s, auto, false, PRICES);
		assertTrue(again.handOff("GLACITE"));
	}

	@Test
	void handPutItemsShownInASackMessageAreNotAtRisk() {
		SackCheck s = started();
		s.onHandDeposit("ENCHANTED_GLACITE", 500, 1_000);
		s.onSackMessage(List.of("  +500 Enchanted Glacite (Mining Sack)"), PRICES);
		s.onWorldChange(5_000);
		s.tick(200_000);
		assertTrue(s.hand.isEmpty());
		// No message at all for a long time: at risk too.
		s.onHandDeposit("GLACITE", 64, 300_000);
		s.tick(300_000 + SackCheck.HAND_MS + 1);
		assertEquals(Map.of("GLACITE", 64L), s.hand);
		// A new base forgets them.
		s.setBase(Map.of(), 400_000);
		assertTrue(s.hand.isEmpty());
	}

	@Test
	void handPutMoreThanTheFamilyWentUpAddsNothingForIt() {
		SackCheck s = started();
		s.onHandDeposit("ENCHANTED_GLACITE", 500, 1_000);
		s.onWorldChange(2_000);
		// Only 100 of them show as a difference (the rest were used meanwhile).
		SackCheck.Result r = s.compare(Map.of("TUNGSTEN", 1100L, "GLACITE", 50L, "ENCHANTED_GLACITE", 100L,
			"ENCHANTED_BREAD", 3L), PRICES);
		History.Check c = History.check(r, 0, PRICES, 10_000);
		History.addHand(c, s, null, false, PRICES);
		c.toggleHand("GLACITE");
		assertEquals(10_000, c.missingCoins(), 1e-6); // Tungsten only
		List<History.Line> lines = new java.util.ArrayList<>();
		History.handWarning(lines, c);
		assertTrue(lines.get(1).text().contains("Enchanted Glacite: 500"));
		History.fark(lines, c);
		assertTrue(lines.stream().anyMatch(l -> "GLACITE".equals(l.click()) && l.text().contains("geri al")));
	}
}
