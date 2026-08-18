package com.terminaldetector.drmd.world.portal.mirror;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;

/**
 * Implemented by blocks a projectile should ricochet off of instead of terminating at.
 *
 * <p>A marker interface rather than a block tag: this codebase has no gameplay-logic block-tag
 * precedent anywhere (its only real tags are vanilla mining-tool tiers) — {@code CarvedBlock}'s own
 * {@code instanceof CarvedBlock} checks are the established idiom for "is this block special," and
 * this continues it.
 */
public interface ReflectiveBlock {
	Vec3d getReflectionNormal(BlockState state);
}
