package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Real local grid/vanilla slab/authoritative USE and mining; no render or full-world cold claim. */
public final class BodyDoorGameTests implements FabricGameTest {
    private static final BlockPos LOWER=BlockPos.ORIGIN,UPPER=LOWER.up();
    private static BlockState door(boolean open){return Blocks.OAK_DOOR.getDefaultState().with(Properties.OPEN,open).with(Properties.DOOR_HINGE,DoorHinge.RIGHT);}
    private static final class Fixture implements AutoCloseable {
        final TestContext c;final AiFixtureChunks chunks;final BlockBodyEntity body;ServerPlayerEntity player;EmbeddedChannel channel;
        Fixture(TestContext c,BlockState lower,Quat rotation){this(c,lower,rotation,new Vec3d(2,.25,-1),new Vec3d(.04,.03,.02));}
        Fixture(TestContext c,BlockState lower,Quat rotation,Vec3d v,Vec3d omega){
            this.c=c;var at=c.getAbsolutePos(new BlockPos(8,150,8));chunks=new AiFixtureChunks(c.getWorld(),at,0);
            body=BlockBodyEntity.createLocal(c.getWorld(),List.of(new BlockBodyEntity.Cell(LOWER,lower.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER)),
                new BlockBodyEntity.Cell(UPPER,lower.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER))),Vec3d.of(at),rotation,v,omega);
        }
        ServerPlayerEntity player(BlockPos clicked){
            if(player==null){
                var w=c.getWorld();var data=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),"local-door"),false);player=new ServerPlayerEntity(w.getServer(),w,data.gameProfile(),data.syncedOptions());
                var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);w.getServer().getPlayerManager().onPlayerConnect(connection,player,data);
                player.changeGameMode(GameMode.CREATIVE);DescentPlayerData.get(player).setEnabled(false);player.setNoGravity(true);player.getAbilities().flying=true;player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
            }aim(clicked);return player;
        }
        void aim(BlockPos clicked){
            var bounds=BodyLocalDoors.collisionBounds(clicked,body.getLocalBlock(clicked));var size=bounds.max().minus(bounds.min());
            var normal=size.x()<.25?new Vec3(1,0,0):new Vec3(0,0,1);var centre=bounds.centre();var eye=centre.plus(normal.scaled(3.4));
            var worldEye=body.localToWorld(new Vec3d(eye.x(),eye.y(),eye.z()));double eyeHeight=player.getEyePos().y-player.getY();player.setPosition(worldEye.subtract(0,eyeHeight,0));
            var target=body.localToWorld(new Vec3d(centre.x(),centre.y(),centre.z()));var ray=target.subtract(player.getEyePos());var angle=PortalTransform.vectorToYawPitch(new Vec3(ray.x,ray.y,ray.z));player.setYaw((float)angle.yawDegrees());player.setPitch((float)angle.pitchDegrees());
        }
        BodyBlockInteraction.Edit edit(BlockPos cell){return new BodyBlockInteraction.Edit(body.getUuid(),body.shapeRevision(),cell,BodyBlockInteraction.USE);}
        public void close(){if(player!=null){player.getServer().getPlayerManager().remove(player);player.discard();channel.finishAndReleaseAll();}body.discard();chunks.close();}
    }
    private void physical(TestContext c,BlockBodyEntity b,UUID id,Vec3 p,Quat q,Vec3 v,Vec3 l,double mass){
        c.assertTrue(id.equals(b.getUuid())&&p.minus(b.massPosition()).length()<1e-12&&q.equals(b.rotation())&&v.minus(b.physics().linearVelocity()).length()<1e-12&&l.minus(b.physics().angularMomentum()).length()<1e-12&&mass==b.physicsMass(),"one existing body retains pose/mass/linear and angular momentum");
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="local-door-paired-use")
    public void EitherHalfUsesOneAtomicMovingDoorAndPreservesAllOtherProperties(TestContext c){
        for(var clicked:List.of(LOWER,UPPER))try(var f=new Fixture(c,door(false).with(Properties.HORIZONTAL_FACING,Direction.WEST).with(Properties.POWERED,true),ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.35))){
            var b=f.body;var p=f.player(clicked);var before0=b.getLocalBlock(LOWER);var before1=b.getLocalBlock(UPPER);var packet=f.edit(clicked);
            var id=b.getUuid();var pos=b.massPosition();var q=b.rotation();var v=b.physics().linearVelocity();var l=b.physics().angularMomentum();var mass=b.physicsMass();
            c.assertTrue(b.canUseLocalBlock(clicked,p.getMainHandStack())&&BodyBlockInteraction.receive(p,packet),"real USE ray to either half reaches the moving local door");
            c.assertTrue(b.getLocalBlock(LOWER).equals(before0.with(Properties.OPEN,true))&&b.getLocalBlock(UPPER).equals(before1.with(Properties.OPEN,true))&&b.shapeRevision()==packet.revision()+2,"both exact half/facing/hinge/powered states commit in one two-cell transaction");
            physical(c,b,id,pos,q,v,l,mass);c.assertTrue(!BodyBlockInteraction.receive(p,packet),"stale old revision cannot toggle either half");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="local-door-thin-geometry")
    public void FacingHingeAndBothHalvesShareExactVanillaLeafAndActualOpenPassage(TestContext c){
        for(var facing:Direction.Type.HORIZONTAL)for(var hinge:DoorHinge.values())try(var f=new Fixture(c,door(false).with(Properties.HORIZONTAL_FACING,facing).with(Properties.DOOR_HINGE,hinge),ImmPtlQuaternions.rotationByRadians(new Vec3(2,1,3).normalized(),.45))){
            var b=f.body;var access=new BlockBodyWorldAccess(b);var probes=new ArrayList<OrientedBox>();
            for(var cell:List.of(LOWER,UPPER)){
                var bounds=BodyLocalDoors.collisionBounds(cell,b.getLocalBlock(cell));c.assertTrue(bounds.equals(b.localBlockBounds().get(cell)),"actual local BVH uses the exact thin vanilla bounds "+facing+hinge+cell);
                Vec3 point=bounds.centre();var probe=OrientedBox.local(new BodyBounds(point.minus(new Vec3(.03,.03,.03)),point.plus(new Vec3(.03,.03,.03))),b.massPosition(),b.localCentre(),b.rotation());probes.add(probe);
                c.assertTrue(!access.queryBodyCollision(probe,0).isEmpty(),"closed leaf physically blocks its actual slab");
                var n=bounds.half().x()<.25?new Vec3(1,0,0):new Vec3(0,0,1);var centre=new Vec3(.5,cell.getY()+.5,.5);
                c.assertTrue(access.raycastBlockBody(b.localToWorld(new Vec3d(centre.x()-2*n.x(),centre.y(),centre.z()-2*n.z())),b.localToWorld(new Vec3d(centre.x()+2*n.x(),centre.y(),centre.z()+2*n.z()))).isPresent(),"closed door actual ray blocks");
            }
            c.assertTrue(b.setLocalBlocks(Map.of(LOWER,b.getLocalBlock(LOWER).with(Properties.OPEN,true),UPPER,b.getLocalBlock(UPPER).with(Properties.OPEN,true))),"paired geometry edit uses existing local atomic batch");
            for(var probe:probes)c.assertTrue(access.queryBodyCollision(probe,0).isEmpty(),"open leaf leaves the former closed slab empty; no hidden full cube");
        }
        c.assertTrue(BodyLocalDoors.supports(Blocks.SPRUCE_DOOR.getDefaultState())&&BodyLocalDoors.supports(Blocks.CHERRY_DOOR.getDefaultState())&&!BodyLocalDoors.supports(Blocks.IRON_DOOR.getDefaultState()),"wooden variants use shared state semantics; iron door is not silently made hand-operated");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="local-door-occupied-upper")
    public void OccupiedUpperLeafRefusesBothHalvesThenClosesAfterRealItemLeaves(TestContext c){
        try(var f=new Fixture(c,door(true),Quat.IDENTITY)){
            var b=f.body;var p=f.player(UPPER);var next=BodyLocalDoors.collisionBounds(UPPER,b.getLocalBlock(UPPER).with(Properties.OPEN,false));var middle=next.centre();var at=b.localToWorld(new Vec3d(middle.x(),middle.y(),middle.z()));
            var item=new ItemEntity(c.getWorld(),at.x,at.y,at.z,new ItemStack(Items.STICK));item.setNoGravity(true);c.getWorld().spawnEntity(item);
            try{var snapshot=b.physics().snapshot();long revision=b.shapeRevision();
                c.assertTrue(!BodyBlockInteraction.receive(p,f.edit(UPPER))&&b.shapeRevision()==revision&&b.getLocalBlock(LOWER).get(Properties.OPEN)&&b.getLocalBlock(UPPER).get(Properties.OPEN)&&b.physics().matches(snapshot),"upper occupancy refuses the whole transaction before geometry/revision/physics mutation");
                item.discard();f.aim(UPPER);c.assertTrue(BodyBlockInteraction.receive(p,f.edit(UPPER))&&!b.getLocalBlock(LOWER).get(Properties.OPEN)&&!b.getLocalBlock(UPPER).get(Properties.OPEN)&&b.shapeRevision()==revision+2,"clearance permits both halves to close together");
            }finally{item.discard();}
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="local-door-auth-orphan")
    public void ForgedCellAdventureAndMismatchedSavedHalfCannotToggleThePair(TestContext c){
        try(var f=new Fixture(c,door(false),Quat.IDENTITY)){
            var p=f.player(LOWER);var b=f.body;long revision=b.shapeRevision();
            c.assertTrue(!BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(b.getUuid(),revision,LOWER.east(),BodyBlockInteraction.USE)),"packet cell must match the real local ray");
            p.changeGameMode(GameMode.ADVENTURE);c.assertTrue(!BodyBlockInteraction.receive(p,f.edit(LOWER))&&b.shapeRevision()==revision,"Adventure cannot edit either half");
            p.changeGameMode(GameMode.CREATIVE);c.assertTrue(b.setLocalBlock(UPPER,Blocks.SPRUCE_DOOR.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER)),"actual damaged/mismatched local material authored");revision=b.shapeRevision();f.aim(LOWER);
            c.assertTrue(!BodyBlockInteraction.receive(p,f.edit(LOWER))&&b.shapeRevision()==revision&&!b.getLocalBlock(LOWER).get(Properties.OPEN),"orphan/mismatched data remains inert without fabricated half repairs");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="local-door-place-save")
    public void PairedPlacementAcrossLocalSectionAndStateNbtRestorePreserveDoorAndMaterial(TestContext c){
        var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,130,8));try(var chunks=new AiFixtureChunks(w,at,0)){
            var lower=new BlockPos(0,15,0);var upper=lower.up();var floor=lower.down();var b=BlockBodyEntity.createLocal(w,List.of(new BlockBodyEntity.Cell(floor,Blocks.IRON_BLOCK.getDefaultState())),Vec3d.of(at));
            try{var access=new BlockBodyWorldAccess(b);long revision=b.shapeRevision();var state=door(false).with(Properties.HORIZONTAL_FACING,Direction.EAST);
                c.assertTrue(b.setLocalBlock(upper,Blocks.IRON_BLOCK.getDefaultState()),"real occupied upper destination");long blockedRevision=b.shapeRevision();
                c.assertTrue(!access.placeLocalBlock(lower,state)&&b.shapeRevision()==blockedRevision&&b.getLocalBlock(lower).isAir(),"upper collision cannot leave a lower half behind");
                b.setLocalBlock(upper,Blocks.AIR.getDefaultState());revision=b.shapeRevision();
                c.assertTrue(!access.placeLocalBlock(new BlockPos(0,BlockBodyEntity.MAX_LOCAL-1,0),state)&&b.shapeRevision()==revision,"out-of-range upper half rejects before any lower write");
                c.assertTrue(access.placeLocalBlock(lower,state)&&b.shapeRevision()==revision+2&&BodyLocalDoors.pair(b,upper).isPresent(),"two real cells across section15/16 are one placement");
                b.setLocalBlocks(Map.of(lower,b.getLocalBlock(lower).with(Properties.OPEN,true),upper,b.getLocalBlock(upper).with(Properties.OPEN,true)));
                b.physics().withLinearVelocity(new Vec3(3,-1,2)).withAngularVelocity(new Vec3(.1,.2,.3));
                var tag=b.writeNbt(new NbtCompound());var a=b.getLocalBlock(lower);var u=b.getLocalBlock(upper);var bounds=b.localBlockBounds();var v=b.physics().linearVelocity();var l=b.physics().angularMomentum();long savedRevision=b.shapeRevision();
                var restored=new BlockBodyEntity(ModEntities.BLOCK_BODY,w);try{restored.readNbt(tag);c.assertTrue(restored.getLocalBlock(lower).equals(a)&&restored.getLocalBlock(upper).equals(u)&&restored.localBlockBounds().equals(bounds)&&restored.shapeRevision()==savedRevision,"actual saved OPEN/facing/hinge/half and exact collider reconstruct without schema changes");c.assertTrue(restored.physics().linearVelocity().equals(v)&&restored.physics().angularMomentum().equals(l),"entity NBT keeps existing momentum");}finally{restored.discard();}
                c.assertTrue(access.breakLocalBlock(upper)&&b.getLocalBlock(lower).isAir()&&b.getLocalBlock(upper).isAir()&&b.getLocalBlock(floor).isOf(Blocks.IRON_BLOCK),"ordinary local break removes both door cells while retaining supporting material");
            }finally{b.discard();}
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="local-door-source-pair")
    public void PartialWorldDoorSelectionRefusesWithoutDeletingItsUnselectedHalf(TestContext c){
        var w=c.getWorld();var lower=c.getAbsolutePos(new BlockPos(8,150,8));var upper=lower.up();try(var chunks=new AiFixtureChunks(w,lower,0)){
            var a=door(false);var u=a.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER);BlockBodyEntity body=null;
            try{w.setBlockState(lower,a,Block.FORCE_STATE);w.setBlockState(upper,u,Block.FORCE_STATE);boolean rejected=false;
                try{BlockBodyEntity.assembleSelection(w,Map.of(lower,a));}catch(IllegalArgumentException expected){rejected=true;}
                c.assertTrue(rejected&&w.getBlockState(lower).equals(a)&&w.getBlockState(upper).equals(u),"unselected upper half never disappears through vanilla neighbour publication");
                body=BlockBodyEntity.assembleSelection(w,Map.of(lower,a,upper,u));c.assertTrue(w.getBlockState(lower).isAir()&&w.getBlockState(upper).isAir()&&BodyLocalDoors.pair(body,LOWER).isPresent(),"complete real world door moves into the existing one-body local grid");
            }finally{if(body!=null)body.discard();w.setBlockState(lower,Blocks.AIR.getDefaultState(),Block.FORCE_STATE);w.setBlockState(upper,Blocks.AIR.getDefaultState(),Block.FORCE_STATE);}
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="local-door-survival-drop",tickLimit=180)
    public void OrdinarySurvivalMiningTheUpperHalfDropsExactlyOneDoor(TestContext c){
        var f=new Fixture(c,door(false),Quat.IDENTITY,Vec3d.ZERO,Vec3d.ZERO);var p=f.player(UPPER);p.changeGameMode(GameMode.SURVIVAL);p.setNoGravity(true);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.DIAMOND_AXE));
        c.assertTrue(!p.isCreative()&&p.interactionManager.getGameMode()==GameMode.SURVIVAL,"actual normal Survival principal, not the creative TestContext subclass");
        var body=f.body;long revision=body.shapeRevision();var origin=body.getPos();int[] ticks={0};boolean[] closed={false};
        c.runAtEveryTick(()->{if(closed[0])return;try{
            c.assertTrue(++ticks[0]<160,"finite ordinary mining progress");
            if(body.getLocalBlock(UPPER).isAir()){
                var drops=c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(origin,origin).expand(4),e->e.getStack().isOf(Items.OAK_DOOR));int count=drops.stream().mapToInt(e->e.getStack().getCount()).sum();
                c.assertTrue(body.getLocalBlock(LOWER).isAir()&&body.shapeRevision()==revision+2&&count==1,"real upper mining removes both halves and creates exactly one vanilla door drop: "+count);drops.forEach(ItemEntity::discard);closed[0]=true;f.close();c.complete();return;
            }
            c.assertTrue(body.getPos().distanceTo(origin)<1e-9,"mining fixture stays at its genuine initial zero velocity; no player-follow teleport is used");
            BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(body.getUuid(),revision,UPPER,BodyBlockInteraction.MINE));
        }catch(RuntimeException|Error failure){closed[0]=true;f.close();throw failure;}});
    }
}
