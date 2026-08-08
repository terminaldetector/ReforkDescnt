package com.terminaldetector.drmd.world.micro;

import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Which of two collision-feel styles a position belongs to — a built structure (CUBIC, reads exactly
 * as solid as vanilla always has) or natural mantle/cave/corridor context (SMOOTH, where the same
 * bump against a chamfered wall shouldn't cost a flight as much speed). See
 * {@code ServerPlayerFlightTravelMixin} for what actually differs between the two, and
 * {@code docs/MOVEMENT.md} for why the split lives in collision *response* rather than in geometry.
 *
 * <p>Reuses {@link MacroWorld}, the existing structure catalogue built for radar/HUD contacts — no new
 * spatial index. A position inside any known structure's bounds is CUBIC; everything else defaults to
 * SMOOTH, which is correct for every current caller of the tunnel-carving system (the Descent shaft,
 * the drill rig, the engineer's hand tool are all natural-cave contexts by construction).
 *
 * <p>{@code MacroWorld} is cleared on every server start and only repopulated as generators rediscover
 * their own structures, so a structure not yet rediscovered after a restart briefly classifies as
 * SMOOTH near its own walls — a gentler graze response for a few extra ticks, not a crash or a
 * collision hole.
 */
public final class TerrainClassifier {
	public enum Zone { CUBIC, SMOOTH }

	/**
	 * How far out to look for a structure that might contain this position. {@code MacroWorld.nearby}
	 * filters by distance to a structure's own *centre*, not its edge, so this has to clear the
	 * largest half-extent a registered structure actually reaches — an infinite-megacity plate's
	 * widest feature sits up to 76 blocks off its own centre (see {@code MEGACITY_COMPLEX.md}); 160
	 * keeps a comfortable margin above that for every structure kind, not just the biggest one.
	 */
	private static final double SEARCH_RADIUS = 160.0;

	private TerrainClassifier() {}

	public static Zone classify(BlockPos pos) {
		for (MacroEntry entry : MacroWorld.nearby(pos, SEARCH_RADIUS)) {
			if (entry.bounds().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
				return Zone.CUBIC;
			}
		}
		return Zone.SMOOTH;
	}
}
