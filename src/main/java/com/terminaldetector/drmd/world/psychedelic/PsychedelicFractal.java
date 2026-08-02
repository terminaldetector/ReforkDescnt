package com.terminaldetector.drmd.world.psychedelic;

/**
 * Stock psychedelic fractal kinds — 18 variants in the 10–20 design range.
 * Picked once per world from the seed and baked into {@code DescentWorldState}.
 */
public enum PsychedelicFractal {
	MENGER_SPONGE("Menger Sponge"),
	SIERPINSKI_TETRA("Sierpinski Tetra"),
	MANDELBULB_SHELL("Mandelbulb Shell"),
	JULIA_SLICE("Julia Slice"),
	APOLLONIAN_SPHERES("Apollonian Spheres"),
	GYROID("Gyroid Lattice"),
	SPIRAL_GALAXY("Spiral Galaxy"),
	TORUS_KNOT("Torus Knot"),
	KOCH_STAR("Koch Star"),
	HILBERT_TUBE("Hilbert Tube"),
	DRAGON_RIBBON("Dragon Ribbon"),
	LORENZ_ATTRACTOR("Lorenz Attractor"),
	FIBONACCI_SUNFLOWER("Fibonacci Sunflower"),
	CANTOR_DUST("Cantor Dust"),
	PLASMA_NEBULA("Plasma Nebula"),
	FLOWER_OF_LIFE("Flower of Life"),
	HYPERBOLIC_LATTICE("Hyperbolic Lattice"),
	QUATERNION_SLICE("Quaternion Slice");

	public final String label;

	PsychedelicFractal(String label) {
		this.label = label;
	}

	public static PsychedelicFractal fromIndex(int i) {
		PsychedelicFractal[] all = values();
		return all[Math.floorMod(i, all.length)];
	}

	public static int count() {
		return values().length;
	}
}
