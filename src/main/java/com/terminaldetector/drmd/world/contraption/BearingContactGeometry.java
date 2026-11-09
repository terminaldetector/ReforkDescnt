package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.physics.collision.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** A rotary bearing's two mating hardware cells form the joint, not a rubbing cube pair.
 * All other material, including either housing against unrelated parts, keeps its contacts.
 * Geometry is refined only when its immutable collider or material-anchor set changes. */
public final class BearingContactGeometry {
    private record Seam(UUID joint,BodyBounds a,BodyBounds b){}
    private static final Map<CompoundCollider,Map<List<BodyBounds>,CompoundCollider>> REFINED=new WeakHashMap<>();
    private final BodyJointRegistry registry;
    private final CompoundCollider a,b;
    private final List<Seam> seams;
    private BearingContactGeometry(BodyJointRegistry registry,CompoundCollider a,CompoundCollider b,List<Seam> seams){this.registry=registry;this.a=a;this.b=b;this.seams=seams;}
    public static void clear(){REFINED.clear();}
    public static BearingContactGeometry of(ServerWorld world,BlockBodyEntity a,BlockBodyEntity b){
        var registry=BodyJointRegistry.get(world);var seams=new ArrayList<Seam>();
        for(var link:registry.forBody(a.getUuid())){
            if(!link.blockDriven()||link.kind()!=BodyJointConstraint.Kind.MOTOR||!registry.active(link.id(),world.getTime()))continue;
            var first=link.a().body().equals(a.getUuid())?link.a():link.b();var second=first==link.a()?link.b():link.a();
            if(!second.body().equals(b.getUuid())||!a.getLocalBlock(first.cell()).isOf(ModWorldBlocks.MECHANICAL_BEARING)||!b.getLocalBlock(second.cell()).isOf(ModWorldBlocks.MECHANICAL_BEARING))continue;
            seams.add(new Seam(link.id(),cell(first.cell()),cell(second.cell())));
        }
        return seams.isEmpty()?new BearingContactGeometry(registry,null,null,List.of()):new BearingContactGeometry(registry,refine(a.collider(),seams.stream().map(Seam::a).toList()),refine(b.collider(),seams.stream().map(Seam::b).toList()),List.copyOf(seams));
    }
    private static BodyBounds cell(BlockPos p){return new BodyBounds(new Vec3(p.getX(),p.getY(),p.getZ()),new Vec3(p.getX()+1,p.getY()+1,p.getZ()+1));}
    private boolean admitted(BodyBounds first,BodyBounds second){for(var seam:seams)if(first.equals(seam.a)&&second.equals(seam.b)&&registry.find(seam.joint).isPresent())return false;return true;}
    private static BodyPairQuery.Motion shape(BodyPairQuery.Motion motion,CompoundCollider shape){return new BodyPairQuery.Motion(shape,motion.centre(),motion.from(),motion.before(),motion.to(),motion.after());}
    public Optional<BodyPairQuery.Impact> impact(BodyPairQuery.Motion first,BodyPairQuery.Motion second){return seams.isEmpty()?BodyPairQuery.impact(first,second):BodyPairQuery.impact(shape(first,a),shape(second,b),this::admitted);}
    public List<BodyPairQuery.Contact> contacts(BodyPairQuery.Motion first,BodyPairQuery.Motion second,double time,boolean resting){return seams.isEmpty()?BodyPairQuery.contacts(first,second,time,resting):BodyPairQuery.contacts(shape(first,a),shape(second,b),time,resting,this::admitted);}
    private static CompoundCollider refine(CompoundCollider original,List<BodyBounds> cells){
        var key=List.copyOf(cells);var cache=REFINED.computeIfAbsent(original,ignored->new HashMap<>());var cached=cache.get(key);if(cached!=null)return cached;
        var boxes=new ArrayList<>(original.boxes());
        for(var cell:cells){
            var next=new ArrayList<BodyBounds>();
            for(var box:boxes){
                double x0=Math.max(box.min().x(),cell.min().x()),y0=Math.max(box.min().y(),cell.min().y()),z0=Math.max(box.min().z(),cell.min().z());
                double x1=Math.min(box.max().x(),cell.max().x()),y1=Math.min(box.max().y(),cell.max().y()),z1=Math.min(box.max().z(),cell.max().z());
                if(x1<=x0||y1<=y0||z1<=z0){next.add(box);continue;}
                // Six disjoint slabs plus the actual anchor cell preserve the original union.
                add(next,box.min().x(),box.min().y(),box.min().z(),x0,box.max().y(),box.max().z());
                add(next,x1,box.min().y(),box.min().z(),box.max().x(),box.max().y(),box.max().z());
                add(next,x0,box.min().y(),box.min().z(),x1,y0,box.max().z());
                add(next,x0,y1,box.min().z(),x1,box.max().y(),box.max().z());
                add(next,x0,y0,box.min().z(),x1,y1,z0);add(next,x0,y0,z1,x1,y1,box.max().z());add(next,x0,y0,z0,x1,y1,z1);
            }boxes=next;
        }
        var result=new CompoundCollider(boxes);cache.put(key,result);return result;
    }
    private static void add(List<BodyBounds> boxes,double x0,double y0,double z0,double x1,double y1,double z1){if(x1>x0&&y1>y0&&z1>z0)boxes.add(new BodyBounds(new Vec3(x0,y0,z0),new Vec3(x1,y1,z1)));}
}
