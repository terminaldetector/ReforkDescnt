package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.collision.BodyJointConstraint;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class BodyJointCommandGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="joint-operator-commands",tickLimit=60)
    public void realCommandsCreateConfigureAndRemoveMaterialLinksWithoutMovingBodies(TestContext c) {
        var w=c.getWorld();var at=Vec3d.of(c.getAbsolutePos(new BlockPos(8,150,8)));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunk(((int)at.x>>4)+x,((int)at.z>>4)+z);
        var cells=List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()));
        var a=BlockBodyEntity.createLocal(w,cells,at);var b=BlockBodyEntity.createLocal(w,cells,at.add(1,0,0));
        var registry=BodyJointRegistry.get(w);var manager=w.getServer().getCommandManager();var source=w.getServer().getCommandSource().withWorld(w);var va=new Vec3(2,1,0);a.physics().withLinearVelocity(va);b.physics().withLinearVelocity(va);
        var hit=a.localToWorld(new Vec3d(1,.5,.5));var point=coordinates(hit);var add="d6body joint add motor "+a.getUuid()+" "+b.getUuid()+" "+point;
        try {
            manager.executeWithPrefix(source.withLevel(0),add);c.assertTrue(registry.forBody(a.getUuid()).isEmpty(),"operator-only root rejects unauthorized creation");
            var beforeA=a.getPos();var beforeB=b.getPos();manager.executeWithPrefix(source,add);
            var links=registry.forBody(a.getUuid());c.assertTrue(links.size()==1&&links.getFirst().kind()==BodyJointConstraint.Kind.MOTOR,"actual dispatcher creates one motor at the shared material point");var id=links.getFirst().id();
            manager.executeWithPrefix(source,"d6body joint motor "+id+" -0.6 7");var updated=registry.find(id).orElseThrow();c.assertTrue(updated.speed()==-.6&&updated.motorForce()==7,"motor command updates force and speed without a second link");
            manager.executeWithPrefix(source,"d6body joint break_load "+id+" 50 20");updated=registry.find(id).orElseThrow();c.assertTrue(updated.breakForce()==50&&updated.breakTorque()==20,"break load command preserves the joint identity");
            manager.executeWithPrefix(source,"d6body joint add fixed "+a.getUuid()+" "+b.getUuid()+" "+coordinates(hit.add(20,0,0)));c.assertTrue(registry.forBody(a.getUuid()).size()==1,"air/foreign coordinates cannot manufacture a material attachment");
            manager.executeWithPrefix(source,"d6body joint remove "+id);c.assertTrue(registry.forBody(a.getUuid()).isEmpty()&&registry.forBody(b.getUuid()).isEmpty(),"remove clears both endpoint indexes");
            c.assertTrue(a.getPos().equals(beforeA)&&b.getPos().equals(beforeB)&&a.physics().linearVelocity().equals(va)&&b.physics().linearVelocity().equals(va),"commands cannot teleport bodies or reset their world velocity");
        }finally{registry.removeBody(a.getUuid());a.discard();b.discard();}c.complete();
    }
    private String coordinates(Vec3d p){return String.format(Locale.ROOT,"%.8f %.8f %.8f",p.x,p.y,p.z);}

}
