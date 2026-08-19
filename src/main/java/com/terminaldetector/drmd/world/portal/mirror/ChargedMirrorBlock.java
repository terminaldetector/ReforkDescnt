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
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
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
		return null; // Nothing to tick: the attached Mirror/Portal entity sits and saves with its chunk.
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (world instanceof ServerWorld sw && PortalComplexity.hasImmersivePortals()
				&& world.getBlockEntity(pos) instanceof ChargedMirrorBlockEntity be) {
			UUID id = ImmPtlMirrorBridge.attach(sw, pos, state.get(FACING));
			be.setAttachedEntityId(id);
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
	 * Called by {@link MirrorLinkerItem} once {@link ImmPtlMirrorBridge#linkPortals} has actually
	 * spawned the two-way portal at this position — records the link and flips {@link #LINKED} so
	 * every client sees the change through the ordinary blockstate-update path.
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
