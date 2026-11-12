package com.mitocet.miningprofit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * /kazanc update: downloads the newest jar from the GitHub Releases page and puts it in place of
 * the running one when the game closes.
 *
 * <p>Windows keeps the running jar locked, so it can't be deleted while the game is open. The new
 * jar is first saved outside the mods folder (in {@code mining-profit-calc-update/}, so Fabric never
 * sees two copies of the mod). When the game closes, a small helper process ({@link UpdateHelper})
 * waits for the game to exit, deletes the old jar and moves the new one into the mods folder.
 * If anything fails the old jar stays and the game still starts; the next game tries again.
 * Same idea as libautoupdate (used by SkyHanni and NEU), written here without it.
 */
public final class Updater {
	public static final String RELEASES_API = "https://api.github.com/repos/MitoCet/Mining-Profit-Calc/releases/latest";
	public static final String RELEASES_PAGE = "https://github.com/MitoCet/Mining-Profit-Calc/releases";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("miningprofit");
	private static final Pattern JAR_NAME = Pattern.compile("mining-profit-calc-(\\d+(?:\\.\\d+)*)\\.jar");

	/** The newest release: its version (1.0.23), jar name, download link, size and sha256 (or null). */
	public record Release(String version, String fileName, String url, long size, String sha256) {}

	private final Path dir;
	private final Path currentJar;
	private final String currentVersion;
	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private volatile boolean helperStarted;

	/**
	 * @param dir         where the downloaded jar waits until the game closes
	 * @param currentJar  the running mod jar (null when it isn't a jar file, e.g. in a dev setup)
	 */
	public Updater(Path dir, Path currentJar, String currentVersion) {
		this.dir = dir;
		this.currentJar = currentJar;
		this.currentVersion = currentVersion;
	}

	public String currentVersion() {
		return currentVersion;
	}

	public boolean canUpdate() {
		return currentJar != null;
	}

	// ---------- versions ----------

