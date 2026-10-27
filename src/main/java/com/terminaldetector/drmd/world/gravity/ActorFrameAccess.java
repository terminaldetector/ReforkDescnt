package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.Vec3d;

/** Implemented by LivingEntity's tracker mixin. Zero means ordinary vanilla geometry. */
public interface ActorFrameAccess {
    Vec3d drmd$actorUp();
    void drmd$setActorUp(Vec3d up);
}
