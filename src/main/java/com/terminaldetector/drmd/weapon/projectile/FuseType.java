package com.terminaldetector.drmd.weapon.projectile;

/**
 * When a projectile is allowed to go off.
 *
 * <p>The fuse is separate from the warhead: any {@link ProjectileKind} can carry any of these, which
 * is what lets one launcher fire a contact rocket, an air-burst and a seeded mine without three
 * different projectile entities.
 */
public enum FuseType {
	/** Goes off on the first thing it touches. The default for guns and rockets. */
	IMPACT,
	/**
	 * Ignores contact until it has armed, then behaves like {@link #IMPACT}.
	 *
	 * <p>Keeps heavy ordnance from detonating in the launcher's own face at point-blank range.
	 */
	DELAYED_IMPACT,
	/** Air-burst: detonates when the fuse timer runs out, wherever it happens to be. */
	TIMED,
	/** Detonates when a valid target enters its trigger radius — mines and seeker charges. */
	PROXIMITY
}
