package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.network.BoardingNetworking;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Ordinary world ticks exercise the real LivingEntity cleanup before the boarding motor. */
public final class BoardingVelocityGameTests implements FabricGameTest {
    private record User(ServerPlayerEntity player,EmbeddedChannel channel,ClientConnection connection) implements AutoCloseable {
        public void close(){player.getServer().getNetworkIo().getConnections().remove(connection);BoardingSystem.externalTeleport(player);player.getServer().getPlayerManager().remove(player);player.discard();channel.finishAndReleaseAll();}
    }
    private static User user(TestContext c,String name,Vec3d position){return user(c,new GameProfile(UUID.randomUUID(),name),position);}
    private static User user(TestContext c,GameProfile profile,Vec3d position){
        var w=c.getWorld();var client=ConnectedClientData.createDefault(profile,false);
        var p=new ServerPlayerEntity(w.getServer(),w,client.gameProfile(),client.syncedOptions());var connection=new ClientConnection(NetworkSide.SERVERBOUND);var channel=new EmbeddedChannel(connection);
        w.getServer().getPlayerManager().onPlayerConnect(connection,p,client);var user=new User(p,channel,connection);
        try{
            channel.runPendingTasks();channel.flushOutbound();int confirmations=0;
            for(var packet:List.copyOf(channel.outboundMessages()))if(packet instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket teleport){channel.writeInbound(new net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket(teleport.getTeleportId()));confirmations++;}
            c.assertTrue(confirmations>0,"actual initial native position packet is confirmed through ordinary inbound handling");
            var pending=ServerPlayNetworkHandler.class.getDeclaredField("requestedTeleportPos");pending.setAccessible(true);c.assertTrue(pending.get(p.networkHandler)==null,"real teleport confirmation precedes fixture position; no pending spawn retry");
            p.changeGameMode(GameMode.SURVIVAL);DescentPlayerData.get(p).setEnabled(false);p.getAbilities().flying=false;p.setNoGravity(true);p.setPosition(position);p.setVelocity(Vec3d.ZERO);com.terminaldetector.drmd.world.gravity.FootGravitySystem.forget(p.getUuid());
            c.assertTrue(!w.getServer().getNetworkIo().getConnections().contains(connection),"no double scheduling of an actual fixture connection");w.getServer().getNetworkIo().getConnections().add(connection);return user;
        }catch(ReflectiveOperationException error){user.close();throw new RuntimeException(error);}catch(RuntimeException|Error error){user.close();throw error;}
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-late-ready-loaded-deck")
    public void reconnectAfterARotatedDeckLeavesTheSavedWorldPositionRestoresItsRealSupport(TestContext c){lateDeck(c,false);}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-late-ready-obstructed-deck")
    public void reconnectNeverPlacesAPassengerInAnObstructedSavedDeckLocation(TestContext c){lateDeck(c,true);}

    private static void lateDeck(TestContext c,boolean obstructed){
        var world=c.getWorld();
        var at=c.getAbsolutePos(new BlockPos(8,140,8));
        var profile=new GameProfile(UUID.randomUUID(),"late-deck");
        try(var chunks=new AiFixtureChunks(world,at,2)){
            var cells=new ArrayList<BlockBodyEntity.Cell>();
            for(int x=0;x<3;x++)for(int z=0;z<3;z++)
                cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,z),Blocks.IRON_BLOCK.getDefaultState()));
            var deck=BlockBodyEntity.createLocal(world,cells,Vec3d.of(at));
            var field=BlockBodyEntity.createLocal(world,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),Vec3d.of(at.add(0,0,-2)));
            try{
                field.setPassengerGravity(new Vec3(0,-16,0));
                var start=deck.localToWorld(new Vec3d(1.5,1.001,1.5));
                var saved=new net.minecraft.nbt.NbtCompound();
                Vec3d localFeet,worldFeet;
                int oldEpoch;
                try(var original=user(c,profile,start)){
                    var p=original.player();
                    BoardingSystem.ready(p,BoardingNetworking.PROTOCOL);
                    deck.tick();field.tick();BoardingSystem.tick(world);
                    deck.tick();field.tick();BoardingSystem.tick(world);
                    var state=BoardingSystem.state(p);
                    c.assertTrue(state!=null&&state.grounded()&&state.support()==deck.getId()&&state.gravityOwner()==field.getId(),"real deck contact and independent field before save: "+state);
                    oldEpoch=BoardingSystem.epoch(p);
                    localFeet=deck.worldToLocal(p.getPos());worldFeet=p.getPos();
                    p.writeNbt(saved);
                    c.assertTrue(saved.getCompound("DrmdData").getInt("boardingResumeSchema")==2,"real grounded player serialization contains the new local snapshot");
                }

                for(var body:List.of(deck,field)){
                    body.physics().withLinearVelocity(new Vec3(18,0,0)).withAngularVelocity(new Vec3(0,.5,0));
                }
                for(int i=0;i<10;i++){deck.tick();field.tick();}
                var expected=deck.localToWorld(localFeet);
                c.assertTrue(expected.distanceTo(worldFeet)>7&&Math.abs(deck.rotation().y())>.1,"real physics moved and turned the original deck clear of the old player");
                var blocked=BlockPos.ofFloored(expected).up();var originalBlock=world.getBlockState(blocked);
                if(obstructed)world.setBlockState(blocked,Blocks.STONE.getDefaultState());
                try(var late=user(c,profile,worldFeet)){
                    var p=late.player();p.readNbt(saved);
                    var data=DescentPlayerData.get(p);var hint=data.pendingBoardingResume();
                    c.assertTrue(hint!=null&&hint.anchor()!=null&&hint.anchor().support().equals(deck.getUuid())
                        &&hint.anchor().gravityOwner().equals(field.getUuid())&&p.getPos().distanceTo(worldFeet)<1e-8,
                        "ordinary player NBT read preserved stale world feet and both exact body identities");
                    int baseline=placements(late).size();
                    BoardingSystem.tick(world);
                    c.assertTrue(!BoardingSystem.drives(p)&&p.getPos().distanceTo(worldFeet)<1e-8
                        &&placements(late).size()==baseline,"no saved position or lease is used before the current login's Ready");
                    BoardingSystem.ready(p,BoardingNetworking.PROTOCOL);BoardingSystem.tick(world);
                    var packets=placements(late);
                    if(obstructed){
                        c.assertTrue(!BoardingSystem.drives(p)&&data.pendingBoardingResume()==null
                            &&p.getPos().distanceTo(worldFeet)<1e-8&&packets.size()==baseline,
                            "resolved ship with obstructed local feet declines placement without moving the player");
                    }else{
                        var state=BoardingSystem.state(p);
                        c.assertTrue(state!=null&&state.grounded()&&state.support()==deck.getId()&&state.gravityOwner()==field.getId()
                            &&BoardingSystem.epoch(p)>oldEpoch&&p.getPos().distanceTo(expected)<1e-8&&packets.size()==baseline+1
                            &&data.pendingBoardingResume()==null,"one native position packet follows exact live support and establishes a fresh server lease: "+state);
                        var relative=deck.rotation().rotate(hint.anchor().velocity());
                        var pointSpeed=BoardingSystem.pure(deck.surfaceVelocityAt(expected));
                        c.assertTrue(state.velocity().minus(pointSpeed.plus(relative)).length()<1e-8,"resume restores current point velocity plus saved relative motion");
                        int resumedEpoch=BoardingSystem.epoch(p);
                        for(int i=0;i<2;i++){deck.tick();field.tick();BoardingSystem.tick(world);}
                        var advanced=p.getPos();
                        c.assertTrue(BoardingSystem.drives(p)&&advanced.distanceTo(expected)>.2,
                            "actual contact advances the player while the original placement waits for confirmation");
                        var last=packets.getLast();late.channel().writeInbound(new net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket(last.getTeleportId()));
                        c.assertTrue(p.getPos().distanceTo(advanced)<1e-8&&BoardingSystem.epoch(p)==resumedEpoch,
                            "confirming the original placement preserves the advanced feet and the current lease");
                        deck.tick();field.tick();BoardingSystem.tick(world);
                        c.assertTrue(BoardingSystem.drives(p)&&p.getPos().distanceTo(advanced)>.2&&placements(late).size()==packets.size(),
                            "contact keeps advancing after the confirmation without another placement packet");
                    }
                }finally{if(obstructed)world.setBlockState(blocked,originalBlock);}
            }finally{deck.discard();field.discard();BoardingSystem.tick(world);}
        }
        c.complete();
    }

    private static List<net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket> placements(User user){
        user.channel().runPendingTasks();user.channel().flushOutbound();
        return user.channel().outboundMessages().stream()
            .filter(net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket.class::isInstance)
            .map(net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket.class::cast).toList();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="boarding-native-small-momentum",tickLimit=160)
    public void actualLowFieldAccumulatesMomentumAndExternalImpulseWhileUnownedActorsKeepNativeCleanup(TestContext c){
        var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,140,8));var chunks=new AiFixtureChunks(w,at,1);
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int x=0;x<2;x++)for(int z=0;z<2;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,z),Blocks.IRON_BLOCK.getDefaultState()));
        var body=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at));body.setPassengerGravity(new Vec3(0,-.5,0));body.setPhysicsGravity(false);
        var owned=user(c,"slow-owned",Vec3d.of(at).add(.75,3,.75));var ordinary=user(c,"slow-unowned",Vec3d.of(at).add(3.5,3,.75));
        var cow=EntityType.COW.create(w);c.assertTrue(cow!=null,"actual vanilla cow");cow.setAiDisabled(true);cow.setNoGravity(true);cow.setPosition(Vec3d.of(at).add(.75,3,3.5));w.spawnEntity(cow);
        ordinary.player.setVelocity(.001,-.001,.002);cow.setVelocity(.001,-.001,.002);
        c.assertTrue(BoardingSystem.ready(owned.player,BoardingNetworking.PROTOCOL)&&!BoardingSystem.ready(ordinary.player),"only actual supported protocol owner is admitted");
        int initialOwnedAge=owned.player.age,initialOrdinaryAge=ordinary.player.age,initialCowAge=cow.age;long started=w.getTime();
        boolean[] done={false},pendingImpulse={false};int[] samples={0};long[] lastTick={Long.MIN_VALUE};Vec3d[] previous={null};
        c.runAtEveryTick(()->{
            if(done[0]||lastTick[0]==w.getTime())return;lastTick[0]=w.getTime();
            try{
                c.assertTrue(w.getTime()-started<130,"finite real-world admission/measurement deadline");
                c.assertTrue(w.getServer().getNetworkIo().getConnections().contains(owned.connection)&&w.getServer().getNetworkIo().getConnections().contains(ordinary.connection),"both real connections remain on ordinary networkHandler/playerTick scheduling");
                var state=BoardingSystem.state(owned.player);if(state==null||!BoardingSystem.drives(owned.player))return;
                c.assertTrue(owned.player.age>initialOwnedAge,"real player tickMovement progressed before observation");
                c.assertTrue(!state.grounded()&&state.gravityOwner()==body.getId()&&!DescentPlayerData.get(owned.player).isEnabled()&&!owned.player.getAbilities().flying,"real airborne body-field motor owns this player without a vanilla gravity or floor impulse");
                var velocity=new Vec3d(state.velocity().x(),state.velocity().y(),state.velocity().z());
                if(previous[0]!=null){var expected=previous[0].add(0,-.025,0);if(pendingImpulse[0])expected=expected.add(.014,-.008,.018);
                    c.assertTrue(velocity.distanceTo(expected)<1e-8,"small momentum must accumulate through actual native cleanup; receiver external impulses survive: actual="+velocity+" expected="+expected);pendingImpulse[0]=false;}
                previous[0]=velocity;samples[0]++;
                if(samples[0]==3){owned.player.addVelocity(.0007,-.0004,.0009);pendingImpulse[0]=true;}
                if(samples[0]<9)return;
                c.assertTrue(velocity.y<-.15&&velocity.x>.0139&&velocity.z>.0179,"slow field history and sub-threshold external tangential momentum are retained");
                c.assertTrue(ordinary.player.age>initialOrdinaryAge+3&&cow.age>initialCowAge+3&&w.getTime()>started+3,"all controls actually ticked in the ordinary world");
                c.assertTrue(!BoardingSystem.drives(ordinary.player)&&ordinary.player.getVelocity().lengthSquared()<1e-14&&cow.getVelocity().lengthSquared()<1e-14,"unowned player and vanilla mob retain ordinary per-axis cleanup");
                done[0]=true;owned.close();ordinary.close();cow.discard();body.discard();chunks.close();c.complete();
            }catch(RuntimeException|Error error){done[0]=true;owned.close();ordinary.close();cow.discard();body.discard();chunks.close();throw error;}
        });
    }
}
