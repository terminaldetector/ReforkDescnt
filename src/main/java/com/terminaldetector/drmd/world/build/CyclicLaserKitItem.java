package com.terminaldetector.drmd.world.build;

import com.terminaldetector.drmd.world.trap.RingDefenseStructures;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 6DoF construction kit — places a powered-rail square loop and cyclic laser carts.
 * Sneak: also embed a turret ring + shield cross around the same centre.
 */
public class CyclicLaserKitItem extends Item {
	public CyclicLaserKitItem(Settings settings) {
		super(settings.maxCount(1));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getWorld().isClient) return ActionResult.SUCCESS;
		if (!(context.getWorld() instanceof ServerWorld world)) return ActionResult.PASS;

		BlockPos hit = context.getBlockPos();
		Direction face = context.getSide();
		BlockPos center = hit.offset(face);
		int y = center.getY();
		// Prefer standing the deck on the hit face so ceiling/wall builds work in 6DoF.
		if (face == Direction.DOWN) y = hit.getY() - 1;
		else if (face != Direction.UP) y = hit.getY();

		BlockPos origin = new BlockPos(center.getX(), y, center.getZ());
		boolean full = context.getPlayer() != null && context.getPlayer().isSneaking();
		RingDefenseStructures.placeCyclicLaserLoop(world, origin, 5, y, full ? 3 : 2);
		if (full) {
			RingDefenseStructures.placeTurretRing(world, origin, 8, y, 8, true);
		}

		if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
			context.getStack().decrement(1);
		}
		if (context.getPlayer() != null) {
			context.getPlayer().sendMessage(Text.literal(full
					? "§bRing defense §f+ cyclic laser loop placed"
					: "§bCyclic laser loop §fplaced (sneak = full ring)"), true);
		}
		return ActionResult.CONSUME;
	}
}
