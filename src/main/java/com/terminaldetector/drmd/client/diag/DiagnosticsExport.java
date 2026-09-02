package com.terminaldetector.drmd.client.diag;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.planet.PlanetClientState;
import com.terminaldetector.drmd.client.planet.PlanetSurfaceMesh;
import com.terminaldetector.drmd.client.portal.MirrorReflectionRenderer;
import com.terminaldetector.drmd.client.portal.PortalSeeThroughRenderer;
import com.terminaldetector.drmd.client.portal.PortalViewDiagnostics;
import com.terminaldetector.drmd.diag.DiagProblems;
import com.terminaldetector.drmd.diag.DiagReport;
import com.terminaldetector.drmd.world.level.LevelBuilder;
import com.terminaldetector.drmd.world.level.WorldLevels;
import com.terminaldetector.drmd.world.portal.PortalComplexity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Writes one file that answers "what is actually going on" — the thing that has been missing every
 * time something looked wrong in game and the only way forward was to guess, rebuild, and look again.
 *
 * <p>Everything here is read from the running client, so the file describes the session that produced
 * it rather than the code that might have. Where a fact is expensive or unavailable it says so instead
 * of being omitted: a row reading {@code null} is a finding, a missing row is a question.
 *
 * <p><b>Scope, stated rather than discovered later.</b> This is the <em>client's</em> view. In single
 * player the integrated server is in the same process, so the server-side rows are real; on a
 * multiplayer server they cannot be, and the report says which case it is instead of quietly printing
 * blanks that look like zeros.
 *
 * <p>Failing to write the report must never take the game down with it — the moment it is wanted is
 * the moment something is already wrong — so the write is guarded and reports its own failure through
 * the same channel as everything else.
 */
public final class DiagnosticsExport {
	private DiagnosticsExport() {}

	/** Mods worth naming individually, because their presence or version changes how DRMD behaves. */
	private static final String[] MODS_OF_INTEREST = {
			"drmd", "minecraft", "fabricloader", "fabric-api",
			"immersive_portals", "imm_ptl_core", "dimlib", "q_misc_util",
			"sodium", "iris", "indium", "modmenu",
	};

	private static final String FILE_NAME = "drmd-diagnostics.txt";

	/**
	 * Gather and write the report.
	 *
	 * @return where it was written, or null if it could not be — the caller should say which.
	 */
	public static Path write() {
		try {
			Path path = FabricLoader.getInstance().getGameDir().resolve(FILE_NAME);
			Files.writeString(path, gather());
			DescentMod.LOGGER.info("[drmd diag] wrote {}", path);
			return path;
		} catch (Throwable failure) {
			// Throwable, not Exception: this runs when something is already wrong, and an Error here
			// (a missing class from a half-loaded optional mod, say) is exactly the case worth catching.
			DescentMod.LOGGER.error("[drmd diag] could not write the report", failure);
			DiagProblems.record("diag", "could not write the report: " + failure);
			return null;
		}
	}

	/** The whole report as text, so a caller can show it without writing a file. */
	public static String gather() {
		DiagReport report = new DiagReport();
		environment(report);
		mods(report);
		clientState(report);
		worldAndPlayer(report);
		portals(report);
		horizon(report);
		generation(report);
		problems(report);
		return report.render();
	}

	private static void environment(DiagReport report) {
		report.section("Environment")
				.row("written", Instant.now())
				.row("java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")")
				.row("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
						+ " " + System.getProperty("os.arch"))
				.row("mods loaded", FabricLoader.getInstance().getAllMods().size())
				.row("dev environment", FabricLoader.getInstance().isDevelopmentEnvironment());

		// The access-widener check, kept because this exact failure once cost an evening: without DRMD's
		// own widener, loading a class that overrides a final vanilla method dies at link time with an
		// IncompatibleClassChangeError, and nothing in a build or in CI can see it coming. If the game
		// got this far the answer is almost always "applied" — which is the point. It rules the whole
		// class of failure out in one line instead of costing a round trip to ask.
		try {
			Class.forName("com.terminaldetector.drmd.world.mega.SkyUfoEntity");
			report.row("access widener", "applied (SkyUfoEntity links)");
		} catch (Throwable failure) {
			report.row("access widener", "NOT APPLIED — " + failure);
			DiagProblems.record("startup", "access widener not applied: " + failure);
		}
	}

	private static void mods(DiagReport report) {
		report.section("Mods");
		for (String id : MODS_OF_INTEREST) {
			report.row(id, FabricLoader.getInstance().getModContainer(id)
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("— not installed"));
		}
		report.note("ImmPtl detected by DRMD: " + PortalComplexity.hasImmersivePortals());
	}

	private static void clientState(DiagReport report) {
		MinecraftClient mc = MinecraftClient.getInstance();
		report.section("Client")
				.row("fps", mc.getCurrentFps())
				.row("view distance (chunks)", mc.options.getClampedViewDistance())
				.row("framebuffer", mc.getWindow().getFramebufferWidth() + "x" + mc.getWindow().getFramebufferHeight())
				.row("integrated server", mc.getServer() != null
						? "yes — server-side rows below are real"
						: "no — multiplayer, server-side state is not visible from here");

		report.section("DRMD options")
				.row("hud", DescentConfig.hud)
				.row("cockpit", DescentConfig.cockpit)
				.row("levelSky", DescentConfig.levelSky)
				.row("planetFloor (voxel horizon)", DescentConfig.planetFloor)
				.row("mirrorReflection", DescentConfig.mirrorReflection)
				.row("portalSeeThrough", DescentConfig.portalSeeThrough)
				.row("cameraShake", DescentConfig.cameraShake)
				.row("weaponView", DescentConfig.weaponView)
				.row("fallAftermath", DescentConfig.fallAftermath);
	}

