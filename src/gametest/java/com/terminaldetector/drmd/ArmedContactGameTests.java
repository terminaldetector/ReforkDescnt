package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.collision.BodyPairQuery;
import com.terminaldetector.drmd.world.contraption.mechanism.MechanicalBearingSystem;
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
            c.assertTrue(MechanicalBearingSystem.refresh(c.getWorld(),link,bodies.get(0),bodies.get(1))!=null,"restored hardware validates before simulation: "+bodies.get(0).getLocalBlock(link.a().cell())+" / "+bodies.get(1).getLocalBlock(link.b().cell())+" frames="+link.a().frame()+" / "+link.b().frame());
            c.waitAndRun(1,()->{
                try{
                    c.assertTrue(registry.find(link.id()).isPresent(),"restored joint still exists on first admitted frame: removed="+bodies.stream().map(body->body.isRemoved()+"/"+body.getRemovalReason()).toList()+" stats="+BodyJointSystem.stats(c.getWorld()));
                    var root=bodies.getFirst();var start=root.massPosition();double maximum=0;int collisions=0;
                    for(int tick=0;tick<80;tick++){
                        bodies.forEach(BlockBodyEntity::tick);BodyJointSystem.solve(c.getWorld());
                        collisions+=BodyContactSystem.solve(c.getWorld());
                        var a=link.a().point();var b=link.b().point();double error=root.localToWorld(new Vec3d(a.x(),a.y(),a.z())).distanceTo(bodies.get(1).localToWorld(new Vec3d(b.x(),b.y(),b.z())));maximum=Math.max(maximum,error);
                        c.assertTrue(error<.03,"native connected-contact anchor error at tick "+tick+": "+error+" contacts="+BodyContactSystem.stats(c.getWorld())+" joints="+BodyJointSystem.stats(c.getWorld()));
                        c.assertTrue(registry.find(link.id()).isPresent(),"contact loads retain the actual material joint at tick "+tick);
                        c.assertTrue(!BodyContactSystem.stats(c.getWorld()).budgetPaused()&&BodyJointSystem.stats(c.getWorld()).paused()==0,"contact correction cannot freeze or discard physical time");
                    }
                    // The saved damaged mechanism has contacts between other material cells.
                    // Only the mating bearing pair is exempt, as tested independently below.
                    c.assertTrue(collisions>0&&root.massPosition().minus(start).length()>1&&registry.find(link.id()).isPresent(),"real material contacts preserve both the joint and physical time: contacts="+collisions+" max="+maximum);
                }finally{bodies.forEach(body->{registry.removeBody(body.getUuid());body.discard();});}c.complete();
            });
        }catch(Throwable failure){bodies.forEach(body->{registry.removeBody(body.getUuid());body.discard();});throw failure;}
    }
    private BodyPairQuery.Motion motion(BlockBodyEntity body){return new BodyPairQuery.Motion(body.collider(),body.localCentre(),body.massPosition(),body.rotation(),body.massPosition(),body.rotation());}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="bearing-material-contact")
    public void bearingInterfaceFilterKeepsCollisionsBetweenAllOtherMaterial(TestContext c){
        var w=c.getWorld();var origin=Vec3d.of(c.getAbsolutePos(new BlockPos(8,220,8)));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.getChunk(((int)origin.x>>4)+x,((int)origin.z>>4)+z);
        var a=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.EAST)),new BlockBodyEntity.Cell(new BlockPos(2,0,0),Blocks.IRON_BLOCK.getDefaultState())),origin);
        var b=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.WEST)),new BlockBodyEntity.Cell(new BlockPos(1,0,0),ModWorldBlocks.SHIP_LASER.getDefaultState())),origin.add(1,0,0));a.setPhysicsGravity(false);b.setPhysicsGravity(false);
        c.assertTrue(MechanicalBearingSystem.connect(w,a,BlockPos.ORIGIN,b,BlockPos.ORIGIN),"real hardware joint created");
        c.waitAndRun(1,()->{try{
            var geometry=BearingContactGeometry.of(w,a,b);var contacts=geometry.contacts(motion(a),motion(b),0,true);
            c.assertTrue(!contacts.isEmpty()&&BodyJointRegistry.get(w).collisionAllowed(a.getUuid(),b.getUuid()),"parent material overlapping the other gun remains in physical narrowphase");
        }finally{BodyJointRegistry.get(w).removeBody(a.getUuid());a.discard();b.discard();}c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="bearing-external-contact",tickLimit=80)
    public void aCollisionOnOneMemberContinuesItsUntouchedJointNeighbourAtTheSameTime(TestContext c){
        var w=c.getWorld();var origin=Vec3d.of(c.getAbsolutePos(new BlockPos(8,220,8)));for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.getChunk(((int)origin.x>>4)+x,((int)origin.z>>4)+z);
        var a=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.EAST))),origin);
        var b=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.WEST))),origin.add(1,0,0));
        var wallCells=new ArrayList<BlockBodyEntity.Cell>();for(int x=-9;x<=0;x++)for(int y=-4;y<=5;y++)wallCells.add(new BlockBodyEntity.Cell(new BlockPos(x,y,0),Blocks.IRON_BLOCK.getDefaultState()));
        var wall=BlockBodyEntity.createLocal(w,wallCells,origin.add(-.2,0,4));var bodies=List.of(a,b,wall);bodies.forEach(body->body.setPhysicsGravity(false));a.physics().withLinearVelocity(new Vec3(0,0,3));b.physics().withLinearVelocity(new Vec3(0,0,3));
        c.assertTrue(MechanicalBearingSystem.connect(w,a,BlockPos.ORIGIN,b,BlockPos.ORIGIN),"free coupled pair aimed at an independent massive wall");
        c.assertTrue(!BodyPairQuery.contacts(motion(a),motion(b),0,true).isEmpty(),"the two unfiltered bearing cubes touch");
        c.assertTrue(BearingContactGeometry.of(w,a,b).contacts(motion(a),motion(b),0,true).isEmpty(),"only the actual mating bearing pair is excluded from contact geometry");
        c.waitAndRun(1,()->{try{
            int collisions=0;var start=a.massPosition();for(int tick=0;tick<55;tick++){
                bodies.forEach(BlockBodyEntity::tick);collisions+=BodyContactSystem.solve(w);
                c.assertTrue(a.localToWorld(new Vec3d(1,.5,.5)).distanceTo(b.localToWorld(new Vec3d(0,.5,.5)))<.03,"non-touching neighbour follows the same contact-time constraints at tick "+tick);
                c.assertTrue(!BodyContactSystem.stats(w).budgetPaused()&&!a.boardingMotionPaused()&&!b.boardingMotionPaused(),"external contact consumes physical time without stalling the mechanism");
            }
            c.assertTrue(collisions>0&&wall.physics().linearVelocity().length()>.001&&a.massPosition().minus(start).length()>1,"the independent wall receives a real impact while the mechanism advances");
        }finally{bodies.forEach(body->{BodyJointRegistry.get(w).removeBody(body.getUuid());body.discard();});}c.complete();});
    }
}
