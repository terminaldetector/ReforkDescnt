package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.FieldShape;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravityFieldsTest {
	private static final RegistryKey<World> WORLD_A = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("drmd", "gravity_a"));
	private static final RegistryKey<World> WORLD_B = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("drmd", "gravity_b"));

	@AfterEach
	void clear() { GravityFields.clear(); }

	@Test
	@DisplayName("a mounted torch field only affects the front half-space")
	void frontOnlyClip() {
		GravityFields.Field field = new GravityFields.Field(UUID.randomUUID(), WORLD_A, new BlockPos(0, 0, 0),
				new Vec3d(0, -1, 0), 8, 1f, FieldShape.SPHERE, "torch", true);
		assertTrue(field.influenceAt(new Vec3d(0.5, 2.5, 0.5)) > 0, "front side must be active");
		assertEquals(0, field.influenceAt(new Vec3d(0.5, -1.5, 0.5)), 1e-9,
				"the mount side must be clipped");
	}

	@Test
	@DisplayName("fields from another dimension never enter the sample")
	void worldIsolation() {
		GravityFields.put(new GravityFields.Field(UUID.randomUUID(), WORLD_A, BlockPos.ORIGIN,
				new Vec3d(0, -1, 0), 8, 1f, FieldShape.SPHERE, "A"));
		assertNotNull(GravityFields.sample(WORLD_A, new Vec3d(0.5, 0.5, 0.5)));
		assertNull(GravityFields.sample(WORLD_B, new Vec3d(0.5, 0.5, 0.5)));
	}

	@Test
	@DisplayName("sphere influence reaches zero exactly at its radius")
	void radiusBoundary() {
		GravityFields.Field field = new GravityFields.Field(UUID.randomUUID(), WORLD_A, BlockPos.ORIGIN,
				new Vec3d(0, -1, 0), 8, 1f, FieldShape.SPHERE, "sphere");
		assertEquals(0, field.influenceAt(new Vec3d(0.5, 0.5 + 8, 0.5)), 1e-9);
	}

}
