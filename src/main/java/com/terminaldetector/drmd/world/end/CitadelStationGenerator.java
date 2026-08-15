package com.terminaldetector.drmd.world.end;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.entity.ReactorDisplayEntity;
import com.terminaldetector.drmd.world.end.CitadelDeckShape.Deck;
import com.terminaldetector.drmd.world.trap.RingDefenseStructures;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Minecraft-facing composition for the Citadel-style End reactor station: sweeps
 * {@link CitadelDeckShape#classify} over the full footprint and writes each cell's block state (per-deck
 * palette), then layers on the Reactor Core deck's shield-crystal pillars + turret ring and the Flight
 * deck's hangar mouths + approach bridges. The "sweep the pure classify function, map 1:1 to a
 * BlockState" idiom mirrors {@code SkyUfoHull.captureTemplate} from earlier this session.
 *
 * <p>{@code placeTurretPad}/{@code spawnCrystal} moved here from {@code EndReactorSession} — this is now
 * where all of the station's geometry/placement logic lives; {@code EndReactorSession} stays a thin
 * state machine (arena lifecycle, boss phase tracking).
 */
public final class CitadelStationGenerator {
	private static final BlockState AIR = Blocks.AIR.getDefaultState();

	private CitadelStationGenerator() {}

	public static void generate(ServerWorld world, BlockPos center) {
		sweepHull(world, center);
		placeReactorCoreFeatures(world, center);
		placeFlightDeckFeatures(world, center);
	}

