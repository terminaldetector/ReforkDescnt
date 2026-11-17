package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.enclave.EnclaveSite;
import com.terminaldetector.drmd.world.enclave.FactionMemory;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** First baseline: actual ordinary Survival interaction, no future mission APIs. */
public final class EnclaveHeraldBaselineGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="enclave-herald-acceptance-baseline",tickLimit=120)
    public void threeActualSurvivalAcceptancesCannotGrantReputation(TestContext context){
        var driver=new Driver(context);
        // Exactly one scheduler registration. No callback mutates GameTest's task map.
        context.runAtEveryTick(()->{
            if(driver.finished)return;
            try{driver.tick();}
            catch(RuntimeException|Error failure){driver.finished=true;driver.close(failure);throw failure;}
        });
    }

    private static final class Driver {
        final TestContext c;final ServerWorld w;final BlockPos herald;final UUID id=UUID.randomUUID();
        final List<Map<String,Object>> uses=new ArrayList<>();
        AiFixtureChunks chunks;ServerPlayerEntity player;EmbeddedChannel channel;
        BlockState previous;EnclaveSite site;FactionMemory memory;
        int admission,before;long lastGameTick=Long.MIN_VALUE,lastServerTick=Long.MIN_VALUE;
        boolean finished,closed;

        Driver(TestContext c){this.c=c;w=c.getWorld();herald=c.getAbsolutePos(new BlockPos(3,2,3));}
        void tick(){
            w.resetIdleTimeout();
            c.assertTrue(c.getTick()<115,"baseline120 tick framework retains a bounded cleanup margin");
            if(chunks==null){chunks=new AiFixtureChunks(w,herald,1);return;}
            if(!chunks.entityTicking(herald)){
                c.assertTrue(++admission<80,"baseline fixture admits actual entity-ticking herald column: "+chunks.readiness(herald));return;
            }
            if(player==null){admit();return;}
            pin();
            if(uses.size()<3){use();return;}
            int after=memory.getRep(id,site.origin);
            var result=new LinkedHashMap<String,Object>();result.put("kind","three-uses-completed-before-criterion");
            result.put("owner",id.toString());result.put("dimension",w.getRegistryKey().getValue().toString());
            result.put("herald",List.of(herald.getX(),herald.getY(),herald.getZ()));result.put("origin",site.origin.id);
            result.put("before",before);result.put("after",after);result.put("actualDelta",after-before);result.put("expectedDelta",0);
            result.put("uses",uses);result.put("actualMode",player.interactionManager.getGameMode().toString());
            result.put("actualPlayerAge",player.age);result.put("realManagerIdentity",w.getServer().getPlayerManager().getPlayer(id)==player);
            result.put("passiveDeliveredMessages",messages());receipt("ENCLAVE_BASELINE_RESULT",result);
            c.assertTrue(uses.size()==3,"all three ordinary server block interactions actually ran before the reward criterion");
            // The intended baseline failure is here, after actual three-use receipts.
            // No assertion of +9 is used to turn the old bug into an expected pass.
            c.assertTrue(after==before,"accepting an enclave offer must not grant reputation before objectives and extraction: actualDelta="+(after-before));
            finished=true;close(null);c.complete();
        }
        void admit(){
            c.assertTrue(!w.getBlockState(herald).hasBlockEntity(),"fixture never replaces a preexisting block entity");
            var client=ConnectedClientData.createDefault(new GameProfile(id,"mq-"+id.toString().substring(0,8)),false);
            player=new ServerPlayerEntity(w.getServer(),w,client.gameProfile(),client.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);
            w.getServer().getPlayerManager().onPlayerConnect(connection,player,client);
            player.changeGameMode(GameMode.SURVIVAL);DescentPlayerData.get(player).setEnabled(false);
            player.getInventory().clear();player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
            player.setNoGravity(true);player.getAbilities().flying=false;player.setSneaking(true);
            previous=w.getBlockState(herald);w.setBlockState(herald,ModWorldBlocks.ENCLAVE_HERALD.getDefaultState(),Block.NOTIFY_ALL);
            site=EnclaveSite.generate(w.getSeed(),herald);memory=FactionMemory.of(w);before=memory.getRep(id,site.origin);pin();
            c.assertTrue(before==0,"fresh actual player UUID has no starting reputation");
            c.assertTrue(player.interactionManager.getGameMode()==GameMode.SURVIVAL&&!player.isCreative()&&!player.isSpectator()&&!player.hasPermissionLevel(2),"real unprivileged Survival connection, not a permanently Creative mock");
            receipt("ENCLAVE_BASELINE_ADMITTED",Map.of("owner",id.toString(),"origin",site.origin.id,"before",before,"gameTick",c.getTick(),"serverTick",w.getServer().getTicks(),"herald",List.of(herald.getX(),herald.getY(),herald.getZ())));
        }
        void pin(){
            player.setPosition(new Vec3d(herald.getX()+.5,herald.getY(),herald.getZ()-2));player.setVelocity(Vec3d.ZERO);player.setNoGravity(true);player.setSneaking(true);
            var d=point().subtract(player.getEyePos());player.setYaw((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90));
            player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));player.setHeadYaw(player.getYaw());
        }
        Vec3d point(){return Vec3d.ofCenter(herald).add(0,0,-.5);}
        void use(){
            c.assertTrue(player.isAlive()&&player.getServerWorld()==w&&w.getServer().getPlayerManager().getPlayer(id)==player&&w.getEntity(id)==player,"every use has the real connected living owner in the actual world");
            c.assertTrue(player.interactionManager.getGameMode()==GameMode.SURVIVAL&&!player.isCreative()&&player.isSneaking()&&player.getMainHandStack().isEmpty()&&player.getOffHandStack().isEmpty(),"every actual use is empty-hand Survival sneak-use");
            c.assertTrue(w.getBlockState(herald).isOf(ModWorldBlocks.ENCLAVE_HERALD)&&player.getEyePos().distanceTo(point())<=player.getBlockInteractionRange(),"actual unchanged herald and ordinary block-interaction reach");
            long gameTick=c.getTick(),serverTick=w.getServer().getTicks();
            if(!uses.isEmpty())c.assertTrue(gameTick==lastGameTick+1&&serverTick==lastServerTick+1,"three actual interactions occur on consecutive game/server ticks");
            var action=player.interactionManager.interactBlock(player,w,player.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(point(),Direction.NORTH,herald,false));
            var use=new LinkedHashMap<String,Object>();use.put("sequence",uses.size()+1);use.put("gameTick",gameTick);use.put("serverTick",serverTick);
            use.put("actualAction",action.toString());use.put("accepted",action.isAccepted());use.put("actualRep",memory.getRep(id,site.origin));
            use.put("owner",id.toString());use.put("mode",player.interactionManager.getGameMode().toString());use.put("sneaking",player.isSneaking());use.put("realPlayerAge",player.age);
            uses.add(use);lastGameTick=gameTick;lastServerTick=serverTick;receipt("ENCLAVE_BASELINE_USE",use);
            c.assertTrue(action.isAccepted(),"actual Survival server block-use path reaches the herald rather than returning PASS");
        }
        List<String> messages(){
            // Read-only normal delivery observation on the following existing tick.
            // No flush, pump, extra wait or expected-message injection.
            var result=new ArrayList<String>();int chars=0;
            for(Object packet:channel.outboundMessages())if(packet instanceof GameMessageS2CPacket message){
                var text=message.content().getString();if(result.size()>=32||chars+text.length()>65_536)break;result.add(text);chars+=text.length();
            }
            return List.copyOf(result);
        }
        void close(Throwable original){
            if(closed)return;closed=true;Throwable failure=original;
            try{if(previous!=null)w.setBlockState(herald,previous,Block.NOTIFY_ALL);}catch(RuntimeException|Error bad){failure=append(failure,bad);}
            try{if(player!=null&&w.getServer().getPlayerManager().getPlayer(id)==player)w.getServer().getPlayerManager().remove(player);}catch(RuntimeException|Error bad){failure=append(failure,bad);}
            try{if(player!=null)player.discard();}catch(RuntimeException|Error bad){failure=append(failure,bad);}
            try{if(channel!=null)channel.finishAndReleaseAll();}catch(RuntimeException|Error bad){failure=append(failure,bad);}
            try{if(chunks!=null)chunks.close();}catch(RuntimeException|Error bad){failure=append(failure,bad);}
            receipt("ENCLAVE_BASELINE_CLOSED",Map.of("usesObserved",uses.size(),"playerRemoved",player==null||player.isRemoved(),"channelOpen",channel!=null&&channel.isOpen(),"originalFailure",original==null?"none":original.toString()));
            if(original==null){if(failure instanceof Error e)throw e;if(failure instanceof RuntimeException e)throw e;}
        }
        static Throwable append(Throwable first,Throwable next){if(first==null)return next;if(first!=next)first.addSuppressed(next);return first;}
    }
    private static void receipt(String kind,Object value){System.out.println(kind+"="+new com.google.gson.Gson().toJson(value));}
}
