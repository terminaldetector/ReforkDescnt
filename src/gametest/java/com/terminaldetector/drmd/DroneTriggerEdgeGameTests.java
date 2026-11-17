package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.inventory.*;
import com.terminaldetector.drmd.weapon.items.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Actual registered items through the server receiver and arsenal tick.
 * This layer deliberately does not claim native client/packet acceptance. */
public final class DroneTriggerEdgeGameTests implements FabricGameTest {
    private static final class Rig implements AutoCloseable {
        final TestContext c;
        final ServerPlayerEntity p;
        final EmbeddedChannel channel;
        boolean closed;
        Rig(TestContext c){
            this.c=c;var world=c.getWorld();
            // Vanilla's connected TestContext recipe, without its permanently creative
            // anonymous player override: spectator rejection must be real and testable.
            var client=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),"trigger-fixture"),false);
            p=new ServerPlayerEntity(world.getServer(),world,client.gameProfile(),client.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);
            world.getServer().getPlayerManager().onPlayerConnect(connection,p,client);
            try{
                p.changeGameMode(GameMode.CREATIVE);p.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2,170,2))));
                p.setNoGravity(true);p.getAbilities().flying=true;p.setVelocity(Vec3d.ZERO);p.setYaw(0);p.setPitch(0);
                p.getInventory().clear();DescentPlayerData.get(p).setEnabled(false);DescentPlayerData.get(p).setEnergy(100);
                c.assertTrue(p.currentScreenHandler==p.playerScreenHandler&&!p.hasVehicle(),"ordinary connected free-player fixture");
            }catch(Throwable error){close();throw error;}
        }
        ItemStack equip(Item item){var stack=new ItemStack(item);p.setStackInHand(Hand.MAIN_HAND,stack);return stack;}
        void masks(int... masks){for(int mask:masks)DroneArsenal.receive(p,mask);}
        void tick(){DroneArsenal.tick(p);}
        List<ProjectileEntity> shots(){
            var out=new ArrayList<ProjectileEntity>();for(var e:c.getWorld().iterateEntities())if(e instanceof ProjectileEntity shot&&shot.getOwnerLiving()==p)out.add(shot);return out;
        }
        long lastFire(ItemStack stack){var custom=stack.get(DataComponentTypes.CUSTOM_DATA);return custom==null?0:custom.copyNbt().getLong("lastFire");}
        void empty(String reason){c.assertTrue(shots().isEmpty(),reason);}
        void sameShots(Set<UUID> expected,String reason){c.assertTrue(shots().stream().map(Entity::getUuid).collect(java.util.stream.Collectors.toSet()).equals(expected),reason);}
        Set<UUID> shotIds(){return shots().stream().map(Entity::getUuid).collect(java.util.stream.Collectors.toSet());}
        public void close(){
            if(closed)return;closed=true;DroneArsenal.clear(p.getUuid());
            for(var shot:shots())shot.discard();p.currentScreenHandler=p.playerScreenHandler;
            DescentPlayerData.get(p).droneLoadout().clear();
            try{p.getServer().getPlayerManager().remove(p);}finally{p.discard();channel.finishAndReleaseAll();}
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-primary")
    public void sameTickReleasedPrimaryTapFiresTheRegisteredItemExactlyOnce(TestContext c){
        try(var r=new Rig(c)){
            var original=r.equip(ModItems.LASER);long now=c.getWorld().getTime();r.masks(1,0);r.tick();
            c.assertTrue(r.shots().size()==2,"decoded 1 then 0 in one server tick must fire the real twin laser once");
            c.assertTrue(r.lastFire(original)==now&&r.p.getItemCooldownManager().isCoolingDown(ModItems.LASER),"real item use writes lastFire and starts its real cooldown");
            c.assertTrue(r.p.getMainHandStack()==original,"ordinary hotbar stack identity is preserved");
            var ids=r.shotIds();r.tick();r.tick();r.sameShots(ids,"released tap cannot be replayed on later arsenal ticks");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-mixed")
    public void repeatedMixedMasksFireEachDistinctSocketOnceWithAnEmptyRealHand(TestContext c){
        try(var r=new Rig(c)){
            DescentPlayerData.get(r.p).setEnabled(true);var slots=DroneLoadout.of(r.p);
            var primary=new ItemStack(ModItems.LASER);var secondary=new ItemStack(ModItems.CONCUSSION);
            slots.setStack(0,primary);slots.setStack(1,secondary);r.masks(1,1,3,3,0,3,0);r.tick();
            c.assertTrue(r.shots().size()==3,"bounded repeated/mixed edge bits fire one twin laser and one actual rocket");
            c.assertTrue(r.lastFire(primary)==c.getWorld().getTime()&&r.lastFire(secondary)==c.getWorld().getTime(),"both actual registered socket items are invoked");
            c.assertTrue(r.p.getMainHandStack().isEmpty()&&slots.getStack(0)==primary&&slots.getStack(1)==secondary,"socket routing restores the empty real hand and original equipment");
            var ids=r.shotIds();r.tick();r.sameShots(ids,"mask repetitions do not create a queue or additional volley");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-fallback")
    public void duplicateFallbackChannelsAndUnknownBitsCannotMultiplyATap(TestContext c){
        try(var r=new Rig(c)){
            r.equip(ModItems.LASER);r.masks(8,0);r.tick();r.empty("bits outside the three real channels cannot fire");
            r.masks(3,3,0,3,0);r.tick();
            c.assertTrue(r.shots().size()==2,"simultaneous ordinary LMB/RMB fallback to one stack produces only its one real volley");
            var ids=r.shotIds();r.tick();r.sameShots(ids,"coalesced duplicate masks cannot replay");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-cooldown")
    public void coolingTapIsDiscardedInsteadOfReplayedAfterCooldown(TestContext c){
        try(var r=new Rig(c)){
            r.equip(ModItems.LASER);r.p.getItemCooldownManager().set(ModItems.LASER,2);r.masks(1,0);r.tick();r.empty("the real manager cooldown rejects an otherwise valid tap");
            // Synchronous receiver-layer test: expire the actual manager, without
            // touching an arsenal mask, queue or production world clock.
            r.p.getItemCooldownManager().update();r.p.getItemCooldownManager().update();
            c.assertTrue(!r.p.getItemCooldownManager().isCoolingDown(ModItems.LASER),"actual manager cooldown has elapsed");
            r.tick();r.empty("the consumed cooling edge cannot become a delayed shot");
            r.masks(1,0);r.tick();c.assertTrue(r.shots().size()==2,"a fresh accepted click still works after expiry");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-fusion-tap")
    public void sameTickFusionTapStartsAndReleasesOnceWithoutALingeringCharge(TestContext c){
        try(var r=new Rig(c)){
            var original=r.equip(ModItems.FUSION);float energy=DescentPlayerData.get(r.p).getEnergy();r.masks(1,0);r.tick();
            c.assertTrue(r.shots().size()==1&&DescentPlayerData.get(r.p).getEnergy()<energy,"a released Fusion tap calls its real zero-charge stop and emits one material shot");
            c.assertTrue(r.lastFire(original)==c.getWorld().getTime()&&r.p.getItemCooldownManager().isCoolingDown(ModItems.FUSION),"real Fusion release owns its debit, lastFire and cooldown");
            c.assertTrue(!r.p.isUsingItem()&&r.p.getMainHandStack()==original,"there is no synthetic lingering vanilla charge or swapped hand");
            var ids=r.shotIds();r.tick();r.tick();r.sameShots(ids,"released Fusion edge cannot stop/fire twice");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-fusion-hold")
    public void realHeldFusionChargeStillWaitsForReleaseAndGuiStillCancelsIt(TestContext c){
        try(var r=new Rig(c)){
            r.equip(ModItems.FUSION);r.masks(1);r.tick();for(int i=0;i<6;i++)r.tick();r.empty("latest held mask keeps normal Fusion charging without an early shot");
            r.masks(0);r.tick();c.assertTrue(r.shots().size()==1&&!r.p.isUsingItem(),"normal held charge releases exactly one real Fusion shot");
            var ids=r.shotIds();r.tick();r.sameShots(ids,"normal release does not repeat");
        }
        try(var r=new Rig(c)){
            r.equip(ModItems.FUSION);r.masks(1);r.tick();r.tick();
            r.p.currentScreenHandler=new DroneInventoryHandler(7,r.p.getInventory(),new SimpleInventory(5));r.masks(0);r.tick();
            r.p.currentScreenHandler=r.p.playerScreenHandler;r.tick();r.empty("GUI ownership cancels charge without an accidental release shot or revived input");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-invalid-owner")
    public void invalidScreensAndSpectatorsCannotRetainOrRevivePendingClicks(TestContext c){
        for(boolean spectator:new boolean[]{false,true})try(var r=new Rig(c)){
            r.equip(ModItems.LASER);r.masks(1,0);
            if(spectator)r.p.changeGameMode(GameMode.SPECTATOR);else r.p.currentScreenHandler=new DroneInventoryHandler(8,r.p.getInventory(),new SimpleInventory(5));
            r.masks(0); // The invalid receiver itself must discard previously accepted edges.
            r.p.currentScreenHandler=r.p.playerScreenHandler;r.p.changeGameMode(GameMode.CREATIVE);r.tick();r.empty("invalid receiver clears older pending edges before eligibility returns");
            r.masks(1,0);
            if(spectator)r.p.changeGameMode(GameMode.SPECTATOR);else r.p.currentScreenHandler=new DroneInventoryHandler(9,r.p.getInventory(),new SimpleInventory(5));
            r.tick();r.p.currentScreenHandler=r.p.playerScreenHandler;r.p.changeGameMode(GameMode.CREATIVE);r.tick();r.empty("invalid arsenal ownership also discards consumed edges permanently");
            r.masks(1,0);r.tick();c.assertTrue(r.shots().size()==2,"fresh valid input works after GUI/spectator ownership ends");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-selection")
    public void edgeBelongsToItsOriginalSelectedStackInHotbarAndFlightSockets(TestContext c){
        for(boolean flight:new boolean[]{false,true})for(boolean empty:new boolean[]{false,true})try(var r=new Rig(c)){
            DescentPlayerData.get(r.p).setEnabled(flight);var original=new ItemStack(ModItems.LASER);
            if(flight)DroneLoadout.of(r.p).setStack(0,original);else r.p.setStackInHand(Hand.MAIN_HAND,original);
            r.masks(1,0);var replacement=empty?ItemStack.EMPTY:new ItemStack(ModItems.LASER);
            if(flight)DroneLoadout.of(r.p).setStack(0,replacement);else r.p.setStackInHand(Hand.MAIN_HAND,replacement);
            r.tick();r.empty("a released edge cannot fire an empty selection or a different stack of the same weapon");
            if(flight)DroneLoadout.of(r.p).setStack(0,original);else r.p.setStackInHand(Hand.MAIN_HAND,original);
            r.tick();r.empty("restoring the old selected item cannot resurrect a consumed stale edge");
            r.masks(1,0);r.tick();c.assertTrue(r.shots().size()==2,"a fresh click against the actual restored stack still fires");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-ttl",tickLimit=60)
    public void LatestHeldInputExpiresWithoutARefreshAndCannotResumeLater(TestContext c){
        var r=new Rig(c);var stack=r.equip(ModItems.LASER);long[] start={-1},expiredLast={-1};
        // One driver registered at entry; never register scheduler work from its callback.
        c.runAtEveryTick(()->{
            if(r.closed)return;
            try{
                long now=c.getWorld().getTime();
                if(start[0]<0){start[0]=now;r.masks(1);r.tick();c.assertTrue(r.lastFire(stack)==now,"actual latest-held input initially fires");return;}
                long elapsed=now-start[0];
                if(elapsed>=9&&expiredLast[0]<0){
                    expiredLast[0]=r.lastFire(stack);
                    c.assertTrue(expiredLast[0]<=start[0]+6,"unrefreshed held input stops within the existing six-tick TTL");
                }
                if(elapsed>=14){
                    r.tick();c.assertTrue(r.lastFire(stack)==expiredLast[0],"expired held mask cannot resume or replay an edge after later cooldown expiry");
                    r.close();c.complete();
                }
            }catch(Throwable error){r.close();throw error;}
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="trigger-edge-charged-cross-bank")
    public void quickPrimaryFallbackTapCannotReleaseOrDuplicateAnExistingSecondaryCharge(TestContext c){
        try(var r=new Rig(c)){
            var original=r.equip(ModItems.FUSION);r.masks(2);r.tick();for(int i=0;i<6;i++)r.tick();
            r.empty("original secondary Fusion hold is genuinely charging before the cross-bank click");
            float energy=DescentPlayerData.get(r.p).getEnergy();r.masks(3,2);r.tick();
            r.empty("released primary edge must not start a second lifecycle or release the still-held same-stack secondary charge");
            c.assertTrue(DescentPlayerData.get(r.p).getEnergy()==energy,"cross-bank fallback click cannot debit or release the original charge");
            r.tick();r.empty("original secondary charge remains held on subsequent ticks");
            r.masks(0);r.tick();
            c.assertTrue(r.shots().size()==1&&DescentPlayerData.get(r.p).getEnergy()<energy,"one actual secondary release emits exactly its one original charged Fusion shot");
            c.assertTrue(r.p.getMainHandStack()==original&&!r.p.isUsingItem(),"cross-bank dedup preserves the selected stack and clears normal use state on final release");
            var ids=r.shotIds();r.tick();r.sameShots(ids,"neither bank can replay a duplicate release after final zero");
        }c.complete();
    }
}
