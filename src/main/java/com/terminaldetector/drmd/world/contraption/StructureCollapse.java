package com.terminaldetector.drmd.world.contraption;

import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Bounded support-cut adapter for a single trunk or freestanding one-block-wide column. */
public final class StructureCollapse {
    private StructureCollapse() {}
    /** Vanilla already decided which blocks this blast may destroy. Do not scan or load terrain. */
    public static void explosion(ServerWorld world,List<BlockPos> affected,Vec3d centre,float power) {
        if(!Float.isFinite(power) || power<=0 || !com.terminaldetector.drmd.physics.PhysicsProfileState.expanded(world))return;
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        var ordered=new ArrayList<>(affected);ordered.sort(Comparator.comparingInt(BlockPos::getY));
        int bodies=0;
        for(var p:ordered) {
            if(bodies>=4)break;
            var state=access.readIfLoaded(p);
            if(state.isEmpty() || !state.orElseThrow().isIn(BlockTags.LOGS))continue;
            Vec3d direction=Vec3d.ofCenter(p).subtract(centre);
            if(direction.lengthSquared()<1e-6)direction=new Vec3d(1,0,0);
            if(rocketCut(world,p,centre,direction,(float)(power*24))!=null)bodies++;
        }
    }
    public static BlockBodyEntity rocketCut(ServerWorld world,BlockPos root,Vec3d impact,Vec3d direction,float damage) {
        if(!com.terminaldetector.drmd.physics.PhysicsProfileState.expanded(world))return null;
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        var rootState=access.readIfLoaded(root);if(rootState.isEmpty())return null;
        var base=rootState.orElseThrow();
        boolean tree=base.isIn(BlockTags.LOGS);
        if(!Float.isFinite(damage) || damage<Math.max(12,base.getHardness(world,root)*12)
            || !BlockBodyEntity.supports(base) || base.isIn(BlockTags.LEAVES) || world.getBlockEntity(root)!=null)return null;
        Map<BlockPos,BlockState> selected=new LinkedHashMap<>();
        int top=root.getY();
        for(int y=1;y<=12;y++) {
            BlockPos p=root.up(y);var loaded=access.readIfLoaded(p);if(loaded.isEmpty())return null;
            var state=loaded.orElseThrow();
            if(tree?!state.isIn(BlockTags.LOGS):!state.isOf(base.getBlock()))break;
            if(!tree)for(Direction d:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})
                if(access.readIfLoaded(p.offset(d)).filter(BlockState::isAir).isEmpty())return null;
            selected.put(p,state);top=p.getY();
        }
        var above=access.readIfLoaded(new BlockPos(root.getX(),top+1,root.getZ()));
        if(selected.size()<2 || above.isEmpty() || (tree?above.orElseThrow().isIn(BlockTags.LOGS):above.orElseThrow().isOf(base.getBlock())))return null;
        // Follow only the face-connected trunk, branches and canopy. The old top-box
        // pre-scan rejected real horizontal branches and could select disconnected leaves.
        // A path into another rooted trunk or an oversized/unavailable graph refuses before mutation.
        if(tree) {
            var queue=new ArrayDeque<BlockPos>(selected.keySet());
            var visited=new HashSet<BlockPos>(selected.keySet());visited.add(root);
            while(!queue.isEmpty()) {
                var p=queue.removeFirst();
                for(var d:Direction.values()) {
                    var q=p.offset(d);
                    if(!visited.add(q))continue;
                    var loaded=access.readIfLoaded(q);if(loaded.isEmpty())return null;
                    var state=loaded.orElseThrow();
                    if(!state.isIn(BlockTags.LEAVES) && !state.isIn(BlockTags.LOGS))continue;
                    if(state.isIn(BlockTags.LOGS) && q.getY()<=root.getY())return null;
                    if(Math.abs(q.getX()-root.getX())>7 || Math.abs(q.getZ()-root.getZ())>7
                        || q.getY()<=root.getY() || q.getY()>root.getY()+15 || selected.size()>=256)return null;
                    selected.put(q,state);queue.addLast(q);
                }
            }
        }
        try {
            var body=BlockBodyEntity.assembleSelection(world,selected);
            if(!world.breakBlock(root,true)){body.disassemble();return null;}
            body.setPhysicsGravity(true);
            // Blast loads the lowest surviving section; offset force creates the tipping torque.
            Vec3d forcePoint=Vec3d.ofCenter(root.up());
            body.weaponImpulse(forcePoint,direction,damage);
            return body;
        } catch(IllegalArgumentException|IllegalStateException rejected) {return null;}
    }
}
