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
		fx = f.x; fy = f.y; fz = f.z;
		setForwardUp(f, levelUp());
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
		setForwardUp(forward(), levelUp());
	}

	public float pitchDegrees() {
		return (float) (-Math.toDegrees(Math.asin(MathHelper.clamp(fy, -1, 1))));
	}

	public float yawDegrees() {
		return (float) Math.toDegrees(yawRadians());
	}

	private double yawRadians() {
		// atan2(0, 0) == 0, so a nose pointing exactly at a pole still yields a defined yaw.
		return Math.atan2(-fx, fz);
	}

	/**
	 * Right vector of a zero-roll camera at this basis' yaw — matches {@code Camera.diagonalPlane}
	 * negated, i.e. the pilot's right when bank is 0.
	 *
	 * <p>Derived from yaw alone, so unlike {@code forward × worldUp} it never degenerates when the
	 * nose points straight up or down. That is what keeps 6DoF looking continuous through the poles.
	 */
	public Vec3d levelRight() {
		return levelRightOf(forward());
	}

	/** Up vector of a zero-roll camera at this basis' yaw/pitch — matches {@code Camera.verticalPlane}. */
	public Vec3d levelUp() {
		return levelUpOf(forward());
	}

	/**
	 * Pole-safe zero-roll right vector for any heading — the reference frame flying mobs bank
	 * against, so a drone climbing straight up does not snap when its heading passes vertical.
	 */
	public static Vec3d levelRightOf(Vec3d forward) {
		double yaw = Math.atan2(-forward.x, forward.z);
		return new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
	}

	/** Pole-safe zero-roll up vector for any heading. */
	public static Vec3d levelUpOf(Vec3d forward) {
		Vec3d u = levelRightOf(forward).crossProduct(forward);
		if (u.lengthSquared() < 1e-12) return new Vec3d(0, 1, 0);
		return u.normalize();
	}

	/**
	 * Bank angle against the zero-roll camera frame, in (-180, 180].
	 *
	 * <p>Feeding this straight into a screen-space Z rotation reproduces the ship basis exactly,
	 * because (yaw, pitch, bank) is a faithful decomposition of it — including at the poles, where
	 * yaw swings fast and bank cancels the swing.
	 */
	public float bankDegrees() {
		Vec3d u = up();
		return (float) Math.toDegrees(Math.atan2(u.dotProduct(levelRight()), u.dotProduct(levelUp())));
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
