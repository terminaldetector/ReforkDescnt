package com.terminaldetector.drmd.world.end;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.fire.FireSystem;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.gen2.MacroWorld;
import com.terminaldetector.drmd.world.smoke.SmokeSystem;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * End mega-reactor core — dragon-analogue phases:
 * SHIELDED (invulnerable while End crystals alive) → EXPOSED → CRITICAL → DEFEATED.
 */
public class EndReactorBossEntity extends HostileEntity {
	private final ServerBossBar bossBar = new ServerBossBar(
			Text.literal("Giga-Reactor Core"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);

	private UUID macroId;
	private Vec3d anchor = Vec3d.ZERO;
	private float orbit;
	private int beamCd;
	private int pulseCd;

	public EndReactorBossEntity(EntityType<? extends EndReactorBossEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 800)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.15)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 128)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 22)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.2)
				.add(EntityAttributes.GENERIC_ARMOR, 12);
	}

	public void setAnchor(Vec3d a) { this.anchor = a; }

	@Override
	protected void initGoals() {}

	@Override
	public void tick() {
		super.tick();
		setNoGravity(true);
		if (!(getWorld() instanceof ServerWorld sw)) return;

		if (anchor == Vec3d.ZERO) anchor = getPos();
		EndReactorState state = EndReactorState.get(sw);
		int crystals = EndReactorSession.countShieldCrystals(sw);

		if (state.getPhase() != EndReactorState.Phase.DEFEATED) {
			if (crystals > 0) {
				if (state.getPhase() != EndReactorState.Phase.SHIELDED) state.setPhase(EndReactorState.Phase.SHIELDED);
			} else if (getHealth() / getMaxHealth() < 0.35f) {
				state.setPhase(EndReactorState.Phase.CRITICAL);
			} else {
				state.setPhase(EndReactorState.Phase.EXPOSED);
			}
		}

		boolean shielded = crystals > 0;
		bossBar.setName(Text.literal(shielded
				? "Giga-Reactor · SHIELD (" + crystals + " crystals)"
				: (state.getPhase() == EndReactorState.Phase.CRITICAL
				? "Giga-Reactor · CRITICAL"
				: "Giga-Reactor · CORE EXPOSED")));
		bossBar.setPercent(MathHelper.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		bossBar.setColor(shielded ? BossBar.Color.BLUE
				: (state.getPhase() == EndReactorState.Phase.CRITICAL ? BossBar.Color.RED : BossBar.Color.PURPLE));

		orbit += shielded ? 0.008f : 0.02f;
		double r = shielded ? 4.0 : 7.0;
		Vec3d target = anchor.add(Math.cos(orbit) * r, Math.sin(orbit * 0.7) * 2.5, Math.sin(orbit) * r);
		setVelocity(target.subtract(getPos()).multiply(0.08));
		velocityModified = true;

		if (beamCd > 0) beamCd--;
		if (pulseCd > 0) pulseCd--;

		PlayerEntity near = sw.getClosestPlayer(this, 64);
		if (near != null) {
			setTarget(near);
			lookAt(near);
			if (!shielded && beamCd <= 0 && squaredDistanceTo(near) < 48 * 48) {
				fireBeam(sw, near);
				beamCd = state.getPhase() == EndReactorState.Phase.CRITICAL ? 18 : 32;
			}
			if (!shielded && pulseCd <= 0) {
				pulse(sw);
				pulseCd = state.getPhase() == EndReactorState.Phase.CRITICAL ? 60 : 100;
			}
		}

		if (age % 20 == 0) {
			sw.spawnParticles(shielded ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME,
					getX(), getY() + 1.5, getZ(), shielded ? 8 : 16, 1.2, 1.2, 1.2, 0.02);
		}

		if (macroId == null) macroId = UUID.randomUUID();
		MacroWorld.put(new MacroEntry(macroId, MacroEntry.Kind.KEEPER, WorldRules.Layer.END_SPACE,
				getBlockPos(), 32, 28, 32, 0xAA44FF, "End Giga-Reactor"));

		for (ServerPlayerEntity p : bossBar.getPlayers().toArray(new ServerPlayerEntity[0])) {
			if (!p.isAlive() || p.getWorld() != sw || squaredDistanceTo(p) > 96 * 96) bossBar.removePlayer(p);
		}
		for (ServerPlayerEntity p : sw.getPlayers()) {
			if (squaredDistanceTo(p) < 80 * 80) bossBar.addPlayer(p);
		}
	}

	private void lookAt(LivingEntity e) {
		Vec3d look = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(getPos());
		if (look.lengthSquared() < 1e-4) return;
		setYaw((float) (MathHelper.atan2(-look.x, look.z) * (180.0 / Math.PI)));
		setPitch((float) (MathHelper.atan2(-look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * (180.0 / Math.PI)));
	}

	private void fireBeam(ServerWorld sw, LivingEntity target) {
		Vec3d from = getPos().add(0, 1.5, 0);
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		WeaponFx.beamExotic(sw, from, to);
		WeaponCore.directDamage(this, target, 14f, DamageClass.ENERGY);
		sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.2f, 0.6f);
		scarPlanetFromOrbit(sw, target, 1);
	}

	private void pulse(ServerWorld sw) {
		WeaponFx.explode(this, sw, getPos(), 40f, 6f, DamageClass.EXPLOSIVE, false);
		FireSystem.igniteBlast(sw, getBlockPos(), 6, 4);
		SmokeSystem.emit(getPos(), SmokeSystem.Source.REACTOR, 3f, 0.7f, 80);
		sw.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.5f);
		scarPlanetFromOrbit(sw, getTarget() != null ? getTarget() : this, 2);
	}

	/**
	 * Reactor fire paints a scar on the Overworld planet map at the pilot's last surface focus —
	 * visible from End orbit and applied as terrain when that chunk is later loaded.
	 */
	private void scarPlanetFromOrbit(ServerWorld sw, LivingEntity focus, int intensity) {
		ServerWorld ow = sw.getServer().getOverworld();
		if (ow == null) return;
		int x = focus.getBlockX();
		int z = focus.getBlockZ();
		if (focus instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
			var data = com.terminaldetector.drmd.DescentPlayerData.get(sp);
			x = data.getLastOverworldX();
			z = data.getLastOverworldZ();
		}
		// Spread a few cells so the orbital map reads a burn zone.
		var map = com.terminaldetector.drmd.world.llod.planet.PlanetMapState.get(ow);
		int cx = com.terminaldetector.drmd.world.llod.planet.PlanetCell.cellOf(x);
		int cz = com.terminaldetector.drmd.world.llod.planet.PlanetCell.cellOf(z);
		map.scar(cx, cz, intensity);
		map.scar(cx + 1, cz, intensity);
		map.scar(cx, cz - 1, intensity);
		if (intensity > 1) {
			map.scar(cx - 1, cz + 1, intensity);
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!(getWorld() instanceof ServerWorld sw)) return false;
		if (EndReactorSession.countShieldCrystals(sw) > 0) {
			if (age % 10 == 0) {
				sw.spawnParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1, getZ(), 12, 1, 1, 1, 0.1);
			}
			return false; // shield up — dragon crystal analogue
		}
		return super.damage(source, amount);
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!(getWorld() instanceof ServerWorld sw)) return;
		EndReactorState state = EndReactorState.get(sw);
		state.setPhase(EndReactorState.Phase.DEFEATED);
		WeaponFx.explode(this, sw, getPos(), 120f, 10f, DamageClass.EXPLOSIVE, true);
		SmokeSystem.emitExplosion(getPos(), 6f);
		Vec3d core = anchor.equals(Vec3d.ZERO) ? getPos() : anchor;
		BlockPos pad = BlockPos.ofFloored(core.x, core.y - 12.0, core.z);
		EndReactorSession.placeExitGateways(sw, pad);
		// Final orbital/End fight: station break + asteroid rain on the planet map
		com.terminaldetector.drmd.world.dungeon.ReactorAftermath.onGigaReactorDestroyed(sw, pad);
		boolean inEnd = sw.getRegistryKey() == net.minecraft.world.World.END;
		String tail = inEnd
				? "station breaking up — debris falling planetward. Exit gateways online."
				: "landing pad secured; planet scars inbound.";
		for (ServerPlayerEntity p : sw.getPlayers()) {
			p.sendMessage(Text.literal("§dGiga-Reactor destroyed §7— " + tail), false);
			p.sendMessage(Text.literal(
					"§8Enable §ffall aftermath§8 in DRMD settings to corkscrew down and inspect impacts."), false);
		}
		bossBar.clearPlayers();
	}

	@Override
	public void remove(RemovalReason reason) {
		bossBar.clearPlayers();
		if (macroId != null) MacroWorld.remove(macroId);
		super.remove(reason);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putDouble("ax", anchor.x);
		nbt.putDouble("ay", anchor.y);
		nbt.putDouble("az", anchor.z);
		if (macroId != null) nbt.putUuid("macro", macroId);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		anchor = new Vec3d(nbt.getDouble("ax"), nbt.getDouble("ay"), nbt.getDouble("az"));
		if (nbt.containsUuid("macro")) macroId = nbt.getUuid("macro");
	}

	@Override
	public boolean isFireImmune() { return true; }

	@Override
	public boolean cannotDespawn() { return true; }
}
