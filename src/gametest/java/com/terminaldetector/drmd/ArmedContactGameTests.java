package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Native-recoil failure replay; all physical state comes from the retained real client world. */
public final class ArmedContactGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="armed-contact-native",tickLimit=80)
    public void collidingConnectedBodiesRecoverMaterialAnchorsWithoutLosingPhysicalTime(TestContext c)throws Exception{
        var bodies=new ArrayList<BlockBodyEntity>();var registry=BodyJointRegistry.get(c.getWorld());
        try(var stream=getClass().getResourceAsStream("/fixtures/mounted-contact-recoil-native.snbt")){
            var saved=StringNbtReader.parse(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));var tags=saved.getList("bodies",NbtElement.COMPOUND_TYPE);
            var first=tags.getCompound(0).getList("Pos",NbtElement.DOUBLE_TYPE);var target=c.getAbsolutePos(new BlockPos(8,220,8));
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)c.getWorld().getChunk((target.getX()>>4)+x,(target.getZ()>>4)+z);
            var shift=Vec3d.of(target).subtract(first.getDouble(0),first.getDouble(1),first.getDouble(2));
            for(int i=0;i<tags.size();i++){
                var body=ModEntities.BLOCK_BODY.create(c.getWorld());body.readNbt(tags.getCompound(i));body.setPosition(body.getPos().add(shift));
                c.assertTrue(c.getWorld().spawnEntity(body),"actual saved physical body restored into disposable clear air");bodies.add(body);
                // Isolate already-observed contact motion from any additional weapon work.
                var edits=new HashMap<BlockPos,net.minecraft.block.BlockState>();for(var cell:body.cells())if(cell.state().isOf(Blocks.LEVER))edits.put(cell.pos(),cell.state().with(Properties.POWERED,false));
                if(!edits.isEmpty())c.assertTrue(body.setLocalBlocks(edits),"only switches change; saved body poses and momenta remain intact");
            }
            var link=BodyJointLink.read(saved.getCompound("joint"));registry.add(c.getWorld(),bodies.get(0),bodies.get(1),link);
            c.waitAndRun(1,()->{
                try{
                    var root=bodies.getFirst();var start=root.massPosition();double maximum=0;int collisions=0;
                    for(int tick=0;tick<80;tick++){
                        bodies.forEach(BlockBodyEntity::tick);collisions+=BodyContactSystem.solve(c.getWorld());
                        var a=link.a().point();var b=link.b().point();double error=root.localToWorld(new Vec3d(a.x(),a.y(),a.z())).distanceTo(bodies.get(1).localToWorld(new Vec3d(b.x(),b.y(),b.z())));maximum=Math.max(maximum,error);
                        c.assertTrue(error<.03,"native connected-contact anchor error at tick "+tick+": "+error+" contacts="+BodyContactSystem.stats(c.getWorld())+" joints="+BodyJointSystem.stats(c.getWorld()));
                        c.assertTrue(!BodyContactSystem.stats(c.getWorld()).budgetPaused()&&BodyJointSystem.stats(c.getWorld()).paused()==0,"contact correction cannot freeze or discard physical time");
                    }
                    c.assertTrue(collisions>0&&root.massPosition().minus(start).length()>1&&registry.find(link.id()).isPresent(),"real colliding island keeps moving and retains its material joint: max="+maximum);
                }finally{bodies.forEach(body->{registry.removeBody(body.getUuid());body.discard();});}c.complete();
            });
        }catch(Throwable failure){bodies.forEach(body->{registry.removeBody(body.getUuid());body.discard();});throw failure;}
    }
}
