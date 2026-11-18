package com.terminaldetector.drmd.entity.model;

import com.terminaldetector.drmd.client.presentation.Ps1AssetProfile;
import java.util.*;

/** Existing authored Pyro asset geometry, with a cached coarse variant that removes cosmetic boxes. */
public final class PyroShipGeometry {
    public record Box(int u,int v,float x,float y,float z,float width,float height,float depth,boolean cosmetic){}
    public record Part(String name,String parent,float x,float y,float z,float pitch,float yaw,float roll,List<Box> boxes){
        public Part{boxes=List.copyOf(boxes);}
    }
    private PyroShipGeometry(){}
    private static final List<Part> NATIVE=List.of(
        new Part("fuselage","",0,0,0,0f,0f,0f,List.of(
            new Box(2,2,-2.5f,-2.5f,-4f,5f,5f,10f,false),
            new Box(2,18,-1.5f,-1.5f,-8f,3f,3f,4f,false),
            new Box(20,18,-0.8f,-0.8f,-10f,1.6f,1.6f,2f,false),
            new Box(2,96,-0.6f,-3.4f,-1f,1.2f,0.9f,3f,true),
            new Box(2,104,-1f,2.5f,-2f,2f,0.6f,5f,true)
        )),
        new Part("canard_l","",0,0,0,0f,0f,0f,List.of(
            new Box(20,96,-4.5f,-0.4f,-6.5f,3f,0.8f,1.6f,true)
        )),
        new Part("canard_r","",0,0,0,0f,0f,0f,List.of(
            new Box(20,96,1.5f,-0.4f,-6.5f,3f,0.8f,1.6f,true)
        )),
        new Part("wing_l","",-2f,0f,0f,0f,0f,0.12f,List.of(
            new Box(44,2,-7f,-1.5f,-3f,7f,3f,6f,false)
        )),
        new Part("wing_r","",2f,0f,0f,0f,0f,-0.12f,List.of(
            new Box(44,2,0f,-1.5f,-3f,7f,3f,6f,false)
        )),
        new Part("laser_l","wing_l",0,0,0,0f,0f,0f,List.of(
            new Box(44,20,-7.5f,-1f,-5.5f,2f,2f,2.5f,false),
            new Box(38,96,-7f,-0.6f,-6.5f,1.2f,1.2f,1f,true)
        )),
        new Part("laser_r","wing_r",0,0,0,0f,0f,0f,List.of(
            new Box(44,20,5.5f,-1f,-5.5f,2f,2f,2.5f,false),
            new Box(38,96,5.8f,-0.6f,-6.5f,1.2f,1.2f,1f,true)
        )),
        new Part("missile_l","wing_l",0,0,0,0f,0f,0f,List.of(
            new Box(100,2,-7.5f,-0.5f,0f,2f,2f,2.5f,false),
            new Box(60,96,-7.5f,-0.3f,2.5f,0.8f,0.8f,0.6f,true),
            new Box(60,100,-6.3f,-0.3f,2.5f,0.8f,0.8f,0.6f,true)
        )),
        new Part("missile_r","wing_r",0,0,0,0f,0f,0f,List.of(
            new Box(100,2,5.5f,-0.5f,0f,2f,2f,2.5f,false),
            new Box(60,96,5.5f,-0.3f,2.5f,0.8f,0.8f,0.6f,true),
            new Box(60,100,6.7f,-0.3f,2.5f,0.8f,0.8f,0.6f,true)
        )),
        new Part("top_pod","",0,0,0,0f,0f,0f,List.of(
            new Box(2,28,-2f,-4.5f,-3f,4f,2f,4f,false),
            new Box(80,96,-0.4f,-5.5f,-1f,0.8f,1f,0.8f,true)
        )),
        new Part("top_pod_tubes","",0,0,0,0f,0f,0f,List.of(
            new Box(44,28,-1.5f,-3.5f,-6f,1.2f,1.2f,3f,false),
            new Box(44,28,0.3f,-3.5f,-6f,1.2f,1.2f,3f,false)
        )),
        new Part("bottom_rig","",0,0,0,0f,0f,0f,List.of(
            new Box(74,28,-1.5f,2.5f,-1f,3f,2f,4f,false),
            new Box(90,96,-0.5f,1.8f,-0.5f,1f,0.8f,1f,true)
        )),
        new Part("bottom_rig_emitter","",0,0,0,0f,0f,0f,List.of(
            new Box(114,28,-0.6f,4.5f,0.4f,1.2f,2f,1.2f,false)
        )),
        new Part("fin","",0f,-2f,3f,0.15f,0f,0f,List.of(
            new Box(2,50,-0.5f,-4.5f,0f,1f,4.5f,3.5f,false),
            new Box(100,96,-0.2f,-5.3f,2.6f,0.4f,1f,0.4f,true)
        )),
        new Part("thruster_l","",-1.2f,0f,6f,0f,-0.1f,0f,List.of(
            new Box(34,50,-1f,-1.2f,0f,2f,2.4f,2.5f,false),
            new Box(2,112,-0.7f,-0.9f,2.5f,1.4f,1.8f,0.8f,true)
        )),
        new Part("thruster_r","",1.2f,0f,6f,0f,0.1f,0f,List.of(
            new Box(34,50,-1f,-1.2f,0f,2f,2.4f,2.5f,false),
            new Box(2,112,-0.7f,-0.9f,2.5f,1.4f,1.8f,0.8f,true)
        ))
    );
    private static final List<Part> LOW_POLY=NATIVE.stream().map(part->new Part(part.name,part.parent,
            part.x,part.y,part.z,part.pitch,part.yaw,part.roll,part.boxes.stream().filter(box->!box.cosmetic).toList()))
            .filter(part->!part.boxes.isEmpty()).toList();
    public static List<Part> parts(Ps1AssetProfile profile){return profile==Ps1AssetProfile.LOW_POLY?LOW_POLY:NATIVE;}
    public static int cuboids(Ps1AssetProfile profile){return parts(profile).stream().mapToInt(part->part.boxes.size()).sum();}
    public static int triangles(Ps1AssetProfile profile){return cuboids(profile)*12;}
}
