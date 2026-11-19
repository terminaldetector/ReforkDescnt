package com.terminaldetector.drmd.world.gravity;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.*;

/** Dimension-scoped 3D broad phase. No world reads, tickets or entity references. */
final class GravityFieldIndex {
    private static final int LEVELS=32;
    private record Cell(long x,long y,long z) {}
    private record Membership(RegistryKey<World> dimension,int level,List<Cell> cells) {}
    private static final class Partition {
        final Map<Integer,Map<Cell,Set<UUID>>> levels=new HashMap<>();
        final Set<UUID> global=new HashSet<>();
        boolean empty(){return levels.isEmpty() && global.isEmpty();}
    }
    private long revision;
    private final Map<UUID,GravityFields.Field> fields=new HashMap<>();
    private final Map<UUID,Membership> memberships=new HashMap<>();
    private final Map<RegistryKey<World>,Partition> dimensions=new HashMap<>();

    synchronized void put(GravityFields.Field field) {
        if(field.equals(fields.get(field.id())))return;
        remove(field.id());
        // A cylinder can extend beyond radius along a world axis when tilted.
        // sqrt(2)*radius+3 also encloses a tilted plane's 3-block half-thickness.
        double extent=Math.sqrt(2)*field.radius()+3;
        int level=0;
        while(level<LEVELS && size(level)<extent*2)level++;
        var partition=dimensions.computeIfAbsent(field.worldKey(),ignored->new Partition());
        List<Cell> cells=new ArrayList<>(8);
        if(level==LEVELS) {
            // Preserve the old API for exceptionally large fields, without allocating
            // an unbounded number of cells. Normal emitters use at most eight cells.
            partition.global.add(field.id());
        } else {
            double size=size(level);
            double x=field.origin().getX()+.5,y=field.origin().getY()+.5,z=field.origin().getZ()+.5;
            var buckets=partition.levels.computeIfAbsent(level,ignored->new HashMap<>());
            for(long cx=cell(x-extent,size);cx<=cell(x+extent,size);cx++)
                for(long cy=cell(y-extent,size);cy<=cell(y+extent,size);cy++)
                    for(long cz=cell(z-extent,size);cz<=cell(z+extent,size);cz++) {
                        var key=new Cell(cx,cy,cz);cells.add(key);
                        buckets.computeIfAbsent(key,ignored->new HashSet<>()).add(field.id());
                    }
        }
        fields.put(field.id(),field);revision++;
        memberships.put(field.id(),new Membership(field.worldKey(),level,cells));
    }

    synchronized void remove(UUID id) {
        var membership=memberships.remove(id);
        fields.remove(id);
        if(membership==null)return;
        revision++;
        var partition=dimensions.get(membership.dimension());
        if(membership.level()==LEVELS)partition.global.remove(id);
        else {
            var buckets=partition.levels.get(membership.level());
            for(var cell:membership.cells()) {
                var bucket=buckets.get(cell);bucket.remove(id);
                if(bucket.isEmpty())buckets.remove(cell);
            }
            if(buckets.isEmpty())partition.levels.remove(membership.level());
        }
        if(partition.empty())dimensions.remove(membership.dimension());
    }

    synchronized List<GravityFields.Field> candidates(RegistryKey<World> dimension,Vec3d point) {
        // Null is the legacy diagnostic query across all dimensions, not gameplay.
        if(dimension==null)return all();
        Set<UUID> ids=new TreeSet<>();
        collect(dimensions.get(dimension),point,ids);
        collect(dimensions.get(null),point,ids);
        List<GravityFields.Field> result=new ArrayList<>(ids.size());
        for(var id:ids)result.add(fields.get(id));
        return result;
    }

    private static void collect(Partition partition,Vec3d point,Set<UUID> ids) {
        if(partition==null)return;
        ids.addAll(partition.global);
        for(var entry:partition.levels.entrySet()) {
            double size=size(entry.getKey());
            var bucket=entry.getValue().get(new Cell(cell(point.x,size),cell(point.y,size),cell(point.z,size)));
            if(bucket!=null)ids.addAll(bucket);
        }
    }

    synchronized List<GravityFields.Field> all() {
        return fields.values().stream().sorted(Comparator.comparing(GravityFields.Field::id)).toList();
    }
    synchronized boolean hasFields(RegistryKey<World> dimension) {
        return dimension==null?!fields.isEmpty():dimensions.containsKey(dimension) || dimensions.containsKey(null);
    }
    synchronized long revision(){return revision;}
    synchronized List<net.minecraft.util.math.Box> interestRegions(RegistryKey<World> dimension) {
        var regions=new java.util.LinkedHashSet<net.minecraft.util.math.Box>();
        var keys=new ArrayList<RegistryKey<World>>();keys.add(dimension);if(dimension!=null)keys.add(null);
        for(var key:keys) {
            var partition=dimensions.get(key);if(partition==null)continue;
            for(var level:partition.levels.keySet().stream().sorted().toList()) {
                double side=size(level);
                var buckets=partition.levels.get(level);
                for(var cell:buckets.keySet().stream().sorted(Comparator.comparingLong(Cell::x).thenComparingLong(Cell::y).thenComparingLong(Cell::z)).toList()) {
                    if(buckets.get(cell).stream().noneMatch(id->fields.get(id).power()>0))continue;
                    regions.add(new net.minecraft.util.math.Box(cell.x*side,cell.y*side,cell.z*side,(cell.x+1)*side,(cell.y+1)*side,(cell.z+1)*side));
                }
            }
            for(var id:partition.global.stream().sorted().toList()) {
                var field=fields.get(id);if(field.power()<=0)continue;
                double reach=Math.min(60000000,Math.sqrt(2)*field.radius()+3);var c=net.minecraft.util.math.Vec3d.ofCenter(field.origin());
                regions.add(new net.minecraft.util.math.Box(c.x-reach,c.y-reach,c.z-reach,c.x+reach,c.y+reach,c.z+reach));
            }
        }
        return List.copyOf(regions);
    }
    synchronized void clear(){fields.clear();memberships.clear();dimensions.clear();revision++;}
    private static double size(int level){return Math.scalb(16.0,level);}
    private static long cell(double coordinate,double size){return (long)Math.floor(coordinate/size);}
}
