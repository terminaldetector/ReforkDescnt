package com.terminaldetector.drmd.world.fixture.city;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Pure original blockout. No Minecraft classes, world queries, actors or startup work. */
public final class CityBlueprint {
    public static final int VERSION=1,MAX_SOLID=65_536,MAX_CUT=65_536,MAX_INSTANCE_SOLID=2_048;
    public static final String AIR="minecraft:air",STONE="minecraft:stone_bricks",TILE="minecraft:deepslate_tiles",ROAD="minecraft:polished_andesite",METAL="minecraft:iron_block";
    public static final Set<String> PALETTE=Set.of(AIR,STONE,TILE,ROAD,METAL,"minecraft:stone");
    private static final Comparator<Point> PAGE_ORDER=Comparator.comparingInt((Point p)->Math.floorDiv(p.y(),16))
        .thenComparingInt(p->Math.floorDiv(p.z(),16)).thenComparingInt(p->Math.floorDiv(p.x(),16)).thenComparing(Comparator.naturalOrder());
    public record Point(int x,int y,int z) implements Comparable<Point>{
        @Override public int compareTo(Point p){int n=Integer.compare(y,p.y);if(n==0)n=Integer.compare(z,p.z);return n==0?Integer.compare(x,p.x):n;}
    }
    /** topY is exclusive; border maxima are inclusive. */
    public record Limits(int bottomY,int topY,int minX,int maxX,int minZ,int maxZ){
        public Limits{if(bottomY>=topY||minX>maxX||minZ>maxZ)throw new IllegalArgumentException("invalid actual world bounds");}
    }
    public enum Archetype { COURTYARD,TOWER,HANGAR,TRANSIT,REACTOR_HALL,LAB_SHAFT,DAMAGED_OFFICE,GANTRY }
    public record Cell(Point position,String state){public Cell{Objects.requireNonNull(position);if(!PALETTE.contains(state))throw new IllegalArgumentException("unsupported city palette");}}
    public record Building(Archetype archetype,Point entry,Point exit,int solidCount){
        public Building{Objects.requireNonNull(archetype);Objects.requireNonNull(entry);Objects.requireNonNull(exit);if(solidCount<=0||solidCount>MAX_INSTANCE_SOLID)throw new IllegalArgumentException("building budget");}
    }
    public record Plan(long seed,Point anchor,Limits limits,List<Building> buildings,List<Cell> cells,int solidCount,int cutCount,String hash){
        public Plan{
            buildings=List.copyOf(buildings);cells=List.copyOf(cells);
            validateBounds(Objects.requireNonNull(anchor),Objects.requireNonNull(limits));
            if(cells.size()>MAX_SOLID+MAX_CUT)throw new IllegalArgumentException("city cell budget");
            var unique=new HashSet<Point>();int solids=0,cuts=0;Point previous=null;
            for(var cell:cells){
                Point p=cell.position();
                if(!unique.add(p)||previous!=null&&PAGE_ORDER.compare(previous,p)>=0)throw new IllegalArgumentException("duplicate or noncanonical city cells");
                if((long)p.x<anchor.x-128L||(long)p.x>anchor.x+127L||(long)p.z<anchor.z-128L||(long)p.z>anchor.z+127L
                    ||(long)p.y<anchor.y-24L||(long)p.y>anchor.y+47L)throw new IllegalArgumentException("city cell outside reviewed bounds");
                if(cell.state().equals(AIR))cuts++;else solids++;previous=p;
            }
            if(solids>MAX_SOLID||cuts>MAX_CUT||solidCount!=solids||cutCount!=cuts)throw new IllegalArgumentException("city material/cut budget");
            if(hash==null||!hash.equals(CityBlueprint.hash(seed,anchor,limits,cells)))throw new IllegalArgumentException("city content hash mismatch");
        }
    }
    private CityBlueprint(){}
    /** Existing validated cell/hash protocol reused by other original manual fixtures. */
    static Plan assembled(long seed,Point anchor,Limits limits,List<Building> buildings,Map<Point,String> authored){
        var sorted=new TreeMap<Point,String>(PAGE_ORDER);sorted.putAll(authored);
        var cells=sorted.entrySet().stream().map(e->new Cell(e.getKey(),e.getValue())).toList();
        int solid=(int)cells.stream().filter(c->!c.state().equals(AIR)).count();
        return new Plan(seed,anchor,limits,buildings,cells,solid,cells.size()-solid,hash(seed,anchor,limits,cells));
    }
    public static Plan create(long seed,Point anchor,Limits limits){
        Objects.requireNonNull(anchor);Objects.requireNonNull(limits);validateBounds(anchor,limits);
        var layout=new ArrayList<Archetype>();for(var a:Archetype.values()){layout.add(a);layout.add(a);}Collections.shuffle(layout,new Random(seed));
        var all=new TreeMap<Point,String>(PAGE_ORDER);var buildings=new ArrayList<Building>();var random=new Random(seed^0x434954595f56314cL);
        for(int i=0;i<16;i++){
            var type=layout.get(i);int width=switch(type){case HANGAR->32;case TOWER,LAB_SHAFT->16;default->24;};
            int depth=switch(type){case TRANSIT,GANTRY,LAB_SHAFT,TOWER->16;default->24;};
            int height=(switch(type){case TOWER->42;case HANGAR->12;case REACTOR_HALL,DAMAGED_OFFICE->24;default->18;})-3*random.nextInt(3);
            int x=anchor.x-120+(i%4)*64+(48-width)/2,z=anchor.z-120+(i/4)*64+(48-depth)/2;
            var local=new TreeMap<Point,String>();int y=anchor.y,base=type==Archetype.TRANSIT||type==Archetype.GANTRY?6:0;
            String frame=type==Archetype.GANTRY||type==Archetype.HANGAR?METAL:STONE;
            for(int a=0;a<width;a++)for(int b=0;b<depth;b++){
                boolean edge=a==0||b==0||a==width-1||b==depth-1;
                boolean shaft=a>=width/2-2&&a<width/2+2&&b>=depth/2-2&&b<depth/2+2;
                if(!shaft)put(local,x+a,y+base,z+b,TILE);
                for(int h=1;h<=height;h++){
                    boolean opening=(b==0||b==depth-1)&&a>=width/2-1&&a<=width/2&&h<=3;
                    boolean post=(a==0||a==width-1)&&(b%4==0||b==depth-1)
                        ||(b==0||b==depth-1)&&(a%4==0||a==width-1);
                    boolean band=edge&&h%6==0;
                    boolean upperFloor=h%6==0&&h<height&&b>=depth/2-2&&b<depth/2+2&&!shaft;
                    boolean roof=h==height&&(a%4==0||b%4==0);
                    boolean damaged=type==Archetype.DAMAGED_OFFICE&&b==0&&a>width/2&&h>height/2;
                    if(!opening&&!damaged&&(post||band||upperFloor||roof))put(local,x+a,y+base+h,z+b,frame);
                }
            }
            // Explicit flight shaft/openings; the surrounding open blockout is not a giant clear cuboid.
            for(int a=width/2-2;a<width/2+2;a++)for(int b=depth/2-2;b<depth/2+2;b++)for(int h=base;h<=base+height;h++)put(local,x+a,y+h,z+b,AIR);
            for(int b=0;b<depth;b++)for(int a=width/2-1;a<=width/2;a++)for(int h=1;h<=3;h++)put(local,x+a,y+base+h,z+b,AIR);
            for(int a:new int[]{0,width-1})for(int b:new int[]{0,depth-1})for(int h=-3;h<base;h++)put(local,x+a,y+h,z+b,frame);
            Point entry=new Point(x+width/2,y+base+1,z),exit=new Point(x+width/2,y+base+1,z+depth-1);
            if(type==Archetype.LAB_SHAFT){
                int cx=x+width/2,cz=z+depth/2;
                for(int a=-3;a<=2;a++)for(int b=-3;b<=2;b++)for(int h=-24;h<0;h++)put(local,cx+a,y+h,cz+b,a==-3||a==2||b==-3||b==2?STONE:AIR);
                exit=new Point(cx,y-23,cz);put(local,exit.x,exit.y,exit.z,AIR);
            }
            if(type==Archetype.REACTOR_HALL)for(int a=1;a<=3;a++)for(int b=1;b<=3;b++)put(local,x+a,y+base+1,z+b,METAL);
            int count=(int)local.values().stream().filter(s->!s.equals(AIR)).count();buildings.add(new Building(type,entry,exit,count));
            for(var e:local.entrySet())merge(all,e.getKey(),e.getValue());
        }
        // Paved eight-block strips within16-block street reservations; slim explicit air routes.
        for(int x=-120;x<120;x++)for(int z=-120;z<120;z++){
            boolean road=false,side=false;
            for(int centre:new int[]{-64,0,64}){road|=x>=centre-4&&x<centre+4||z>=centre-4&&z<centre+4;side|=x>=centre-2&&x<centre+2||z>=centre-2&&z<centre+2;}
            if(road)merge(all,new Point(anchor.x+x,anchor.y,anchor.z+z),ROAD);
            boolean central=x>=-4&&x<4||z>=-4&&z<4;
            if(central||side)for(int h=1;h<=(central?8:4);h++)merge(all,new Point(anchor.x+x,anchor.y+h,anchor.z+z),AIR);
        }
        var cells=all.entrySet().stream().map(e->new Cell(e.getKey(),e.getValue())).toList();int solid=(int)cells.stream().filter(c->!c.state.equals(AIR)).count(),cut=cells.size()-solid;
        if(solid>MAX_SOLID||cut>MAX_CUT)throw new IllegalArgumentException("city material/cut budget");
        return new Plan(seed,anchor,limits,buildings,cells,solid,cut,hash(seed,anchor,limits,cells));
    }
    private static void validateBounds(Point anchor,Limits limits){
        if((long)anchor.x-128<limits.minX||(long)anchor.x+127>limits.maxX||(long)anchor.z-128<limits.minZ||(long)anchor.z+127>limits.maxZ
            ||(long)anchor.y-24<limits.bottomY||(long)anchor.y+47>=limits.topY)throw new IllegalArgumentException("city outside actual world bounds");
    }
    private static void put(Map<Point,String> cells,int x,int y,int z,String state){cells.put(new Point(x,y,z),state);}
    private static void merge(Map<Point,String> cells,Point p,String state){String old=cells.putIfAbsent(p,state);if(old!=null&&!old.equals(state))throw new IllegalArgumentException("overlapping city modules at "+p);}
    private static String hash(long seed,Point anchor,Limits limits,List<Cell> cells){
        try{var d=MessageDigest.getInstance("SHA-256");d.update((VERSION+"|"+seed+"|"+anchor+"|"+limits+"\n").getBytes(StandardCharsets.UTF_8));
            for(var c:cells)d.update((c.position.x+","+c.position.y+","+c.position.z+"="+c.state+"\n").getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(d.digest());
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
