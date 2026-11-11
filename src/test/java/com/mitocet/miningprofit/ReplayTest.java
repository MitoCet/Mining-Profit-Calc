package com.mitocet.miningprofit;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plays every recorded log in src/test/resources/replay back (see the README there). */
class ReplayTest {
	@TestFactory
	Stream<DynamicTest> recordedLogsCountWhatTheyShould() throws Exception {
		Path dir = Path.of("src/test/resources/replay");
		List<Path> files;
		try (Stream<Path> s = Files.list(dir)) {
			files = s.filter((p) -> p.toString().endsWith(".jsonl")).sorted().toList();
		}
		assertTrue(!files.isEmpty(), "no replay files in " + dir.toAbsolutePath());
		return files.stream().map((f) -> DynamicTest.dynamicTest(f.getFileName().toString(), () -> {
			Replay.Result r = Replay.run(Files.readAllLines(f, StandardCharsets.UTF_8));
			assertEquals(List.of(), r.mismatches(), "events that counted something else");
		}));
	}
}
