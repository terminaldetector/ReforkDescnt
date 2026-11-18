package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.portal.PortalExitInterest;
import com.terminaldetector.drmd.world.portal.mirror.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual BE/ordinary-world ticket progress. Unknown-exit loading is not a portal traversal claim. */
public final class PortalExitInterestGameTests implements FabricGameTest {
    private static final AtomicInteger IDS=new AtomicInteger();
    private static final class Fixture implements AutoCloseable {
        final TestContext c;final BlockPos source,destination;final AiFixtureChunks chunks;
        final ServerPlayerEntity player;final EmbeddedChannel channel;PortalPanelBlockEntity panel;
        ChargedMirrorBlockEntity mirror;boolean closed;int age,wait;
        Fixture(TestContext c){
            this.c=c;source=new BlockPos(96008+IDS.getAndIncrement()*128,140,144008);destination=source.add(4096,0,4096);
            chunks=new AiFixtureChunks(c.getWorld(),source,0);
            var data=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),"portal-interest"),false);
            player=new ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),data.gameProfile(),data.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);
            c.getWorld().getServer().getPlayerManager().onPlayerConnect(connection,player,data);
            player.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.setNoGravity(true);
            DescentPlayerData.get(player).setEnabled(false);nearby();
            c.assertTrue(c.getWorld().getServer().getPlayerManager().getPlayerList().contains(player),"actual connected user is the interest authority");
            c.assertTrue(c.getWorld().getBlockState(source).isAir(),"source fixture needs real AIR");
            var state=ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,Direction.SOUTH).with(PortalPanelBlock.LINKED,true);
            c.getWorld().setBlockState(source,state,Block.NOTIFY_LISTENERS);
            panel=(PortalPanelBlockEntity)c.getWorld().getBlockEntity(source);panel.setLink(null,destination,c.getWorld().getRegistryKey());
            c.assertTrue(c.getWorld().getChunkManager().getWorldChunk(destination.getX()>>4,destination.getZ()>>4)==null,"far prospective exit starts genuinely unloaded");
        }
        void nearby(){player.setPosition(Vec3d.ofCenter(source).add(0,1,3));player.setVelocity(Vec3d.ZERO);}
        void away(){player.setPosition(Vec3d.ofCenter(source).add(80,1,3));player.setVelocity(Vec3d.ZERO);}
        void addMirror(){
            var at=source.east(5);c.assertTrue(c.getWorld().getBlockState(at).isAir(),"second source starts AIR");
            var state=ModWorldBlocks.CHARGED_MIRROR.getDefaultState().with(ChargedMirrorBlock.FACING,Direction.SOUTH).with(ChargedMirrorBlock.LINKED,true);
            c.getWorld().setBlockState(at,state,Block.NOTIFY_LISTENERS);
            mirror=(ChargedMirrorBlockEntity)c.getWorld().getBlockEntity(at);mirror.setLink(null,destination.north(2048),c.getWorld().getRegistryKey());
        }
        public void close(){
            if(closed)return;closed=true;
            try{
                if(panel!=null)panel.clearLink();if(mirror!=null)mirror.clearLink();
                c.getWorld().setBlockState(source,net.minecraft.block.Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                if(mirror!=null)c.getWorld().setBlockState(source.east(5),net.minecraft.block.Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                c.getWorld().getServer().getPlayerManager().remove(player);player.discard();
            }finally{channel.finishAndReleaseAll();chunks.close();}
        }
    }
    private static void drive(Fixture f,java.util.function.IntConsumer action){
        f.c.runAtEveryTick(()->{
            if(f.closed)return;
            try{
                f.c.assertTrue(++f.wait<180,"finite ordinary async warmup admission/progress");
                if(!f.chunks.entityTicking(f.source))return;
                action.accept(f.age++);
            }catch(RuntimeException|Error failure){f.close();throw failure;}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-interest-async",tickLimit=200)
    public void NearbyRealUserWarmsAnUnknownDestinationThroughOrdinaryTickets(TestContext c){
        var f=new Fixture(c);boolean[] held={false};
        drive(f,age->{
            int count=PortalExitInterest.heldColumns(c.getWorld(),f.source);held[0]|=count>0;
            c.assertTrue(count<=PortalExitInterest.MAX_COLUMNS_PER_LINK,"per-link interest remains bounded");
            if(WorldAccess.of(c.getWorld()).readIfLoaded(f.destination).isPresent()){
                c.assertTrue(age>0&&held[0],"real async lease precedes actual FULL destination readiness");
                c.assertTrue(f.panel.isLinked()&&f.destination.equals(f.panel.getLinkPartnerPos()),"temporary unavailability never deletes the saved link");
                f.close();c.complete();
            }
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-interest-proximity",tickLimit=200)
    public void NoNearbyUserMakesNoRequestAndLeavingReleasesTheLease(TestContext c){
        var f=new Fixture(c);f.away();int[] phase={0},since={0};
        drive(f,age->{
            int held=PortalExitInterest.heldColumns(c.getWorld(),f.source);
            if(phase[0]==0){
                c.assertTrue(held==0&&c.getWorld().getChunkManager().getWorldChunk(f.destination.getX()>>4,f.destination.getZ()>>4)==null,"loaded source alone cannot warm distant terrain without a genuine nearby user");
                if(age>=5){f.nearby();phase[0]=1;}
            }else if(phase[0]==1&&held>0){f.away();phase[0]=2;since[0]=age;}
            else if(phase[0]==2&&age>since[0]+2){
                c.assertTrue(held==0,"leaving removes owned tickets; vanilla cache retention is not reported as an active lease");
                f.close();c.complete();
            }
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-interest-lifecycle",tickLimit=200)
    public void PanelUnlinkAndMirrorRemovalImmediatelyRetireIndependentInterests(TestContext c){
        var f=new Fixture(c);f.addMirror();
        drive(f,age->{
            var mirrorPos=f.source.east(5);
            if(PortalExitInterest.heldColumns(c.getWorld(),f.source)>0&&PortalExitInterest.heldColumns(c.getWorld(),mirrorPos)>0){
                f.panel.clearLink();
                c.assertTrue(PortalExitInterest.heldColumns(c.getWorld(),f.source)==0&&PortalExitInterest.heldColumns(c.getWorld(),mirrorPos)>0,"one unlink releases only its own UUID owner");
                f.mirror.markRemoved();
                c.assertTrue(PortalExitInterest.heldColumns(c.getWorld(),mirrorPos)==0,"actual BE removal releases its lease immediately");
                f.close();c.complete();
            }
        });
    }
}
