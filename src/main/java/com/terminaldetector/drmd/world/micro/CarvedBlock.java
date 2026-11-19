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
public class CarvedBlock extends BlockWithEntity implements net.minecraft.block.Waterloggable {
	public static final MapCodec<CarvedBlock> CODEC = createCodec(CarvedBlock::new);

	/** Shapes by mask. Bounded by how many distinct shapes the world actually contains. */
	private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();

	/** Ceiling on the cache, so a pathological world cannot grow it without end. */
	private static final int MAX_CACHED_SHAPES = 4096;

	public CarvedBlock(Settings settings) {
		super(settings);
        setDefaultState(getStateManager().getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED,false)
            .with(net.minecraft.state.property.Properties.LEVEL_1_8,8).with(net.minecraft.state.property.Properties.FALLING,false));
	}

    @Override protected void appendProperties(net.minecraft.state.StateManager.Builder<Block,BlockState> builder){builder.add(net.minecraft.state.property.Properties.WATERLOGGED,net.minecraft.state.property.Properties.LEVEL_1_8,net.minecraft.state.property.Properties.FALLING);}
    public static boolean water(net.minecraft.fluid.Fluid fluid){return fluid==net.minecraft.fluid.Fluids.WATER||fluid==net.minecraft.fluid.Fluids.FLOWING_WATER;}
    public static BlockState withFluid(BlockState state,net.minecraft.fluid.FluidState fluid){
        boolean wet=water(fluid.getFluid());return state.with(net.minecraft.state.property.Properties.WATERLOGGED,wet)
            .with(net.minecraft.state.property.Properties.LEVEL_1_8,wet?fluid.getLevel():8)
            .with(net.minecraft.state.property.Properties.FALLING,wet&&fluid.get(net.minecraft.fluid.FlowableFluid.FALLING));
    }
    @Override public net.minecraft.fluid.FluidState getFluidState(BlockState state){
        if(!state.get(net.minecraft.state.property.Properties.WATERLOGGED))return super.getFluidState(state);
        int level=state.get(net.minecraft.state.property.Properties.LEVEL_1_8);boolean falling=state.get(net.minecraft.state.property.Properties.FALLING);
        return level==8&&!falling?net.minecraft.fluid.Fluids.WATER.getStill(false):net.minecraft.fluid.Fluids.FLOWING_WATER.getFlowing(level,falling);
    }
    @Override public BlockState getStateForNeighborUpdate(BlockState state,net.minecraft.util.math.Direction direction,BlockState neighbour,net.minecraft.world.WorldAccess world,BlockPos pos,BlockPos neighbourPos){
        if(state.get(net.minecraft.state.property.Properties.WATERLOGGED)){var fluid=getFluidState(state).getFluid();world.scheduleFluidTick(pos,fluid,fluid.getTickRate(world));}
        return super.getStateForNeighborUpdate(state,direction,neighbour,world,pos,neighbourPos);
    }
    private static boolean admitsWater(BlockView world,BlockPos pos){
        if(world instanceof World actual&&!com.terminaldetector.drmd.world.edit.WorldEditSafety.neighbourhoodLoaded(actual,pos))return false;
        if(!(world.getBlockEntity(pos)instanceof CarvedBlockEntity carved)||carved.thermalReadOnly()||carved.mask()==MicroGrid.FULL)return false;
        var key=com.terminaldetector.drmd.world.geometry.HybridTerrain.snapshot(world,pos,carved.getRenderData());
        return com.terminaldetector.drmd.world.geometry.MicroWaterVolume.of(key).fillable();
    }
    @Override public boolean canFillWithFluid(net.minecraft.entity.player.PlayerEntity player,BlockView world,BlockPos pos,BlockState state,net.minecraft.fluid.Fluid fluid){
        return water(fluid)&&!state.get(net.minecraft.state.property.Properties.WATERLOGGED)&&admitsWater(world,pos);
    }
    @Override public boolean tryFillWithFluid(net.minecraft.world.WorldAccess world,BlockPos pos,BlockState state,net.minecraft.fluid.FluidState fluid){
        if(!canFillWithFluid(null,world,pos,state,fluid.getFluid()))return false;
        if(!world.isClient()){
            if(!world.setBlockState(pos,withFluid(state,fluid),Block.NOTIFY_ALL))return false;
            world.scheduleFluidTick(pos,fluid.getFluid(),fluid.getFluid().getTickRate(world));
        }return true;
    }
    @Override public ItemStack tryDrainFluid(net.minecraft.entity.player.PlayerEntity player,net.minecraft.world.WorldAccess world,BlockPos pos,BlockState state){
        if(!state.get(net.minecraft.state.property.Properties.WATERLOGGED)||!getFluidState(state).isStill())return ItemStack.EMPTY;
        if(!world.setBlockState(pos,withFluid(state,net.minecraft.fluid.Fluids.EMPTY.getDefaultState()),Block.NOTIFY_ALL))return ItemStack.EMPTY;
        return new ItemStack(net.minecraft.item.Items.WATER_BUCKET);
    }

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CarvedBlockEntity(pos, state);
	}

	/** Meshed into terrain sections through Fabric Renderer API. */
	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
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
		if(uncutHeat(world,pos) instanceof CarvedBlockEntity carved)return carved.source().isTransparent(world,pos);
		return true;
	}
	@Override
	public int getOpacity(BlockState state,BlockView world,BlockPos pos){
		if(uncutHeat(world,pos) instanceof CarvedBlockEntity carved)return carved.source().getOpacity(world,pos);
		return super.getOpacity(state,world,pos);
	}
	private static BlockEntity uncutHeat(BlockView world,BlockPos pos){
		var entity=world.getBlockEntity(pos);
		return entity instanceof CarvedBlockEntity carved&&carved.mask()==MicroGrid.FULL
			&&(carved.thermalReadOnly()||carved.thermal()!=null&&carved.thermal().bores().isEmpty())?carved:null;
	}

	/** What it drops is what the block it used to be drops. */
	@Override
	public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
		BlockEntity be = builder.getOptional(net.minecraft.loot.context.LootContextParameters.BLOCK_ENTITY);
		if (be instanceof CarvedBlockEntity carved) {
			BlockState src = carved.source();
			// A source carrying itself recurses here forever the moment anything asks for a drop —
			// see the guard in replace() that stops it from being written in the first place. This
			// is only reachable from a save that already has that corruption from before the guard
			// existed; an empty drop is the safe fallback, not a real answer for "what was this."
			if (src.getBlock() instanceof CarvedBlock) return List.of();
			return src.getDroppedStacks(builder);
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
		return carved.organic() || carved.mask()!=MicroGrid.FULL ? carved.organicShape(world) : shapeOf(carved.mask());
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
	 * <p>{@code pos} being carved already is routine, not an edge case: the Descent shaft's own
	 * boundary chamfer and a drill bore can both cross the same corner, and two passes of the drill
	 * itself do the same. {@code source} is only ever the true material on the <em>first</em> carve —
	 * on every carve after that, {@code world.getBlockState(pos)} is this very block, and wrapping a
	 * carved block's default state as if it were the material it drops made {@code getDroppedStacks}
	 * call itself through that "source" with no base case. The real source and the intersection of
	 * both masks — only what both passes still leave solid — replace it instead.
	 *
	 * @return the block entity holding the shape, or {@code null} if the swap did not take (including
	 *         when the two masks no longer share any solid cell, so the block breaks outright)
	 */
	public static CarvedBlockEntity replace(ServerWorld world, BlockPos pos, BlockState source, long mask) {
		if (!com.terminaldetector.drmd.world.access.CubicAvailability.onWorldThread(world)
				|| com.terminaldetector.drmd.world.access.WorldAccess.of(world).readIfLoaded(pos).isEmpty()) return null;
		if (!(world.getBlockEntity(pos) instanceof CarvedBlockEntity)
				&& !BlockDamage.isDamageable(world,pos,source)) return null;
		boolean organic = source.getBlock() instanceof com.terminaldetector.drmd.world.geometry.OrganicBlock;
        BlockState trueSource = organic ? ((com.terminaldetector.drmd.world.geometry.OrganicBlock)source.getBlock()).source() : source;
        organic |= com.terminaldetector.drmd.world.geometry.NaturalGeometry.applies(trueSource);
		long combinedMask = mask;
		if (world.getBlockEntity(pos) instanceof CarvedBlockEntity existing) {
			trueSource = existing.source();
			combinedMask = mask & existing.mask();
		}
		// Belt and suspenders against a save that already carries the self-reference this guards
		// against above: unwrapping it here too means fixed data, not just no more of it.
		if (trueSource.getBlock() instanceof CarvedBlock) {
			trueSource = Blocks.STONE.getDefaultState();
		}
		if (combinedMask == MicroGrid.EMPTY) {
			world.breakBlock(pos.toImmutable(), true, null);
			return null;
		}
        var retainedFluid=world.getFluidState(pos);
		world.setBlockState(pos, withFluid(com.terminaldetector.drmd.entity.ModWorldBlocks.CARVED.getDefaultState(),retainedFluid),
				Block.NOTIFY_ALL);
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof CarvedBlockEntity carved)) return null;
		carved.init(trueSource, combinedMask);
        if (organic) carved.setOrganic(true);
		return carved;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
		if (!state.isOf(next.getBlock()) && world instanceof ServerWorld server) MicroStore.get(server).clear(pos);
		super.onStateReplaced(state, world, pos, next, moved);
	}

	/** Whole again: put the original block back. */
	public static void restore(ServerWorld world, BlockPos pos) {
		if (!com.terminaldetector.drmd.world.access.CubicAvailability.onWorldThread(world)
				|| com.terminaldetector.drmd.world.access.WorldAccess.of(world).readIfLoaded(pos).isEmpty()) return;
		BlockEntity be = world.getBlockEntity(pos);
		BlockState source = be instanceof CarvedBlockEntity carved ? carved.source() : Blocks.STONE.getDefaultState();
		if (source.getBlock() instanceof CarvedBlock) source = Blocks.STONE.getDefaultState();
		world.setBlockState(pos, source, Block.NOTIFY_ALL);
	}

	@Override
	public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(
			World world, BlockState state, BlockEntityType<T> type) {
		return null; // Nothing to tick: the shape only changes when something hits it.
	}
	@Override
	protected void scheduledTick(BlockState state,ServerWorld world,BlockPos pos,net.minecraft.util.math.random.Random random){
		if(world.getBlockEntity(pos) instanceof CarvedBlockEntity carved&&!carved.thermalReadOnly()&&carved.thermal()!=null&&carved.thermal().heat()>0){
			var thermal=carved.thermal();
			if(world.getTime()>=thermal.coolAt()){
				if(thermal.bores().isEmpty()&&carved.mask()==MicroGrid.FULL)restore(world,pos);
				else carved.setThermal(thermal.bores().isEmpty()?null:thermal.cooled());
			}
			else world.scheduleBlockTick(pos,this,(int)Math.max(1,Math.min(ThermalDrillCell.COOL_TICKS,thermal.coolAt()-world.getTime())));
		}
	}
}
