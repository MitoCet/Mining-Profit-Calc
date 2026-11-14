package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoppingListTest {
	// Copied from the site (Mining → Forge planı → Mod için kopyala), plus a Rough → Flawed craft row.
	static final String PLAN = "MPC-PLAN1 {\"v\":1,\"at\":1794668722032,\"forge\":[[\"GLACITE_AMALGAMATION\",\"Glacite Amalgamation\",1],[\"REFINED_TUNGSTEN\",\"Refined Tungsten\",2]],"
		+ "\"rows\":[[\"c\",\"FLAWED_CITRINE_GEM\",\"Flawed Citrine Gemstone\",1,240,\"FINE_CITRINE_GEM\",\"\"],"
		+ "[\"c\",\"FINE_CITRINE_GEM\",\"Fine Citrine Gemstone\",3,3,\"\",\"\"],[\"b\",\"FINE_CITRINE_GEM\",\"Fine Citrine Gemstone\",1,4,\"\",\"bz\"],"
		+ "[\"b\",\"FINE_ONYX_GEM\",\"Fine Onyx Gemstone\",4,4,\"\",\"bz\"],[\"x\",\"RUBY_CRYSTAL\",\"Ruby Crystal\",1,1,\"\",\"\"],"
		+ "[\"h\",\"ENCHANTED_GLACITE\",\"Enchanted Glacite\",256,256,\"\",\"\"],[\"h\",\"ENCHANTED_TUNGSTEN\",\"Enchanted Tungsten\",320,320,\"\",\"\"]]}";

	private static ShoppingList.Row row(ShoppingList l, char a, String id) {
		for (ShoppingList.Row r : l.rows) if (r.action() == a && r.id().equals(id)) return r;
		throw new AssertionError(a + " " + id);
	}

	@Test
	void parsesAndRejects() {
		ShoppingList l = ShoppingList.parse(PLAN);
		assertEquals(2, l.forge.size());
		assertEquals(7, l.rows.size());
		assertThrows(IllegalArgumentException.class, () -> ShoppingList.parse("hello"));
		assertThrows(IllegalArgumentException.class, () -> ShoppingList.parse("MPC-PLAN1 {broken"));
		assertThrows(IllegalArgumentException.class, () -> ShoppingList.parse(null));
	}

	@Test
	void craftThenBuySameItem() {
		ShoppingList l = ShoppingList.parse(PLAN);
		l.setBase(Map.of("FLAWED_CITRINE_GEM", 239L, "ENCHANTED_GLACITE", 256L), Map.of());
		ShoppingList.Row craft = row(l, 'c', "FINE_CITRINE_GEM"), buy = row(l, 'b', "FINE_CITRINE_GEM");
		ShoppingList.Row rough = row(l, 'c', "FLAWED_CITRINE_GEM");
		assertFalse(l.done(craft));
		assertFalse(l.done(rough));
		assertTrue(l.done(row(l, 'h', "ENCHANTED_GLACITE")));
		// Rough → 1 Flawed (sack message), then Supercraft 3 Fine from 240 Flawed.
		l.onSackMessage(List.of("Added items:\n  +1 Flawed Citrine Gemstone (Gemstones Sack)\nRemoved items:\n  -80 Rough Citrine Gemstone (Gemstones Sack)"));
		assertTrue(l.done(rough));
		l.onSackMessage(List.of("Added items:\n  +3 Fine Citrine Gemstone (Gemstones Sack)\nRemoved items:\n  -240 Flawed Citrine Gemstone (Gemstones Sack)"));
		assertTrue(l.done(craft));
		// Flawed are gone now, but its parent (Fine) has enough: still ticked.
		assertTrue(l.done(rough));
		assertFalse(l.done(buy));
		assertEquals(0, l.progress(buy));
		assertEquals(1, l.left(buy));
		// Bought one: it lands in the inventory.
		l.setInventory(List.of(Map.entry("Fine Citrine Gemstone", 1)));
		assertTrue(l.done(buy));
		assertEquals(1, l.progress(buy));
	}

	@Test
	void storageAndGfs() {
		ShoppingList l = ShoppingList.parse(PLAN);
		l.setBase(Map.of("ENCHANTED_TUNGSTEN", 100L), Map.of("ec:1", Map.of("ENCHANTED_TUNGSTEN", 200L)));
		assertEquals(300, l.owned("ENCHANTED_TUNGSTEN"));
		// Opened ender chest page 1 and took 64 out into the inventory.
		l.setStoragePage("ec:1", List.of(Map.entry("Enchanted Tungsten", 136)));
		l.setInventory(List.of(Map.entry("§aEnchanted Tungsten", 64)));
		assertEquals(300, l.owned("ENCHANTED_TUNGSTEN"));
		// /gfs 20 more from the sacks.
		l.onChatLine("Moved 20 Enchanted Tungsten from your Sacks to your inventory.");
		l.setInventory(List.of(Map.entry("Enchanted Tungsten", 84)));
		assertEquals(300, l.owned("ENCHANTED_TUNGSTEN"));
		assertFalse(l.done(row(l, 'h', "ENCHANTED_TUNGSTEN")));
		l.setInventory(List.of(Map.entry("Enchanted Tungsten", 104)));
		assertTrue(l.done(row(l, 'h', "ENCHANTED_TUNGSTEN")));
	}

	@Test
	void storageTitles() {
		assertEquals("ec:1", ShoppingList.storageKey("Ender Chest"));
		assertEquals("ec:3", ShoppingList.storageKey("Ender Chest (3/9)"));
		assertEquals("bp:2", ShoppingList.storageKey("Jumbo Backpack (Slot #2)"));
		assertEquals("bp:12", ShoppingList.storageKey("§6Greater Backpack§r (Slot #12)"));
		assertEquals("vault", ShoppingList.storageKey("Personal Vault"));
		assertNull(ShoppingList.storageKey("Bazaar ➜ Mining"));
	}

	@Test
	void apiStorage() throws Exception {
		// Ender chest: one item on page 1 (slot 3), one on page 2 (slot 45). Backpack "0" = Slot #1.
		String ec = blob(new String[] { null, null, null, "FINE_ONYX_GEM" }, 50, 3);
		String ec2 = blob(new String[46], 0, 0);
		String bp = blob(new String[] { "ENCHANTED_GLACITE" }, 64, 0);
		String withPage2 = blobAt("ENCHANTED_TUNGSTEN", 45, 10);
		String json = "{\"success\":true,\"profiles\":[{\"selected\":true,\"members\":{\"abc\":{\"inventory\":{"
			+ "\"ender_chest_contents\":{\"data\":\"" + withPage2 + "\"},"
			+ "\"backpack_contents\":{\"0\":{\"data\":\"" + bp + "\"}},"
			+ "\"personal_vault_contents\":{\"data\":\"" + ec + "\"}}}}}]}";
		Map<String, Map<String, Long>> s = ShoppingList.parseStorage(json, "abc");
		assertEquals(10L, s.get("ec:2").get("ENCHANTED_TUNGSTEN"));
		assertEquals(64L, s.get("bp:1").get("ENCHANTED_GLACITE"));
		assertEquals(50L, s.get("vault").get("FINE_ONYX_GEM"));
		assertTrue(ec2.length() > 0);
	}

	private static String blobAt(String id, int slot, int count) throws Exception {
		String[] ids = new String[slot + 1];
		ids[slot] = id;
		return blob(ids, count, slot);
	}

	/** A Hypixel-style item blob: root compound { i: [ {Count, tag:{ExtraAttributes:{id}}} | {} ] }. */
	private static String blob(String[] ids, int count, int ignored) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
			out.writeByte(10); out.writeUTF("");
			out.writeByte(9); out.writeUTF("i"); out.writeByte(10); out.writeInt(ids.length);
			for (String id : ids) {
				if (id != null) {
					out.writeByte(1); out.writeUTF("Count"); out.writeByte(count);
					out.writeByte(10); out.writeUTF("tag");
					out.writeByte(10); out.writeUTF("ExtraAttributes");
					out.writeByte(8); out.writeUTF("id"); out.writeUTF(id);
					out.writeByte(0);
					out.writeByte(0);
				}
				out.writeByte(0);
			}
			out.writeByte(0);
		}
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}
}
