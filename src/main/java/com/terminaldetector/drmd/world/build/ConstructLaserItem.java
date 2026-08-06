package com.terminaldetector.drmd.world.build;

import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Tiered construction laser (green → purple).
 * Construction Mode: scaffold frame → confirm build.
 * Blue+: STREAM shape = continuous hold placement.
 */
public class ConstructLaserItem extends Item {
	private static final String SHAPE_KEY = "drmd_construct_shape";
	private final ConstructLaserTier tier;

	public ConstructLaserItem(Settings settings, ConstructLaserTier tier) {
		super(settings.maxCount(1));
		this.tier = tier;
	}

	public ConstructLaserTier tier() {
		return tier;
	}

	public static int shapeIndex(ItemStack stack) {
		NbtComponent custom = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return custom.copyNbt().getInt(SHAPE_KEY);
	}

	public static ConstructShape shapeOf(ItemStack stack, ConstructLaserTier tier) {
		return ConstructShapes.shapeAt(tier, shapeIndex(stack));
	}

	public static ConstructShape cycleShape(ItemStack stack, ConstructLaserTier tier) {
		int next = ConstructShapes.nextIndex(tier, shapeIndex(stack));
		NbtComponent existing = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		NbtCompound nbt = existing.copyNbt();
		nbt.putInt(SHAPE_KEY, next);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		return ConstructShapes.shapeAt(tier, next);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(stack);

		if (!(user.getOffHandStack().getItem() instanceof BlockItem)) {
			user.sendMessage(Text.literal(tier.colorCode + tier.label
					+ " laser §7— put a block in the off-hand"), true);
			return TypedActionResult.fail(stack);
		}

		if (user instanceof ServerPlayerEntity sp && !ConstructionMode.isActive(sp.getUuid())) {
			ConstructionMode.set(sp, true);
		}

		ConstructShape shape = shapeOf(stack, tier);

		// Sneak + empty draft: cycle shape
		if (user.isSneaking() && !ConstructScaffold.hasDraft(user.getUuid())) {
			ConstructShape next = cycleShape(stack, tier);
			user.sendMessage(Text.literal(tier.colorCode + tier.label
					+ " laser §7shape: §f" + next.label), true);
			return TypedActionResult.success(stack);
		}

		// Sneak + draft: cancel frame
		if (user.isSneaking() && ConstructScaffold.hasDraft(user.getUuid())) {
			if (user instanceof ServerPlayerEntity sp) {
				ConstructScaffold.clear(sp);
				sp.sendMessage(Text.literal("§7Scaffold cancelled"), true);
			}
			return TypedActionResult.success(stack);
		}

		// Blue+ STREAM → hold for continuous
		if (tier.continuous && shape == ConstructShape.STREAM) {
			user.setCurrentHand(hand);
			return TypedActionResult.consume(stack);
		}

		// Confirm existing scaffold
		if (ConstructScaffold.hasDraft(user.getUuid()) && user instanceof ServerPlayerEntity sp) {
			return commit(world, sp, stack);
		}

		// Draft new scaffold (Construction Mode: frame → build)
		List<BlockPos> cells = ConstructShapes.resolve(world, user, tier, shape, false);
		if (cells.isEmpty()) {
			user.sendMessage(Text.literal("§7No cells for " + shape.label), true);
			return TypedActionResult.fail(stack);
		}

		boolean useFrame = ConstructionMode.isActive(user.getUuid()) && shape.usesScaffold();
		if (useFrame && user instanceof ServerPlayerEntity sp) {
			ConstructScaffold.setDraft(sp, tier, shape, cells);
			sp.sendMessage(Text.literal(tier.colorCode + "Каркас §f" + shape.label
					+ " §7(" + cells.size() + ") — click again to build · sneak cancel"), true);
			beam(world, user, cells.get(cells.size() / 2));
			return TypedActionResult.success(stack);
		}

		// Instant place (fallback / non-scaffold)
		int n = AdaptivePlacement.placeMany(world, user, cells, true);
		if (n > 0) {
			beam(world, user, cells.get(0));
			world.playSound(null, cells.get(0), SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.55f, 1.25f);
			user.sendMessage(Text.literal(tier.colorCode + "Built §f" + n + " §7blocks (" + shape.label + ")"), true);
		}
		return TypedActionResult.success(stack);
	}

	private TypedActionResult<ItemStack> commit(World world, ServerPlayerEntity sp, ItemStack stack) {
		ConstructScaffold.Draft draft = ConstructScaffold.take(sp.getUuid());
		ConstructScaffold.sync(sp);
		if (draft == null || draft.positions().isEmpty()) {
			return TypedActionResult.fail(stack);
		}
		int n = AdaptivePlacement.placeMany(world, sp, draft.positions(), true);
		if (n > 0) {
			BlockPos mid = draft.positions().get(draft.positions().size() / 2);
			beam(world, sp, mid);
			world.playSound(null, mid, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.4f, 1.4f);
			sp.sendMessage(Text.literal(tier.colorCode + "Build §f" + n + " §7/ "
					+ draft.positions().size() + " · " + draft.shape().label), true);
		} else {
			sp.sendMessage(Text.literal("§cBuild failed — no replaceable cells / no material"), true);
		}
		return TypedActionResult.success(stack);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BOW;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (world.isClient || !(user instanceof PlayerEntity player)) return;
		if (!tier.continuous) return;
		if (remainingUseTicks % 2 != 0) return;
		if (!(player.getOffHandStack().getItem() instanceof BlockItem)) return;

		ConstructShape shape = shapeOf(stack, tier);
		if (shape != ConstructShape.STREAM) return;
		if (ConstructScaffold.hasDraft(player.getUuid())) return;

		if (player instanceof ServerPlayerEntity sp && !ConstructionMode.isActive(sp.getUuid())) {
			ConstructionMode.set(sp, true);
		}

		BlockPos placed = AdaptivePlacement.placeAlongLook(world, player,
				player.isSneaking() ? 28 : 14, true);
		if (placed != null && world instanceof ServerWorld sw) {
			WeaponFx.beam(sw, player.getEyePos(), placed.toCenterPos(), tier.beamRgb, 0.7f);
			if (remainingUseTicks % 8 == 0) {
				world.playSound(null, placed, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.25f, 1.5f);
			}
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		ConstructShape shape = shapeOf(stack, tier);
		tooltip.add(Text.literal(tier.colorCode + tier.label + " §7construction laser"));
		tooltip.add(Text.literal("§7Shape: §f" + shape.label
				+ (tier.continuous ? " §8· stream from blue+" : "")));
		tooltip.add(Text.literal("§8Off-hand block · sneak cycle · frame→build in Construct Mode"));
	}

	private void beam(World world, PlayerEntity user, BlockPos target) {
		if (!(world instanceof ServerWorld sw)) return;
		WeaponFx.beam(sw, user.getEyePos(), target.toCenterPos(), tier.beamRgb, 0.9f);
	}
}
