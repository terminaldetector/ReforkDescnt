package com.terminaldetector.drmd.world.build;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Shared place + consume for build tool / construction laser.
 * Creative: free iron fallback. Survival: needs BlockItem in offhand or hotbar.
 */
public final class BlockInstall {
	private BlockInstall() {}

	public record Material(Block block, int slot) {
		static final int OFFHAND = 40;
		boolean creativeFallback() { return slot < 0; }
	}

	public static Material resolve(PlayerEntity player) {
		ItemStack off = player.getOffHandStack();
		if (off.getItem() instanceof BlockItem bi && !off.isEmpty()) {
			return new Material(bi.getBlock(), Material.OFFHAND);
		}
		for (int i = 0; i < 9; i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (s.getItem() instanceof BlockItem bi && !s.isEmpty()) {
				return new Material(bi.getBlock(), i);
			}
		}
		if (player.getAbilities().creativeMode) {
			return new Material(Blocks.IRON_BLOCK, -1);
		}
		return null;
	}

	/** Orient player's material to face, place, consume one (survival). */
	public static boolean placeOriented(World world, PlayerEntity player, BlockPos at, Direction face) {
		if (!world.getBlockState(at).isReplaceable()) return false;
		Material mat = resolve(player);
		if (mat == null) {
			if (!world.isClient) {
				player.sendMessage(net.minecraft.text.Text.literal(
						"§cНет блоков — BlockItem в оффхенд / хотбар"), true);
			}
			return false;
		}
		BlockState state = AdaptivePlacement.orient(mat.block().getDefaultState(), face);
		if (!world.isClient) {
			world.setBlockState(at, state, Block.NOTIFY_ALL);
			consume(player, mat);
		}
		return true;
	}

	/** Place an already-built state, but swap to player's material block when needed. */
	public static boolean placeState(World world, PlayerEntity player, BlockPos at, BlockState desired) {
		if (!world.getBlockState(at).isReplaceable()) return false;
		Material mat = resolve(player);
		if (mat == null) {
			if (!world.isClient) {
				player.sendMessage(net.minecraft.text.Text.literal(
						"§cНет блоков — BlockItem в оффхенд / хотбар"), true);
			}
			return false;
		}
		BlockState state = mat.block().getDefaultState();
		if (desired.contains(Properties.FACING) && state.contains(Properties.FACING)) {
			state = state.with(Properties.FACING, desired.get(Properties.FACING));
		} else if (desired.contains(Properties.HORIZONTAL_FACING) && state.contains(Properties.HORIZONTAL_FACING)) {
			state = state.with(Properties.HORIZONTAL_FACING, desired.get(Properties.HORIZONTAL_FACING));
		}
		if (!world.isClient) {
			world.setBlockState(at, state, Block.NOTIFY_ALL);
			consume(player, mat);
		}
		return true;
	}

	private static void consume(PlayerEntity player, Material mat) {
		if (player.getAbilities().creativeMode || mat.creativeFallback()) return;
		ItemStack stack = mat.slot() == Material.OFFHAND
				? player.getOffHandStack()
				: player.getInventory().getStack(mat.slot());
		if (!stack.isEmpty()) stack.decrement(1);
	}
}
