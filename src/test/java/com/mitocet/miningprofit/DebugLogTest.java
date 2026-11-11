package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugLogTest {
	@Test
	void startFreshMovesLastGameToOld(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("debug.log");
		DebugLog log = new DebugLog(file);
		Files.writeString(dir.resolve("debug.log.old"), "older game\n", StandardCharsets.UTF_8);
		Files.writeString(file, "last game\n", StandardCharsets.UTF_8);

		log.startFresh();

		assertFalse(Files.exists(file));
		assertEquals("last game\n", Files.readString(log.oldFile(), StandardCharsets.UTF_8));
		log.simple("start", 1L);
		assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"start\""));
	}

	@Test
	void startFreshKeepsOldWhenNothingWasWritten(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("debug.log");
		DebugLog log = new DebugLog(file);
		Files.writeString(log.oldFile(), "older game\n", StandardCharsets.UTF_8);

		log.startFresh();

		assertEquals("older game\n", Files.readString(log.oldFile(), StandardCharsets.UTF_8));
	}
}