	private static void worldAndPlayer(DiagReport report) {
		MinecraftClient mc = MinecraftClient.getInstance();
		report.section("World and player");
		if (mc.world == null || mc.player == null) {
			report.note("not in a world");
			return;
		}
		PlayerEntity player = mc.player;
		report.row("dimension", mc.world.getRegistryKey().getValue())
				.row("position", String.format("%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ()))
				.row("layer at this Y", WorldLevels.at(player.getY()))
				.row("6DoF enabled", DescentClientState.enabled)
				.row("flight assist", DescentClientState.flightAssist)
				.row("speed", String.format("%.2f", DescentClientState.speed))
				.row("energy / shield", DescentClientState.energy + " / " + DescentClientState.shield)
				// The ship-glued-to-player bug lived here: a player still riding what they had just
				// dismounted. Worth naming outright rather than leaving to be inferred from a position.
				.row("riding", player.hasVehicle()
						? player.getVehicle().getName().getString()
						: "nothing");
	}

	private static void portals(DiagReport report) {
		report.section("Portals and mirrors")
				.row("mirror faces found", MirrorReflectionRenderer.scannedCount())
				.row("portal faces found", PortalSeeThroughRenderer.scannedCount())
				.row("mirror view", orNever(PortalViewDiagnostics.lastSummary("mirror")))
				.row("portal view", orNever(PortalViewDiagnostics.lastSummary("portal")));
		if (!DescentConfig.mirrorReflection && !DescentConfig.portalSeeThrough) {
			report.note("both views are off, so the two lines above will read 'never' — turn them on in "
					+ "DRMD options before reproducing a rendering problem.");
		}
	}

	private static void horizon(DiagReport report) {
		PlanetClientState state = PlanetClientState.INSTANCE;
		report.section("Voxel horizon")
				.row("has seed", state.hasSeed())
				.rowIf(state.hasSeed(), "seed", state.seed())
				.row("rebuilds this session", state.rebuilds())
				.row("last rebuild", state.lastRebuildMillis() < 0 ? "never" : state.lastRebuildMillis() + "ms")
				.row("worst rebuild", state.worstRebuildMillis() < 0 ? "never" : state.worstRebuildMillis() + "ms");

		PlanetSurfaceMesh mesh = state.currentMesh();
		if (mesh == null) {
			report.note("no field built — either the horizon is off, or the eye is below the fade-in height");
			return;
		}
		report.row("quads in the field", mesh.quads)
				.row("built around", String.format("%.0f, %.0f, %.0f", mesh.originX, mesh.originY, mesh.originZ))
				.row("inner radius / reach", String.format("%.0f / %.0f", mesh.innerRadius, mesh.reach));
		// The rebuild is the horizon's whole remaining cost: drawing it is free between rebuilds. So a
		// stutter while flying is this number, and it is here so nobody has to guess that.
		report.note("drawing is free between rebuilds (the field is on the GPU); a stutter while flying "
				+ "is the rebuild time above");
	}

	/**
	 * The column fill's backlog, and why both numbers are here rather than one.
	 *
	 * <p>A hole in the ground below the pilot has two opposite causes that look the same from inside
	 * the game. A deep queue with the budget saturated means the writes cannot keep up. An <em>empty</em>
	 * queue with terrain still missing means those chunks were never queued at all — the streaming
	 * window is narrower than what the pilot can see, and a bigger budget would change nothing. Reading
	 * the code cannot tell which is happening on a given flight; these two rows can.
	 */
	private static void generation(DiagReport report) {
		report.section("Column generation");
		if (MinecraftClient.getInstance().getServer() == null) {
			report.note("multiplayer — the server does the filling and this client cannot see its queue");
			return;
		}
		report.row("chunks queued now", LevelBuilder.queueDepth())
				.row("deepest queue this session", LevelBuilder.worstQueueDepth())
				.row("saturated for", LevelBuilder.saturatedTicks() > 0
						? (LevelBuilder.saturatedTicks() / 20) + "s and counting"
						: "not saturated")
				.note("deep queue + saturated = the write budget is the limit")
				.note("empty queue + missing terrain = the streaming window is, and budget will not help");
	}

	private static void problems(DiagReport report) {
		List<DiagProblems.Entry> entries = DiagProblems.snapshot();
		report.section("Problems DRMD noticed (" + entries.size() + " distinct, newest first)");
		if (entries.isEmpty()) {
			report.note("none recorded");
			return;
		}
		long now = System.currentTimeMillis();
		for (DiagProblems.Entry entry : entries) {
			String age = ((now - entry.lastSeenMillis()) / 1000L) + "s ago";
			report.row("[" + entry.area() + "] x" + entry.count() + ", last " + age, entry.message());
		}
	}

	private static String orNever(String summary) {
		return summary == null ? "never reported" : summary;
	}
}
