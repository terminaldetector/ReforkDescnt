package com.terminaldetector.drmd.world.micro;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.BlastStructuralCollapse;

import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.engineer.HeavyDrillDrops;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Strong handheld pulses only. No background excavation, entities, tickets or per-block ticking. */
public final class ThermalDrillCut {
    public static final int MAX_WRITES=128,MAX_READS=256,MAX_SCAN=4096,MAX_EMITTERS=32;
    private static final Map<ServerWorld,Budget> WORLDS=new WeakHashMap<>();
    private static final class Budget {
        long tick=Long.MIN_VALUE;int scan,reads,writes,cuts,refused;final LinkedHashMap<UUID,Cursor> emitters=new LinkedHashMap<>();
        void at(long now){if(tick!=now){tick=now;scan=reads=writes=cuts=refused=0;}emitters.values().removeIf(c->now-c.last>100);}
    }
    private static final class Cursor {
        final Vec3d a,b;final int radius,minX,minY,minZ,nx,ny,nz,volume;int index,incomplete;long last;boolean cycleFinished,complete;
        Cursor(Vec3d a,Vec3d b,int r,long now){
            this.a=a;this.b=b;radius=r;last=now;minX=MathHelper.floor(Math.min(a.x,b.x)-r-1);minY=MathHelper.floor(Math.min(a.y,b.y)-r-1);minZ=MathHelper.floor(Math.min(a.z,b.z)-r-1);
            nx=MathHelper.floor(Math.max(a.x,b.x)+r+1)-minX+1;ny=MathHelper.floor(Math.max(a.y,b.y)+r+1)-minY+1;nz=MathHelper.floor(Math.max(a.z,b.z)+r+1)-minZ+1;volume=nx*ny*nz;
        }
        boolean matches(Vec3d start,Vec3d end,int r){return radius==r&&a.squaredDistanceTo(start)<1e-12&&b.squaredDistanceTo(end)<1e-12;}
        boolean sameHeldFront(Vec3d start,Vec3d end,int r,LivingEntity user){
            if(complete||r!=radius||a.squaredDistanceTo(user.getEyePos())>48*48)return false;
            var old=b.subtract(a).normalize();var next=end.subtract(start).normalize();var shift=start.subtract(a);
            return old.lengthSquared()>0&&old.dotProduct(next)>.99999&&shift.crossProduct(old).lengthSquared()<.0625&&Math.abs(shift.dotProduct(old))<=48;
        }
        BlockPos next(){int n=index;index=(index+1)%volume;cycleFinished=index==0;return new BlockPos(minX+n/(ny*nz),minY+(n/nz)%ny,minZ+n%nz);}
    }
    public record Stats(long tick,int scanned,int inspected,int writes,int geometryChanges,int refused,int emitters){}
    public static Stats stats(ServerWorld world){var b=WORLDS.get(world);return b==null?new Stats(world.getTime(),0,0,0,0,0,0):new Stats(b.tick,b.scan,b.reads,b.writes,b.cuts,b.refused,b.emitters.size());}
    private ThermalDrillCut(){}
    public static int pulse(ServerWorld world,BlockPos origin,Vec3d direction,LivingEntity user,int radius,int length){
        if(!world.getServer().isOnThread()||user==null||user.getWorld()!=world||!user.isAlive()||user.isRemoved()||!Double.isFinite(direction.lengthSquared())||direction.lengthSquared()<1e-12||radius<2||radius>3||length<1||length>5)return 0;
        long now=world.getTime();if(now<1||now>Long.MAX_VALUE-ThermalDrillCell.COOL_TICKS)return 0;
        var a=Vec3d.ofCenter(origin);var b=a.add(direction.normalize().multiply(length-1));var budget=WORLDS.computeIfAbsent(world,w->new Budget());budget.at(now);
        var cursor=budget.emitters.get(user.getUuid());
        if(cursor==null||!cursor.matches(a,b,radius)&&!cursor.sameHeldFront(a,b,radius,user)){
            if(cursor==null&&budget.emitters.size()>=MAX_EMITTERS){budget.refused++;return 0;}
            cursor=new Cursor(a,b,radius,now);if(cursor.volume>MAX_SCAN){budget.refused++;return 0;}budget.emitters.put(user.getUuid(),cursor);
        }
        cursor.last=now;int changes=0,visited=0;
        try(var supports=new BlastStructuralCollapse.SupportEdits(world,cursor.a,radius,user);
            var drops=new HeavyDrillDrops(world,user,cursor.a)){
            while(visited++<cursor.volume&&budget.scan<MAX_SCAN&&budget.reads<MAX_READS&&budget.writes<MAX_WRITES){
                if(cursor.complete)break;
                if(cursor.cycleFinished){cursor.cycleFinished=false;if(cursor.incomplete==0){cursor.complete=true;break;}cursor.incomplete=0;}
                var pos=cursor.next();budget.scan++;
                if(ThermalBore.distanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,cursor.a.x,cursor.a.y,cursor.a.z,cursor.b.x,cursor.b.y,cursor.b.z)>radius+Math.sqrt(3)*.5)continue;
                budget.reads++;if(!WorldEditSafety.mayChange(world,pos,user)){budget.refused++;cursor.incomplete++;continue;}
                var state=WorldAccess.of(world).readIfLoaded(pos).orElse(null);
                if(state==null||state.isAir()||!state.getFluidState().isEmpty())continue;
                var entity=world.getBlockEntity(pos);if(entity!=null&&!(entity instanceof CarvedBlockEntity))continue;
                var carved=entity instanceof CarvedBlockEntity c?c:null;
                if(carved!=null&&carved.thermalReadOnly()){budget.refused++;cursor.incomplete++;continue;}
                var source=carved!=null?carved.source():state.getBlock() instanceof com.terminaldetector.drmd.world.geometry.OrganicBlock organic?organic.source():state;
                float hardness=source.getHardness(world,pos);
                if(!Float.isFinite(hardness)||hardness<0||hardness>=50||source.isOf(Blocks.BEDROCK)||!BlockDamage.isDamageable(world,pos,state))continue;
                var desired=new ThermalBore(cursor.a.x-pos.getX(),cursor.a.y-pos.getY(),cursor.a.z-pos.getZ(),cursor.b.x-pos.getX(),cursor.b.y-pos.getY(),cursor.b.z-pos.getZ(),radius);
                var before=carved==null||carved.thermal()==null?ThermalDrillCell.cold(now):carved.thermal();
                if(before.lastPulse()>now){budget.refused++;cursor.incomplete++;continue;}
                var next=before.pulse(now,hardness,desired);
                if(next.heat()<3||next.bores().stream().noneMatch(q->q.sameAxis(desired)&&q.radius()>=desired.radius()))cursor.incomplete++;
                if(next==before){if(now>before.lastPulse())budget.refused++;continue;}
                long mask=carved==null?MicroGrid.FULL:carved.mask(),after=next.mask(mask);
                boolean geometry=after!=mask||!before.bores().equals(next.bores());
                if(after==MicroGrid.EMPTY){
                    if(drops.breakBlock(pos)){
                        budget.writes++;budget.cuts++;changes++;updates(world,pos);
                        supports.removed(pos,carved==null||carved.authoredOrganic()?state:carved.source());
                    }
                    continue;
                }
                boolean wrapped=carved==null;
                if(wrapped){carved=CarvedBlock.replace(world,pos,state,after);if(carved==null)continue;}
                else if(after!=mask)carved.setMask(after);
                carved.setThermal(next);budget.writes++;
                if(geometry){budget.cuts++;changes++;}
                if(geometry||wrapped)updates(world,pos);
            }
        }
        return changes;
    }
    /** At most six already-known neighbours per actual geometry change; no invented fluid sources. */
    private static void updates(ServerWorld world,BlockPos pos){
        world.getChunkManager().getLightingProvider().checkBlock(pos);world.updateNeighbors(pos,world.getBlockState(pos).getBlock());
        for(var d:Direction.values()){
            var at=pos.offset(d);
            var state=WorldAccess.of(world).readIfLoaded(at).orElse(null);
            if(state==null)continue;
            var fluid=state.getFluidState();
            if(!fluid.isEmpty())world.scheduleFluidTick(at,fluid.getFluid(),fluid.getFluid().getTickRate(world));
        }
        com.terminaldetector.drmd.world.store.SurfaceIngest.onGroundChanged(world,pos);
    }
}
