package com.terminaldetector.drmd.world.micro;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.explosion.Explosion;
import com.mojang.serialization.MapCodec;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A block with a hole in it.
 *
 * <p>What a damaged block becomes once the damage is meant to be a shape rather than a texture. It
 * holds no material of its own — the block it replaced lives in its {@link CarvedBlockEntity}, which
 * is what makes a carved wall still drop planks and still sound like wood.
 *
 * <p>The collision shape comes from the mask, so a hole shot through a wall is a hole you can fly
 * through. Shapes are cached by mask: there are only so many shapes a block can be in, the same few
 * recur across a whole battlefield, and rebuilding a {@link VoxelShape} for every collision query
 * would cost more than the damage system saves.
 */
public class CarvedBlock extends BlockWithEntity {
	public static final MapCodec<CarvedBlock> CODEC = createCodec(CarvedBlock::new);

	/** Shapes by mask. Bounded by how many distinct shapes the world actually contains. */
	private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();

	/** Ceiling on the cache, so a pathological world cannot grow it without end. */
	private static final int MAX_CACHED_SHAPES = 4096;

	public CarvedBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CarvedBlockEntity(pos, state);
	}

	/**
	 * Drawn by its block entity renderer, not by a baked model.
	 *
	 * <p>There is no model that could be right: the block looks like whatever it used to be, minus
	 * the parts that are gone.
	 */
	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
									  net.minecraft.block.ShapeContext context) {
		return shapeAt(world, pos);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos,
										net.minecraft.block.ShapeContext context) {
		return shapeAt(world, pos);
	}

	@Override
	public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos,
											  net.minecraft.block.ShapeContext context) {
		return shapeAt(world, pos);
	}

	@Override
	public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
		return 1.0f;
	}

	@Override
	public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
		return true;
	}

	/** What it drops is what the block it used to be drops. */
	@Override
	public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
		BlockEntity be = builder.getOptional(net.minecraft.loot.context.LootContextParameters.BLOCK_ENTITY);
		if (be instanceof CarvedBlockEntity carved) {
			return carved.source().getDroppedStacks(builder);
		}
		return super.getDroppedStacks(state, builder);
	}

	@Override
	public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
		BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof CarvedBlockEntity carved) {
			return new ItemStack(carved.source().getBlock());
		}
		return super.getPickStack(world, pos, state);
	}

	@Override
	public float getBlastResistance() {
		return 0.5f;
	}

	@Override
	public void onDestroyedByExplosion(World world, BlockPos pos, Explosion explosion) {
		super.onDestroyedByExplosion(world, pos, explosion);
	}

	private static VoxelShape shapeAt(BlockView world, BlockPos pos) {
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof CarvedBlockEntity carved)) return VoxelShapes.fullCube();
		return shapeOf(carved.mask());
	}

	/** The shape for a mask, built once and kept. */
	public static VoxelShape shapeOf(long mask) {
		if (mask == MicroGrid.FULL) return VoxelShapes.fullCube();
		if (mask == MicroGrid.EMPTY) return VoxelShapes.empty();
		VoxelShape cached = SHAPES.get(mask);
		if (cached != null) return cached;
		VoxelShape built = VoxelShapes.empty();
		for (MicroGrid.Box box : MicroGrid.boxes(mask)) {
			built = VoxelShapes.union(built, VoxelShapes.cuboid(
					box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
		}
		built = built.simplify();
		if (SHAPES.size() < MAX_CACHED_SHAPES) SHAPES.put(mask, built);
		return built;
	}

	/**
	 * Put a carved block where an ordinary one was, remembering what it was.
	 *
	 * @return the block entity holding the shape, or {@code null} if the swap did not take
	 */
	public static CarvedBlockEntity replace(ServerWorld world, BlockPos pos, BlockState source, long mask) {
		world.setBlockState(pos, com.terminaldetector.drmd.entity.ModWorldBlocks.CARVED.getDefaultState(),
				Block.NOTIFY_ALL);
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof CarvedBlockEntity carved)) return null;
		carved.init(source, mask);
		return carved;
	}

	/** Whole again: put the original block back. */
	public static void restore(ServerWorld world, BlockPos pos) {
		BlockEntity be = world.getBlockEntity(pos);
		BlockState source = be instanceof CarvedBlockEntity carved ? carved.source() : Blocks.STONE.getDefaultState();
		world.setBlockState(pos, source, Block.NOTIFY_ALL);
	}

	@Override
	public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(
			World world, BlockState state, BlockEntityType<T> type) {
		return null; // Nothing to tick: the shape only changes when something hits it.
	}
}
