package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.Predicate;

/** Bounded support-cut adapter for a single trunk or freestanding one-block-wide column. */
public final class StructureCollapse {
    private static final int TREE_LIMIT=384;
    private static final int CANOPY_DISTANCE=6;
    private StructureCollapse() {}
    /** Use the blast's original cut candidates and read only loaded material. */
    public static void explosion(ServerWorld world,List<BlockPos> affected,Vec3d centre,float power,Predicate<BlockPos> allowed) {
        if(!Float.isFinite(power) || power<=0 || !BlastStructuralCollapse.enabled(world))return;
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        var ordered=new ArrayList<>(affected.subList(0,Math.min(affected.size(),2048)));
        ordered.sort(Comparator.comparingInt(BlockPos::getY).thenComparingDouble(p->p.toCenterPos().squaredDistanceTo(centre)));
        int bodies=0;
        for(var p:ordered) {
            if(bodies>=4)break;
            var state=access.readIfLoaded(p);
            if(state.isEmpty() || !state.orElseThrow().isIn(BlockTags.LOGS))continue;
            Vec3d direction=Vec3d.ofCenter(p).subtract(centre);
            if(direction.lengthSquared()<1e-6)direction=new Vec3d(1,0,0);
            if(rocketCut(world,p,direction,power*24,allowed)!=null)bodies++;
        }
    }
    public static BlockBodyEntity rocketCut(ServerWorld world,BlockPos root,Vec3d impact,Vec3d direction,float damage) {
        return rocketCut(world,root,direction,damage,
            p->com.terminaldetector.drmd.world.edit.WorldEditSafety.mayChange(world,p,null));
    }

    public static BlockBodyEntity rocketCut(ServerWorld world,BlockPos root,Vec3d direction,float damage,Predicate<BlockPos> allowed) {
        if(!BlastStructuralCollapse.canDetach(world,root.toCenterPos())||!allowed.test(root))return null;
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(world);
        var rootState=access.readIfLoaded(root);if(rootState.isEmpty())return null;
        var base=rootState.orElseThrow();
        boolean tree=base.isIn(BlockTags.LOGS);
        if(base.getBlock() instanceof com.terminaldetector.drmd.world.geometry.OrganicBlock
            || !BlastStructuralCollapse.canBreak(world,root,base,damage)
            || !BlockBodyEntity.supports(base) || base.isIn(BlockTags.LEAVES) || world.getBlockEntity(root)!=null)return null;
        Map<BlockPos,BlockState> selected=new LinkedHashMap<>();
        int top=root.getY();
        for(int y=1;y<=(tree?24:12);y++) {
            BlockPos p=root.up(y);var loaded=access.readIfLoaded(p);if(loaded.isEmpty())return null;
            var state=loaded.orElseThrow();
            if(tree?!state.isIn(BlockTags.LOGS):!state.isOf(base.getBlock()))break;
            if(!tree)for(Direction d:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})
                if(access.readIfLoaded(p.offset(d)).filter(BlockState::isAir).isEmpty())return null;
            selected.put(p,state);top=p.getY();
        }
        var above=access.readIfLoaded(new BlockPos(root.getX(),top+1,root.getZ()));
        if(selected.size()<2 || above.isEmpty() || (tree?above.orElseThrow().isIn(BlockTags.LOGS):above.orElseThrow().isOf(base.getBlock())))return null;
        // Only real wood carries the trunk. Interlocking crowns must not join the
        // trunks of different generated trees into one immovable structure.
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
                    if(!state.isIn(BlockTags.LOGS))continue;
                    if(q.getY()<=root.getY())return null;
                    if(Math.abs(q.getX()-root.getX())>7 || Math.abs(q.getZ()-root.getZ())>7
                        || q.getY()>root.getY()+24 || selected.size()>=TREE_LIMIT)return null;
                    selected.put(q,state);queue.addLast(q);
                }
            }
            addCanopy(world,root,selected);
        }
        if(selected.keySet().stream().anyMatch(p->!allowed.test(p)))return null;
        var body=BlastStructuralCollapse.detach(world,selected);
        if(body==null)return null;
        if(!world.breakBlock(root,true)){body.disassemble();return null;}
        body.setPhysicsGravity(true);
        // Blast loads the lowest surviving section; offset force creates the tipping torque.
        Vec3d forcePoint=Vec3d.ofCenter(root.up());
        body.weaponImpulse(forcePoint,direction,damage);
        return body;
    }

    private record CanopyStep(BlockPos pos,int distance) {}

    private static void addCanopy(ServerWorld world,BlockPos root,Map<BlockPos,BlockState> selected) {
        var access=WorldAccess.of(world);
        var pending=new ArrayDeque<CanopyStep>();
        var seen=new HashSet<BlockPos>(selected.keySet());
        for(var log:selected.keySet())pending.addLast(new CanopyStep(log,0));
        while(!pending.isEmpty()&&selected.size()<TREE_LIMIT) {
            var source=pending.removeFirst();
            int distance=source.distance()+1;
            if(distance>CANOPY_DISTANCE)continue;
            for(var direction:Direction.values()) {
                var leaf=source.pos().offset(direction);
                if(leaf.getY()<=root.getY()||!seen.add(leaf)||selected.size()>=TREE_LIMIT)continue;
                var state=access.readIfLoaded(leaf).orElse(null);
                if(state==null||!state.isIn(BlockTags.LEAVES)||!state.getFluidState().isEmpty())continue;
                // A lower vanilla distance means that a different, standing log
                // supports this leaf more closely than the trunk being cut.
                if(state.contains(LeavesBlock.PERSISTENT)&&state.get(LeavesBlock.PERSISTENT)
                  ||state.contains(LeavesBlock.DISTANCE)&&state.get(LeavesBlock.DISTANCE)<distance)continue;
                selected.put(leaf,state);
                pending.addLast(new CanopyStep(leaf,distance));
            }
        }
    }
}
