package com.terminaldetector.drmd.client.planet;

import com.terminaldetector.drmd.world.gen2.MacroEntry;

/**
 * One built thing, as much of it as the horizon needs to know.
 *
 * <p>Position, extent, colour and what sort of thing it is. The shape is not sent — it is rebuilt
 * from the kind at draw time ({@link PlanetSurfaceMesh}), because a mast and a dish cost two boxes
 * to describe and several kilobytes to transmit.
 */
public record HorizonLandmark(int x, int y, int z, int sizeX, int sizeY, int sizeZ,
							  int colour, MacroEntry.Kind kind) {

	/**
	 * Should this leave a silhouette at all?
	 *
	 * <p>Rifts and canyons are holes — a box where one sits would read as the opposite of what is
	 * there. The mega fauna and the flying saucer move, and a landmark that has drifted a kilometre
	 * from where it was catalogued is worse than no landmark.
	 */
	public boolean drawable() {
		return switch (kind) {
			case RIFT, CANYON, WORM, SWARM, KEEPER, UFO -> false;
			default -> true;
		};
	}

	/** Height to stand it at — its own extent, with a floor so a flat mark still shows. */
	public int height() {
		return Math.max(8, sizeY);
	}

	/** Half-width of the footprint, clamped so a catalogue oddity cannot fill the sky. */
	public int halfWidth() {
		return Math.max(4, Math.min(160, (sizeX + sizeZ) / 4));
	}
}
