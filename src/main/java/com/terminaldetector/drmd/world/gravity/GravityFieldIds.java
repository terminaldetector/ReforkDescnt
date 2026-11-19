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
		// Vanilla packs Y into twelve bits. Preserve existing ordinary-world identities,
        // but never alias separate native cubic layers outside that representable range.
        String location = pos.getY() >= -2048 && pos.getY() <= 2047 ? Long.toString(pos.asLong())
            : "xyz:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        String seed = worldKey.getValue() + "@" + location;
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}
}
