package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime catalogue of gravity fields — generators, torches, station zones.
 * Queried each flight tick to blend local UP / gravity strength.
 */
public final class GravityFields {
	public record Field(
		UUID id,
		BlockPos origin,
		Vec3d downDir,
		double radius,
		float power,
		FieldShape shape,
		String label
	) {
		public double influenceAt(Vec3d pos) {
			Vec3d rel = pos.subtract(Vec3d.ofCenter(origin));
			return switch (shape) {
				case SPHERE -> {
					double d = rel.length();
					yield d > radius ? 0 : (1.0 - d / radius) * power;
				}
				case CYLINDER -> {
					Vec3d axis = downDir.normalize();
					double along = rel.dotProduct(axis);
					Vec3d radial = rel.subtract(axis.multiply(along));
					double rd = radial.length();
					yield (Math.abs(along) > radius || rd > radius * 0.55) ? 0
							: (1.0 - rd / (radius * 0.55)) * power;
				}
				case PLANE -> {
					double dist = Math.abs(rel.dotProduct(downDir.normalize()));
					double planar = rel.subtract(downDir.normalize().multiply(rel.dotProduct(downDir.normalize()))).length();
					yield (dist > 3.0 || planar > radius) ? 0 : (1.0 - planar / radius) * power;
				}
			};
		}
	}

	private static final Map<UUID, Field> FIELDS = new ConcurrentHashMap<>();

	private GravityFields() {}

	public static void put(Field field) {
		FIELDS.put(field.id(), field);
	}

	public static void remove(UUID id) {
		FIELDS.remove(id);
	}

	public static void clear() {
		FIELDS.clear();
	}

	public static List<Field> all() {
		return new ArrayList<>(FIELDS.values());
	}

	/**
	 * Blended sample: weighted average of down directions + max power.
	 * Returns null if no field affects the point.
	 */
	public static Sample sample(World world, Vec3d pos) {
		Vec3d sumDir = Vec3d.ZERO;
		double weight = 0;
		float maxPower = 0;
		String dominant = null;
		for (Field f : FIELDS.values()) {
			if (world != null && world.getRegistryKey() == null) {
				// keep signature open for future per-world filtering
			}
			double w = f.influenceAt(pos);
			if (w <= 1e-4) continue;
			sumDir = sumDir.add(f.downDir().normalize().multiply(w));
			weight += w;
			if (f.power() >= maxPower) {
				maxPower = f.power();
				dominant = f.label();
			}
		}
		if (weight < 1e-4) return null;
		return new Sample(sumDir.normalize(), (float) Math.min(2.0, weight), maxPower, dominant);
	}

	public record Sample(Vec3d downDir, float strength, float maxPower, String label) {
		public Vec3d upDir() {
			return downDir.negate();
		}
	}
}
