package com.terminaldetector.drmd.world.portal.mirror;

import com.mojang.serialization.MapCodec;
import com.terminaldetector.drmd.diag.DiagTrace;
import com.terminaldetector.drmd.world.portal.PortalComplexity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** A reflective surface that becomes a native portal after a Fusion impact. */
public class ChargedMirrorBlock extends BlockWithEntity implements ReflectiveBlock {
	public static final DirectionProperty FACING = Properties.FACING;
	/** Client-visible cue only — the actual link data lives in {@link ChargedMirrorBlockEntity}. */
	public static final BooleanProperty LINKED = BooleanProperty.of("linked");
	public static final MapCodec<ChargedMirrorBlock> CODEC = createCodec(ChargedMirrorBlock::new);

	public ChargedMirrorBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LINKED, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected net.minecraft.block.BlockRenderType getRenderType(BlockState state) {
		return net.minecraft.block.BlockRenderType.MODEL;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, LINKED);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getSide());
	}

	@Override
	public Vec3d getReflectionNormal(BlockState state) {
		return MirrorReflection.normalFor(state.get(FACING));
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ChargedMirrorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		// Only a linked pair needs ticking, and only on the server: that is where travel is decided.
		// An unlinked charged mirror still has nothing to do — the attached Mirror entity, when ImmPtl
		// is present, sits and saves with its chunk exactly as before.
		if (world.isClient || !state.get(LINKED)) return null;
		return validateTicker(type, com.terminaldetector.drmd.entity.ModBlockEntities.CHARGED_MIRROR,
				ChargedMirrorBlockEntity::tick);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld sw
				&& world.getBlockEntity(pos) instanceof ChargedMirrorBlockEntity be) {
			if (PortalComplexity.hasImmersivePortals()) ImmPtlMirrorBridge.detach(sw, be.getAttachedEntityId());
			unlinkPartner(sw, pos, be.getLinkPartnerPos(), be.getLinkPartnerDim());
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	/**
	 * Record a working link with no portal entity behind it — the same bookkeeping
	 * {@link ImmPtlMirrorBridge#linkPortals} ends with, minus the two {@code Portal}s it spawns first.
	 *
	 * <p>This is what makes a linked pair mean anything without Immersive Portals installed. Before it,
	 * both link paths refused outright when ImmPtl was absent, so {@code LINKED} was never set, and the
	 * native travel in {@link com.terminaldetector.drmd.world.portal.PortalTravel} could not fire —
	 * dead code exactly in the case it exists for.
	 *
	 * <p>A null entity id is not an oversight: it is the flag that says "nothing of ImmPtl's is carrying
	 * anyone through here", which is what {@code ChargedMirrorBlockEntity.tick} reads to decide whether
	 * to do the carrying itself.
	 */
	static void linkNatively(ServerWorld world, BlockPos a, BlockPos b) {
		markLinked(world, a, null, b, world.getRegistryKey());
		markLinked(world, b, null, a, world.getRegistryKey());
		DiagTrace.record("portal", "mirrors linked natively: " + a + " <-> " + b);
	}

	/**
	 * Break the far end of a link when this end is removed.
	 *
	 * <p>A link is a pair, and half of one is not a smaller link — it is a block that claims to be
	 * linked and silently is not. Natively that only misleads; with ImmPtl the far {@code Portal}
	 * entity outlives the block it was anchored to and goes on carrying people into a wall.
	 *
	 * <p>Only breaks a link that still points back here. The far block may have been re-linked to
	 * someone else in the meantime, and that link is not this one's to cut.
	 *
	 * <p>Same dimension only, like everything else here: reaching into another world from inside a
	 * block removal would mean loading its chunks at the worst possible moment.
	 */
	private static void unlinkPartner(ServerWorld world, BlockPos self,
			@Nullable BlockPos partnerPos, @Nullable RegistryKey<World> partnerDim) {
		if (partnerPos == null) return;
		if (partnerDim != null && !partnerDim.equals(world.getRegistryKey())) return;
		BlockState partner = world.getBlockState(partnerPos);
		if (!(partner.getBlock() instanceof ChargedMirrorBlock) || !partner.get(LINKED)) return;
		if (world.getBlockEntity(partnerPos) instanceof ChargedMirrorBlockEntity partnerBe) {
			if (partnerBe.getLinkPartnerPos() != null && !partnerBe.getLinkPartnerPos().equals(self)) return;
			if (PortalComplexity.hasImmersivePortals()) {
				ImmPtlMirrorBridge.detach(world, partnerBe.getAttachedEntityId());
			}
			partnerBe.clearLink();
		}
		DiagTrace.record("portal", "link broken: " + self + " removed, so " + partnerPos + " unlinked");
		world.setBlockState(partnerPos, partner.with(LINKED, false), Block.NOTIFY_LISTENERS);
	}

	/**
	 * Records the link and flips {@link #LINKED}, so every client sees the change through the ordinary
	 * blockstate-update path. Called from all four link paths: automatic or manual, with a live
	 * {@link ImmPtlMirrorBridge#linkPortals} portal behind it or with nothing behind it at all
	 * ({@link #linkNatively}). {@code portalEntityId} is null in the latter case, and that null is the
	 * signal the tick reads — see {@link #linkNatively}.
	 */
	public static void markLinked(ServerWorld world, BlockPos pos, UUID portalEntityId,
			BlockPos partnerPos, RegistryKey<World> partnerDim) {
		if (world.getBlockEntity(pos) instanceof ChargedMirrorBlockEntity be) {
			be.setLink(portalEntityId, partnerPos, partnerDim);
		}
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof ChargedMirrorBlock) {
			world.setBlockState(pos, state.with(LINKED, true), Block.NOTIFY_LISTENERS);
		}
	}
}
