package com.terminaldetector.drmd.world.fixture.city;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.terminaldetector.drmd.world.fixture.city.CityBlueprint.*;

/** Original small damaged-ring/Citadel blockout. Uses the existing city CAS/WAL, never worldgen. */
public final class OrbitalRouteBlueprint {
    public static final int VERSION=1;
    public record Gate(String id,Point position){public Gate{if(id==null||id.isBlank()||position==null)throw new IllegalArgumentException("route gate");}}
    private static final int[][] OCTAGON={{-64,0},{-45,45},{0,64},{45,45},{64,0},{45,-45},{0,-64},{-45,-45}};
    private OrbitalRouteBlueprint(){}
    public static List<Gate> gates(Point anchor){
        return List.of(gate("ring-west",anchor,-64,9,0),gate("southern-arc",anchor,-45,9,45),
            gate("service-hangar",anchor,-24,9,0),gate("citadel-west",anchor,-11,9,0),
            gate("citadel-shaft",anchor,0,9,0),gate("upper-gallery",anchor,0,25,0),
            gate("citadel-east",anchor,11,25,0),gate("ring-east",anchor,64,9,0),gate("return",anchor,-64,9,0));
    }
    private static Gate gate(String id,Point anchor,int x,int y,int z){return new Gate(id,new Point(Math.addExact(anchor.x(),x),Math.addExact(anchor.y(),y),Math.addExact(anchor.z(),z)));}
    public static Plan create(long seed,Point anchor,Limits limits){
        var all=new LinkedHashMap<Point,String>();var buildings=new ArrayList<Building>();int ruined=Math.floorMod(seed,8);
        for(int section=0;section<8;section++){
            var local=new LinkedHashMap<Point,String>();int[] a=OCTAGON[section],b=OCTAGON[(section+1)%8];int steps=Math.max(Math.abs(b[0]-a[0]),Math.abs(b[1]-a[1]));
            for(int t=0;t<=steps;t++){
                if(section==ruined&&Math.abs(t-steps/2)<=3)continue;
                int x=a[0]+Math.floorDiv((b[0]-a[0])*t,steps),z=a[1]+Math.floorDiv((b[1]-a[1])*t,steps);
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)put(local,anchor,x+dx,8,z+dz,TILE);
            }
            for(int h=0;h<8;h++)put(local,anchor,a[0],h,a[1],METAL);
            merge(all,local);buildings.add(building(Archetype.GANTRY,anchor,a[0],9,a[1],b[0],9,b[1],local));
        }
        var hangar=new LinkedHashMap<Point,String>();
        for(int x=-32;x<=-16;x++)for(int z=-6;z<=6;z++){
            put(hangar,anchor,x,8,z,TILE);
            if(x==-32||x==-16||z==-6||z==6)for(int y=9;y<=19;y++)
                if((x==-32||x==-16)&&(z==-6||z==6)||y==16||y==19)put(hangar,anchor,x,y,z,METAL);
        }
        merge(all,hangar);buildings.add(building(Archetype.HANGAR,anchor,-24,9,-6,-24,9,6,hangar));
        var hub=new LinkedHashMap<Point,String>();
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
            boolean shaft=Math.abs(x)<=2&&Math.abs(z)<=2;
            for(int y:new int[]{8,16,24})if(!shaft)put(hub,anchor,x,y,z,TILE);
            if(Math.abs(x)==10&&Math.abs(z)==10)for(int y=0;y<=34;y++)put(hub,anchor,x,y,z,STONE);
            if(Math.abs(x)==10||Math.abs(z)==10)for(int y:new int[]{15,23,31})put(hub,anchor,x,y,z,STONE);
        }
        merge(all,hub);buildings.add(building(Archetype.REACTOR_HALL,anchor,-10,9,0,10,25,0,hub));
        var route=gates(anchor);
        for(int i=1;i<route.size();i++)clearPassage(all,route.get(i-1).position(),route.get(i).position());
        return CityBlueprint.assembled(seed,anchor,limits,buildings,all);
    }
    private static Building building(Archetype type,Point anchor,int x,int y,int z,int ex,int ey,int ez,Map<Point,String> cells){
        int count=(int)cells.values().stream().filter(s->!s.equals(AIR)).count();
        return new Building(type,gate("entry",anchor,x,y,z).position(),gate("exit",anchor,ex,ey,ez).position(),count);
    }
    private static void put(Map<Point,String> cells,Point anchor,int x,int y,int z,String material){cells.put(gate("cell",anchor,x,y,z).position(),material);}
    private static void merge(Map<Point,String> all,Map<Point,String> module){
        for(var e:module.entrySet()){var before=all.putIfAbsent(e.getKey(),e.getValue());if(before!=null&&!before.equals(e.getValue()))throw new IllegalArgumentException("orbital module overlap");}
    }
    private static void clearPassage(Map<Point,String> cells,Point a,Point b){
        int dx=b.x()-a.x(),dy=b.y()-a.y(),dz=b.z()-a.z(),steps=Math.max(1,Math.max(Math.abs(dx),Math.max(Math.abs(dy),Math.abs(dz))));
        for(int t=0;t<=steps;t++){
            int x=a.x()+Math.floorDiv(dx*t,steps),y=a.y()+Math.floorDiv(dy*t,steps),z=a.z()+Math.floorDiv(dz*t,steps);
            for(int sx=-1;sx<=1;sx++)for(int sz=-1;sz<=1;sz++)for(int h=0;h<3;h++)cells.put(new Point(x+sx,y+h,z+sz),AIR);
        }
    }
}
