package com.terminaldetector.drmd.client.flight;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;

/** Full-basis camera on the character's render frame; world Euler angles are never interpolated. */
public final class BoardingCamera {
    private BoardingCamera() {}
    public static void apply(Camera camera,CameraAccessor access,PlayerEntity player,boolean thirdPerson,boolean front,float delta) {
        Quat frame=BoardingPose.renderFrame(player,delta);
        Vec3 up=PassengerFrame.up(frame),look=BoardingPose.renderLook(player,delta),forward=front?look.scaled(-1):look;
        var angles=PortalTransform.vectorToYawPitch(forward);
        access.drmd$invokeSetRotation((float)angles.yawDegrees(),(float)angles.pitchDegrees());
        var base=camera.getVerticalPlane();Vec3 baseUp=new Vec3(base.x,base.y,base.z);
        Vec3 desiredUp=up.minus(forward.scaled(up.dot(forward))).normalized();
        double roll=Math.atan2(forward.dot(baseUp.cross(desiredUp)),Math.max(-1,Math.min(1,baseUp.dot(desiredUp))));
        var bank=new Quaternionf().rotationAxis((float)roll,(float)forward.x(),(float)forward.y(),(float)forward.z());
        camera.getRotation().premul(bank).normalize();
        camera.getHorizontalPlane().rotate(bank);camera.getVerticalPlane().rotate(bank);camera.getDiagonalPlane().rotate(bank);
        Vec3d eye=player.getLerpedPos(delta).add(BoardingSystem.mc(up).multiply(player.getEyeHeight(player.getPose())));
        Vec3d position=eye;
        if(thirdPerson) {
            Vec3d offset=BoardingSystem.mc(look).multiply(front?4:-4),target=eye.add(offset);double fraction=1;
            var terrain=player.getWorld().raycast(new RaycastContext(eye,target,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
            if(terrain.getType()!=HitResult.Type.MISS)fraction=Math.min(fraction,eye.distanceTo(terrain.getPos())/4);
            fraction=Math.min(fraction,hullFraction(player,eye,target,delta));
            position=eye.add(offset.multiply(Math.max(0,fraction-.02)));
        }
        access.drmd$invokeSetPos(position);
    }
    /** Also clip ordinary/6DoF F5 views after boarding releases its movement lease. */
    public static void clipHulls(Camera camera,CameraAccessor access,PlayerEntity player,float delta) {
        Vec3d eye=player.getCameraPosVec(delta),target=camera.getPos();
        double length=eye.distanceTo(target);if(length<1e-6)return;
        double fraction=hullFraction(player,eye,target,delta);
        if(fraction<1)access.drmd$invokeSetPos(eye.add(target.subtract(eye).multiply(Math.max(0,fraction-.08/length))));
    }
    private static double hullFraction(PlayerEntity player,Vec3d eye,Vec3d target,float delta) {
        double fraction=1;
        for(var body:player.getWorld().getEntitiesByClass(BlockBodyEntity.class,new Box(eye,target).expand(1),b->!b.isRemoved())) {
            var predicted=BoardingClient.bodyView(body,delta);
            Quat rotation=predicted==null?body.renderRotation(delta):predicted.rotation();
            Vec3 centre=predicted==null?body.renderMassPosition(delta):predicted.position();
            Vec3 from=body.localCentre().plus(rotation.inverse().rotate(BoardingSystem.pure(eye).minus(centre)));
            Vec3 to=body.localCentre().plus(rotation.inverse().rotate(BoardingSystem.pure(target).minus(centre)));
            var hit=body.collider().raycast(from,to);
            if(hit.isPresent()&&!hit.get().inside())fraction=Math.min(fraction,hit.get().fraction());
        }
        return fraction;
    }

}
