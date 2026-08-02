package com.terminaldetector.drmd.world.enclave;

import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

/**
 * Short procedural lines — atmosphere + intel, no cutscenes.
 */
public final class EnclaveDialogue {
	private EnclaveDialogue() {}

	public static Text line(EnclaveSite site, long tick) {
		Random rng = Random.create(site.seed ^ (tick / 2400L) ^ 0xD1A6L);
		return Text.translatable(pickKey(site, rng));
	}

	private static String pickKey(EnclaveSite site, Random rng) {
		String[] pool = switch (site.origin) {
			case ENGINEERS -> new String[]{
					"dialogue.drmd.engineer.ring",
					"dialogue.drmd.engineer.reactor",
					"dialogue.drmd.engineer.suit",
					"dialogue.drmd.engineer.drone"
			};
			case CULTISTS -> new String[]{
					"dialogue.drmd.cult.archons",
					"dialogue.drmd.cult.order",
					"dialogue.drmd.cult.pulse",
					"dialogue.drmd.cult.offer"
			};
			case MILITARY -> new String[]{
					"dialogue.drmd.military.tripod",
					"dialogue.drmd.military.perimeter",
					"dialogue.drmd.military.ammo",
					"dialogue.drmd.military.scout"
			};
			case MINERS -> new String[]{
					"dialogue.drmd.miner.shaft",
					"dialogue.drmd.miner.mantle",
					"dialogue.drmd.miner.ore",
					"dialogue.drmd.miner.collapse"
			};
			case SCAVENGERS -> new String[]{
					"dialogue.drmd.scav.junk",
					"dialogue.drmd.scav.trade",
					"dialogue.drmd.scav.raid",
					"dialogue.drmd.scav.guild"
			};
		};

		if (site.attitude == MachineAttitude.HATE && rng.nextFloat() < 0.35f) {
			return "dialogue.drmd.attitude.hate";
		}
		if (site.attitude == MachineAttitude.WORSHIP && rng.nextFloat() < 0.35f) {
			return "dialogue.drmd.attitude.worship";
		}
		if (site.resourcePressure <= 1 && rng.nextFloat() < 0.25f) {
			return "dialogue.drmd.need.scarce";
		}
		return pool[rng.nextInt(pool.length)];
	}
}
