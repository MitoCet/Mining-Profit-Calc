package com.mitocet.miningprofit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plays a {@link DebugLog} file back through a new {@link Tracker}, for tests: every sack and
 * chat event must count what it says. An event's "expect" (written by hand when the mod was
 * wrong) wins over the "coins" the mod counted then.
 */
public final class Replay {
	/** One event that counted something else than expected. */
	public record Mismatch(int line, String kind, double expected, double got) {}

	public record Result(Tracker tracker, List<Mismatch> mismatches) {}

	public static Result run(List<String> lines) {
		Prices[] prices = {Prices.EMPTY};
		Tracker t = new Tracker(() -> prices[0]);
		List<Mismatch> bad = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String raw = lines.get(i).trim();
			if (raw.isEmpty() || raw.startsWith("//")) continue;
			JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
			long now = o.get("t").getAsLong();
			String kind = o.get("kind").getAsString();
			double got;
			switch (kind) {
				case "prices" -> {
					prices[0] = Prices.parse(o.get("data").toString());
					continue;
				}
				case "start" -> {
					t.start(now);
					continue;
				}
				case "stop" -> {
					t.stop(now);
					continue;
				}
				case "resume" -> {
					t.resume(now);
					continue;
				}
				case "end" -> {
					t.end(now);
					continue;
				}
				case "sackmenu-open", "sackmenu-close" -> {
					t.onSackMenu(kind.equals("sackmenu-open"), now);
					continue;
				}
				case "menu-open", "menu-close" -> {
					t.onMenu(kind.equals("menu-open"), now);
					continue;
				}
				case "world" -> {
					t.onWorldChange(now);
					continue;
				}
				case "stash" -> {
					t.onStashPickup(now);
					continue;
				}
				case "inv" -> {
					List<Map.Entry<String, Integer>> stacks = new ArrayList<>();
					for (JsonElement e : o.getAsJsonArray("stacks")) {
						JsonArray pair = e.getAsJsonArray();
						stacks.add(Map.entry(pair.get(0).getAsString(), pair.get(1).getAsInt()));
					}
					t.onInventory(stacks, now);
					continue;
				}
				case "sack" -> {
					List<String> hovers = new ArrayList<>();
					for (JsonElement e : o.getAsJsonArray("hovers")) hovers.add(e.getAsString());
					got = t.onSackMessage(hovers, now);
				}
				case "chat" -> got = t.onChatLine(o.get("line").getAsString(), now);
				default -> {
					continue;
				}
			}
			JsonElement want = o.has("expect") ? o.get("expect") : o.get("coins");
			if (want != null && Math.abs(want.getAsDouble() - got) > 0.01) {
				bad.add(new Mismatch(i + 1, kind, want.getAsDouble(), got));
			}
		}
		return new Result(t, bad);
	}
}
