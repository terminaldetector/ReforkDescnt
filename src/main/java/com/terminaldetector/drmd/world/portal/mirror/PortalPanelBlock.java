package com.terminaldetector.drmd.world.portal.mirror;

import com.mojang.serialization.MapCodec;
import com.terminaldetector.drmd.world.portal.PortalComplexity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A thin, flush-mounted portal panel — {@link PortalGunItem}'s payload. Unlike {@link MirrorBlock}/
 * {@link ChargedMirrorBlock} (full cubes, since they double as physical mirrors {@link ReflectiveBlock}
 * bounces lasers off), this is deliberately not a {@link ReflectiveBlock}: it's not meant to be walked
 * into or reflect anything, only to mark the plane the portal actually occupies, painting- or
 * carpet-style against whichever of the 6 faces it lands on ({@link #FACING} exactly like the mirrors).
 * That plane is a live ImmPtl portal when the mod is installed and a natively carried link when it is
 * not — see {@link ChargedMirrorBlock}'s own class note, which this block follows exactly.
 *
 * <p>Its one real difference from {@link ChargedMirrorBlock}'s own pairing: the live portal it attaches
 * ({@link ImmPtlMirrorBridge#attach(ServerWorld, BlockPos, Direction, Box)}) is shaped from a 4-block-
 * wide {@link #panelBox}, not a single block — {@code PortalAPI.setPortalOrthodoxShape} already takes
 * an arbitrary {@link Box}, so one placed block can anchor a portal plane far bigger than itself, the
 * same way the existing call already anchors a 1-block mirror from a 1-block {@code new Box(pos)}.
 */
public class PortalPanelBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.FACING;
	/** Client-visible cue only — the actual link data lives in {@link PortalPanelBlockEntity}. */
	public static final BooleanProperty LINKED = BooleanProperty.of("linked");
	public static final MapCodec<PortalPanelBlock> CODEC = createCodec(PortalPanelBlock::new);

	/**
	 * Half the panel's in-plane span — 2 either side of the anchor block makes a 4-wide portal.
	 * Package-private because {@link PortalPanelBlockEntity} carries travellers across exactly this
	 * span; a second copy of the number there could drift from the one the portal is shaped from.
	 */
	static final double HALF_SPAN = 2.0;
	/** Same reach as {@link ChargedMirrorBlock}'s own auto-link walk — a real hallway, not the whole world. */
	private static final int AUTO_LINK_MAX_RANGE = 24;

	public PortalPanelBlock(Settings settings) {
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
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(FACING)) {
			case DOWN -> VoxelShapes.cuboid(0.0, 0.9, 0.0, 1.0, 1.0, 1.0);
			case UP -> VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.1, 1.0);
			case NORTH -> VoxelShapes.cuboid(0.0, 0.0, 0.9, 1.0, 1.0, 1.0);
			case SOUTH -> VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 1.0, 0.1);
			case WEST -> VoxelShapes.cuboid(0.9, 0.0, 0.0, 1.0, 1.0, 1.0);
			case EAST -> VoxelShapes.cuboid(0.0, 0.0, 0.0, 0.1, 1.0, 1.0);
		};
	}

	/**
	 * The box a live ImmPtl portal gets shaped from once attached here: full block thickness along
	 * {@code facing}'s own axis (matching the 1-block thickness {@code new Box(pos)} already used for
	 * plain mirrors — this changes span, not depth), {@link #HALF_SPAN} either side of the anchor along
	 * both other axes.
	 */
	static Box panelBox(BlockPos pos, Direction facing) {
		Vec3d c = Vec3d.ofCenter(pos);
		return switch (facing.getAxis()) {
			case X -> new Box(c.x - 0.5, c.y - HALF_SPAN, c.z - HALF_SPAN, c.x + 0.5, c.y + HALF_SPAN, c.z + HALF_SPAN);
			case Y -> new Box(c.x - HALF_SPAN, c.y - 0.5, c.z - HALF_SPAN, c.x + HALF_SPAN, c.y + 0.5, c.z + HALF_SPAN);
			case Z -> new Box(c.x - HALF_SPAN, c.y - HALF_SPAN, c.z - 0.5, c.x + HALF_SPAN, c.y + HALF_SPAN, c.z + 0.5);
		};
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PortalPanelBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		// Only a linked pair needs ticking, and only on the server: that is where travel is decided.
		// Was null while the attached ImmPtl Portal did the carrying; a linked panel now carries
		// travellers itself when there is no such portal — see PortalPanelBlockEntity.tick.
		if (world.isClient || !state.get(LINKED)) return null;
		return validateTicker(type, com.terminaldetector.drmd.entity.ModBlockEntities.PORTAL_PANEL,
				PortalPanelBlockEntity::tick);
	}

	/** Ambient glow while linked — purely cosmetic, client-only, same idiom as {@code LocatorPanelBlock}. */
	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!state.get(LINKED) || random.nextInt(4) != 0) return;
		Direction facing = state.get(FACING);
		Vec3d c = Vec3d.ofCenter(pos).add(Vec3d.of(facing.getVector()).multiply(0.52));
		double u = (random.nextDouble() - 0.5) * 3.6;
		double v = (random.nextDouble() - 0.5) * 3.6;
		Vec3d spread = switch (facing.getAxis()) {
			case X -> new Vec3d(0, u, v);
			case Y -> new Vec3d(u, 0, v);
			case Z -> new Vec3d(u, v, 0);
		};
		world.addParticle(ParticleTypes.REVERSE_PORTAL, c.x + spread.x, c.y + spread.y, c.z + spread.z, 0, 0.01, 0);
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!(world instanceof ServerWorld sw)) return;
		// Not a fresh placement, so nothing to attach or pair. Both of this block's own link edits
		// come back through here: markLinked flips LINKED with a setBlockState, and unlinkPartner
		// flips it back, and vanilla calls onBlockAdded again for each new state. Reading oldState
		// rather than the LINKED bit catches both, and without it pairing ran a second time from
		// inside itself — harmless natively, but it spawned a second pair of ImmPtl portals and
		// orphaned the first.
		if (oldState.isOf(state.getBlock())) return;

		Direction facing = state.get(FACING);
		// The live portal entity is ImmPtl's see-through surface, and only that — the pairing and the
		// travel below stand on their own.
		if (PortalComplexity.hasImmersivePortals()
				&& world.getBlockEntity(pos) instanceof PortalPanelBlockEntity be) {
			be.setAttachedEntityId(ImmPtlMirrorBridge.attach(sw, pos, facing, panelBox(pos, facing)));
		}
		tryAutoLink(sw, pos, facing);
	}

	/**
	 * Auto-pairing, walked and gated exactly like {@link ChargedMirrorBlock#tryAutoLink}: two freshly
	 * placed panels facing each other down an open line link immediately, same-dimension only — a
	 * deliberate cross-dimension/rotated link stays out of scope for a gun's fire-and-forget placement.
	 */
	private static void tryAutoLink(ServerWorld world, BlockPos pos, Direction facing) {
		Direction facingBack = facing.getOpposite();
		for (int i = 1; i <= AUTO_LINK_MAX_RANGE; i++) {
			BlockPos check = pos.offset(facing, i);
			BlockState state = world.getBlockState(check);
			if (state.isAir()) continue;
			if (!(state.getBlock() instanceof PortalPanelBlock) || state.get(LINKED)
					|| state.get(FACING) != facingBack) {
				return;
			}
			if (!(world.getBlockEntity(check) instanceof PortalPanelBlockEntity partnerBe)
					|| !(world.getBlockEntity(pos) instanceof PortalPanelBlockEntity selfBe)) {
				return;
			}
			if (PortalComplexity.hasImmersivePortals()) {
				ImmPtlMirrorBridge.linkPortalPanels(
						world, pos, facing, selfBe.getAttachedEntityId(), panelBox(pos, facing),
						world, check, facingBack, partnerBe.getAttachedEntityId(), panelBox(check, facingBack));
			} else {
				linkNatively(world, pos, check);
			}
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
				&& world.getBlockEntity(pos) instanceof PortalPanelBlockEntity be) {
			if (PortalComplexity.hasImmersivePortals()) ImmPtlMirrorBridge.detach(sw, be.getAttachedEntityId());
			unlinkPartner(sw, pos, be.getLinkPartnerPos(), be.getLinkPartnerDim());
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	/**
	 * Record a working link with no portal entity behind it — see {@code ChargedMirrorBlock.linkNatively}
	 * for why this exists at all. Same shape here, for this block's own types.
	 */
	private static void linkNatively(ServerWorld world, BlockPos a, BlockPos b) {
		markLinked(world, a, null, b, world.getRegistryKey());
		markLinked(world, b, null, a, world.getRegistryKey());
		// Same note ChargedMirrorBlock.noteNativeLink gives, worded for panels: the surface looks no
		// different, so without this a working link is indistinguishable from nothing having happened.
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.squaredDistanceTo(Vec3d.ofCenter(a)) < 256 || p.squaredDistanceTo(Vec3d.ofCenter(b)) < 256) {
				p.sendMessage(Text.literal(
						"§dPanels linked §7— walk in to travel. §8Seeing through needs Immersive Portals "
								+ "(docs/IMMPTL_STACK.md)."), false);
			}
		}
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
		if (!(partner.getBlock() instanceof PortalPanelBlock) || !partner.get(LINKED)) return;
		if (world.getBlockEntity(partnerPos) instanceof PortalPanelBlockEntity partnerBe) {
			if (partnerBe.getLinkPartnerPos() != null && !partnerBe.getLinkPartnerPos().equals(self)) return;
			if (PortalComplexity.hasImmersivePortals()) {
				ImmPtlMirrorBridge.detach(world, partnerBe.getAttachedEntityId());
			}
			partnerBe.clearLink();
		}
		world.setBlockState(partnerPos, partner.with(LINKED, false), Block.NOTIFY_LISTENERS);
	}

	/** Mirrors {@link ChargedMirrorBlock#markLinked} exactly, for this block's own entity/state types. */
	public static void markLinked(ServerWorld world, BlockPos pos, UUID portalEntityId,
			BlockPos partnerPos, RegistryKey<World> partnerDim) {
		if (world.getBlockEntity(pos) instanceof PortalPanelBlockEntity be) {
			be.setLink(portalEntityId, partnerPos, partnerDim);
		}
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof PortalPanelBlock) {
			world.setBlockState(pos, state.with(LINKED, true), Block.NOTIFY_LISTENERS);
		}
	}
}
