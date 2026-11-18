package com.terminaldetector.drmd.world.portal.gun;

import net.minecraft.util.math.Vec3d;

/** Restores only velocity lost in the crossing's own collision step, retaining subsequent forces. */
public interface PortalMotionAccess {
    Vec3d drmd$portalVelocity(long tick);
    /** Genuine completed server-player movement only; null after an external discontinuity. */
    default Vec3d drmd$portalMovementStart(long tick){return null;}
}
