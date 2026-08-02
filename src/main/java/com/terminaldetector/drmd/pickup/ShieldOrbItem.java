package com.terminaldetector.drmd.pickup;

import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Descent-style shield powerup — restores shield pool on use / auto-pickup. */
public class ShieldOrbItem extends Item {
	public static final float RESTORE = 25f;

	public ShieldOrbItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(stack);
		DescentPlayerData data = DescentPlayerData.get(user);
		if (data.getShield() >= data.getShieldMax()) return TypedActionResult.fail(stack);
		data.setShield(Math.min(data.getShieldMax(), data.getShield() + RESTORE));
		if (!user.getAbilities().creativeMode) stack.decrement(1);
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.45f, 1.6f);
		return TypedActionResult.success(stack);
	}

	public static void apply(PlayerEntity user, float amount) {
		DescentPlayerData data = DescentPlayerData.get(user);
		data.setShield(Math.min(data.getShieldMax(), data.getShield() + amount));
	}
}