	/** Compares 1.0.9 and 1.0.23 by number; anything after the numbers ("-test") is ignored. */
	public static int compare(String a, String b) {
		int[] x = numbers(a), y = numbers(b);
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
			if (p != q) return Integer.compare(p, q);
		}
		return 0;
	}

	private static int[] numbers(String v) {
		Matcher m = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)").matcher(v == null ? "" : v.trim());
		if (!m.find()) return new int[0];
		String[] parts = m.group(1).split("\\.");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try {
				out[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException e) {
				out[i] = Integer.MAX_VALUE;
			}
		}
		return out;
	}

	public boolean isNewer(String version) {
		return compare(version, currentVersion) > 0;
	}

	// ---------- the newest release ----------

	/** Reads GitHub's "latest release" answer: the version and the mod jar in it. */
	public static Release parseRelease(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		String tag = root.has("tag_name") ? root.get("tag_name").getAsString() : "";
		String version = tag.startsWith("v") ? tag.substring(1) : tag;
		if (!root.has("assets")) throw new IllegalStateException("Release'te dosya yok.");
		for (JsonElement e : root.getAsJsonArray("assets")) {
			JsonObject a = e.getAsJsonObject();
			String name = a.get("name").getAsString();
			Matcher m = JAR_NAME.matcher(name);
			if (!m.matches()) continue;
			String sha = null;
			if (a.has("digest") && !a.get("digest").isJsonNull()) {
				String d = a.get("digest").getAsString();
				if (d.startsWith("sha256:")) sha = d.substring(7).toLowerCase(Locale.ROOT);
			}
			long size = a.has("size") ? a.get("size").getAsLong() : -1;
			return new Release(version.isEmpty() ? m.group(1) : version, name,
				a.get("browser_download_url").getAsString(), size, sha);
		}
		throw new IllegalStateException("Release'te mining-profit-calc jar'ı yok.");
	}

	/** Asks GitHub for the newest release (call off the game thread). */
	public Release latest() throws IOException, InterruptedException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(RELEASES_API))
			.header("Accept", "application/vnd.github+json")
			.header("User-Agent", "MiningProfitCalc/" + currentVersion)
			.timeout(Duration.ofSeconds(30)).GET().build();
		HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) throw new IOException("GitHub HTTP " + res.statusCode());
		return parseRelease(res.body());
	}

	// ---------- download ----------

	/** The jar already downloaded and waiting for the game to close, or null. */
	public Path pending() {
		try (Stream<Path> files = Files.list(dir)) {
			Path best = null;
			String bestVersion = null;
			for (Path p : (Iterable<Path>) files::iterator) {
				Matcher m = JAR_NAME.matcher(p.getFileName().toString());
				if (!m.matches()) continue;
				if (bestVersion == null || compare(m.group(1), bestVersion) > 0) {
					best = p;
					bestVersion = m.group(1);
				}
			}
			return best != null && isNewer(bestVersion) ? best : null;
		} catch (IOException e) {
			return null;
		}
	}

	public static String versionOf(Path jar) {
		Matcher m = JAR_NAME.matcher(jar.getFileName().toString());
		return m.matches() ? m.group(1) : "?";
	}

	/**
	 * Start of a game: downloaded jars that are not newer than the running mod are left over from
	 * an update that already happened (or an older one): they are deleted.
	 */
	public void cleanUp() {
		if (!Files.isDirectory(dir)) return;
		Path keep = pending();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path p : (Iterable<Path>) files::iterator) {
				String n = p.getFileName().toString();
				if (p.equals(keep) || n.equals("update.log")) continue;
				if (n.endsWith(".jar") || n.endsWith(".part")) Files.deleteIfExists(p);
			}
		} catch (IOException e) {
			LOG.warn("Update clean up failed: {}", e.toString());
		}
	}

	/** Downloads the release jar into the waiting folder and checks it (call off the game thread). */
	public Path download(Release r) throws IOException, InterruptedException {
		Files.createDirectories(dir);
		Path part = dir.resolve(r.fileName() + ".part");
		Path target = dir.resolve(r.fileName());
		HttpRequest req = HttpRequest.newBuilder(URI.create(r.url()))
			.header("User-Agent", "MiningProfitCalc/" + currentVersion)
			.timeout(Duration.ofMinutes(2)).GET().build();
		HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(part));
		try {
			if (res.statusCode() != 200) throw new IOException("İndirme HTTP " + res.statusCode());
			verify(part, r);
			Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(part);
		}
		return target;
	}

	/** Size and sha256 as GitHub lists them, and the jar must really be this mod. */
	static void verify(Path jar, Release r) throws IOException {
		if (r.size() >= 0 && Files.size(jar) != r.size()) {
			throw new IOException("İndirilen dosya eksik (" + Files.size(jar) + " / " + r.size() + " bayt).");
		}
		if (r.sha256() != null) {
			String sha = sha256(jar);
			if (!sha.equalsIgnoreCase(r.sha256())) throw new IOException("İndirilen dosyanın sha256'sı tutmuyor.");
		}
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			var entry = zip.getEntry("fabric.mod.json");
			if (entry == null) throw new IOException("İndirilen dosya Fabric modu değil.");
			try (InputStream in = zip.getInputStream(entry)) {
				JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				if (!"miningprofit".equals(o.get("id").getAsString())) throw new IOException("İndirilen dosya bu mod değil.");
			}
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("İndirilen jar okunamadı: " + e.getMessage());
		}
	}

	static String sha256(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
			return HexFormat.of().formatHex(md.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	// ---------- when the game closes ----------

	/**
	 * The game is closing: if a newer jar is waiting, start the helper that swaps the jars once
	 * this process has exited. Runs once; safe to call from CLIENT_STOPPING and a shutdown hook.
	 */
	public synchronized void onGameClosing() {
		if (helperStarted || currentJar == null) return;
		Path next = pending();
		if (next == null) return;
		helperStarted = true;
		try {
			Path target = currentJar.resolveSibling(next.getFileName().toString());
			Path classes = dir.resolve("helper");
			extractHelper(classes);
			List<String> cmd = new ArrayList<>();
			cmd.add(javaExecutable());
			cmd.add("-cp");
			cmd.add(classes.toString());
			cmd.add(UpdateHelper.class.getName());
			cmd.addAll(UpdateHelper.args(ProcessHandle.current().pid(), currentJar, next, target));
			new ProcessBuilder(cmd)
				.directory(dir.toFile())
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.appendTo(dir.resolve("update.log").toFile()))
				.start();
			LOG.info("Update helper started: {} -> {}", currentJar, target);
		} catch (Exception e) {
			LOG.warn("Could not start the update helper: {}", e.toString());
		}
	}

	/** Copies UpdateHelper.class out of the running jar: the helper must not run from (and lock) it. */
	private static void extractHelper(Path classes) throws IOException {
		String res = "/" + UpdateHelper.class.getName().replace('.', '/') + ".class";
		Path out = classes.resolve(UpdateHelper.class.getName().replace('.', '/') + ".class");
		Files.createDirectories(out.getParent());
		try (InputStream in = Updater.class.getResourceAsStream(res)) {
			if (in == null) throw new IOException("UpdateHelper.class not found");
			Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** The game's own Java, without a console window on Windows. */
	private static String javaExecutable() {
		Path bin = Path.of(System.getProperty("java.home"), "bin");
		for (String n : new String[] {"javaw.exe", "java.exe", "java"}) {
			Path p = bin.resolve(n);
			if (Files.isRegularFile(p)) return p.toString();
		}
		return ProcessHandle.current().info().command().orElse("java");
	}
}
