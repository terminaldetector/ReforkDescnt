package com.terminaldetector.drmd.world.portal.mirror;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Fires a {@link PortalPanelBlock} onto the first solid surface in range — a plain {@link #use} raycast
 * rather than {@code useOnBlock}, deliberately: {@code useOnBlock} only ever fires within vanilla's own
 * short built-in interaction reach (see {@code BuildToolItem}'s own "LOOK" mode, capped at a fixed 3.5
 * blocks for exactly that reason), which reads as reach, not a shot. This instead raycasts explicitly,
 * the same {@code world.raycast(new RaycastContext(...))} call {@link
 * com.terminaldetector.drmd.weapon.core.WeaponCore#hitscan} already uses for actual weapons.
 *
 * <p>Placement then reuses {@link PortalPanelBlock} unchanged: dropping the block via
 * {@code world.setBlockState} fires the same {@code onBlockAdded} → {@link ImmPtlMirrorBridge#attach}
 * → auto-link path a creative-mode hand placement already goes through, so the gun needs no pairing
 * logic of its own.
 */
public class PortalGunItem extends Item {
	private static final double RANGE = 48.0;

	public PortalGunItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient || !(world instanceof ServerWorld sw)) return TypedActionResult.success(stack);
		// No Immersive Portals check any more. The gun refused to fire without it because a placed panel
		// could not link; panels pair and carry travellers on their own now, so the only thing a missing
		// ImmPtl costs here is seeing through the panel — which is not a reason to refuse to place one.
		Vec3d start = user.getEyePos();
		Vec3d end = start.add(user.getRotationVec(1f).multiply(RANGE));
		BlockHitResult hit = world.raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
		if (hit.getType() == HitResult.Type.MISS) {
			user.sendMessage(Text.literal("§7No surface in range."), true);
			return TypedActionResult.fail(stack);
		}

		Direction facing = hit.getSide();
		BlockPos target = hit.getBlockPos().offset(facing);
		if (!world.getBlockState(target).isReplaceable()) {
			user.sendMessage(Text.literal("§7No room for a panel there."), true);
			return TypedActionResult.fail(stack);
		}

		BlockState panelState = ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING, facing);
		com.terminaldetector.drmd.diag.DiagTrace.record("portal",
				"portal gun placed a panel at " + target + " facing " + facing);
		world.setBlockState(target, panelState, Block.NOTIFY_ALL);
		sw.playSound(null, target, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.9f, 1.2f);
		return TypedActionResult.success(stack);
	}
}
