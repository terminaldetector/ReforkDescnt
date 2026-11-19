package com.terminaldetector.drmd.entity.model;

import com.terminaldetector.drmd.entity.FlightEnemyKind;
import java.util.*;

/** Original low-poly hulls in metres: +Z nose,+Y up. Immutable and shared by every instance. */
public final class FlightEnemyMesh {
    public record Vertex(float x,float y,float z){}
    public record Face(List<Vertex> vertices,Vertex normal,int rgb,boolean emissive){public Face{vertices=List.copyOf(vertices);}}
    private static final Map<FlightEnemyKind,List<Face>> MESHES=new EnumMap<>(FlightEnemyKind.class);
    private static final Map<FlightEnemyKind,List<Face>> DISTANT_MESHES = new EnumMap<>(FlightEnemyKind.class);
    static {
        for (var kind : FlightEnemyKind.values()) {
            List<Face> faces = build(kind);
            MESHES.put(kind, faces);
            DISTANT_MESHES.put(kind, kind == FlightEnemyKind.TELEPORT_FLYER ? faces
                : faces.stream().filter(face -> !face.emissive()).toList());
        }
    }
    private FlightEnemyMesh(){}
    public static List<Face> faces(FlightEnemyKind kind){return MESHES.get(Objects.requireNonNull(kind));}
    public static List<Face> faces(FlightEnemyKind kind, boolean detail) {
        return detail ? faces(kind) : DISTANT_MESHES.get(Objects.requireNonNull(kind));
    }
    private static Vertex v(double x,double y,double z){return new Vertex((float)x,(float)y,(float)z);}
    private static Face face(int rgb,boolean emissive,Vertex... vertices) {
        if(vertices.length<3||vertices.length>4)throw new IllegalArgumentException("triangle or quad required");
        var a=vertices[0];var b=vertices[1];var c=vertices[2];
        double ax=b.x-a.x,ay=b.y-a.y,az=b.z-a.z,bx=c.x-a.x,by=c.y-a.y,bz=c.z-a.z;
        double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,len=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(len<1e-10)throw new IllegalArgumentException("degenerate hull face");
        return new Face(List.of(vertices),v(nx/len,ny/len,nz/len),rgb,emissive);
    }
    private static List<Face> build(FlightEnemyKind kind) {
        var f=new ArrayList<Face>();int body=kind.primaryColour,dark=kind.secondaryColour;
        if(kind==FlightEnemyKind.ORB) {
            var top=v(0,.42,0);var bottom=v(0,-.42,0);var ring=new Vertex[]{v(.42,0,0),v(0,0,.36),v(-.42,0,0),v(0,0,-.42)};
            for(int i=0;i<4;i++){f.add(face(i%2==0?body:dark,false,top,ring[(i+1)%4],ring[i]));f.add(face(dark,false,bottom,ring[i],ring[(i+1)%4]));}
            f.add(face(0xb6fff0,true,v(-.10,-.07,.371),v(.10,-.07,.371),v(.10,.07,.371),v(-.10,.07,.371)));
        }else if(kind==FlightEnemyKind.ROCKET) {
            var a=v(-.20,-.20,-.60);var b=v(.20,-.20,-.60);var c=v(.20,.20,-.60);var d=v(-.20,.20,-.60);
            var e=v(-.20,-.20,.40);var g=v(.20,-.20,.40);var h=v(.20,.20,.40);var j=v(-.20,.20,.40);var nose=v(0,0,.85);
            f.add(face(dark,false,a,b,g,e));f.add(face(body,false,d,j,h,c));f.add(face(body,false,a,e,j,d));f.add(face(dark,false,b,c,h,g));
            f.add(face(body,false,e,g,nose));f.add(face(body,false,g,h,nose));f.add(face(body,false,h,j,nose));f.add(face(body,false,j,e,nose));
            f.add(face(dark,false,a,d,c,b));
            for(int sign:new int[]{-1,1}) {
                f.add(face(body,false,v(sign*.18,0,-.55),v(sign*.75,0,-.40),v(sign*.18,0,.10)));
                f.add(face(body,false,v(0,sign*.18,-.55),v(0,sign*.70,-.40),v(0,sign*.18,.10)));
            }
            f.add(face(0xffc78e,true,v(-.14,-.14,-.612),v(-.14,.14,-.612),v(.14,.14,-.612),v(.14,-.14,-.612)));
        }else if(kind==FlightEnemyKind.JET) {
            var nose=v(0,0,.98);var left=v(-1.1,-.08,-.45);var tail=v(0,0,-.75);var right=v(1.1,-.08,-.45);var roof=v(0,.22,-.2);var belly=v(0,-.18,-.1);
            var ring=new Vertex[]{nose,left,tail,right};
            for(int i=0;i<4;i++){f.add(face(i<2?body:dark,false,roof,ring[(i+1)%4],ring[i]));f.add(face(dark,false,belly,ring[i],ring[(i+1)%4]));}
            f.add(face(0x8beaff,true,v(0,.16,.55),v(.13,.21,.12),v(-.13,.21,.12)));
            f.add(face(0xffb76d,true,v(-.18,-.08,-.77),v(-.18,.08,-.77),v(.18,.08,-.77),v(.18,-.08,-.77)));
        }
        if(kind==FlightEnemyKind.TELEPORT_FLYER){
            // Prototype v0: an authored compact core and four phase vanes, not another species' mesh.
            var top=v(0,.34,0);var bottom=v(0,-.34,0);
            var ring=new Vertex[]{v(.34,0,0),v(0,0,.34),v(-.34,0,0),v(0,0,-.34)};
            for(int i=0;i<4;i++){f.add(face(i%2==0?body:dark,false,top,ring[(i+1)%4],ring[i]));f.add(face(dark,false,bottom,ring[i],ring[(i+1)%4]));}
            for(int sign:new int[]{-1,1}){
                f.add(face(0xcbbdff,true,v(sign*.42,-.12,0),v(sign*.70,-.12,0),v(sign*.70,.12,0),v(sign*.42,.12,0)));
                f.add(face(0xcbbdff,true,v(0,-.12,sign*.42),v(0,-.12,sign*.70),v(0,.12,sign*.70),v(0,.12,sign*.42)));
            }
        }
        if(f.isEmpty())throw new IllegalArgumentException("unknown flight hull: "+kind);
        return List.copyOf(f);
    }
}
