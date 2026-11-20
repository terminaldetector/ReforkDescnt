package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.BodyStepBudget;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.mega.SkyUfoEntity;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Bounded load admission for a saved passenger, never a position-following attachment. */
public final class BoardingRestore {
    public static final int MAX_WAIT_TICKS=100;
    private record Pending(int tick,Vec3d feet) {}
    private static final Map<ServerPlayerEntity,Pending> START=new WeakHashMap<>();
    private BoardingRestore(){}
    public static void forgetConnection(ServerPlayerEntity p){START.remove(p);}
    public static void clearAll(){START.clear();}
    public static void clear(ServerPlayerEntity p) {
        boolean pending=DescentPlayerData.get(p).pendingBoardingResume()!=null;
        DescentPlayerData.get(p).clearBoardingResume();START.remove(p);
        if(pending&&!BoardingSystem.drives(p))BoardingPose.clear(p);
    }
    public static boolean holds(ServerPlayerEntity p) {
        var data=DescentPlayerData.get(p);var hint=data.pendingBoardingResume();if(hint==null){START.remove(p);return false;}
        if(data.isEnabled()||!p.isAlive()||p.isSpectator()||p.hasVehicle()||p.isSleeping()||p.getAbilities().flying
            ||!p.getWorld().getRegistryKey().getValue().toString().equals(hint.dimension())){clear(p);return false;}
        var body=BodySpatialIndex.find(p.getWorld(),hint.body()).orElse(null);
        if(body instanceof SkyUfoEntity ufo&&ufo.isCrashing()){clear(p);return false;}
        int tick=p.getServer().getTicks();var start=START.computeIfAbsent(p,ignored->new Pending(tick,p.getPos()));
        if(tick-start.tick()>=MAX_WAIT_TICKS||hint.anchor()!=null&&p.getPos().squaredDistanceTo(start.feet())>.0625){clear(p);return false;}
        if(!BoardingPose.active(p)||!BoardingPose.frame(p).equals(hint.frame()))BoardingPose.publish(p,hint.frame());
        return true;
    }
    public static boolean waiting(BlockBodyEntity body) {
        if(body.getWorld().isClient||body.hasSimulatedPhysics())return false;
        for(var p:BoardingSystem.players((ServerWorld)body.getWorld())) {
            var active=BoardingSystem.state(p);
            if(BoardingSystem.drives(p)&&active!=null&&(active.support()==body.getId()||active.gravityOwner()==body.getId()))return false;
        }
        for(var p:BoardingSystem.players((ServerWorld)body.getWorld()))if(holds(p)) {
            var hint=DescentPlayerData.get(p).pendingBoardingResume();
            if((hint.body().equals(body.getUuid())||hint.anchor()!=null&&hint.anchor().support().equals(body.getUuid()))
                &&body.getBoundingBox().expand(4).contains(p.getPos()))return true;
        }
        return false;
    }
    public static Quat frame(ServerPlayerEntity p,int bodyId) {
        var hint=DescentPlayerData.get(p).pendingBoardingResume();var body=BodySpatialIndex.find(p.getWorld(),bodyId).orElse(null);
        return hint!=null&&body!=null&&hint.body().equals(body.getUuid())?hint.frame():Quat.IDENTITY;
    }
    public static void remember(ServerPlayerEntity p,DynamicBodyAttachment.State state) {
        var field=BodySpatialIndex.find(p.getWorld(),state.gravityOwner()).orElse(null);
        var support=BodySpatialIndex.find(p.getWorld(),state.support()).orElse(null);
        var body=field==null?support:field;
        if(body==null){clear(p);return;}
        DescentPlayerData.BoardingAnchor anchor=null;
        if(state.grounded()&&support!=null&&(state.gravityOwner()<0||field!=null)){
            var feet=BoardingSystem.mc(state.feet());
            var relativeVelocity=state.velocity().minus(BoardingSystem.pure(support.surfaceVelocityAt(feet)));
            var frame=field==null?state.frame():ImmPtlQuaternions.hamiltonProduct(field.rotation().inverse(),state.frame()).normalized();
            anchor=new DescentPlayerData.BoardingAnchor(support.getUuid(),field==null?null:field.getUuid(),
                BoardingSystem.pure(support.worldToLocal(feet)),support.rotation().inverse().rotate(relativeVelocity),frame);
        }
        DescentPlayerData.get(p).rememberBoarding(body.getUuid(),p.getWorld().getRegistryKey().getValue().toString(),state.frame(),anchor);
        START.remove(p);
    }

    /** Validate a loaded saved support at its current pose. The caller owns the new lease. */
    static DynamicBodyAttachment.State localAdmission(ServerPlayerEntity p,DescentPlayerData.BoardingAnchor anchor) {
        var world=p.getServerWorld();
        var support=BodySpatialIndex.find(world,anchor.support()).orElse(null);
        var field=anchor.gravityOwner()==null?null:BodySpatialIndex.find(world,anchor.gravityOwner()).orElse(null);
        if(support==null||anchor.gravityOwner()!=null&&field==null)return null;
        if(support instanceof SkyUfoEntity ufo&&ufo.isCrashing())return reject(p);
        double localLimit=BlockBodyEntity.MAX_LOCAL+2;
        if(anchor.feet().lengthSquared()>3*localLimit*localLimit||anchor.velocity().length()>2*BodyStepBudget.MAX_LINEAR_SPEED)
            return reject(p);
        var feet=BoardingSystem.pure(support.localToWorld(BoardingSystem.mc(anchor.feet())));
        var frame=field==null?anchor.frame():ImmPtlQuaternions.hamiltonProduct(field.rotation(),anchor.frame()).normalized();
        var up=PassengerFrame.up(frame);
        var box=PassengerFrame.box(feet,p.getWidth(),p.getHeight(),frame);
        var bounds=box.bounds();
        var area=new Box(BoardingSystem.mc(bounds.min()),BoardingSystem.mc(bounds.max()));
        if(!world.getWorldBorder().contains(area)||area.minY<world.getBottomY()||area.maxY>world.getTopY())return reject(p);
        if(!support.collisionRegionAvailable(area))return null;
        var supportMotion=support.boardingMotion();
        var fieldMotion=field==null?null:field==support?supportMotion:field.boardingMotion();
        var centre=feet.plus(up.scaled(p.getHeight()/2));
        if(fieldMotion==null?up.y()<=.6:fieldMotion.gravity(centre,1).isEmpty()||fieldMotion.gravityUp(1).dot(up)<=.6)
            return reject(p);
        var relativeVelocity=support.rotation().rotate(anchor.velocity());
        var contact=supportMotion.query(1).contacts(box,.01).stream()
            .anyMatch(c->c.normal().dot(up)>.6&&c.penetration()<=.02&&relativeVelocity.dot(c.normal())<.1);
        if(!contact)return reject(p);
        var velocity=BoardingSystem.pure(support.surfaceVelocityAt(BoardingSystem.mc(feet))).plus(relativeVelocity);
        var state=new DynamicBodyAttachment.State(feet,velocity,true,support.getId(),frame,field==null?-1:field.getId());
        var nearby=BodySpatialIndex.query(world,area.expand(.02)).stream().map(BlockBodyEntity::boardingMotion).toList();
        if(!DynamicBodyAttachment.canOrient(feet,frame,nearby,1,p.getWidth(),p.getHeight(),BoardingWorldMotion.capture(p,state,true)))return reject(p);
        return state;
    }

    private static DynamicBodyAttachment.State reject(ServerPlayerEntity p){clear(p);return null;}
}
