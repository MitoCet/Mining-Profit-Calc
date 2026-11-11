package com.mitocet.miningprofit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HelpTest {
	/** Every /kazanc subcommand registered in the client must be explained in /kazanc help. */
	@Test
	void everyCommandIsInHelp() throws Exception {
		String src = Files.readString(Path.of("src/main/java/com/mitocet/miningprofit/MiningProfitClient.java"));
		Set<String> registered = new TreeSet<>();
		Matcher m = Pattern.compile("ClientCommands\\.literal\\(\"(\\w+)\"\\)").matcher(src);
		while (m.find()) registered.add(m.group(1));
		registered.remove("kazanc");
		registered.remove("yardim"); // same as help
		registered.add(""); // bare /kazanc

		Set<String> listed = new TreeSet<>();
		for (Help.Group g : Help.GROUPS) {
			for (Help.Entry e : g.entries()) {
				listed.add(e.command());
				assertFalse(e.shortText().isBlank(), e.command());
				assertFalse(e.details().isBlank(), e.command());
			}
		}
		assertEquals(registered, listed);
	}
}
