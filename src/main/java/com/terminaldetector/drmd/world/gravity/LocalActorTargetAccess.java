package com.terminaldetector.drmd.world.gravity;
/** Exact native virtual hitbox query; no geometry substitute or exposed mutable state. */
public interface LocalActorTargetAccess {
    net.minecraft.util.math.Box drmd$targetHitbox();
}
