package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.shield.ShieldSystem;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float drmd$shieldAbsorb(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof PlayerEntity player)) return amount;
		DescentPlayerData data = DescentPlayerData.get(player);
		if (!data.isEnabled()) return amount;
		DamageClass cls = DamageClass.KINETIC;
		String n = source.getName();
		if (n.contains("magic") || n.contains("lightning") || n.contains("thorns")) cls = DamageClass.ENERGY;
		else if (n.contains("explosion") || n.contains("fireworks")) cls = DamageClass.EXPLOSIVE;
		return ShieldSystem.absorb(data, amount, cls);
	}

	/**
	 * Foot-gravity only. 6DoF is applied in {@link PlayerEntityMixin} so creative
	 * {@code abilities.flying} cannot rewrite velocity after {@code super.travel}.
	 */
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void drmd$footTravel(Vec3d movementInput, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof PlayerEntity player)) return;
		if (player.getVehicle() instanceof PyroShipEntity) return;

		DescentPlayerData data = DescentPlayerData.get(player);
		if (data.isEnabled()) {
			// Safety net if PlayerEntity.travel was skipped — still own the tick.
			if (com.terminaldetector.drmd.flight.FlightMotion.applyTravel(player)) {
				ci.cancel();
			}
			return;
		}

		if (!FootGravitySystem.isActive(player)) return;
		FootGravitySystem.travel(player, movementInput);
		ci.cancel();
	}
}
