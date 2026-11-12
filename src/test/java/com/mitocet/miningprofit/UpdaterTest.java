package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class UpdaterTest {
	@TempDir
	Path tmp;

	@Test
	void comparesVersionsByNumber() {
		assertTrue(Updater.compare("1.0.23", "1.0.9") > 0);
		assertTrue(Updater.compare("v1.0.10", "1.0.9") > 0);
		assertEquals(0, Updater.compare("1.0.22-test", "1.0.22"));
		assertTrue(Updater.compare("1.1", "1.0.99") > 0);
		assertTrue(Updater.compare("1.0.22", "1.0.23") < 0);
	}

	@Test
	void readsLatestRelease() {
		String json = """
			{"tag_name":"v1.0.23","assets":[
			 {"name":"notes.txt","size":3,"browser_download_url":"https://x/notes.txt"},
			 {"name":"mining-profit-calc-1.0.23.jar","size":1234,"digest":"sha256:ABCD",
			  "browser_download_url":"https://github.com/MitoCet/Mining-Profit-Calc/releases/download/v1.0.23/mining-profit-calc-1.0.23.jar"}]}
			""";
		Updater.Release r = Updater.parseRelease(json);
		assertEquals("1.0.23", r.version());
		assertEquals("mining-profit-calc-1.0.23.jar", r.fileName());
		assertEquals(1234, r.size());
		assertEquals("abcd", r.sha256());
		assertThrows(IllegalStateException.class, () -> Updater.parseRelease("{\"tag_name\":\"v1\",\"assets\":[]}"));
	}

	@Test
	void pendingIsOnlyANewerDownloadedJar() throws IOException {
		Path dir = tmp.resolve("update");
		Files.createDirectories(dir);
		Updater u = new Updater(dir, tmp.resolve("mods/mining-profit-calc-1.0.22.jar"), "1.0.22");
		assertNull(u.pending());
		Files.writeString(dir.resolve("mining-profit-calc-1.0.21.jar"), "old");
		assertNull(u.pending());
		Files.writeString(dir.resolve("mining-profit-calc-1.0.24.jar"), "new");
		Files.writeString(dir.resolve("mining-profit-calc-1.0.23.jar"), "new");
		assertEquals("mining-profit-calc-1.0.24.jar", u.pending().getFileName().toString());

		// After the update ran, the new game cleans up what is not newer than itself.
		Updater after = new Updater(dir, tmp.resolve("mods/mining-profit-calc-1.0.24.jar"), "1.0.24");
		after.cleanUp();
		assertNull(after.pending());
		try (var files = Files.list(dir)) {
			assertEquals(0, files.filter((p) -> p.toString().endsWith(".jar")).count());
		}
	}

	@Test
	void verifiesTheDownloadedJar() throws IOException {
		Path jar = tmp.resolve("mining-profit-calc-1.0.23.jar");
		writeJar(jar, "{\"id\": \"miningprofit\"}");
		long size = Files.size(jar);
		String sha = Updater.sha256(jar);
		Updater.verify(jar, new Updater.Release("1.0.23", "x.jar", "u", size, sha));
		Updater.verify(jar, new Updater.Release("1.0.23", "x.jar", "u", -1, null));
		assertThrows(IOException.class, () -> Updater.verify(jar, new Updater.Release("1.0.23", "x.jar", "u", size + 1, null)));
		assertThrows(IOException.class, () -> Updater.verify(jar, new Updater.Release("1.0.23", "x.jar", "u", size, "00" + sha.substring(2))));

		Path other = tmp.resolve("other.jar");
		writeJar(other, "{\"id\": \"othermod\"}");
		assertThrows(IOException.class, () -> Updater.verify(other, new Updater.Release("1", "x.jar", "u", -1, null)));
		Path notJar = tmp.resolve("bad.jar");
		Files.writeString(notJar, "<html>not found</html>");
		assertThrows(IOException.class, () -> Updater.verify(notJar, new Updater.Release("1", "x.jar", "u", -1, null)));
	}

	@Test
	void helperSwapsJars() throws Exception {
		Path mods = Files.createDirectories(tmp.resolve("mods"));
		Path old = Files.writeString(mods.resolve("mining-profit-calc-1.0.22.jar"), "old");
		Path down = Files.writeString(Files.createDirectories(tmp.resolve("update")).resolve("mining-profit-calc-1.0.23.jar"), "new");
		Path target = mods.resolve("mining-profit-calc-1.0.23.jar");
		String r = UpdateHelper.swap(old, down, target, 3);
		assertTrue(r.startsWith("done"), r);
		assertFalse(Files.exists(old));
		assertFalse(Files.exists(down));
		assertEquals("new", Files.readString(target));
		assertTrue(UpdateHelper.swap(old, down, target, 3).startsWith("nothing"));
	}

	@Test
	void helperUsesOnlyTheJdk() throws IOException {
		// It runs alone, copied out of the jar: one class file, no inner classes, no mod/Minecraft classes.
		String src = Files.readString(Path.of("src/main/java/com/mitocet/miningprofit/UpdateHelper.java"));
		for (String line : src.split("\n")) {
			if (line.startsWith("import ")) assertTrue(line.startsWith("import java."), line);
		}
		assertFalse(src.contains("new Runnable") || src.contains("class ") && src.indexOf("class ") != src.lastIndexOf("class "),
			"UpdateHelper must stay a single class");
	}

	private static void writeJar(Path jar, String modJson) throws IOException {
		try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(modJson.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
	}
}
