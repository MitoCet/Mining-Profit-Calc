package com.mitocet.miningprofit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Runs as its own small Java process after the game closes (started by {@link Updater}). It is
 * copied out of the mod jar and uses only the JDK, so it neither needs nor locks the mod jar.
 *
 * <p>Waits for the game to exit, deletes the old jar (Windows unlocks it only then; tried for up to
 * a minute), then moves the downloaded jar into the mods folder. If the old jar can't be deleted,
 * the new one is NOT moved, so Fabric never finds the mod twice; the next game tries again.
 */
public final class UpdateHelper {
	private UpdateHelper() {}

	static List<String> args(long gamePid, Path oldJar, Path downloaded, Path target) {
		return List.of(Long.toString(gamePid), oldJar.toAbsolutePath().toString(),
			downloaded.toAbsolutePath().toString(), target.toAbsolutePath().toString());
	}

	public static void main(String[] a) throws Exception {
		long pid = Long.parseLong(a[0]);
		Path oldJar = Path.of(a[1]);
		Path downloaded = Path.of(a[2]);
		Path target = Path.of(a[3]);
		log("start: " + oldJar + " -> " + target);

		Optional<ProcessHandle> game = ProcessHandle.of(pid);
		long waitUntil = System.currentTimeMillis() + 5 * 60_000;
		while (game.isPresent() && game.get().isAlive() && System.currentTimeMillis() < waitUntil) {
			Thread.sleep(500);
		}
		log(swap(oldJar, downloaded, target, 60));
	}

	/** Deletes the old jar (retrying while it's locked), then moves the new one in. */
	static String swap(Path oldJar, Path downloaded, Path target, int tries) throws InterruptedException {
		if (!Files.isRegularFile(downloaded)) return "nothing to do: " + downloaded + " is gone";
		if (!oldJar.equals(target)) {
			boolean deleted = false;
			for (int i = 0; i < tries && !deleted; i++) {
				try {
					Files.deleteIfExists(oldJar);
					deleted = true;
				} catch (Exception e) {
					Thread.sleep(1000);
				}
			}
			if (!deleted) return "FAILED: old jar still locked, kept both as they were: " + oldJar;
		}
		try {
			Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			try {
				Files.copy(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
				Files.deleteIfExists(downloaded);
			} catch (Exception e2) {
				return "FAILED to move the new jar: " + e2;
			}
		}
		return "done: " + target;
	}

	private static void log(String s) {
		System.out.println(LocalDateTime.now() + " " + s);
	}
}
