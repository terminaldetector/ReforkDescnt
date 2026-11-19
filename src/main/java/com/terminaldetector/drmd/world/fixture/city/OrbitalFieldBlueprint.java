package com.terminaldetector.drmd.world.fixture.city;

import com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import static com.terminaldetector.drmd.world.fixture.city.CityBlueprint.*;

/** Original explicit abandoned station + two volumetric asteroids, using existing64-cell authoring. */
public final class OrbitalFieldBlueprint {
    public static final int VERSION=1;
    public record ObjectSpec(String id,OrbitalObjectGeometry.Shape shape,Point origin,int sizeX,int sizeY,int sizeZ){
        public ObjectSpec{if(id==null||id.length()>48||!id.matches("[a-z0-9_]+")||origin==null||!OrbitalObjectGeometry.supports(shape,sizeX,sizeY,sizeZ))throw new IllegalArgumentException("orbital object specification");}
        public Point centre(){return new Point(Math.addExact(origin.x(),sizeX/2),Math.addExact(origin.y(),sizeY/2),Math.addExact(origin.z(),sizeZ/2));}
        public boolean contains(int x,int y,int z){return x>=origin.x()&&(long)x<origin.x()+(long)sizeX&&y>=origin.y()&&(long)y<origin.y()+(long)sizeY&&z>=origin.z()&&(long)z<origin.z()+(long)sizeZ;}
    }
    private OrbitalFieldBlueprint(){}
    private static Point offset(Point a,int x,int y,int z){return new Point(Math.addExact(a.x(),x),Math.addExact(a.y(),y),Math.addExact(a.z(),z));}
    public static List<ObjectSpec> objects(Point anchor){
        return List.of(new ObjectSpec("abandoned_station",OrbitalObjectGeometry.Shape.STATION,offset(anchor,-12,0,-10),24,16,20),
            new ObjectSpec("inner_asteroid",OrbitalObjectGeometry.Shape.ASTEROID,offset(anchor,-56,20,24),12,12,12),
            new ObjectSpec("outer_asteroid",OrbitalObjectGeometry.Shape.ASTEROID,offset(anchor,32,30,-36),16,12,12));
    }
    public static Plan create(long seed,Point anchor,Limits limits){
        var all=new LinkedHashMap<Point,String>();var buildings=new ArrayList<Building>();
        for(var object:objects(anchor)){
            var material=new LinkedHashMap<Point,String>();
            for(var cell:OrbitalObjectGeometry.cells(object.shape(),object.sizeX(),object.sizeY(),object.sizeZ()))material.put(offset(object.origin(),cell.x(),cell.y(),cell.z()),cell.state());
            for(int x=0;x<object.sizeX();x++)for(int y=0;y<object.sizeY();y++)for(int z=0;z<object.sizeZ();z++){
                var p=offset(object.origin(),x,y,z);if(all.putIfAbsent(p,material.getOrDefault(p,AIR))!=null)throw new IllegalArgumentException("overlapping orbital field objects");
            }
            var centre=object.centre();var entry=offset(object.origin(),object.sizeX()/2,object.shape()==OrbitalObjectGeometry.Shape.STATION?object.sizeY()/4+1:object.sizeY(),0);
            buildings.add(new Building(object.shape()==OrbitalObjectGeometry.Shape.STATION?Archetype.HANGAR:Archetype.COURTYARD,entry,centre,material.size()));
        }
        return CityBlueprint.assembled(seed,anchor,limits,buildings,all);
    }
}
