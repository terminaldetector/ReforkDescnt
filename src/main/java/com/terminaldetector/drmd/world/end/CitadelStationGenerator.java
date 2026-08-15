package com.terminaldetector.drmd.world.end;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Minecraft-facing composition for the Citadel-style End reactor station: sweeps
 * {@link CitadelDeckShape#classify} over the full footprint and writes each cell's block state — the same
 * "sweep the pure classify function, map 1:1 to a BlockState" idiom {@code SkyUfoHull.captureTemplate}
 * already established this session.
 *
 * <p>Skeleton for now (Phase 2 of the redesign): exterior shell + shaft + deck floors only. Per-deck
 * theming, turret rings, shield crystals, and the boss anchor retune are later phases, layered on from
 * {@code EndReactorSession.generateBase}'s own call site.
 */
public final class CitadelStationGenerator {
	private static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.getDefaultState();
	private static final BlockState GLASS = Blocks.CYAN_STAINED_GLASS.getDefaultState();
	private static final BlockState FLOOR = Blocks.DARK_PRISMARINE.getDefaultState();
	private static final BlockState AIR = Blocks.AIR.getDefaultState();

	private CitadelStationGenerator() {}

	public static void generate(ServerWorld world, BlockPos center) {
		int half = CitadelDeckShape.HALF_EXTENT;
		int lowestY = CitadelDeckShape.deckFloorY(CitadelDeckShape.Deck.REACTOR_CORE);
		int highestY = CitadelDeckShape.deckTopY(CitadelDeckShape.Deck.FLIGHT);

		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				for (int y = lowestY; y < highestY; y++) {
					BlockState state = stateFor(CitadelDeckShape.classify(x, y, z));
					if (state == null) continue;
					m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					// Skip no-op air-over-air writes — each one still pays for a lighting recalc, and this
					// sweep covers ~12x today's flat-disc volume (EndReactorSession's own clear pass already
					// established this exact optimization for the same reason).
					if (state == AIR && world.getBlockState(m).isAir()) continue;
					world.setBlockState(m, state, Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	private static BlockState stateFor(CitadelDeckShape.Cell cell) {
		return switch (cell) {
			case HULL, WALL_INTERIOR -> WALL;
			case GLASS -> GLASS;
			case DECK_FLOOR -> FLOOR;
			case SHAFT_AIR, ROOM_AIR -> AIR;
			case NONE -> null;
		};
	}
}
