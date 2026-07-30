package com.terminaldetector.drmd.world.build;

import com.terminaldetector.drmd.world.LocalOrientation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 6DoF Build Tool — architecture for space, not houses.
 * Cycles placement mode; rotates placement axes; snaps local orientation from surfaces.
 */
public class BuildToolItem extends Item {
	public BuildToolItem(Settings settings) {
		super(settings.maxCount(1));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.isSneaking()) {
			BuildMode mode = readMode(stack).next();
			writeMode(stack, mode);
			if (!world.isClient) {
				user.sendMessage(Text.literal("§bBuild mode: §f" + mode.label), true);
			}
			return TypedActionResult.success(stack);
		}
		int axis = (readAxis(stack) + 1) % 3;
		writeAxis(stack, axis);
		if (!world.isClient) {
			String name = axis == 0 ? "PITCH" : axis == 1 ? "YAW" : "ROLL";
			user.sendMessage(Text.literal("§bRotate axis: §f" + name + "  rot=" + readRotation(stack)), true);
		}
		return TypedActionResult.success(stack);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		if (player == null) return ActionResult.PASS;
		ItemStack tool = context.getStack();
		BlockPos hit = context.getBlockPos();
		Direction face = context.getSide();

		if (player.isSneaking()) {
			LocalOrientation.setFromDirection(player.getUuid(), face);
			if (!world.isClient) {
				player.sendMessage(Text.literal("§aLocal UP = " + face.asString() + " (surface mode)"), true);
			}
			writeMode(tool, BuildMode.SURFACE);
			return ActionResult.success(world.isClient);
		}

		// Construction Mode (server + client mirror): surface-normal on both sides — no ghost blocks.
		if (ConstructionMode.isActive(player)) {
			BlockPos placed = AdaptivePlacement.placeOnSurface(world, player, hit, face);
			return placed != null ? ActionResult.success(world.isClient) : ActionResult.FAIL;
		}

		BuildMode mode = readMode(tool);
		BlockPos placeAt = resolvePlacePos(player, hit, face, mode);
		var mat = BlockInstall.resolve(player);
		if (mat == null) {
			if (!world.isClient) {
				player.sendMessage(Text.literal("§cНет блоков — BlockItem в оффхенд / хотбар"), true);
			}
			return ActionResult.FAIL;
		}
		BlockState oriented = applyRotation(mat.block().getDefaultState(), face, readAxis(tool), readRotation(tool));
		if (!BlockInstall.placeState(world, player, placeAt, oriented)) {
			return ActionResult.FAIL;
		}
		if (!world.isClient) bumpRotation(tool);
		return ActionResult.success(world.isClient);
	}

	private static BlockPos resolvePlacePos(PlayerEntity player, BlockPos hit, Direction face, BuildMode mode) {
		return switch (mode) {
			case LOOK -> {
				Vec3d eye = player.getEyePos();
				Vec3d dir = player.getRotationVec(1f);
				yield BlockPos.ofFloored(eye.add(dir.multiply(3.5)));
			}
			case SURFACE -> hit.offset(face);
			case PLANE -> {
				Vec3d up = LocalOrientation.getUp(player.getUuid());
				Vec3d look = player.getRotationVec(1f);
				Vec3d projected = look.subtract(up.multiply(look.dotProduct(up)));
				if (projected.lengthSquared() < 1e-4) yield hit.offset(face);
				Direction step = Direction.getFacing(projected.x, projected.y, projected.z);
				yield hit.offset(step);
			}
		};
	}

	private static BlockState applyRotation(BlockState state, Direction face, int axis, int rot) {
		if (state.contains(net.minecraft.state.property.Properties.FACING)) {
			Direction facing = face;
			if (axis == 1) {
				Direction[] cycle = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
				if (face.getAxis().isVertical()) {
					facing = cycle[Math.floorMod(rot, 4)];
				}
			} else if (axis == 2 && face.getAxis().isHorizontal()) {
				facing = rot % 2 == 0 ? face : face.getOpposite();
			}
			return state.with(net.minecraft.state.property.Properties.FACING, facing);
		}
		if (state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING)) {
			Direction[] cycle = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
			return state.with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, cycle[Math.floorMod(rot, 4)]);
		}
		return state;
	}

	private static BuildMode readMode(ItemStack stack) {
		NbtCompound nbt = custom(stack);
		try {
			return BuildMode.valueOf(nbt.getString("mode"));
		} catch (Exception e) {
			return BuildMode.LOOK;
		}
	}

	private static void writeMode(ItemStack stack, BuildMode mode) {
		NbtCompound nbt = custom(stack);
		nbt.putString("mode", mode.name());
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	private static int readAxis(ItemStack stack) {
		return custom(stack).getInt("axis");
	}

	private static void writeAxis(ItemStack stack, int axis) {
		NbtCompound nbt = custom(stack);
		nbt.putInt("axis", axis);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	private static int readRotation(ItemStack stack) {
		return custom(stack).getInt("rot");
	}

	private static void bumpRotation(ItemStack stack) {
		NbtCompound nbt = custom(stack);
		nbt.putInt("rot", nbt.getInt("rot") + 1);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	private static NbtCompound custom(ItemStack stack) {
		NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
		return c != null ? c.copyNbt() : new NbtCompound();
	}
}
