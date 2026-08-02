package com.terminaldetector.drmd.pickup;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergySystem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Descent-style energy powerup. */
public class EnergyOrbItem extends Item {
	public static final float RESTORE = 30f;

	public EnergyOrbItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) return TypedActionResult.success(stack);
		DescentPlayerData data = DescentPlayerData.get(user);
		if (data.getEnergy() >= data.getEnergyMax()) return TypedActionResult.fail(stack);
		EnergySystem.add(data, RESTORE);
		if (!user.getAbilities().creativeMode) stack.decrement(1);
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.4f, 1.8f);
		return TypedActionResult.success(stack);
	}
}
