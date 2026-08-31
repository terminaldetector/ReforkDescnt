package com.terminaldetector.drmd.world.portal.mirror;

import com.mojang.serialization.MapCodec;
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

/**
 * The mirror block's upgraded sibling: still {@link ReflectiveBlock} and, when ImmPtl is present,
 * still a literal live mirror on placement — but a {@link MirrorLinkerItem} can spend it on turning
 * a pair of these blocks into a real two-way portal (see {@link ImmPtlMirrorBridge#linkPortals}).
 * Not a subclass of {@link MirrorBlock}: with almost every method needing a different block entity
 * and different attach/detach targets, inheriting would mean overriding nearly everything anyway —
 * matches this codebase's own preference (see {@code GravityGeneratorBlock}/{@code GravityTorchBlock})
 * for small sibling classes over a shared base for this family of block.
 */
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
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!(world instanceof ServerWorld sw)) return;
		if (!PortalComplexity.hasImmersivePortals()) {
			// The single most likely cause of "can't walk through a linked mirror": there is no real
			// portal behind it at all, because ImmPtl (see MirrorLinkerItem's own identical warning
			// for the manual-link path) isn't installed — this is the placement/auto-link path, which
			// had no such feedback before, so a missing-dependency setup looked identical to a broken
			// one. Broadcast rather than targeting a placer: onBlockAdded fires for world-gen and
			// non-player causes too, and has no player reference to target even when one exists.
			for (ServerPlayerEntity p : sw.getPlayers()) {
				if (p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 36) {
					p.sendMessage(Text.literal(
							"§dCharged mirror is decorative only §7— needs the Immersive Portals stack "
									+ "to actually link (see docs/IMMPTL_STACK.md)."), false);
				}
			}
			return;
		}
		if (world.getBlockEntity(pos) instanceof ChargedMirrorBlockEntity be) {
			UUID id = ImmPtlMirrorBridge.attach(sw, pos, state.get(FACING));
			be.setAttachedEntityId(id);
			tryAutoLink(sw, pos, state.get(FACING));
		}
	}

	/** How far an auto-link ray walks before giving up — long enough for a real hallway, short
	 *  enough not to go hunting through unrelated, unloaded terrain on every placement. */
	private static final int AUTO_LINK_MAX_RANGE = 24;

	/**
	 * Auto-pairing: when a freshly placed charged mirror looks straight down an open line at another
	 * unlinked charged mirror facing back at it, link them on the spot. Walks one block at a time
	 * along {@code facing} and stops at the first non-air block that isn't that facing-back partner —
	 * deliberately no line-of-sight-free pairing through solid terrain: two mirrors either end of an
	 * open corridor connect, two mirrors either side of a mountain don't. Only ever produces the
	 * cheapest, unrotated, same-dimension link ({@link MirrorLinkerTier#SAME_DIMENSION}) — rotation,
	 * rescale, and cross-dimension links stay a deliberate {@link MirrorLinkerItem} action, never
	 * something placement alone triggers.
	 */
	private static void tryAutoLink(ServerWorld world, BlockPos pos, Direction facing) {
		Direction facingBack = facing.getOpposite();
		for (int i = 1; i <= AUTO_LINK_MAX_RANGE; i++) {
			BlockPos check = pos.offset(facing, i);
			BlockState state = world.getBlockState(check);
			if (state.isAir()) continue;
			if (!(state.getBlock() instanceof ChargedMirrorBlock) || state.get(LINKED)
					|| state.get(FACING) != facingBack) {
				return;
			}
			if (!(world.getBlockEntity(check) instanceof ChargedMirrorBlockEntity partnerBe)
					|| !(world.getBlockEntity(pos) instanceof ChargedMirrorBlockEntity selfBe)) {
				return;
			}
			ImmPtlMirrorBridge.linkPortals(
					world, pos, facing, selfBe.getAttachedEntityId(),
					world, check, facingBack, partnerBe.getAttachedEntityId(),
					MirrorLinkerTier.SAME_DIMENSION);
			// No player to message (this fires from placement, not a hand action) — particles/sound
			// at both ends are the only feedback, same effect MirrorLinkerItem plays on a manual link.
			for (BlockPos p : new BlockPos[]{pos, check}) {
				world.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
						30, 0.4, 0.4, 0.4, 0.02);
				world.playSound(null, p, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.8f, 1.1f);
			}
			return;
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld sw
				&& PortalComplexity.hasImmersivePortals()
				&& world.getBlockEntity(pos) instanceof ChargedMirrorBlockEntity be) {
			ImmPtlMirrorBridge.detach(sw, be.getAttachedEntityId());
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	/**
	 * Called once {@link ImmPtlMirrorBridge#linkPortals} has actually spawned the two-way portal at
	 * this position — by {@link MirrorLinkerItem} after a manual link, or by {@link #tryAutoLink}
	 * right here after an automatic one — records the link and flips {@link #LINKED} so every client
	 * sees the change through the ordinary blockstate-update path.
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
