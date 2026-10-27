package com.terminaldetector.drmd.world.portal.gun;

import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.portal.PortalCrossing;
import com.terminaldetector.drmd.world.portal.mirror.*;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Bounded placement beam through existing native gun pairs. Uses the engine's exact rotation. */
public final class PortalGunRay {
    public static final int MAX_HOPS=4;
    public record Result(BlockHitResult hit,boolean unavailable,int hops) {}
    private record Crossing(BlockPos pos,Vec3d point,Vec3d normal,double distance,PortalPanelBlockEntity source) {}
    private PortalGunRay() {}
    public static Result trace(ServerPlayerEntity player,Vec3d start,Vec3d direction) {
        var world=player.getServerWorld();var access=WorldAccess.of(world);
        Vec3d from=start,dir=direction.normalize();double remaining=PortalProjector.RANGE;
        var anchors=new HashSet<BlockPos>();
        for(var pair:PortalGunState.get(world).pairs()) {
            if(pair.blue()!=null)anchors.add(pair.blue());if(pair.orange()!=null)anchors.add(pair.orange());
        }
        for(int hop=0;hop<=MAX_HOPS;hop++) {
            Vec3d end=WorldEditSafety.loadedEnd(world,from,from.add(dir.multiply(remaining)));
            double loadedLength=end.distanceTo(from);
            if(loadedLength<.001)return new Result(null,true,hop);
            var solid=world.raycast(new RaycastContext(from,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
            double closest=solid.getType()==HitResult.Type.BLOCK?solid.getPos().distanceTo(from):loadedLength;
            Crossing crossing=null;
            for(BlockPos pos:anchors) {
                if(pos.getSquaredDistance(from)>Math.pow(remaining+4,2))continue;
                var state=access.readIfLoaded(pos);
                if(state.isEmpty() || !state.get().isOf(ModWorldBlocks.PORTAL_PANEL)
                    || !(world.getBlockEntity(pos) instanceof PortalPanelBlockEntity be) || !be.isLinked() || be.projectorOwner()==null)continue;
                Vec3d normal=Vec3d.of(state.get().get(PortalPanelBlock.FACING).getVector());
                double facing=dir.dotProduct(normal);if(facing>=-1e-8)continue;
                Vec3d plane=Vec3d.ofCenter(pos).add(normal.multiply(.5));
                double distance=plane.subtract(from).dotProduct(normal)/facing;
                if(distance<1e-4 || distance>closest+1e-6)continue;
                Vec3d point=from.add(dir.multiply(distance));
                if(!PortalCrossing.withinFace(pure(point),pure(plane),pure(normal),PortalPanelBlock.HALF_SPAN-.125))continue;
                closest=distance;crossing=new Crossing(pos,point,normal,distance,be);
            }
            if(crossing==null)return new Result(solid.getType()==HitResult.Type.BLOCK?solid:null,loadedLength+.001<remaining,hop);
            if(hop==MAX_HOPS)return new Result(null,true,hop);
            var partner=crossing.source().getLinkPartnerPos();
            if(partner==null || access.readIfLoaded(partner).isEmpty()
                || !(world.getBlockEntity(partner) instanceof PortalPanelBlockEntity other)
                || !other.ownedBy(crossing.source().projectorOwner(),3-crossing.source().projectorSlot())
                || !other.isLinked() || !crossing.pos().equals(other.getLinkPartnerPos()))return new Result(null,true,hop);
            var partnerState=access.readIfLoaded(partner).orElseThrow();
            Vec3d destNormal=Vec3d.of(partnerState.get(PortalPanelBlock.FACING).getVector());
            Vec3d src=Vec3d.ofCenter(crossing.pos()).add(crossing.normal().multiply(.5));
            Vec3d dst=Vec3d.ofCenter(partner).add(destNormal.multiply(.5));
            Vec3d mapped=vec(PortalTransform.transformPoint(pure(crossing.point()),pure(src),pure(crossing.normal()),pure(dst),pure(destNormal),1));
            dir=vec(PortalTransform.rotationBetween(pure(crossing.normal()),pure(destNormal.negate())).rotate(pure(dir))).normalize();
            from=mapped.add(destNormal.multiply(.002));remaining-=crossing.distance()+.002;
        }
        throw new AssertionError("bounded portal ray");
    }
    private static PortalTransform.Vec3 pure(Vec3d v){return new PortalTransform.Vec3(v.x,v.y,v.z);}
    private static Vec3d vec(PortalTransform.Vec3 v){return new Vec3d(v.x(),v.y(),v.z());}
}
