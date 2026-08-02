package com.terminaldetector.drmd.world.soil;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 6D Soil — six working faces. Trees/fungi/vines/flowers/moss grow outward
 * relative to the face they sit on: spherical forests, ring gardens, ceiling jungles.
 */
public class SixDSoilBlock extends BlockWithEntity {
	public static final BooleanProperty GROW_UP = BooleanProperty.of("grow_up");
	public static final BooleanProperty GROW_DOWN = BooleanProperty.of("grow_down");
	public static final BooleanProperty GROW_NORTH = BooleanProperty.of("grow_north");
	public static final BooleanProperty GROW_SOUTH = BooleanProperty.of("grow_south");
	public static final BooleanProperty GROW_EAST = BooleanProperty.of("grow_east");
	public static final BooleanProperty GROW_WEST = BooleanProperty.of("grow_west");

	public static final MapCodec<SixDSoilBlock> CODEC = createCodec(SixDSoilBlock::new);

	public SixDSoilBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState()
				.with(GROW_UP, false).with(GROW_DOWN, false)
				.with(GROW_NORTH, false).with(GROW_SOUTH, false)
				.with(GROW_EAST, false).with(GROW_WEST, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(GROW_UP, GROW_DOWN, GROW_NORTH, GROW_SOUTH, GROW_EAST, GROW_WEST);
	}

	public static BooleanProperty propertyFor(Direction dir) {
		return switch (dir) {
			case UP -> GROW_UP;
			case DOWN -> GROW_DOWN;
			case NORTH -> GROW_NORTH;
			case SOUTH -> GROW_SOUTH;
			case EAST -> GROW_EAST;
			case WEST -> GROW_WEST;
		};
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SixDSoilBlockEntity(pos, state);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);
		Direction face = hit.getSide();
		BooleanProperty prop = propertyFor(face);

		FaceCrop crop = FaceCrop.fromItem(stack);
		if (crop != null) {
			if (!state.get(prop)) {
				world.setBlockState(pos, state.with(prop, true), Block.NOTIFY_ALL);
			}
			if (world.getBlockEntity(pos) instanceof SixDSoilBlockEntity be) {
				be.setCrop(face, crop);
			}
			if (!player.getAbilities().creativeMode) stack.decrement(1);
			tryGrowOutward(world, pos, face, crop);
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	@Override
	protected boolean hasRandomTicks(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		for (Direction dir : Direction.values()) {
			if (!state.get(propertyFor(dir))) continue;
			if (random.nextInt(8) != 0) continue;
			FaceCrop crop = FaceCrop.MOSS;
			if (world.getBlockEntity(pos) instanceof SixDSoilBlockEntity be) {
				crop = be.getCrop(dir);
			}
			tryGrowOutward(world, pos, dir, crop);
		}
	}

	/** Growth always goes outward from the face — never absolute +Y. */
	public static void tryGrowOutward(World world, BlockPos soil, Direction face, FaceCrop crop) {
		BlockPos target = soil.offset(face);
		if (!world.getBlockState(target).isAir()) return;
		BlockState plant = crop.plantState(face);
		if (plant != null) {
			world.setBlockState(target, plant, Block.NOTIFY_ALL);
		}
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return null;
	}

	public enum FaceCrop {
		TREE, MUSHROOM, VINE, FLOWER, MOSS;

		@Nullable
		public static FaceCrop fromItem(ItemStack stack) {
			if (stack.isOf(Items.OAK_SAPLING) || stack.isOf(Items.BIRCH_SAPLING) || stack.isOf(Items.SPRUCE_SAPLING))
				return TREE;
			if (stack.isOf(Items.BROWN_MUSHROOM) || stack.isOf(Items.RED_MUSHROOM)) return MUSHROOM;
			if (stack.isOf(Items.VINE) || stack.isOf(Items.GLOW_LICHEN)) return VINE;
			if (stack.isOf(Items.POPPY) || stack.isOf(Items.DANDELION) || stack.isOf(Items.SHORT_GRASS)) return FLOWER;
			if (stack.isOf(Items.MOSS_BLOCK) || stack.isOf(Items.MOSS_CARPET)) return MOSS;
			return null;
		}

		@Nullable
		public BlockState plantState(Direction outward) {
			return switch (this) {
				case TREE -> {
					// Sapling proxy: oak leaves cluster as "tree grows outward"
					yield net.minecraft.block.Blocks.OAK_LEAVES.getDefaultState();
				}
				case MUSHROOM -> net.minecraft.block.Blocks.BROWN_MUSHROOM.getDefaultState();
				case VINE -> {
					var vine = net.minecraft.block.Blocks.VINE.getDefaultState();
					yield switch (outward.getOpposite()) {
						case NORTH -> vine.with(net.minecraft.block.VineBlock.NORTH, true);
						case SOUTH -> vine.with(net.minecraft.block.VineBlock.SOUTH, true);
						case EAST -> vine.with(net.minecraft.block.VineBlock.EAST, true);
						case WEST -> vine.with(net.minecraft.block.VineBlock.WEST, true);
						default -> net.minecraft.block.Blocks.GLOW_LICHEN.getDefaultState();
					};
				}
				case FLOWER -> net.minecraft.block.Blocks.POPPY.getDefaultState();
				case MOSS -> net.minecraft.block.Blocks.MOSS_CARPET.getDefaultState();
			};
		}
	}
}
