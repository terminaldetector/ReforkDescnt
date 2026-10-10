package com.terminaldetector.drmd.client.portal;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Camera-relative view transforms. Preserve outer roll and the mirror's negative determinant. */
public final class PortalViewMatrix {
    private PortalViewMatrix() {}
    public static Matrix4f through(Matrix4f outer, PortalTransform.Quat turn) {
        return new Matrix4f(outer).rotate(new Quaternionf((float)-turn.x(), (float)-turn.y(),
            (float)-turn.z(), (float)turn.w()).normalize());
    }
    public static Matrix4f reflected(Matrix4f outer, PortalTransform.Vec3 normal) {
        var n=normal.normalized();
        return new Matrix4f(outer).mul(new Matrix4f().reflection((float)n.x(),(float)n.y(),(float)n.z(),0));
    }
}
