package com.terminaldetector.drmd.client.portal;

import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Exact pose of the real camera borrowed by a nested mirror/portal render. */
public final class BorrowedCameraState implements AutoCloseable {
    private final Camera camera;
    private final CameraAccessor access;
    private final Vec3d position;
    private final float yaw,pitch;
    private final Quaternionf rotation;
    private final Vector3f forward,up,left;

    public BorrowedCameraState(Camera camera,CameraAccessor access) {
        this.camera=camera;this.access=access;
        position=camera.getPos();yaw=camera.getYaw();pitch=camera.getPitch();
        rotation=new Quaternionf(camera.getRotation());
        forward=new Vector3f(camera.getHorizontalPlane());
        up=new Vector3f(camera.getVerticalPlane());
        left=new Vector3f(camera.getDiagonalPlane());
    }

    @Override public void close() {
        access.drmd$invokeSetPos(position);
        access.drmd$invokeSetRotation(yaw,pitch);
        // setRotation reconstructs a yaw/pitch-only quaternion and all three basis vectors.
        // Boarding/local-frame cameras may have an additional bank; restore their exact values.
        camera.getRotation().set(rotation);
        camera.getHorizontalPlane().set(forward);
        camera.getVerticalPlane().set(up);
        camera.getDiagonalPlane().set(left);
    }
}
