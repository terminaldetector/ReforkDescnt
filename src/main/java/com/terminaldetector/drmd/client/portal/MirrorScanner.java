package com.terminaldetector.drmd.client.portal;

import com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock;
import com.terminaldetector.drmd.world.portal.mirror.MirrorBlock;
import com.terminaldetector.drmd.world.portal.mirror.MirrorReflection;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds same-dimension literal mirrors near a position, for {@code MirrorReflectionRenderer} to feed
 * through {@link MirrorRenderGate}. Client-side only, no new networking: {@link MirrorBlock} and
 * {@link ChargedMirrorBlock} are both {@code BlockWithEntity}, so every placed one already has a block
 * entity sitting in its own chunk's block-entity map — tracked by vanilla regardless of DRMD's own
 * ticker (null for both, see each block's own {@code getTicker}) — so iterating that map per loaded
 * chunk costs the same as vanilla's own block-entity tick dispatch, not a brute-force per-block scan.
 *
 * <p>A linked {@link ChargedMirrorBlock} ({@code LINKED=true}) is deliberately excluded: once linked it
 * is a real two-point {@code Portal} (a different destination, no reflection) — Phase R3's target, not
 * this same-dimension-mirror phase's. {@code PortalPanelBlock} also implements {@code ReflectiveBlock}
 * but is the portal gun's flat portal panel (task #70), excluded the same way.
 */
public final class MirrorScanner {
	private MirrorScanner() {}

	/**
	 * @param planePoint the mirror's own face centre, in world space — {@link PortalTransform#reflectPoint}/
	 *                    {@link PortalTransform#reflectVector}'s {@code planePoint} argument.
	 * @param normal      unit outward normal of that face — their {@code normal} argument.
	 */
	public record MirrorFace(BlockPos pos, Direction facing, Vec3d planePoint, Vec3d normal) {}

	/** Every loaded chunk within {@code chunkRadius} of {@code center}'s own chunk, inclusive. */
	public static List<MirrorFace> findNearby(World world, BlockPos center, int chunkRadius) {
		List<MirrorFace> found = new ArrayList<>();
		int centerChunkX = center.getX() >> 4;
		int centerChunkZ = center.getZ() >> 4;
		for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
			for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
				if (!world.isChunkLoaded(cx, cz)) continue;
				WorldChunk chunk = world.getChunk(cx, cz);
				for (BlockPos pos : chunk.getBlockEntities().keySet()) {
					Direction facing = mirrorFacing(world.getBlockState(pos));
					if (facing == null) continue;
					Vec3d normal = MirrorReflection.normalFor(facing);
					Vec3d planePoint = Vec3d.ofCenter(pos).add(normal.multiply(0.5));
					found.add(new MirrorFace(pos, facing, planePoint, normal));
				}
			}
		}
		return found;
	}

	private static Direction mirrorFacing(BlockState state) {
		if (state.getBlock() instanceof MirrorBlock) {
			return state.get(Properties.FACING);
		}
		if (state.getBlock() instanceof ChargedMirrorBlock && !state.get(ChargedMirrorBlock.LINKED)) {
			return state.get(Properties.FACING);
		}
		return null;
	}
}
