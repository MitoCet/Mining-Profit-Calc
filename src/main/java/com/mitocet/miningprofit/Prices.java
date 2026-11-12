package com.mitocet.miningprofit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Unit values from the site's price collector (mod-values.json on the data branch).
 * Mining materials and gemstones carry a family (fam): they are valued by the site's best
 * route, and they are the only items counted from sack messages.
 */
public final class Prices {
	public static final String URL = "https://raw.githubusercontent.com/MitoCet/skyblock-forge-flip/data/mod-values.json";

	public record Item(String id, String name, double value, String family) {
		public boolean mining() {
			return family != null;
		}
	}

	private final Map<String, Item> byName;
	private final Map<String, Item> byId;
	public final long timestamp;

	private Prices(Map<String, Item> byName, Map<String, Item> byId, long timestamp) {
		this.byName = byName;
		this.byId = byId;
		this.timestamp = timestamp;
	}

	public static final Prices EMPTY = new Prices(Map.of(), Map.of(), 0);

	public static Prices parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		Map<String, Item> byName = new HashMap<>();
		Map<String, Item> byId = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("items").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			String name = o.has("name") ? o.get("name").getAsString() : null;
			Item item = new Item(e.getKey(), name, o.get("v").getAsDouble(), o.has("fam") ? o.get("fam").getAsString() : null);
			byId.put(item.id, item);
			if (name != null) {
				// Mining items win a name clash: they are the ones counted from the sacks.
				Item old = byName.get(key(name));
				if (old == null || (!old.mining() && item.mining())) byName.put(key(name), item);
			}
		}
		return new Prices(byName, byId, root.has("ts") ? root.get("ts").getAsLong() : 0);
	}

	public boolean loaded() {
		return !byId.isEmpty();
	}

	public int size() {
		return byId.size();
	}

	/** Chat line after /kazanc fiyat succeeded: item count and how old the site's data is. */
	public String doneText(long now) {
		String text = "Fiyatlar yenilendi: " + size() + " item.";
		if (timestamp > 0) {
			long min = Math.max(0, (now - timestamp * 1000) / 60_000);
			text += min < 1 ? " Veri az önce toplanmış." : " Veri " + min + " dk önce toplanmış.";
		}
		return text;
	}

	public Item byName(String name) {
		return byName.get(key(name));
	}

	public Item byId(String id) {
		return byId.get(id);
	}

	/** Name as a lookup key: no colour codes, symbols or case. */
	public static String key(String name) {
		String s = name.replaceAll("§.", "");
		s = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		s = s.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
		return s;
	}
}
