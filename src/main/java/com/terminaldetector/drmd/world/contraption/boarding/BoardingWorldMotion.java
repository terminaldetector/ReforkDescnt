package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.world.access.*;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;
import java.util.*;

/** One bounded collision snapshot per motor tick. Unknown XYZ volumes are solid, never generated. */
public final class BoardingWorldMotion implements DynamicBodyAttachment.Motion {
    private final PlayerEntity player;
    private final OrientedCharacterClip clip;
    private final boolean replay;
    private BoardingWorldMotion(PlayerEntity player,OrientedCharacterClip clip,boolean replay){this.player=player;this.clip=clip;this.replay=replay;}
    public static BoardingWorldMotion capture(PlayerEntity player,DynamicBodyAttachment.State state,boolean replay) {
        var world=player.getWorld();
        if(!CubicAvailability.onWorldThread(world))throw new IllegalStateException("Passenger geometry requires the world thread");
        double reach=3+Math.min(3,state.velocity().length()*.05);
        var p=state.feet();int x0=MathHelper.floor(p.x()-reach),x1=MathHelper.floor(p.x()+reach);
        int y0=MathHelper.floor(p.y()-reach),y1=MathHelper.floor(p.y()+reach);
        int z0=MathHelper.floor(p.z()-reach),z1=MathHelper.floor(p.z()+reach);
        var access=WorldAccess.of(world);var shapes=new ArrayList<BodyBounds>();
        var available=new HashMap<D6ChunkPos,Boolean>();
        for(int x=(x0-1)>>4;x<=(x1+1)>>4;x++)for(int y=(y0-1)>>4;y<=(y1+1)>>4;y++)for(int z=(z0-1)>>4;z<=(z1+1)>>4;z++) {
            var cube=new D6ChunkPos(x,y,z);available.put(cube,!CubicAvailability.withinHeight(world,cube)||access.isVolumeAvailable(cube));
        }
        boolean overflow=false;
        outer:for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++) {
            var pos=new BlockPos(x,y,z);if(world.isOutOfHeightLimit(pos))continue;
            boolean known=true;
            // Shape providers can read neighbours. Prove the whole one-block halo before asking.
            for(int dx=-1;dx<=1&&known;dx+=2)for(int dy=-1;dy<=1&&known;dy+=2)for(int dz=-1;dz<=1;dz+=2)
                if(!available.getOrDefault(D6ChunkPos.ofBlock(x+dx,y+dy,z+dz),false)){known=false;break;}
            if(!known||!world.getWorldBorder().contains(pos)) {
                shapes.add(new BodyBounds(new Vec3(x,y,z),new Vec3(x+1,y+1,z+1)));continue;
            }
            var stateAt=access.readIfLoaded(pos);
            if(stateAt.isEmpty()){shapes.add(new BodyBounds(new Vec3(x,y,z),new Vec3(x+1,y+1,z+1)));continue;}
            if(stateAt.get().isAir())continue;
            var shape=stateAt.get().getCollisionShape(world,pos,ShapeContext.of(player));
            for(var b:shape.getBoundingBoxes())if(b.getLengthX()>1e-10&&b.getLengthY()>1e-10&&b.getLengthZ()>1e-10)
                shapes.add(new BodyBounds(new Vec3(x+b.minX,y+b.minY,z+b.minZ),new Vec3(x+b.maxX,y+b.maxY,z+b.maxZ)));
            if(shapes.size()>16000){overflow=true;break outer;}
        }
        var region=new Box(x0,y0,z0,x1+1,y1+1,z1+1);
        if(overflow){shapes.clear();shapes.add(bounds(region));}
        else for(var shape:world.getEntityCollisions(player,region))for(var box:shape.getBoundingBoxes())
            if(box.getLengthX()>1e-10&&box.getLengthY()>1e-10&&box.getLengthZ()>1e-10)shapes.add(bounds(box));
        // A checked move may not leave its captured world snapshot during substeps or correction.
        shapes.add(new BodyBounds(new Vec3(x0-1,y0-1,z0-1),new Vec3(x0,y1+2,z1+2)));
        shapes.add(new BodyBounds(new Vec3(x1+1,y0-1,z0-1),new Vec3(x1+2,y1+2,z1+2)));
        shapes.add(new BodyBounds(new Vec3(x0-1,y0-1,z0-1),new Vec3(x1+2,y0,z1+2)));
        shapes.add(new BodyBounds(new Vec3(x0-1,y1+1,z0-1),new Vec3(x1+2,y1+2,z1+2)));
        shapes.add(new BodyBounds(new Vec3(x0-1,y0-1,z0-1),new Vec3(x1+2,y1+2,z0)));
        shapes.add(new BodyBounds(new Vec3(x0-1,y0-1,z1+1),new Vec3(x1+2,y1+2,z1+2)));
        return new BoardingWorldMotion(player,new OrientedCharacterClip(new CompoundCollider(shapes),player.getWidth(),player.getHeight()),replay);
    }
    @Override public boolean canOrient(Vec3 feet,Quat frame){return clip.canOrient(feet,frame);}
    @Override public DynamicBodyAttachment.Move move(Vec3 feet,Quat frame,Vec3 requested) {
        var allowed=clip.move(feet,frame,requested);if(replay)return allowed;
        Vec3d before=player.getPos();
        BoardingPose.move(player,frame,()->player.move(MovementType.SELF,BoardingSystem.mc(allowed.displacement())));
        return new DynamicBodyAttachment.Move(BoardingSystem.pure(player.getPos().subtract(before)),allowed.blockingNormals());
    }
    private static BodyBounds bounds(Box b){return new BodyBounds(new Vec3(b.minX,b.minY,b.minZ),new Vec3(b.maxX,b.maxY,b.maxZ));}
}
