package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.portal.PortalTravel;
import com.terminaldetector.drmd.world.portal.mirror.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.entity.MovementType;
import net.minecraft.network.*;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real saved far BE unload/ordinary ticket reload. No fake availability or destination writes after unload. */
public final class PortalPendingTravelGameTests implements FabricGameTest {
    private static final AtomicInteger IDS=new AtomicInteger();
    private static final class Player implements AutoCloseable {
        final ServerPlayerEntity entity;final EmbeddedChannel channel;
        Player(TestContext c,BlockPos source,String name){
            var w=c.getWorld();var data=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),name),false);
            entity=new ServerPlayerEntity(w.getServer(),w,data.gameProfile(),data.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);w.getServer().getPlayerManager().onPlayerConnect(connection,entity,data);
            entity.changeGameMode(GameMode.CREATIVE);entity.getAbilities().flying=true;entity.setNoGravity(true);DescentPlayerData.get(entity).setEnabled(false);
            entity.setPosition(source.getX()+.5,source.getY()-.4,source.getZ()-.8);entity.setVelocity(Vec3d.ZERO);
        }
        void clearPackets(TestContext c){channel.runPendingTasks();for(int i=0;i<4096&&!channel.outboundMessages().isEmpty();i++)io.netty.util.ReferenceCountUtil.release(channel.readOutbound());c.assertTrue(channel.outboundMessages().isEmpty(),"finite initial connection backlog");}
        public void close(){entity.getServer().getPlayerManager().remove(entity);entity.discard();channel.finishAndReleaseAll();}
    }
    private static final class Fixture implements AutoCloseable {
        final TestContext c;final BlockPos source,exit;final AiFixtureChunks chunks;final Player stays,retreats;boolean closed;int ticks,phase,after;
        Fixture(TestContext c){
            this.c=c;var w=c.getWorld();source=new BlockPos(180008+IDS.getAndIncrement()*128,140,176008);exit=source.add(4096,0,4096);chunks=new AiFixtureChunks(w,source,0);
            stays=new Player(c,source,"portal-waits");retreats=new Player(c,source,"portal-retreat");retreats.entity.setPosition(retreats.entity.getPos().add(.65,0,0));
            try{c.assertTrue(WorldAccess.of(w).readIfLoaded(exit).isEmpty(),"far save authoring begins outside all existing interest");
            // Explicit authored fixture setup only. Production warmup below must reload this exact
            // normal saved BE after genuine automatic eviction; no destination edits occur then.
            w.getChunk(exit.getX()>>4,exit.getZ()>>4);
            var panel=ModWorldBlocks.PORTAL_PANEL.getDefaultState();
            w.setBlockState(source,panel.with(PortalPanelBlock.FACING,Direction.NORTH),Block.NOTIFY_LISTENERS);
            w.setBlockState(exit,panel.with(PortalPanelBlock.FACING,Direction.EAST).with(PortalPanelBlock.LINKED,true),Block.NOTIFY_LISTENERS);
            ((PortalPanelBlockEntity)w.getBlockEntity(exit)).setLink(null,source,w.getRegistryKey());
            c.assertTrue(!((PortalPanelBlockEntity)w.getBlockEntity(source)).isLinked(),"source stays inactive until the real far column unloads; no lease can keep it loaded");
            }catch(RuntimeException|Error failure){close();throw failure;}
        }
        void tick(){
            if(closed)return;var w=c.getWorld();c.assertTrue(++ticks<360,"finite cold exit and pending crossing fixture");if(!chunks.entityTicking(source))return;
            if(phase==0){
                if(WorldAccess.of(w).readIfLoaded(exit).isPresent()){c.assertTrue(ticks<220,"ordinary far chunk must actually unload before testing pending travel");return;}
                var state=w.getBlockState(source).with(PortalPanelBlock.LINKED,true);w.setBlockState(source,state,Block.NOTIFY_LISTENERS);
                ((PortalPanelBlockEntity)w.getBlockEntity(source)).setLink(null,exit,w.getRegistryKey());
                stays.clearPackets(c);retreats.clearPackets(c);
                for(var p:List.of(stays.entity,retreats.entity)){p.setVelocity(0,0,.4);p.move(MovementType.SELF,new Vec3d(0,0,1));p.setVelocity(Vec3d.ZERO);c.assertTrue(p.getZ()>source.getZ(),"actual ordinary move crossed the linked source before its exit is FULL");}
                c.assertTrue(WorldAccess.of(w).readIfLoaded(exit).isEmpty(),"the crossing itself never synchronously warms the exit");phase=1;return;
            }
            if(phase==1){
                c.assertTrue(retreats.entity.getX()<exit.getX()-100,"first unready crossing remained near source before the retreat input");
                retreats.entity.move(MovementType.SELF,new Vec3d(0,0,-1));retreats.entity.setVelocity(Vec3d.ZERO);c.assertTrue(retreats.entity.getZ()<source.getZ()-.2,"actual reverse step retreats through the source plane");
                // Native prev coordinates naturally reset later; no new inward motion is authored.
                phase=2;
            }
            if(phase==2){
                c.assertTrue(retreats.entity.getX()<exit.getX()-100,"retreat cancels old crossing, even when another player warms the exit");
                if(stays.entity.getX()>exit.getX()){
                    var restored=WorldAccess.of(w).readIfLoaded(exit);c.assertTrue(restored.isPresent()&&restored.get().isOf(ModWorldBlocks.PORTAL_PANEL),"actual saved destination, not synthetic AIR, is FULL before travel");
                    c.assertTrue(w.getBlockEntity(exit) instanceof PortalPanelBlockEntity target&&target.isLinked()&&source.equals(target.getLinkPartnerPos()),"normal disk reload preserved the exact reciprocal BE link");
                    c.assertTrue(stays.entity.getVelocity().lengthSquared()<1e-12,"retry uses current stopped momentum, never replays old crossing velocity");phase=3;
                }else c.assertTrue(ticks<300,"bounded warmup must give a waiting near-gate player an actual ready destination");
            }else if(phase==3&&++after>=12){
                stays.channel.runPendingTasks();int packets=0;for(Object raw:stays.channel.outboundMessages())if(raw instanceof PlayerPositionLookS2CPacket)packets++;
                c.assertTrue(packets==1&&stays.entity.getX()>exit.getX()&&retreats.entity.getX()<exit.getX()-100,"one retained real crossing transfers once; retreating neighbour stays: packets="+packets);
                close();c.complete();
            }
        }
        public void close(){
            if(closed)return;closed=true;var w=c.getWorld();
            try{
                if(WorldAccess.of(w).readIfLoaded(source).isPresent()&&w.getBlockEntity(source) instanceof PortalPanelBlockEntity panel)panel.clearLink();
                if(WorldAccess.of(w).readIfLoaded(exit).isPresent()&&w.getBlockEntity(exit) instanceof PortalPanelBlockEntity panel)panel.clearLink();
                stays.close();retreats.close();PortalTravel.clear();
            }finally{chunks.close();}
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-pending-cold-exit",tickLimit=400)
    public void aRealSavedUnknownExitWarmsThenTransfersTheWaitingCrossingExactlyOnceAndRetreatCancels(TestContext c){
        var f=new Fixture(c);c.runAtEveryTick(()->{if(f.closed)return;try{f.tick();}catch(RuntimeException|Error failure){f.close();throw failure;}});
    }
}
