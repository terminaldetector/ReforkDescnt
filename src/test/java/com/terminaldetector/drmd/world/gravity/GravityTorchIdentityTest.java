package com.terminaldetector.drmd.world.gravity;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GravityTorchIdentityTest {
	private static final RegistryKey<World> OVERWORLD = RegistryKey.of(
			RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));
	private static final RegistryKey<World> NETHER = RegistryKey.of(
			RegistryKeys.WORLD, Identifier.of("minecraft", "the_nether"));

	@Test
	@DisplayName("torch identity is stable but isolated by dimension")
	void dimensionIsPartOfTorchIdentity() {
		BlockPos pos = new BlockPos(12, 34, -56);
		assertEquals(GravityTorchBlock.torchId(OVERWORLD, pos), GravityTorchBlock.torchId(OVERWORLD, pos));
		assertNotEquals(GravityTorchBlock.torchId(OVERWORLD, pos), GravityTorchBlock.torchId(NETHER, pos));
	}
}
