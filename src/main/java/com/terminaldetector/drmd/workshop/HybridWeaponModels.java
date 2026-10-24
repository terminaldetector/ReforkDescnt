package com.terminaldetector.drmd.workshop;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared original procedural meshes. Inches; +X right, +Y up, +Z aft; muzzle is (0,0,0).
 * Polygon shells/coils and discrete voxel fittings are separate, named parts. */
public final class HybridWeaponModels {
    public static final String IMPULSE = "hybrid_impulse", PLASMA = "hybrid_plasma", RAIL = "hybrid_rail";
    public enum Material {
        OLIVE(0x8b9468, false), STEEL(0x8b9bab, false), DARK(0x29333e, false),
        COPPER(0xc18c59, false), CELL(0x506579, false), ORANGE(0xffa335, true),
        CYAN(0x61edff, true), VIOLET(0xbc8bff, true);
        public final int rgb; public final boolean emissive;
        Material(int rgb, boolean emissive) { this.rgb = rgb; this.emissive = emissive; }
    }
    public record Point(float x, float y, float z) {}
    public record Face(Point a, Point b, Point c, Point d) {
        public List<Point> points() { return List.of(a,b,c,d); }
    }
    public record Part(String name, Material material, boolean moving, List<Face> faces) {}
    private static final Map<String,List<Part>> MODELS = Map.of(
            IMPULSE, impulse(), PLASMA, plasma(), RAIL, rail());
    private HybridWeaponModels() {}
    public static boolean contains(String id) { return MODELS.containsKey(id); }
    public static List<Part> parts(String id) { return MODELS.getOrDefault(id, List.of()); }

    /** Same module transform for GUI and world. Origin remains the physical muzzle at any angle/scale. */
    public static Vector3f transform(ClusterModule m, Point p, float recoil, boolean moving) {
        var v = new Vector3f(p.x(), p.y(), p.z() + (moving ? recoil * m.kick : 0));
        v.mul(m.scaleX, m.scaleY, m.scaleZ);
        new Quaternionf().rotateZ(rad(m.roll)).rotateY(rad(m.yaw)).rotateX(rad(m.pitch)).transform(v);
        return v;
    }
    private static float rad(float degrees) { return (float)Math.toRadians(degrees); }

    public static List<ClusterModule> sample(String model) {
        if (!contains(model)) throw new IllegalArgumentException("Unknown hybrid sample: " + model);
        List<ClusterModule> modules = new ArrayList<>();
        for (int side : new int[]{-1,1}) {
            ClusterModule m = WeaponClusters.make(side < 0 ? "SideLeft" : "SideRight", 1,
                    side < 0 ? "Port " + model.substring(7) : "Starboard " + model.substring(7),
                    model, 34, side * 15, -9, 1, true, side < 0 ? 1 : 2);
            // Parallel barrels: aim is along hull forward, positions are true muzzle anchors.
            m.yaw = 0; m.kick = model.equals(RAIL) ? 2.4f : 1.2f;
            modules.add(m);
        }
        return modules;
    }
    public static String weapon(String model) {
        return switch(model) { case IMPULSE -> "laser_pulse"; case PLASMA -> "plasma";
            case RAIL -> "gravy_railgun"; default -> throw new IllegalArgumentException(model); };
    }

