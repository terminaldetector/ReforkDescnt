package com.terminaldetector.drmd.world.smoke;

import com.terminaldetector.drmd.world.atmosphere.AtmosphereBand;
import com.terminaldetector.drmd.world.llod.LlodLevel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Smoke — volumetric clouds for nav/combat, with LLOD draw budgets.
 *
 * LLOD0 far columns → LLOD1 large puffs → LLOD2 local particle sim near player.
 */
public final class SmokeSystem {
	public enum Source {
		TNT, FIRE, ENGINE, REACTOR, DAMAGE, VOLCANO, INDUSTRIAL, BOMB_TRAIL
	}

	public static final class Cloud {
		public final UUID id = UUID.randomUUID();
		public Vec3d pos;
		public Vec3d vel;
		public float radius;
		public float density;
		public int life;
		public final Source source;
		public final int colorRgb;

		Cloud(Vec3d pos, Vec3d vel, float radius, float density, int life, Source source, int color) {
			this.pos = pos;
			this.vel = vel;
			this.radius = radius;
			this.density = density;
			this.life = life;
			this.source = source;
			this.colorRgb = color;
		}
	}

	private static final Map<UUID, Cloud> CLOUDS = new ConcurrentHashMap<>();

	private SmokeSystem() {}

	public static void clear() {
		CLOUDS.clear();
	}

	public static void emit(Vec3d pos, Source source, float radius, float density, int lifeTicks) {
		AtmosphereBand band = AtmosphereBand.at(pos.y);
		int life = (int) (lifeTicks * band.smokeLifeScale());
		Vec3d vel = switch (source) {
			case BOMB_TRAIL -> new Vec3d(0, 0.02, 0);
			case ENGINE -> new Vec3d(0, 0.05, 0);
			case VOLCANO, REACTOR -> new Vec3d(0, 0.12, 0);
			default -> new Vec3d(
					(Math.random() - 0.5) * 0.04,
					0.06 + Math.random() * 0.06,
					(Math.random() - 0.5) * 0.04);
		};
		int color = switch (source) {
			case FIRE, BOMB_TRAIL -> 0x554433;
			case REACTOR -> 0x336655;
			case ENGINE -> 0x888888;
			case VOLCANO -> 0x222222;
			default -> 0x666666;
		};
		Cloud c = new Cloud(pos, vel, radius, density, life, source, color);
		CLOUDS.put(c.id, c);
		// Cap total clouds for performance
		if (CLOUDS.size() > 256) {
			Iterator<UUID> it = CLOUDS.keySet().iterator();
			if (it.hasNext()) CLOUDS.remove(it.next());
		}
	}

	public static void emitExplosion(Vec3d pos, float power) {
		emit(pos, Source.TNT, 2f + power, 0.8f, 80 + (int) (power * 20));
		emit(pos.add(0, 1, 0), Source.FIRE, 1.5f + power * 0.5f, 0.6f, 60);
	}

	public static void tick() {
		List<UUID> dead = new ArrayList<>();
		for (Cloud c : CLOUDS.values()) {
			c.life--;
			if (c.life <= 0 || c.density < 0.05f) {
				dead.add(c.id);
				continue;
			}
			AtmosphereBand band = AtmosphereBand.at(c.pos.y);
			// Drift + buoyant rise; near-space → spherical expansion
			c.pos = c.pos.add(c.vel);
			if (band == AtmosphereBand.NEAR_SPACE) {
				c.radius += 0.04f;
				c.vel = c.vel.multiply(0.98).add(
						(Math.random() - 0.5) * 0.01,
						(Math.random() - 0.5) * 0.01,
						(Math.random() - 0.5) * 0.01);
			} else {
				c.radius += 0.02f * (1.2f - band.airDrag);
				c.vel = c.vel.multiply(0.96f * band.airDrag + 0.02);
				c.vel = c.vel.add(0, 0.01 * (1.1 - band.airDrag), 0);
			}
			c.density *= 0.992f;
		}
		dead.forEach(CLOUDS::remove);
	}

	public static List<Cloud> all() {
		return new ArrayList<>(CLOUDS.values());
	}

	public static List<Cloud> visibleNear(Vec3d viewer, int max) {
		List<Cloud> list = all();
		list.sort((a, b) -> Double.compare(a.pos.squaredDistanceTo(viewer), b.pos.squaredDistanceTo(viewer)));
		if (list.size() > max) return list.subList(0, max);
		return list;
	}

	/** Which smoke LLOD band to draw this cloud at. */
	public static LlodLevel drawBand(Cloud c, Vec3d viewer) {
		double d = Math.sqrt(c.pos.squaredDistanceTo(viewer));
		if (d < 48) return LlodLevel.CHUNK; // local particles handled separately
		if (d < 160) return LlodLevel.LLOD2;
		if (d < 512) return LlodLevel.LLOD1;
		return LlodLevel.LLOD0;
	}

	public static float obscurityAt(Vec3d eye) {
		float sum = 0;
		for (Cloud c : CLOUDS.values()) {
			double d = c.pos.distanceTo(eye);
			if (d < c.radius * 2) {
				sum += c.density * (1f - (float) (d / (c.radius * 2)));
			}
		}
		return Math.min(0.85f, sum);
	}
}