	private static void sweepHull(ServerWorld world, BlockPos center) {
		int half = CitadelDeckShape.HALF_EXTENT;
		int lowestY = CitadelDeckShape.deckFloorY(Deck.REACTOR_CORE);
		int highestY = CitadelDeckShape.deckTopY(Deck.FLIGHT);

		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				for (int y = lowestY; y < highestY; y++) {
					Deck deck = CitadelDeckShape.deckAt(y);
					BlockState state = stateFor(CitadelDeckShape.classify(x, y, z), paletteFor(deck));
					if (state == null) continue;
					m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					// Skip no-op air-over-air writes — each one still pays for a lighting recalc, and this
					// sweep covers ~12x today's old flat-disc volume (EndReactorSession's own clear pass,
					// and RingDefenseStructures' own set() helper, already established this exact idiom).
					if (state == AIR && world.getBlockState(m).isAir()) continue;
					world.setBlockState(m, state, Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	private static BlockState stateFor(CitadelDeckShape.Cell cell, Palette p) {
		return switch (cell) {
			case HULL, WALL_INTERIOR -> p.wall();
			case GLASS -> p.glass();
			case DECK_FLOOR -> p.floor();
			case SHAFT_AIR, ROOM_AIR -> AIR;
			case NONE -> null;
		};
	}

	private record Palette(BlockState wall, BlockState glass, BlockState floor) {}

	/**
	 * One palette per deck, loosely drawing on {@code WorldRules.ComplexStyle}'s existing material
	 * language (ANCIENT_POWER, AUTO_FACTORY, CRYSTAL_REACTOR, ORBITAL_SHELL) rather than inventing new
	 * combinations, so the station reads as part of the same visual family as the mod's other complexes.
	 */
	private static Palette paletteFor(Deck deck) {
		return switch (deck) {
			case REACTOR_CORE -> new Palette(Blocks.BLACKSTONE.getDefaultState(),
					Blocks.MAGMA_BLOCK.getDefaultState(), Blocks.CRYING_OBSIDIAN.getDefaultState());
			case ENGINEERING -> new Palette(Blocks.IRON_BLOCK.getDefaultState(),
					Blocks.REDSTONE_BLOCK.getDefaultState(), Blocks.CUT_COPPER.getDefaultState());
			case STORAGE -> new Palette(Blocks.POLISHED_DEEPSLATE.getDefaultState(),
					Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(), Blocks.POLISHED_DEEPSLATE.getDefaultState());
			case LABS -> new Palette(Blocks.PURPUR_BLOCK.getDefaultState(),
					Blocks.BLUE_ICE.getDefaultState(), Blocks.AMETHYST_BLOCK.getDefaultState());
			case COMMAND -> new Palette(Blocks.DEEPSLATE_TILES.getDefaultState(),
					Blocks.SEA_LANTERN.getDefaultState(), Blocks.IRON_BLOCK.getDefaultState());
			case FLIGHT -> new Palette(Blocks.END_STONE_BRICKS.getDefaultState(),
					Blocks.BLUE_ICE.getDefaultState(), Blocks.PURPUR_PILLAR.getDefaultState());
			case NONE -> new Palette(AIR, AIR, AIR); // never reached — classify() returns NONE before paletteFor is consulted
		};
	}

	/**
	 * Shield-crystal pillars (dragon-shield analogue) and the inner turret ring, both scoped to the
	 * Reactor Core deck (local Y 0..32) — same radius/height the old flat-disc arena used, since both
	 * comfortably fit this deck's own span; see {@code EndReactorBossEntity}/{@code countShieldCrystals}
	 * for why nothing needs to change there for these positions to already be covered.
	 */
	private static void placeReactorCoreFeatures(ServerWorld world, BlockPos center) {
		placeReactorPedestal(world, center);

		BlockPos[] pillars = {
				center.add(22, 0, 0), center.add(-22, 0, 0),
				center.add(0, 0, 22), center.add(0, 0, -22)
		};
		for (BlockPos base : pillars) {
			for (int y = 1; y <= 14; y++) {
				world.setBlockState(base.up(y), Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_LISTENERS);
				if (y % 3 == 0) {
					world.setBlockState(base.up(y).east(), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
					world.setBlockState(base.up(y).west(), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
			world.setBlockState(base.up(15), ModWorldBlocks.PLASMA_GRANITE.getDefaultState(), Block.NOTIFY_LISTENERS);
			EndCrystalEntity crystal = spawnCrystal(world, base.up(16));
			if (crystal != null) world.spawnEntity(crystal);

			placeTurretPad(world, base.add(3, 8, 0), ModWorldBlocks.LASER_TURRET);
			placeTurretPad(world, base.add(-3, 8, 0), ModWorldBlocks.PLASMA_TURRET);
			placeTurretPad(world, base.add(0, 8, 3), ModWorldBlocks.POINT_DEFENSE_TURRET);
		}

		// withShields=true only here: placeTurretRing's own placeShieldCross adds a second set of 4
		// shorter crystal towers, and this is the only ring on the station with shields on — keeps the
		// total crystal count (and so when shields-down triggers) matching the old arena's behavior.
		RingDefenseStructures.placeTurretRing(world, center, 16, center.getY() + 1, 8, true);
	}

	/**
	 * Purely decorative — {@code EndReactorBossEntity}'s own HP/death is the fight's real trigger, this
	 * block never gates anything. Same local-Y offsets (1..9) the old flat-disc arena used; still valid
	 * since they sit well below the boss's own new anchor at local Y 16.
	 */
	private static void placeReactorPedestal(ServerWorld world, BlockPos center) {
		for (int y = 1; y <= 6; y++) {
			world.setBlockState(center.up(y), Blocks.RESPAWN_ANCHOR.getDefaultState(), Block.NOTIFY_ALL);
		}
		world.setBlockState(center.up(7), ModWorldBlocks.UNSTABLE_REACTOR.getDefaultState(), Block.NOTIFY_ALL);

		ReactorDisplayEntity core = ModEntities.REACTOR_DISPLAY.create(world);
		if (core != null) {
			core.refreshPositionAndAngles(center.getX() + 0.5, center.getY() + 9.0, center.getZ() + 0.5, 0, 0);
			world.spawnEntity(core);
		}
	}

	/**
	 * Four cardinal hangar-mouth punctures through the outer hull's thickness (matching
	 * {@code CitadelDeckShape}'s own wall-backing depth of 2), and the approach bridges leading into
	 * them — reattached at the Flight deck instead of a flat disc's edge.
	 */
	private static void placeFlightDeckFeatures(ServerWorld world, BlockPos center) {
		int half = CitadelDeckShape.HALF_EXTENT;
		int floorY = CitadelDeckShape.deckFloorY(Deck.FLIGHT);
		int topY = CitadelDeckShape.deckTopY(Deck.FLIGHT);
		int mouthY = (floorY + topY) / 2;

		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int wallDepth = half - 1; wallDepth <= half; wallDepth++) {
					world.setBlockState(center.add(wallDepth, mouthY + dy, dx), AIR, Block.NOTIFY_LISTENERS);
					world.setBlockState(center.add(-wallDepth, mouthY + dy, dx), AIR, Block.NOTIFY_LISTENERS);
					world.setBlockState(center.add(dx, mouthY + dy, wallDepth), AIR, Block.NOTIFY_LISTENERS);
					world.setBlockState(center.add(dx, mouthY + dy, -wallDepth), AIR, Block.NOTIFY_LISTENERS);
				}
			}
		}

		BlockState bridge = Blocks.END_STONE_BRICKS.getDefaultState();
		for (int d = half; d <= half + 20; d++) {
			world.setBlockState(center.add(d, mouthY - 1, 0), bridge, Block.NOTIFY_LISTENERS);
			world.setBlockState(center.add(-d, mouthY - 1, 0), bridge, Block.NOTIFY_LISTENERS);
			world.setBlockState(center.add(0, mouthY - 1, d), bridge, Block.NOTIFY_LISTENERS);
			world.setBlockState(center.add(0, mouthY - 1, -d), bridge, Block.NOTIFY_LISTENERS);
		}
	}

	private static void placeTurretPad(ServerWorld world, BlockPos pad, Block turret) {
		world.setBlockState(pad, Blocks.END_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
		world.setBlockState(pad.up(), turret.getDefaultState(), Block.NOTIFY_ALL);
	}

	private static EndCrystalEntity spawnCrystal(ServerWorld world, BlockPos at) {
		EndCrystalEntity c = net.minecraft.entity.EntityType.END_CRYSTAL.create(world);
		if (c == null) return null;
		c.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		c.setShowBottom(false);
		return c;
	}
}
