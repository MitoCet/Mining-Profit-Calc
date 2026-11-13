package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyCheckTest {
	@Test
	void readsAnswers() {
		assertEquals(KeyCheck.Result.OK, KeyCheck.result(200, "{\"success\":true,\"uuid\":\"x\",\"session\":{\"online\":false}}"));
		assertEquals(KeyCheck.Result.INVALID, KeyCheck.result(403, "{\"success\":false,\"cause\":\"Invalid API key\"}"));
		assertEquals(KeyCheck.Result.INVALID, KeyCheck.result(403, "<html>forbidden</html>"));
		// Throttled, Hypixel down or a broken answer: say nothing about the key.
		assertEquals(KeyCheck.Result.UNKNOWN, KeyCheck.result(429, "{\"success\":false,\"cause\":\"Key throttle\"}"));
		assertEquals(KeyCheck.Result.UNKNOWN, KeyCheck.result(503, "<html>down</html>"));
		assertEquals(KeyCheck.Result.UNKNOWN, KeyCheck.result(200, "{\"success\":false}"));
	}

	@Test
	void warnsInTheLast12Hours() {
		long h = 3600_000L;
		long saved = 1_000_000_000L;
		assertEquals(-1, KeyCheck.hoursLeftToWarn(0, saved)); // save time unknown
		assertEquals(-1, KeyCheck.hoursLeftToWarn(saved, saved + 24 * h));
		assertEquals(12, KeyCheck.hoursLeftToWarn(saved, saved + 60 * h));
		assertEquals(3, KeyCheck.hoursLeftToWarn(saved, saved + 69 * h + 1));
		assertEquals(-1, KeyCheck.hoursLeftToWarn(saved, saved + 73 * h)); // past: the key check itself says so
	}

	@Test
	void knowsSkyBlockSidebar() {
		org.junit.jupiter.api.Assertions.assertTrue(KeyCheck.isSkyBlockTitle("§e§lSKYBLOCK"));
		org.junit.jupiter.api.Assertions.assertTrue(KeyCheck.isSkyBlockTitle("SKYBLOCK CO-OP"));
		org.junit.jupiter.api.Assertions.assertTrue(KeyCheck.isSkyBlockTitle("SKIBLOCK"));
		org.junit.jupiter.api.Assertions.assertFalse(KeyCheck.isSkyBlockTitle("§e§lHYPIXEL"));
		org.junit.jupiter.api.Assertions.assertFalse(KeyCheck.isSkyBlockTitle("BED WARS"));
		org.junit.jupiter.api.Assertions.assertFalse(KeyCheck.isSkyBlockTitle(null));
	}
}
