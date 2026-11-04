package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.*;

/** Bounded load admission for a saved passenger, never a position-following attachment. */
public final class BoardingRestore {
    public static final int MAX_WAIT_TICKS=100;
    private static final Map<ServerPlayerEntity,Integer> START=new WeakHashMap<>();
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
        var entity=p.getServerWorld().getEntity(hint.body());
        if(entity instanceof com.terminaldetector.drmd.world.mega.SkyUfoEntity ufo&&ufo.isCrashing()){clear(p);return false;}
        int tick=p.getServer().getTicks(),start=START.computeIfAbsent(p,ignored->tick);
        if(tick-start>=MAX_WAIT_TICKS){clear(p);return false;}
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
            if(hint.body().equals(body.getUuid())&&body.getBoundingBox().expand(4).contains(p.getPos()))return true;
        }
        return false;
    }
    public static Quat frame(ServerPlayerEntity p,int bodyId) {
        var hint=DescentPlayerData.get(p).pendingBoardingResume();var body=BodySpatialIndex.find(p.getWorld(),bodyId).orElse(null);
        return hint!=null&&body!=null&&hint.body().equals(body.getUuid())?hint.frame():Quat.IDENTITY;
    }
    public static void remember(ServerPlayerEntity p,DynamicBodyAttachment.State state) {
        int id=state.gravityOwner()>=0?state.gravityOwner():state.support();var body=BodySpatialIndex.find(p.getWorld(),id).orElse(null);
        if(body==null){clear(p);return;}
        DescentPlayerData.get(p).rememberBoarding(body.getUuid(),p.getWorld().getRegistryKey().getValue().toString(),state.frame());START.remove(p);
    }
}
