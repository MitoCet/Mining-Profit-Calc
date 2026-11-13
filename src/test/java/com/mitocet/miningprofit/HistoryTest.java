package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTest {
	private static final Prices PRICES = Prices.parse("""
		{"ts":1,"items":{
		 "TUNGSTEN":{"v":100,"fam":"TUNGSTEN","name":"Tungsten"},
		 "ROUGH_RUBY_GEM":{"v":5,"fam":"GEM_RUBY","name":"Rough Ruby Gemstone"}
		}}""");

	private static Tracker tracker() {
		return new Tracker(() -> PRICES);
	}

	/** Earns like a fix does (same bookkeeping as sack earnings), for a quick session. */
	private static History.Session session(Tracker t, long start, String id, double count) {
		t.start(start);
		t.addFix(PRICES.byId(id), count, true);
		History.Session r = t.record(start + 600_000);
		t.end(start + 600_000);
		return r;
	}

	@Test
	void totalIsTheSumAndDeletingTakesOut() {
		Tracker t = tracker();
		History h = new History();
		h.sessions.add(session(t, 1_000, "TUNGSTEN", 10));      // 1000
		h.sessions.add(session(t, 2_000_000, "ROUGH_RUBY_GEM", 40)); // 200
		assertEquals(1200, t.totalCoins, 1e-9);
		assertEquals(2, h.sessions.size());
		assertEquals(1_000, h.sessions.get(0).start);

		History.Session first = h.find(1_000);
		h.sessions.remove(first);
		t.remove(first);
		assertEquals(200, t.totalCoins, 1e-9);
		assertEquals(0, t.totalBreakdown.total(Breakdown.METAL), 1e-9);
		assertEquals(200, t.totalBreakdown.total(Breakdown.GEMSTONE), 1e-9);
		assertTrue(t.totalBreakdown.cats.get(Breakdown.METAL) == null);
	}

	@Test
	void savedBreakdownIsACopy() {
		Tracker t = tracker();
		History.Session r = session(t, 1_000, "TUNGSTEN", 10);
		t.start(5_000_000);
		t.addFix(PRICES.byId("TUNGSTEN"), 5, true);
		assertEquals(1000, r.breakdown.total(Breakdown.METAL), 1e-9);
		assertEquals(1000, r.coins, 1e-9);
	}

	@Test
	void resetKeepsOnlyTheRunningSession() {
		Tracker t = tracker();
		session(t, 1_000, "TUNGSTEN", 10);
		t.start(2_000_000);
		t.addFix(PRICES.byId("TUNGSTEN"), 3, true);
		t.resetTotal(2_100_000);
		assertEquals(300, t.totalCoins, 1e-9);
		assertEquals(300, t.totalBreakdown.total(Breakdown.METAL), 1e-9);
		// The running session ends later: deleting it leaves Toplam at 0, not below.
		History.Session r = t.record(2_200_000);
		t.end(2_200_000);
		t.remove(r);
		assertEquals(0, t.totalCoins, 1e-9);
	}

	@Test
	void checkAndFixGoToTheSession() {
		Tracker t = tracker();
		History.Session r = session(t, 1_000, "TUNGSTEN", 10);
		SackCheck.Result res = new SackCheck.Result(false,
			List.of(new SackCheck.Diff("TUNGSTEN", 14, 10), new SackCheck.Diff("ROUGH_RUBY_GEM", 0, 2)), 3);
		r.check = History.check(res, 1000, PRICES, 9_000);
		// Ruby went down: never taken off, so the Fark is what Düzelt adds.
		assertEquals(400, r.check.diffCoins, 1e-9);
		assertEquals(Map.of("TUNGSTEN", 4L), r.check.missing);
		assertEquals(400, r.check.missingCoins(), 1e-9);
		assertTrue(History.status(r.check).startsWith("⚠ sack farkı +"));

		t.addFixTo(r, PRICES.byId("TUNGSTEN"), 4, 100);
		r.check.fixedCoins = 400;
		r.check.missing.clear();
		assertEquals(1400, r.coins, 1e-9);
		assertEquals(1400, t.totalCoins, 1e-9);
		assertEquals("✔ düzeltildi (+400)", History.status(r.check));
		List<History.Line> lines = History.lines(r, "x", false);
		assertTrue(lines.stream().anyMatch((l) -> l.text().contains("Düzelt ile eklendi")));
		assertTrue(lines.stream().anyMatch((l) -> l.text().contains("Tungsten: gerçek +14, mod +10")));
	}

	@Test
	void savedWithGson() {
		Tracker t = tracker();
		Config c = new Config();
		c.history = new History();
		c.history.sessions.add(session(t, 1_000, "TUNGSTEN", 10));
		com.google.gson.Gson g = new com.google.gson.Gson();
		Config back = g.fromJson(g.toJson(c), Config.class);
		assertEquals(1000, back.history.sessions.get(0).coins, 1e-9);
		assertEquals(1000, back.history.sessions.get(0).breakdown.total(Breakdown.METAL), 1e-9);
	}

	/**
	 * Mito's session of 14 Kas 00:23 (v1.0.33): the menu showed Fark +2,04M but Düzelt +14,38M. Netted
	 * per family both are ~7,59M: the Enchanted Glacite that a lost Supercraft message hid (+10,3M)
	 * minus the Glacite it used (-6,6M), and the 2 Flawless Onyx from corpse loot that stayed in the
	 * inventory are no longer a -5,7M difference.
	 */
	@Test
	void oldCheckIsNettedPerFamily() throws Exception {
		java.nio.file.Path dir = java.nio.file.Path.of("src/test/resources/history");
		Prices p = Prices.parse(java.nio.file.Files.readString(dir.resolve("eski-kontrol-fiyat.json")));
		History.Check c = new com.google.gson.Gson().fromJson(
			java.nio.file.Files.readString(dir.resolve("eski-kontrol.json")), History.Check.class);
		assertEquals(14_383_445, c.missingCoins(), 1);
		assertTrue(c.renet(p));
		assertEquals(c.diffCoins, c.missingCoins(), 0.001);
		assertEquals(7_594_482, c.diffCoins, 1);
		assertEquals(-229_863L, c.missing.get("GLACITE"));
		assertEquals(2_240L, c.missing.get("ENCHANTED_GLACITE"));
		// Onyx went down overall (the Flawless ones): not added, never taken off.
		assertTrue(!c.missing.containsKey("FLAWLESS_ONYX_GEM") && !c.missing.containsKey("FINE_ONYX_GEM"));
		// Düzelt adds one line per family, never negative counts.
		List<SackCheck.Fix> fixes = SackCheck.fixes(c.missing, p, id -> p.byId(id).value());
		double sum = 0;
		for (SackCheck.Fix f : fixes) {
			assertTrue(f.count() > 0);
			sum += f.coins();
		}
		assertEquals(c.diffCoins, sum, 1);
		// Done once.
		assertTrue(!c.renet(p));
	}
}
