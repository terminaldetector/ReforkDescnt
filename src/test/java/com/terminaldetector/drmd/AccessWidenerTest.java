package com.terminaldetector.drmd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@code drmd.accesswidener} — the one file in this project whose mistakes are invisible to both
 * the compiler and CI.
 *
 * <p>Unlike every other test here, this one reads a resource rather than exercising pure logic, because
 * the failure it guards has no Java surface to test: DRMD compiles against Immersive Portals
 * ({@code modImplementation} in {@code build.gradle}), and Loom applies a mod dependency's
 * <em>transitive</em> access wideners to this project's compile environment. So any vanilla member ImmPtl
 * widens is silently widened for DRMD's compile too — javac accepts the code and CI stays green — while
 * at runtime ImmPtl is optional, Fabric Loader only applies wideners from <em>installed</em> mods, and a
 * player without ImmPtl gets {@code IncompatibleClassChangeError} at class-load. That is not a
 * hypothetical: it crashed a real client at startup, before the title screen, on 2026-08-30.
 */
class AccessWidenerTest {
	/** Access kinds the Fabric v2 format defines. {@code transitive-*} is for libraries exporting a
	 *  widening to dependents — DRMD is not one, so a stray {@code transitive-} prefix here would be a
	 *  mistake worth failing on rather than a valid choice. */
	private static final Set<String> VALID_ACCESS_KINDS = Set.of("accessible", "extendable", "mutable");
	private static final Set<String> VALID_TARGET_KINDS = Set.of("class", "field", "method");

	private static List<String> readAccessWidenerLines() throws IOException {
		try (InputStream in = AccessWidenerTest.class.getResourceAsStream("/drmd.accesswidener")) {
			assertNotNull(in, "drmd.accesswidener is not on the classpath — fabric.mod.json declares it, "
					+ "so a missing file means the mod ships a broken accessWidener reference");
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				return reader.lines().toList();
			}
		}
	}

	/** Content lines only: comments and blanks stripped, exactly as Fabric's own reader does. */
	private static List<String> directives(List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String line : lines) {
			int comment = line.indexOf('#');
			String stripped = (comment >= 0 ? line.substring(0, comment) : line).trim();
			if (!stripped.isEmpty()) out.add(stripped);
		}
		return out;
	}

	@Test
	@DisplayName("Entity.getBoundingBox is widened by DRMD itself, not inherited from optional ImmPtl")
	void entityGetBoundingBoxIsWidenedByDrmdItself() throws IOException {
		List<String> directives = directives(readAccessWidenerLines());

		boolean declared = directives.stream().anyMatch(line ->
				line.equals("extendable\tmethod\tnet/minecraft/entity/Entity\tgetBoundingBox"
						+ "\t()Lnet/minecraft/util/math/Box;"));

		assertTrue(declared,
				"SkyUfoEntity overrides Entity.getBoundingBox(), which vanilla declares final. Without this "
						+ "line the override still compiles (Immersive Portals' transitive access widener "
						+ "covers it on the compile classpath) and CI still passes, but every client without "
						+ "ImmPtl installed crashes at startup with IncompatibleClassChangeError. Do not remove "
						+ "this line unless the override in SkyUfoEntity goes away too.");
	}

	@Test
	@DisplayName("the header declares the v2 named format the rest of the file is written in")
	void headerIsV2Named() throws IOException {
		List<String> directives = directives(readAccessWidenerLines());
		assertTrue(directives.size() >= 1, "accesswidener is empty");
		assertEquals("accessWidener\tv2\tnamed", directives.get(0),
				"every entry below is written in Yarn (named) names, so the header has to say so — an "
						+ "intermediary header would make each line silently fail to resolve");
	}

	@Test
	@DisplayName("every directive is tab-separated and uses a real access/target kind")
	void everyDirectiveIsWellFormed() throws IOException {
		List<String> directives = directives(readAccessWidenerLines());

		for (String line : directives.subList(1, directives.size())) {
			String[] parts = line.split("\t");
			assertTrue(parts.length >= 3,
					"not tab-separated into at least access/target/class — a malformed line is skipped "
							+ "silently at runtime rather than reported: " + line);
			assertTrue(VALID_ACCESS_KINDS.contains(parts[0]),
					"unknown access kind '" + parts[0] + "' in: " + line);
			assertTrue(VALID_TARGET_KINDS.contains(parts[1]),
					"unknown target kind '" + parts[1] + "' in: " + line);
			assertTrue(parts[2].startsWith("net/minecraft/"),
					"target class should be an internal (slash-separated) vanilla name, was '" + parts[2]
							+ "' in: " + line);
		}
	}
}
