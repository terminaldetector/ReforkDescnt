package com.terminaldetector.drmd.world.gravity;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable identities for field emitters that do not own a persistent block entity UUID. */
final class GravityFieldIds {
	private GravityFieldIds() {}

	/** Equal coordinates through a portal are separate emitters because the dimension is in the seed. */
	static UUID torch(RegistryKey<World> worldKey, BlockPos pos) {
		String seed = worldKey.getValue() + "@" + pos.asLong();
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}
}
