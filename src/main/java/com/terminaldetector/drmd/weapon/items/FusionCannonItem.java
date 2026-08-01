package com.terminaldetector.drmd.weapon.items;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.registry.WeaponDef;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Fusion Cannon — the Descent charge weapon.
 *
 * <p>Unlike every other DRMD gun this one is held, not tapped: charge builds while the trigger is
 * down and the bolt scales with it. A tap is a weak, cheap shot; a full 2-second charge is the
 * heaviest single projectile in the mod and shoves the firer backwards hard enough to matter in
 * 6DoF. Holding past full does not build further — Descent's fusion tops out and then just sits
 * there humming, so the ceiling is the ceiling.
 */
public class FusionCannonItem extends DescentWeaponItem {
	/** Ticks from trigger-down to full charge. */
	public static final int CHARGE_TICKS = 40;

	/** Effectively unbounded hold time; the charge itself still tops out at CHARGE_TICKS. */
	private static final int MAX_USE_TICKS = 72000;

	private static final float BASE_DAMAGE = 45f;
	private static final float CHARGED_DAMAGE = 195f;
	private static final float BASE_COST = 25f;
	private static final float CHARGED_COST = 70f;

	public FusionCannonItem(WeaponDef def, Settings settings) {
		super(def, settings);
	}

	/**
	 * Charge 0..1 from the item's remaining-use counter.
	 *
	 * <p>{@link #getMaxUseTime} returns a very large number so the shot can be held indefinitely,
	 * so held time is {@code MAX_USE - remaining}, clamped to the charge window.
	 */
	public static float chargeOf(int remainingUseTicks) {
		int held = MathHelper.clamp(MAX_USE_TICKS - remainingUseTicks, 0, CHARGE_TICKS);
		return held / (float) CHARGE_TICKS;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		// Energy lives server-side, so only the server can refuse; the client always starts the
		// hold animation, otherwise the two sides disagree about whether the item is in use.
		if (!world.isClient) {
			// Refuse to start spinning up on an empty capacitor rather than fizzling on release.
			DescentPlayerData data = DescentPlayerData.get(user);
			if (data.getEnergy() < BASE_COST) {
				return TypedActionResult.fail(stack);
			}
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.5f, 1.6f);
		}
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BOW;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		// Stays "in use" well past full charge so the player can hold the shot.
		return MAX_USE_TICKS;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(world instanceof ServerWorld sw)) return;
		float charge = chargeOf(remainingUseTicks);
		// Off the nose, like every other muzzle — the look vector cannot point past vertical.
		Vec3d muzzle = user.getEyePos().add(WeaponCore.aimDir(user).multiply(0.8));
		int count = 1 + Math.round(charge * 5);
		sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, muzzle.x, muzzle.y, muzzle.z,
				count, 0.12 + charge * 0.2, 0.12 + charge * 0.2, 0.12 + charge * 0.2, 0.01);
		if (charge >= 1f && user.age % 10 == 0) {
			sw.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.35f, 2.0f);
		}
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (world.isClient || !(user instanceof PlayerEntity player)) return;
		float charge = chargeOf(remainingUseTicks);

		DescentPlayerData data = DescentPlayerData.get(player);
		float cost = MathHelper.lerp(charge, BASE_COST, CHARGED_COST);
		if (!consumeEnergy(data, cost)) return;

		WeaponCore.FireConfig cfg = baseCfg(player);
		cfg.dmgClass = DamageClass.EXOTIC;
		cfg.directDamage = MathHelper.lerp(charge, BASE_DAMAGE, CHARGED_DAMAGE);
		cfg.splashDamage = MathHelper.lerp(charge, 20f, 90f);
		cfg.splashRadius = MathHelper.lerp(charge, 90f, 260f);
		cfg.speed = MathHelper.lerp(charge, 3400f, 2400f);   // heavier bolt, slower
		cfg.life = 4f;
		cfg.recoil = MathHelper.lerp(charge, 40f, 320f);
		cfg.meshKind = ProjectileEntity.MESH_ORB;
		cfg.visualScale = 1.0f + charge * 1.1f;
		cfg.worldBlast = charge > 0.6f;
		cfg.pierceCount = charge >= 1f ? 1 : 0;
		cfg.colorR = 170; cfg.colorG = 90; cfg.colorB = 255;
		// A full-charge bolt arms slightly out so it cannot detonate in the firer's own cockpit.
		cfg.fuse = com.terminaldetector.drmd.weapon.projectile.FuseType.DELAYED_IMPACT;
		cfg.armSeconds = 0.1f;
		WeaponCore.fireProjectile(cfg);

		// Two calls rather than a ternary: vanilla's SoundEvents fields are not uniformly typed
		// (some are SoundEvent, some RegistryEntry<SoundEvent>), so mixing them in one conditional
		// leaves the expression with no applicable playSound overload.
		float volume = 0.6f + charge * 0.5f;
		float pitch = 1.6f - charge * 0.5f;
		if (charge > 0.75f) {
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, volume, pitch);
		} else {
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, volume, pitch);
		}
		player.getItemCooldownManager().set(this, Math.round(MathHelper.lerp(charge, 8f, 26f)));
		DescentWeaponItem.NbtWrite.lastFire(stack, world.getTime());
	}
}
