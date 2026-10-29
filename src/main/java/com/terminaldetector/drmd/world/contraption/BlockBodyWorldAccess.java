package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;
import java.util.*;

/** Local-grid access. A BodyHit is deliberately not a vanilla world BlockHitResult. */
public final class BlockBodyWorldAccess {
    public record BodyHit(BlockPos localBlock,Vec3 localPoint,Vec3 localNormal,Vec3 worldPoint,Vec3 worldNormal,
                          double fraction,long revision,boolean inside) {
        public Direction localFace(){return Direction.getFacing(localNormal.x(),localNormal.y(),localNormal.z());}
    }
    private final BlockBodyEntity body;
    public BlockBodyWorldAccess(BlockBodyEntity body){this.body=Objects.requireNonNull(body);}
    public BlockState getLocalBlock(BlockPos pos){return body.getLocalBlock(pos);}
    public boolean setLocalBlock(BlockPos pos,BlockState state){return body.setLocalBlock(pos,state);}
    public boolean breakLocalBlock(BlockPos pos){return setLocalBlock(pos,Blocks.AIR.getDefaultState());}
    public Optional<BodyHit> raycastBlockBody(Vec3d from,Vec3d to){return raycastBlockBody(from,to,body.massPosition(),body.rotation());}
    public Optional<BodyHit> raycastBlockBody(Vec3d from,Vec3d to,Vec3 worldCom,Quat rotation) {
        Vec3 start=rotation.inverse().rotate(BoardingSystem.pure(from).minus(worldCom)).plus(body.localCentre());
        Vec3 end=rotation.inverse().rotate(BoardingSystem.pure(to).minus(worldCom)).plus(body.localCentre());
        if(body.collider().raycast(start,end).isEmpty())return Optional.empty();
        CompoundCollider.RayHit best=null;BlockPos block=null;
        var min=new Vec3(Math.min(start.x(),end.x())-1e-8,Math.min(start.y(),end.y())-1e-8,Math.min(start.z(),end.z())-1e-8);
        var max=new Vec3(Math.max(start.x(),end.x())+1e-8,Math.max(start.y(),end.y())+1e-8,Math.max(start.z(),end.z())+1e-8);
        var candidates=new ArrayList<>(body.localCollisionCandidates(new BodyBounds(min,max)));
        candidates.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        for(var pos:candidates) {
            var hit=CompoundCollider.raycastBox(body.localBlockBounds().get(pos),start,end);
            if(hit.isPresent()&&(best==null||hit.get().fraction()<best.fraction())){best=hit.get();block=pos;}
        }
        return best==null?Optional.empty():Optional.of(transformHitResult(block,best,worldCom,rotation));
    }
    public BodyHit transformHitResult(BlockPos pos,CompoundCollider.RayHit local,Vec3 worldCom,Quat rotation) {
        return new BodyHit(pos,local.point(),local.normal(),worldCom.plus(rotation.rotate(local.point().minus(body.localCentre()))),
            rotation.rotate(local.normal()),local.fraction(),body.shapeRevision(),local.inside());
    }
    public List<BodyCollisionQuery.Contact> queryBodyCollision(OrientedBox actor,double skin){return body.collisionQuery().contacts(actor,skin);}
    public boolean placeLocalBlock(BlockPos pos,BlockState state) {
        if(!getLocalBlock(pos).isAir()||state.isAir()||!BlockBodyEntity.supports(state)||state.hasBlockEntity())return false;
        if(!BlockBodyEntity.validLocal(pos))return false;
        var bounds=new BodyBounds(new Vec3(pos.getX(),pos.getY(),pos.getZ()),new Vec3(pos.getX()+1,pos.getY()+1,pos.getZ()+1));
        // Full placement cube is conservative for smoothed blocks, and never permits an occupied head.
        var cube=OrientedBox.local(bounds,body.massPosition(),body.localCentre(),body.rotation());var b=cube.bounds();
        var broad=new Box(b.min().x(),b.min().y(),b.min().z(),b.max().x(),b.max().y(),b.max().z());
        if(!body.collisionRegionAvailable(broad))return false;
        for(var shape:body.getWorld().getBlockCollisions(body,broad.contract(1e-6)))for(var box:shape.getBoundingBoxes())
            if(overlaps(cube,OrientedBox.axisAligned(new BodyBounds(new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ)))))return false;
        for(var entity:BodySpatialIndex.otherEntities(body.getWorld(),body,broad,entity->entity.isAlive()&&!entity.isSpectator())) {
            OrientedBox actor;
            if(entity instanceof PlayerEntity player)actor=PassengerFrame.box(BoardingSystem.pure(player.getPos()),player.getWidth(),player.getHeight(),BoardingPose.frame(player));
            else if(entity instanceof BlockBodyEntity other){if(!other.collisionQuery().contacts(cube,0).isEmpty())return false;else continue;}
            else {var box=entity.getBoundingBox();actor=OrientedBox.axisAligned(new BodyBounds(new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ)));}
            if(overlaps(cube,actor))return false;
        }
        return setLocalBlock(pos,state);
    }
    private static boolean overlaps(OrientedBox a,OrientedBox b){return a.contact(b,0).filter(c->c.penetration()>1e-6).isPresent();}
}
