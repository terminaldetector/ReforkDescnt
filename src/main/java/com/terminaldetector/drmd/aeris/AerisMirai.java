package com.terminaldetector.drmd.aeris;

import com.terminaldetector.drmd.DescentMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * ÆRis/Mirai — registration entry point for the step 6 PoC. Registers
 * {@link AerisDensityChunkGenerator}'s codec so {@code data/drmd/dimension/aeris_test.json} can
 * reference it by id ({@code "type": "drmd:aeris_density_generator"}).
 *
 * <p>Deliberately unconditional — no {@code WorldFeatures} gate, no config toggle. This is a separate,
 * always-present test dimension (like the Nether/End always exist), not a shipped gameplay feature; a
 * player who never visits it never notices it. Reach it as an operator with
 * {@code /execute in drmd:aeris_test run teleport @s 0 64 0} — no custom teleport command was added for
 * this first experiment (see {@code aeris-mirai/04-first-experiment-density-generator.md} for why).
 */
public final class AerisMirai {
	private AerisMirai() {}

	public static void register() {
		Registry.register(Registries.CHUNK_GENERATOR,
				Identifier.of(DescentMod.MOD_ID, "aeris_density_generator"), AerisDensityChunkGenerator.CODEC);
		DescentMod.LOGGER.info("Registered ÆRis/Mirai step 6 density generator (drmd:aeris_test)");
	}
}
