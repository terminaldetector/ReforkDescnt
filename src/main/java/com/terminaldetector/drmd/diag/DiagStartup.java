package com.terminaldetector.drmd.diag;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The few facts worth having in the log before anything can go wrong.
 *
 * <p>The diagnostics report is the better artefact, but it is written by pressing a button, and the
 * failures that hurt most are the ones where nobody gets to press it: a crash during startup, or a
 * game that never reaches the title screen. Those leave only {@code latest.log}, so the handful of
 * facts that decide most of those cases belong there, printed early and unconditionally.
 *
 * <p>Deliberately short. A banner nobody reads is as useless as no banner, so this is versions, which
 * optional mods actually loaded, and the one self-check that has already cost this project an evening
 * — not a dump of everything the report can gather.
 */
public final class DiagStartup {
	private DiagStartup() {}

	/** Optional mods whose presence changes how DRMD behaves, so their absence is worth seeing. */
	private static final String[] OPTIONAL = {"immersive_portals", "dimlib", "sodium", "iris"};

	public static void logBanner() {
		DescentMod.LOGGER.info("[drmd] {} on Minecraft {} / loader {} / {} mods",
				version("drmd"), version("minecraft"), version("fabricloader"),
				FabricLoader.getInstance().getAllMods().size());

		StringBuilder optional = new StringBuilder();
		for (String id : OPTIONAL) {
			if (optional.length() > 0) optional.append(", ");
			optional.append(id).append('=').append(
					FabricLoader.getInstance().isModLoaded(id) ? version(id) : "no");
		}
		DescentMod.LOGGER.info("[drmd] optional: {}", optional);

		checkAccessWidener();
	}

	/**
	 * Confirm DRMD's own access widener actually applied.
	 *
	 * <p>This is the check for the failure that once shipped past a green CI and cost an evening:
	 * {@code SkyUfoEntity} overrides a method vanilla declares {@code final}, which only links because
	 * {@code drmd.accesswidener} widens it. When that widener is missing the class dies at link time
	 * with an {@code IncompatibleClassChangeError}, at whatever random moment something first touches
	 * it — a crash whose message says nothing about wideners. Forcing the link here moves it to the
	 * first second of startup and names the cause.
	 *
	 * <p>Nothing is rethrown. If the widener is genuinely missing the game is going to fail anyway;
	 * what this adds is a log line saying why, and turning that into a second, earlier crash would
	 * only bury it.
	 */
	private static void checkAccessWidener() {
		try {
			Class.forName("com.terminaldetector.drmd.world.mega.SkyUfoEntity");
			DescentMod.LOGGER.info("[drmd] access widener applied");
		} catch (Throwable failure) {
			DescentMod.LOGGER.error("[drmd] ACCESS WIDENER NOT APPLIED — drmd.accesswidener did not take "
					+ "effect. Expect IncompatibleClassChangeError later; this is the cause.", failure);
			DiagProblems.record("startup", "access widener not applied: " + failure);
		}
	}

	private static String version(String id) {
		return FabricLoader.getInstance().getModContainer(id)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("absent");
	}
}
