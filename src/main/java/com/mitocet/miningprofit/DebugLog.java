package com.mitocet.miningprofit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * Writes what the mod read from the game, one JSON object per line, so the real Hypixel messages
 * can be looked at and turned into tests (see {@link Replay}). Kinds:
 * prices (the values in use), start, stop, resume, end, sack (hover texts), chat (a line),
 * inv (mining items in the inventory), stash (/pickupstash), warn (a warning).
 * Every event that reads something also has "coins": what the mod counted for it.
 */
public final class DebugLog {
	private static final Gson GSON = new Gson();
	private static final long MAX_BYTES = 5L * 1024 * 1024;
	private final Path file;

	public DebugLog(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	public void prices(String json, long now) {
		JsonObject o = event("prices", now);
		try {
			o.add("data", JsonParser.parseString(json));
		} catch (Exception e) {
			return;
		}
		write(o);
	}

	public void simple(String kind, long now) {
		write(event(kind, now));
	}

	public void sack(List<String> hovers, double coins, long now) {
		JsonObject o = event("sack", now);
		JsonArray a = new JsonArray();
		for (String h : hovers) a.add(h);
		o.add("hovers", a);
		o.addProperty("coins", coins);
		write(o);
	}

	public void chat(String line, double coins, long now) {
		JsonObject o = event("chat", now);
		o.addProperty("line", line);
		o.addProperty("coins", coins);
		write(o);
	}

	public void inventory(List<Map.Entry<String, Integer>> stacks, long now) {
		JsonObject o = event("inv", now);
		JsonArray a = new JsonArray();
		for (Map.Entry<String, Integer> s : stacks) {
			JsonArray pair = new JsonArray();
			pair.add(s.getKey());
			pair.add(s.getValue());
			a.add(pair);
		}
		o.add("stacks", a);
		write(o);
	}

	public void warn(String text, long now) {
		JsonObject o = event("warn", now);
		o.addProperty("text", text);
		write(o);
	}

	private static JsonObject event(String kind, long now) {
		JsonObject o = new JsonObject();
		o.addProperty("t", now);
		o.addProperty("kind", kind);
		return o;
	}

	private synchronized void write(JsonObject o) {
		try {
			Files.createDirectories(file.getParent());
			// Keep it small: when full, the old part moves to .old and a new file starts.
			if (Files.exists(file) && Files.size(file) > MAX_BYTES) {
				Files.move(file, file.resolveSibling(file.getFileName() + ".old"), StandardCopyOption.REPLACE_EXISTING);
			}
			Files.writeString(file, GSON.toJson(o) + "\n", StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception e) {
			MiningProfitClient.LOG.warn("Could not write {}", file, e);
		}
	}
}
