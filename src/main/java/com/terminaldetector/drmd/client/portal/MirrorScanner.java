package com.terminaldetector.drmd.client.portal;

import com.terminaldetector.drmd.world.portal.PortalComplexity;
import com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlock;
import com.terminaldetector.drmd.world.portal.mirror.ChargedMirrorBlockEntity;
import com.terminaldetector.drmd.world.portal.mirror.MirrorBlock;
import com.terminaldetector.drmd.world.portal.mirror.MirrorReflection;
import com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlock;
import com.terminaldetector.drmd.world.portal.mirror.PortalPanelBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Finds same-dimension literal mirrors near a position, for {@code MirrorReflectionRenderer} to feed
 * through {@link MirrorRenderGate}. Client-side only, no new networking: {@link MirrorBlock} and
 * {@link ChargedMirrorBlock} are both {@code BlockWithEntity}, so every placed one already has a block
 * entity sitting in its own chunk's block-entity map — tracked by vanilla regardless of DRMD's own
 * ticker (null for both, see each block's own {@code getTicker}) — so iterating that map per loaded
 * chunk costs the same as vanilla's own block-entity tick dispatch, not a brute-force per-block scan.
 *
 * <p>A linked {@link ChargedMirrorBlock} ({@code LINKED=true}) is excluded from {@link #findNearby}
 * and is the whole subject of {@link #findLinkedNearby}: once linked it is no longer a mirror but one
 * end of a two-point portal, with a destination instead of a reflection. Portal panels are the same
 * story and appear in the same second list. The two lists never overlap, so the reflection renderer and
 * the see-through renderer never draw two different pictures of one face.
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

	/**
	 * One end of a live link, and where looking through it leads.
	 *
	 * @param faceSize   the side of the square face, in blocks — one for a mirror, four for a portal
	 *                   panel. Read from the block rather than assumed, so the drawn face is the same
	 *                   size as the one travellers are actually carried across.
	 * @param destPoint  the partner's face centre in world space, and {@code destNormal} its outward
	 *                   normal — together the plane the second view is clipped at.
	 */
	public record PortalFace(BlockPos pos, Direction facing, Vec3d planePoint, Vec3d normal,
			double faceSize, Vec3d destPoint, Vec3d destNormal) {}

	/**
	 * Linked charged mirrors and portal panels near {@code center} whose partner is loaded and in this
	 * same world — the ones {@code PortalSeeThroughRenderer} can actually draw through.
	 *
	 * <p>Deliberately the complement of {@link #findNearby}: that one takes plain and <em>unlinked</em>
	 * mirrors, which reflect, and this one takes the linked pairs, which lead somewhere. No block is in
	 * both lists, so the two renderers never fight over one face.
	 *
	 * <p>A face with a live ImmPtl portal attached is skipped: that portal draws itself, and drawing it
	 * twice would be two different pictures of the same surface. The same question the block entity's
	 * own tick asks before carrying anyone.
	 */
	public static List<PortalFace> findLinkedNearby(World world, BlockPos center, int chunkRadius) {
		List<PortalFace> found = new ArrayList<>();
		boolean immPtl = PortalComplexity.hasImmersivePortals();
		int centerChunkX = center.getX() >> 4;
		int centerChunkZ = center.getZ() >> 4;
		for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
			for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
				if (!world.isChunkLoaded(cx, cz)) continue;
				WorldChunk chunk = world.getChunk(cx, cz);
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					PortalFace face = linkedFace(world, entry.getKey(), entry.getValue(), immPtl);
					if (face != null) found.add(face);
				}
			}
		}
		return found;
	}

	private static PortalFace linkedFace(World world, BlockPos pos, BlockEntity be, boolean immPtl) {
		UUID attached;
		BlockPos partnerPos;
		RegistryKey<World> partnerDim;
		double faceSize;
		Class<? extends Block> blockType;
		if (be instanceof ChargedMirrorBlockEntity mirror) {
			if (!mirror.isLinked()) return null;
			attached = mirror.getAttachedEntityId();
			partnerPos = mirror.getLinkPartnerPos();
			partnerDim = mirror.getLinkPartnerDim();
			faceSize = 1.0;
			blockType = ChargedMirrorBlock.class;
		} else if (be instanceof PortalPanelBlockEntity panel) {
			if (!panel.isLinked()) return null;
			attached = panel.getAttachedEntityId();
			partnerPos = panel.getLinkPartnerPos();
			partnerDim = panel.getLinkPartnerDim();
			faceSize = PortalPanelBlock.HALF_SPAN * 2.0;
			blockType = PortalPanelBlock.class;
		} else {
			return null;
		}

		if (attached != null && immPtl) return null;
		if (partnerPos == null) return null;
		// Same world only, and the far end has to be loaded — there is nothing to render from a chunk
		// the client does not have, and asking for one would hand back air and draw a blank portal.
		if (partnerDim != null && !partnerDim.equals(world.getRegistryKey())) return null;
		if (!world.isChunkLoaded(partnerPos.getX() >> 4, partnerPos.getZ() >> 4)) return null;

		BlockState state = world.getBlockState(pos);
		BlockState partnerState = world.getBlockState(partnerPos);
		if (!blockType.isInstance(state.getBlock()) || !blockType.isInstance(partnerState.getBlock())) {
			return null;
		}

		Direction facing = state.get(Properties.FACING);
		Direction partnerFacing = partnerState.get(Properties.FACING);
		Vec3d normal = MirrorReflection.normalFor(facing);
		Vec3d partnerNormal = MirrorReflection.normalFor(partnerFacing);
		return new PortalFace(pos, facing,
				Vec3d.ofCenter(pos).add(normal.multiply(0.5)), normal, faceSize,
				Vec3d.ofCenter(partnerPos).add(partnerNormal.multiply(0.5)), partnerNormal);
	}
}
