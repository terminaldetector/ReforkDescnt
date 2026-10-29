package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Predicate;

/** World-owned full-volume hull broadphase. No chunk reads, entity scans or client class loading.
 * Weak world keys AND weak body references prevent the static registry from retaining a closed world.
 * A world's tree is used only on that world's simulation/render thread. */
public final class BodySpatialIndex {
    private static final class State {
        final AabbTree<Integer> tree=new AabbTree<>();
        final Map<Integer,WeakReference<BlockBodyEntity>> bodies=new HashMap<>();
    }
    private static final Map<World,State> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    private BodySpatialIndex(){}
    public static void registerServer() {
        ServerWorldEvents.UNLOAD.register((server,world)->close(world));
    }
    public static void track(BlockBodyEntity body) {
        if(body.isRemoved())return;
        State s=WORLDS.computeIfAbsent(body.getWorld(),w->new State());
        s.bodies.put(body.getId(),new WeakReference<>(body));update(body);
    }
    public static void untrack(BlockBodyEntity body) {
        State s=WORLDS.get(body.getWorld());if(s==null)return;
        var ref=s.bodies.get(body.getId());
        if(ref!=null&&ref.get()==body){s.bodies.remove(body.getId());s.tree.remove(body.getId());}
    }
    /** Does not enroll unspawned NBT clones or partially constructed entities. */
    public static void update(BlockBodyEntity body) {
        State s=WORLDS.get(body.getWorld());if(s==null)return;
        var ref=s.bodies.get(body.getId());if(ref==null||ref.get()!=body)return;
        if(body.isRemoved()){untrack(body);return;}
        var bounds=body.spatialBounds();
        if(bounds.isPresent())s.tree.update(body.getId(),bounds.get());else s.tree.remove(body.getId());
    }
    public static List<BlockBodyEntity> query(World world,Box area) {
        State s=WORLDS.get(world);if(s==null)return List.of();
        var found=new ArrayList<BlockBodyEntity>();
        for(int id:s.tree.query(bounds(area)).values()) {
            var ref=s.bodies.get(id);var body=ref==null?null:ref.get();
            if(body==null||body.isRemoved()||body.getWorld()!=world){s.bodies.remove(id);s.tree.remove(id);}
            else found.add(body);
        }
        found.sort(Comparator.comparingInt(Entity::getId));return List.copyOf(found);
    }
    /** Keep vanilla behavior for ordinary entities, substitute this index only for BlockBody. */
    public static List<Entity> otherEntities(World world,Entity except,Box area,Predicate<? super Entity> accepted) {
        var found=new ArrayList<>(world.getOtherEntities(except,area,e->!(e instanceof BlockBodyEntity)&&accepted.test(e)));
        for(var body:query(world,area))if(body!=except&&accepted.test(body))found.add(body);
        return found;
    }
    public static void close(World world){WORLDS.remove(world);}
    public static void closeClients(){synchronized(WORLDS){WORLDS.keySet().removeIf(w->w.isClient);}}
    private static BodyBounds bounds(Box b) {
        // Plane/point query boxes are legitimate; the numeric primitive requires positive extents.
        double x=b.maxX==b.minX?Math.nextUp(b.maxX):b.maxX;
        double y=b.maxY==b.minY?Math.nextUp(b.maxY):b.maxY;
        double z=b.maxZ==b.minZ?Math.nextUp(b.maxZ):b.maxZ;
        return new BodyBounds(new Vec3(b.minX,b.minY,b.minZ),new Vec3(x,y,z));
    }
}
