package com.terminaldetector.drmd.world.store;

import com.terminaldetector.drmd.world.fixture.city.CityBlueprint;
import com.terminaldetector.drmd.world.fixture.city.OrbitalFieldBlueprint.ObjectSpec;
import com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Exact authored orbital footprints and possible source samples for the old unlayered surface cache. */
final class OrbitalGroundColumns {
    private final List<ObjectSpec> objects = new ArrayList<>();
    private final Set<Long> roofSamples = new HashSet<>();
    private final Set<CityBlueprint.Point> pilotSolids = new HashSet<>();

    static Map<Long, OrbitalGroundColumns> index(List<ObjectSpec> objects, ToIntFunction<OrbitalObjectGeometry.Shape> mapColour) {
        return index(objects,mapColour,List.of(),ignored->0);
    }

    static Map<Long, OrbitalGroundColumns> index(List<ObjectSpec> objects,ToIntFunction<OrbitalObjectGeometry.Shape> mapColour,
                                                List<CityBlueprint.Cell> pilot,ToIntFunction<String> pilotColour) {
        var result = new HashMap<Long, OrbitalGroundColumns>();
        for (var object : objects) {
            var min = object.origin();
            int lastX = min.x() + object.sizeX() - 1, lastZ = min.z() + object.sizeZ() - 1;
            for (int x = min.x() >> 4; x <= lastX >> 4; x++) {
                for (int z = min.z() >> 4; z <= lastZ >> 4; z++) {
                    result.computeIfAbsent(ChunkPos.toLong(x, z), key -> new OrbitalGroundColumns()).objects.add(object);
                }
            }
            int colour = mapColour.applyAsInt(object.shape());
            for (var cell : OrbitalObjectGeometry.cells(object.shape(), object.sizeX(), object.sizeY(), object.sizeZ())) {
                long key = ChunkPos.toLong((min.x() + cell.x()) >> 4, (min.z() + cell.z()) >> 4);
                result.get(key).roofSamples.add(sample(SurfaceSection.encodeHeight(min.y() + cell.y() + 1), colour));
            }
        }
        for(var cell:pilot){
            if(CityBlueprint.AIR.equals(cell.state()))continue;
            var p=cell.position();
            long key=ChunkPos.toLong(p.x()>>4,p.z()>>4);
            var column=result.computeIfAbsent(key,ignored->new OrbitalGroundColumns());
            column.pilotSolids.add(p);
            column.roofSamples.add(sample(SurfaceSection.encodeHeight(p.y()+1),pilotColour.applyAsInt(cell.state())));
        }
        return result;
    }

    boolean contains(int x, int y, int z) {
        for (var object : objects) if (object.contains(x, y, z)) return true;
        return !pilotSolids.isEmpty()&&pilotSolids.contains(new CityBlueprint.Point(x,y,z));
    }

    boolean couldBeOldRoof(short height, int colour) {
        return roofSamples.contains(sample(height, colour));
    }

    private static long sample(short height, int colour) {
        return ((long) height << 24) | (colour & 0xffffff);
    }
}
