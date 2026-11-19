package com.terminaldetector.drmd.world.access;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Admission only; the unchanged vanilla explosion owns resistance, damage, shielding and loot. */
public final class ExplosionWorldAccess {
    public static final int MAX_CUBES=1024,MAX_ENTITIES=256,MAX_RAY_STEPS=4096;
    private ExplosionWorldAccess() {}

    public static boolean canCollect(ServerWorld world,Explosion explosion) {
        if(!CubicAvailability.onWorldThread(world))return false;
        var at=explosion.getPosition();float power=explosion.getPower();
        if(!Double.isFinite(at.lengthSquared())||!Float.isFinite(power)||power<=0)return false;
        // Actual 1.21.1 empty-ray upper bound: maximum random launch energy, .22500001F
        // decay and .30000001192092896D position steps. No arbitrary extra view-distance halo.
        float energy=power*(.7f+Math.nextDown(1f)*.6f);int steps=0;
        for(;energy>0;energy-=.22500001f)if(++steps>MAX_RAY_STEPS)return false;
        double reach=Math.max(0,steps-1)*.30000001192092896+1e-6;
        var admission=new Admission(world);
        if(!admission.loaded(new Box(at,at).expand(reach)))return false;
        // Exposure samples may originate outside the centre-radius box for a large actor.
        // Match vanilla's entity query, then guard the genuine endpoint envelope as well.
        double radius=(double)(power*2f);
        var query=new Box(MathHelper.floor(at.x-radius-1),MathHelper.floor(at.y-radius-1),MathHelper.floor(at.z-radius-1),
            MathHelper.floor(at.x+radius+1),MathHelper.floor(at.y+radius+1),MathHelper.floor(at.z+radius+1));
        var entities=new ArrayList<Entity>();
        world.collectEntitiesByType(TypeFilter.instanceOf(Entity.class),query,
            e->e!=explosion.getEntity()&&!e.isSpectator(),entities,MAX_ENTITIES+1);
        if(entities.size()>MAX_ENTITIES)return false;
        for(var entity:entities) {
            if(entity.isImmuneToExplosion(explosion)||entity.squaredDistanceTo(at)>radius*radius)continue;
            var box=entity.getBoundingBox();
            // Match vanilla's X/Z lattice offsets instead of adding a blanket half-block pad.
            double dx=1/((box.maxX-box.minX)*2+1),dz=1/((box.maxZ-box.minZ)*2+1);
            double offsetX=(1-Math.floor(1/dx)*dx)*.5,offsetZ=(1-Math.floor(1/dz)*dz)*.5;
            var samples=new Box(box.minX+offsetX,box.minY,box.minZ+offsetZ,
                box.maxX+offsetX,box.maxY,box.maxZ+offsetZ);
            if(!admission.loaded(samples.union(new Box(at,at))))return false;
        }
        return true;
    }

    private static final class Admission {
        final ServerWorld world;final WorldAccess access;final Set<D6ChunkPos> checked=new HashSet<>();
        Admission(ServerWorld world){this.world=world;this.access=WorldAccess.of(world);}
        boolean loaded(Box samples) {
            // Collision/organic shape evaluation may inspect one neighbouring block.
            var box=samples.expand(1);
            if(!Double.isFinite(box.minX+box.minY+box.minZ+box.maxX+box.maxY+box.maxZ))return false;
            if(box.minX<Integer.MIN_VALUE||box.maxX>Integer.MAX_VALUE||box.minZ<Integer.MIN_VALUE||box.maxZ>Integer.MAX_VALUE)return false;
            int x0=MathHelper.floor(box.minX)>>4,x1=MathHelper.floor(box.maxX)>>4;
            int z0=MathHelper.floor(box.minZ)>>4,z1=MathHelper.floor(box.maxZ)>>4;
            int y0=MathHelper.floor(Math.max(world.getBottomY(),box.minY))>>4;
            int y1=MathHelper.floor(Math.min(world.getTopY()-1,box.maxY))>>4;
            if(box.maxY<world.getBottomY()||box.minY>=world.getTopY())return true;
            if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>MAX_CUBES)return false;
            for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++) {
                var cube=new D6ChunkPos(x,y,z);
                if(checked.contains(cube))continue;
                if(checked.size()>=MAX_CUBES||!access.isVolumeAvailable(cube))return false;
                checked.add(cube);
            }
            return true;
        }
    }
}