    private static List<Part> impulse() {
        var b = new Builder();
        b.shell("chamfered breech", Material.OLIVE, 0,0,13, 5.8f,5.1f,11, .8f, false);
        b.shell("receiver spine", Material.DARK, 0,-.5f,15, 3.8f,4,13,.5f,false);
        b.ring("octagonal barrel",Material.STEEL,0,0,0, 1.45f,.83f,10);
        b.ring("muzzle collar",Material.DARK,0,0,0,1.9f,.83f,1.3f);
        for(int i=0;i<3;i++) b.ring("cooling band "+i,Material.DARK,0,0,3+i*2.4f,1.75f,1.43f,.7f);
        for(int side:new int[]{-1,1}) {
            b.shell("side armour",Material.OLIVE,side*2.5f,.3f,12,1.2f,3.8f,9,.3f,false);
            for(int i=0;i<4;i++) b.box("vent "+side+" "+i,Material.DARK,side*3.12f,.7f,9+i*1.6f,.18f,1.2f,.7f,false);
            b.box("voxel cell",Material.CELL,side*2.5f,-2.8f,16,2,2,4,false);
            b.box("cell latch",Material.COPPER,side*2.5f,-3.85f,16,1.4f,.3f,1.3f,false);
        }
        b.box("cycling bolt",Material.STEEL,0,2.5f,13,1.8f,.6f,5,true);
        b.box("orange status strip",Material.ORANGE,0,2.9f,14,.5f,.15f,3,false);
        b.box("voxel hull socket",Material.DARK,0,-3.5f,20,4,2,3,false);
        return List.copyOf(b.parts);
    }
    private static List<Part> plasma() {
        var b = new Builder();
        b.shell("chamfered power housing",Material.STEEL,0,0,13,7,6,10,1.3f,false);
        b.shell("rear ceramic cover",Material.CELL,0,.5f,18,5.8f,5,4,.9f,false);
        b.ring("hollow emitter lip",Material.DARK,0,0,0,2.8f,1.75f,1.3f);
        b.ring("luminous aperture",Material.CYAN,0,0,.35f,1.8f,1.5f,.5f);
        b.ring("inner chamber",Material.DARK,0,0,1,2.2f,1.6f,8);
        for(int i=0;i<4;i++) {
            b.ring("exposed copper coil "+i,Material.COPPER,0,0,2+i*1.7f,3,2.3f,.65f);
            b.ring("coil separator "+i,Material.CYAN,0,0,2.7f+i*1.7f,2.55f,2.25f,.45f);
        }
        for(int side:new int[]{-1,1}) {
            b.shell("emitter prong",Material.STEEL,side*2.9f,0,5,1.3f,1.4f,10,.3f,false);
            b.box("power feed",Material.CYAN,side*3.58f,0,13,.2f,1,5,false);
            for(int i=0;i<3;i++) b.box("voxel power cassette",Material.CELL,side*2.6f,-3.4f,11+i*2.2f,2.1f,2,1.7f,false);
        }
        b.box("inductor shuttle",Material.COPPER,0,3.2f,13,2,.65f,5,true);
        b.box("charge window",Material.CYAN,0,3.6f,13,1,.15f,3,false);
        b.box("voxel hull socket",Material.DARK,0,-4,19,4,2,3,false);
        return List.copyOf(b.parts);
    }
    private static List<Part> rail() {
        var b = new Builder();
        b.shell("armoured capacitor housing",Material.CELL,0,0,17,7,6.4f,9,1,false);
        b.shell("chamfered rear bridge",Material.STEEL,0,.5f,21,6,5,3,.8f,false);
        for(int side:new int[]{-1,1}) {
            b.shell("separated acceleration rail",Material.STEEL,side*2.2f,0,7,1.8f,2.8f,14,.45f,false);
            b.box("open channel electrode",Material.VIOLET,side*1.23f,0,6,.18f,1.5f,11,false);
            for(int i=0;i<5;i++) b.box("rail clamp "+i,Material.DARK,side*2.2f,0,2+i*2.4f,2.3f,3.3f,.6f,false);
            b.box("voxel capacitor",Material.CELL,side*3.1f,-3.2f,17,2.6f,2.4f,5,false);
            b.box("capacitor load bar",Material.VIOLET,side*3.1f,-4.45f,17,1.6f,.18f,3,false);
        }
        b.box("recoiling carriage",Material.COPPER,0,2.7f,16,2.4f,.8f,6,true);
        b.box("load indicator",Material.VIOLET,0,3.2f,17,.65f,.2f,4,false);
        b.box("voxel hull socket",Material.DARK,0,-4,22,4,2,3,false);
        return List.copyOf(b.parts);
    }

    private static class Builder {
        final List<Part> parts = new ArrayList<>();
        void box(String name,Material mat,float x,float y,float z,float w,float h,float length,boolean moving) {
            shell(name,mat,x,y,z,w,h,length,0,moving);
        }
        void shell(String name,Material mat,float x,float y,float z,float w,float h,float length,float chamfer,boolean moving) {
            float a=w/2,c=h/2,k=chamfer;
            float[][] xy={{-a+k,-c},{a-k,-c},{a,-c+k},{a,c-k},{a-k,c},{-a+k,c},{-a,c-k},{-a,-c+k}};
            var faces = new ArrayList<Face>();
            for(int i=0;i<8;i++) {
                int j=(i+1)%8;
                if(xy[i][0]==xy[j][0] && xy[i][1]==xy[j][1]) continue;
                faces.add(new Face(point(x,y,z-length/2,xy[i]),point(x,y,z-length/2,xy[j]),
                        point(x,y,z+length/2,xy[j]),point(x,y,z+length/2,xy[i])));
                faces.add(new Face(new Point(x,y,z-length/2),point(x,y,z-length/2,xy[j]),point(x,y,z-length/2,xy[i]),new Point(x,y,z-length/2)));
                faces.add(new Face(new Point(x,y,z+length/2),point(x,y,z+length/2,xy[i]),point(x,y,z+length/2,xy[j]),new Point(x,y,z+length/2)));
            }
            parts.add(new Part(name,mat,moving,List.copyOf(faces)));
        }
        Point point(float x,float y,float z,float[] p) { return new Point(x+p[0],y+p[1],z); }
        Point ringPoint(float x,float y,float z,float radius,int index) {
            double angle=Math.PI*2*index/8+Math.PI/8;
            return new Point(x+(float)Math.cos(angle)*radius,y+(float)Math.sin(angle)*radius,z);
        }
        void ring(String name,Material mat,float x,float y,float z,float outer,float inner,float length) {
            var f=new ArrayList<Face>();
            for(int i=0;i<8;i++) {
                int j=(i+1)%8;
                Point a=ringPoint(x,y,z,outer,i),b=ringPoint(x,y,z,outer,j),c=ringPoint(x,y,z+length,outer,j),d=ringPoint(x,y,z+length,outer,i);
                Point e=ringPoint(x,y,z,inner,i),g=ringPoint(x,y,z,inner,j),h=ringPoint(x,y,z+length,inner,j),k=ringPoint(x,y,z+length,inner,i);
                f.add(new Face(a,b,c,d)); f.add(new Face(g,e,k,h));
                f.add(new Face(e,g,b,a)); f.add(new Face(d,c,h,k));
            }
            parts.add(new Part(name,mat,false,List.copyOf(f)));
        }
    }
}
