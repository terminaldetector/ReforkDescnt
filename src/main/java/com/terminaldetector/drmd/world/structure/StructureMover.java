package com.terminaldetector.drmd.world.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Map;

/**
 * Minecraft-facing shell around {@link StructureDelta}'s pure set-algebra: places, clears, and moves a
 * {@link StructureInstance}'s blocks in a real {@link ServerWorld}. Every world call here is copied from
 * a call shape already proven in {@code SkyUfoHull} — a mechanical relocation, not new API guessing.
 */
public final class StructureMover {
	private StructureMover() {}

	/** Writes every cell of the instance's current footprint into the world. */
	public static void place(ServerWorld world, StructureInstance instance) {
		for (Map.Entry<StructureDelta.Cell, BlockState> e : instance.occupiedCells().entrySet()) {
			world.setBlockState(toBlockPos(e.getKey()), e.getValue(), Block.NOTIFY_LISTENERS);
		}
	}

	/** Clears every cell of the instance's current footprint back to air, without moving the anchor. */
	public static void clear(ServerWorld world, StructureInstance instance) {
		for (StructureDelta.Cell c : instance.occupiedCells().keySet()) {
			world.setBlockState(toBlockPos(c), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	/**
	 * Moves the instance to {@code newAnchor}, touching only the leading/trailing shell that actually
	 * changed: cells common to both the old and new footprint (the overwhelming majority, for any
	 * per-step delta small relative to the structure's own size) are never written at all.
	 */
	public static void moveTo(ServerWorld world, StructureInstance instance, BlockPos newAnchor) {
		Map<StructureDelta.Cell, BlockState> oldWorld = instance.occupiedCells();
		instance.setAnchor(newAnchor);
		Map<StructureDelta.Cell, BlockState> newWorld = instance.occupiedCells();

		StructureDelta.Diff diff = StructureDelta.diff(oldWorld.keySet(), newWorld.keySet());
		for (StructureDelta.Cell c : diff.toClear()) {
			world.setBlockState(toBlockPos(c), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		for (StructureDelta.Cell c : diff.toPlace()) {
			world.setBlockState(toBlockPos(c), newWorld.get(c), Block.NOTIFY_LISTENERS);
		}
	}

	private static BlockPos toBlockPos(StructureDelta.Cell c) {
		return new BlockPos(c.x(), c.y(), c.z());
	}

	/** {@code remainder}: the fractional accumulator to pass into the next tick's {@code tickCrashDescent} call. */
	public record DescentTick(double remainder, boolean touchedGround) {}

	/**
	 * Advances one tick of a vertical crash-descent: drops the instance by {@link StructureCrash}'s
	 * fall-speed curve (accumulated fractionally via {@link StructureCrash#nextDrop}, since {@code moveTo}
	 * only ever moves whole blocks) and reports ground contact via the same
	 * {@code world.getTopY(Heightmap.Type.MOTION_BLOCKING, ...)} call {@code SkyUfoEntity.burnGround}
	 * already makes today. Purely vertical — no horizontal drift/tumble in this pass.
	 */
	public static DescentTick tickCrashDescent(ServerWorld world, StructureInstance instance, int crashTicks,
			double fallAccumulator) {
		StructureCrash.DescentStep step = StructureCrash.nextDrop(fallAccumulator, crashTicks);
		if (step.wholeBlocks() > 0) {
			moveTo(world, instance, instance.anchor().add(0, -step.wholeBlocks(), 0));
		}
		BlockPos anchor = instance.anchor();
		int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, anchor.getX(), anchor.getZ());
		boolean touchedGround = instance.lowestWorldY() <= groundY;
		return new DescentTick(step.remainder(), touchedGround);
	}
}
