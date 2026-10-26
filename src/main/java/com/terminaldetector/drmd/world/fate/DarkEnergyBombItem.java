package com.terminaldetector.drmd.world.fate;

import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Ending-2 key — arm only at the planetary core (deep Core band).
 * Non-obvious: most players never craft or dig this far; the reactor ending comes first.
 */
public class DarkEnergyBombItem extends Item {
	public DarkEnergyBombItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(stack);
		if (!(user instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(stack);
		return tryDetonate(sp, stack, sp.getBlockPos())
				? TypedActionResult.success(stack)
				: TypedActionResult.fail(stack);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext ctx) {
		if (ctx.getWorld().isClient) return ActionResult.SUCCESS;
		if (!(ctx.getPlayer() instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
		return tryDetonate(sp, ctx.getStack(), ctx.getBlockPos())
				? ActionResult.SUCCESS
				: ActionResult.FAIL;
	}

	private static boolean tryDetonate(ServerPlayerEntity player, ItemStack stack, BlockPos at) {
		ServerWorld world = player.getServerWorld();
		WorldFateState fate = WorldEndings.state(player.getServer());
		if (fate != null && fate.isVoid()) {
			player.sendMessage(Text.literal("§8Already nothing."), true);
			return false;
		}

		// Planetary core seam — bottom of the Nether band / dig path.
		int y = at.getY();
		boolean atCore = y <= WorldLevels.NETHER_FLOOR + WorldLevels.NETHER_FLOOR_THICKNESS + 12
				&& y >= WorldLevels.WORLD_BOTTOM
				&& world.getRegistryKey() == World.OVERWORLD;
		if (!atCore) {
			player.sendMessage(Text.literal(
					"§5The bomb stays inert. §8It wants the planetary core — deeper than the mantle, under continuous nether."), false);
			player.sendMessage(Text.literal(
					"§8The reactor is only a switch. The wound is the planet."), true);
			return false;
		}

		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		world.playSound(null, at, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.2f, 0.4f);
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
				at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
				120, 4, 4, 4, 0.05);
		world.spawnParticles(ParticleTypes.SONIC_BOOM,
				at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0, 0, 0, 0);

		WorldEndings.applyVoid(player.getServer(), player, at.toImmutable());
		return true;
	}
}
