package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.model.DescentDroneModel;
import com.terminaldetector.drmd.entity.model.SpiderTurretModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LowPolyMobMeshesTest {
    /** Observe actual baked cuboid emissions, including transformed positions, UVs and normals. */
    private static Map<String,List<List<Float>>> baked(ModelPart root){
        var result=new LinkedHashMap<String,List<List<Float>>>();
        root.forEachCuboid(new MatrixStack(),(entry,path,index,cuboid)->{
            var sink=new Sink();cuboid.renderCuboid(entry,sink,15728880,0,0xffffffff);
            assertEquals(24,sink.vertices.size());String key=(path.startsWith("/")?path.substring(1):path)+"#"+index;result.put(key,List.copyOf(sink.vertices));
        });return result;
    }
    private static void subset(Map<String,List<List<Float>>> full,Map<String,List<List<Float>>> coarse,int nativeCount,int coarseCount){
        assertEquals(nativeCount,full.size());assertEquals(coarseCount,coarse.size());
        coarse.forEach((path,vertices)->assertEquals(full.get(path),vertices,"retained baked geometry/UV mapping: "+path));
        assertEquals(nativeCount*12,full.values().stream().mapToInt(List::size).sum()/2);
        assertEquals(coarseCount*12,coarse.values().stream().mapToInt(List::size).sum()/2);
    }
    @Test void scoutBakesSixToFourRealCuboidsWithoutChangingKeptUvsOrTheSpinRig(){
        var full=DescentDroneModel.getTexturedModelData().createModel();
        var coarse=DescentDroneModel.getLowPolyTexturedModelData().createModel();
        subset(baked(full),baked(coarse),6,4);
        assertFalse(full.getChild("spar_n").isEmpty());assertFalse(full.getChild("spar_s").isEmpty());
        assertTrue(coarse.getChild("spar_n").isEmpty());assertTrue(coarse.getChild("spar_s").isEmpty());
        var model=new DescentDroneModel<net.minecraft.entity.Entity>(coarse);model.setAngles(null,0,0,4,0,0);
        assertEquals(.6f,coarse.getChild("spar_n").roll,1e-6);assertEquals(-.6f,coarse.getChild("spar_s").roll,1e-6);
        assertEquals(Set.of("core#0","eye#0","spar_e#0","spar_w#0"),baked(coarse).keySet());
        assertEquals(6,baked(full).size());
    }
    @Test void spiderBakesTwelveToEightRealCuboidsAndRetainsEveryMovingLegAndWeapon(){
        var full=SpiderTurretModel.getTexturedModelData().createModel();
        var coarse=SpiderTurretModel.getLowPolyTexturedModelData().createModel();
        subset(baked(full),baked(coarse),12,8);assertDoesNotThrow(()->new SpiderTurretModel(coarse));
        assertNotNull(coarse.getChild("head").getChild("mg"));assertNotNull(coarse.getChild("head").getChild("emitter"));
        for(int i=0;i<4;i++){
            var a=full.getChild("leg"+i);var b=coarse.getChild("leg"+i);
            assertEquals(a.pivotX,b.pivotX);assertEquals(a.pivotY,b.pivotY);assertEquals(a.pivotZ,b.pivotZ);assertEquals(a.yaw,b.yaw);
            assertTrue(baked(coarse).containsKey("leg"+i+"#0"));assertFalse(baked(coarse).containsKey("leg"+i+"#1"));
        }
        assertEquals(12,baked(full).size());
    }
    private static final class Sink implements VertexConsumer {
        private float x,y,z,u,v;private final List<List<Float>> vertices=new ArrayList<>();
        public VertexConsumer vertex(float x,float y,float z){this.x=x;this.y=y;this.z=z;return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer texture(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer overlay(int u,int v){return this;}
        public VertexConsumer light(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){vertices.add(List.of(this.x,this.y,this.z,u,v,x,y,z));return this;}
    }
}
