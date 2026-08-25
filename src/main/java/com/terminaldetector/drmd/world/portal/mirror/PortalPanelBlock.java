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
 * into or reflect anything, only to mark the plane a live ImmPtl portal actually occupies, painting- or
 * carpet-style against whichever of the 6 faces it lands on ({@link #FACING} exactly like the mirrors).
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

	/** Half the panel's in-plane span — 2 either side of the anchor block makes a 4-wide portal. */
	private static final double HALF_SPAN = 2.0;
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
		return null; // Nothing to tick server-side: the attached Mirror/Portal entity sits and saves with its chunk.
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
		if (!PortalComplexity.hasImmersivePortals()) {
			for (ServerPlayerEntity p : sw.getPlayers()) {
				if (p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 36) {
					p.sendMessage(Text.literal(
							"§dPortal panel is decorative only §7— needs the Immersive Portals stack "
									+ "to actually link (see docs/IMMPTL_STACK.md)."), false);
				}
			}
			return;
		}
		if (world.getBlockEntity(pos) instanceof PortalPanelBlockEntity be) {
			Direction facing = state.get(FACING);
			UUID id = ImmPtlMirrorBridge.attach(sw, pos, facing, panelBox(pos, facing));
			be.setAttachedEntityId(id);
			tryAutoLink(sw, pos, facing);
		}
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
			ImmPtlMirrorBridge.linkPortalPanels(
					world, pos, facing, selfBe.getAttachedEntityId(), panelBox(pos, facing),
					world, check, facingBack, partnerBe.getAttachedEntityId(), panelBox(check, facingBack));
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
				&& world.getBlockEntity(pos) instanceof PortalPanelBlockEntity be) {
			ImmPtlMirrorBridge.detach(sw, be.getAttachedEntityId());
		}
		super.onStateReplaced(state, world, pos, newState, moved);
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
