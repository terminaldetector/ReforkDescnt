package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;
import java.util.Objects;

/**
 * Runtime catalogue of gravity fields — generators, torches, station zones.
 * Queried each flight tick to blend local UP / gravity strength.
 */
public final class GravityFields {
	public record Field(
		UUID id,
		net.minecraft.registry.RegistryKey<World> worldKey,
		BlockPos origin,
		Vec3d downDir,
		double radius,
		float power,
		FieldShape shape,
		String label,
		/**
		 * Clip the field to the side the emitter faces.
		 *
		 * <p>A torch bolted to a wall should not flip somebody walking on the floor of the room
		 * behind that wall — the pull has to stop at the surface it is mounted on.
		 */
		boolean frontOnly
	) {
		public Field {
			Objects.requireNonNull(id); Objects.requireNonNull(origin);
			Objects.requireNonNull(downDir); Objects.requireNonNull(shape);
			if(!finite(downDir) || downDir.lengthSquared()<1e-12 || !Double.isFinite(downDir.lengthSquared())
					|| !Double.isFinite(radius) || radius<=0 || !Float.isFinite(power) || power<0)
				throw new IllegalArgumentException("Invalid gravity field");
			origin=origin.toImmutable();
		}
		public Field(UUID id, net.minecraft.registry.RegistryKey<World> worldKey, BlockPos origin,
					 Vec3d downDir, double radius, float power, FieldShape shape, String label) {
			this(id, worldKey, origin, downDir, radius, power, shape, label, false);
		}

		public double influenceAt(Vec3d pos) {
			Vec3d rel = pos.subtract(Vec3d.ofCenter(origin));
			if (frontOnly && rel.dotProduct(downDir.normalize()) > 0.5) return 0;
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

	private static final GravityFieldIndex FIELDS = new GravityFieldIndex();

	private GravityFields() {}

	public static void put(Field field) {
		FIELDS.put(field);
	}

	public static void remove(UUID id) {
		FIELDS.remove(id);
	}

	public static void clear() {
		FIELDS.clear();
	}

	public static List<Field> all() {
		return new java.util.ArrayList<>(FIELDS.all());
	}
	public static boolean hasFields(net.minecraft.registry.RegistryKey<World> dimension) {
		return FIELDS.hasFields(dimension);
	}

	/**
	 * Blended sample: weighted average of down directions + max power.
	 * Returns null if no field affects the point.
	 */
	public static Sample sample(World world, Vec3d pos) {
		return sample(world != null ? world.getRegistryKey() : null, pos);
	}

	/** Key-only variant used by diagnostics and pure tests; keeps dimension filtering explicit. */
	public static Sample sample(net.minecraft.registry.RegistryKey<World> key, Vec3d pos) {
		if(pos==null || !finite(pos))return null;
		Vec3d sumDir = Vec3d.ZERO;
		double weight = 0;
		float maxPower = 0;
		double dominantWeight = -1;
		UUID dominantId = null;
		Vec3d dominantDir = null;
		String dominant = null;
		for (Field f : FIELDS.candidates(key,pos)) {
			// Fields live in one map but belong to one world each: without this a torch bolted to a
			// wall in the Nether would flip a player's "down" while they stood in the Overworld.
			if (key != null && f.worldKey() != null && !f.worldKey().equals(key)) continue;
			double w = f.influenceAt(pos);
			if (w <= 1e-4) continue;
			sumDir = sumDir.add(f.downDir().normalize().multiply(w));
			weight += w;
			maxPower = Math.max(maxPower, f.power());
			// Pick the strongest influence at this point and use UUID as a deterministic tie-break,
			// so overlapping equal fields do
			// not make labels and fallback direction flicker from tick to tick.
			if (w > dominantWeight || (Math.abs(w - dominantWeight) < 1e-9
					&& (dominantId == null || f.id().compareTo(dominantId) < 0))) {
				dominantWeight = w;
				dominantId = f.id();
				dominantDir = f.downDir().normalize();
				dominant = f.label();
			}
		}
		if (weight < 1e-4) return null;
		// Equal opposing fields can cancel exactly. A zero gravity direction poisons every later
		// normalization and used to leave entities in an undefined orientation. In that singular
		// point, keep the deterministic dominant field's direction instead.
		Vec3d down = sumDir.lengthSquared() > 1e-10 ? sumDir.normalize() : dominantDir;
		if (down == null || down.lengthSquared() < 1e-10) return null;
		return new Sample(down, (float) Math.min(2.0, weight), maxPower, dominant, dominantId);
	}
	private static boolean finite(Vec3d point) {
		return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
	}

	public record Sample(Vec3d downDir, float strength, float maxPower, String label, UUID sourceId) {
        public Sample(Vec3d downDir,float strength,float maxPower,String label) {
            this(downDir,strength,maxPower,label,null);
        }
		public Vec3d upDir() {
			return downDir.negate();
		}
	}
}
