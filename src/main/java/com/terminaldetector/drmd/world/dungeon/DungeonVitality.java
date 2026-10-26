package com.terminaldetector.drmd.world.dungeon;

/**
 * Technogenic dungeon vitality classes.
 *
 * <p>Hostile 6DoF mobs are deferred — living sections use turrets/traps only for now.
 * Complex SEMI_ALIVE sites can look dead on the shell and hide a living reactor deeper in.
 */
public enum DungeonVitality {
	/** Cold ruins — no active reactor, sparse hazards. */
	DEAD("Dead", false, false),
	/** Partially powered — some traps, dormant core that can wake. */
	SEMI_ALIVE("Semi-alive", true, true),
	/** Active installation — reactor core + living defense sections. */
	ALIVE("Alive", true, false);

	public final String label;
	/** Places {@code unstable_reactor} + can start a Descent-style breach fight. */
	public final boolean hasLivingReactor;
	/** Outer shell reads as dead (cracks, no ring AA); deeper systems stay alive. */
	public final boolean disguiseAsDead;

	DungeonVitality(String label, boolean hasLivingReactor, boolean disguiseAsDead) {
		this.label = label;
		this.hasLivingReactor = hasLivingReactor;
		this.disguiseAsDead = disguiseAsDead;
	}
}
