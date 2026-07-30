package com.terminaldetector.drmd.flight;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Descent ship basis — local pitch / yaw / roll around Right / Up / Forward
 * (port of d6_client.lua Ang:RotateAroundAxis).
 */
public final class ShipAttitude {
	public double fx = 0, fy = 0, fz = 1;
	public double ux = 0, uy = 1, uz = 0;

	public ShipAttitude() {}

	public ShipAttitude copy() {
		ShipAttitude a = new ShipAttitude();
		a.fx = fx; a.fy = fy; a.fz = fz;
		a.ux = ux; a.uy = uy; a.uz = uz;
		return a;
	}

	public Vec3d forward() { return new Vec3d(fx, fy, fz); }
	public Vec3d up() { return new Vec3d(ux, uy, uz); }
	public Vec3d right() { return forward().crossProduct(up()).normalize(); }

	public void setForwardUp(Vec3d f, Vec3d u) {
		f = f.normalize();
		u = u.subtract(f.multiply(u.dotProduct(f)));
		if (u.lengthSquared() < 1e-8) u = new Vec3d(0, 1, 0);
		u = u.normalize();
		fx = f.x; fy = f.y; fz = f.z;
		ux = u.x; uy = u.y; uz = u.z;
	}

	public void fromLook(Vec3d look) {
		Vec3d f = look.normalize();
		Vec3d r = f.crossProduct(new Vec3d(0, 1, 0));
		if (r.lengthSquared() < 1e-8) r = new Vec3d(1, 0, 0);
		r = r.normalize();
		setForwardUp(f, r.crossProduct(f).normalize());
	}

	/** Pitch: rotate around local Right (nose up = negative Minecraft pitch). */
	public void pitchLocal(double deg) {
		Vec3d r = right();
		setForwardUp(rotate(forward(), r, deg), rotate(up(), r, deg));
	}

	/** Yaw: rotate around local Up. */
	public void yawLocal(double deg) {
		Vec3d u = up();
		setForwardUp(rotate(forward(), u, deg), u);
	}

	/** Barrel roll: rotate around local Forward. */
	public void rollLocal(double deg) {
		Vec3d f = forward();
		setForwardUp(f, rotate(up(), f, deg));
	}

	/** Level bank while keeping nose direction. */
	public void levelRoll() {
		Vec3d f = forward();
		Vec3d r = f.crossProduct(new Vec3d(0, 1, 0));
		if (r.lengthSquared() < 1e-8) r = new Vec3d(1, 0, 0);
		r = r.normalize();
		setForwardUp(f, r.crossProduct(f).normalize());
	}

	public float pitchDegrees() {
		return (float) (-Math.toDegrees(Math.asin(MathHelper.clamp(fy, -1, 1))));
	}

	public float yawDegrees() {
		return (float) Math.toDegrees(Math.atan2(-fx, fz));
	}

	/** Bank angle vs world-level horizon. */
	public float bankDegrees() {
		Vec3d f = forward();
		Vec3d u = up();
		Vec3d worldUp = Math.abs(f.y) > 0.95 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0);
		Vec3d levelRight = f.crossProduct(worldUp);
		if (levelRight.lengthSquared() < 1e-8) return 0;
		levelRight = levelRight.normalize();
		Vec3d levelUp = levelRight.crossProduct(f).normalize();
		Vec3d r = right();
		return (float) Math.toDegrees(Math.atan2(u.dotProduct(levelRight), u.dotProduct(levelUp)));
	}

	public static Vec3d rotate(Vec3d v, Vec3d axis, double deg) {
		Vec3d k = axis.normalize();
		double rad = Math.toRadians(deg);
		double c = Math.cos(rad);
		double s = Math.sin(rad);
		Vec3d kxv = k.crossProduct(v);
		double kdv = k.dotProduct(v);
		return v.multiply(c).add(kxv.multiply(s)).add(k.multiply(kdv * (1 - c)));
	}
}
