package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PortalViewMatrixTest {
    @Test void portalKeepsRolledCameraCoordinates() {
        Matrix4f outer=new Matrix4f().rotateZ(.7f).rotateX(.4f);
        var turn=PortalTransform.cameraRotation(new Vec3(0,0,1),new Vec3(1,0,0));
        Vec3 point=new Vec3(1,2,3), turned=turn.rotate(point);
        Vector3f expected=outer.transformDirection(new Vector3f(1,2,3));
        Vector3f actual=PortalViewMatrix.through(outer,turn).transformDirection(new Vector3f((float)turned.x(),(float)turned.y(),(float)turned.z()));
        assertEquals(0,actual.distance(expected),1e-5);
    }
    @Test void mirrorChangesHandednessAndKeepsRoll() {
        Matrix4f outer=new Matrix4f().rotateZ(.8f).rotateY(.3f);
        Vec3 normal=new Vec3(1,2,3).normalized(), point=new Vec3(3,1,2);
        Vec3 reflected=PortalTransform.reflectVector(point,normal);
        Matrix4f view=PortalViewMatrix.reflected(outer,normal);
        assertEquals(-1,view.determinant(),1e-5);
        Vector3f expected=outer.transformDirection(new Vector3f(3,1,2));
        Vector3f actual=view.transformDirection(new Vector3f((float)reflected.x(),(float)reflected.y(),(float)reflected.z()));
        assertEquals(0,actual.distance(expected),1e-5);
    }
}
