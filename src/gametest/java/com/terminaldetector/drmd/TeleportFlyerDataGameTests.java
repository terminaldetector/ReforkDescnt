package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.*;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Protocol/storage layer only. These tests never manufacture a gameplay teleport or damage hit. */
public final class TeleportFlyerDataGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-saved-identity")
    public void independentDefaultAndCompressedPendingFutureStateNeverGrantLoadTeleport(TestContext c)throws Exception{
        var actor=ModEntities.TELEPORT_FLYER.create(c.getWorld());var owned=new ArrayList<Entity>();owned.add(actor);
        try{
            c.assertTrue(actor.getClass()==TeleportFlyerEntity.class&&actor.kind()==FlightEnemyKind.TELEPORT_FLYER&&actor.getRole()==AiRole.INTERCEPTOR
                &&actor.getHealth()==AiRole.INTERCEPTOR.hullHp&&actor.getDroneShield()==AiRole.INTERCEPTOR.shield&&actor.hasNoGravity()&&!actor.isAiDisabled(),"separate full-health enabled authored default, unchanged INTERCEPTOR profile");
            c.assertTrue(Arrays.asList(ModItems.creativeSpawnEggs()).contains(ModItems.EGG_TELEPORT_FLYER),"own discoverable creative egg");
            c.assertTrue(getClass().getResource("/assets/drmd/models/item/spawn_egg_teleport_flyer.json")!=null,"real inventory model present");
            actor.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(6,9,6))));actor.setVelocity(.1,.2,-.1);actor.setFlightRoll(45);
            var tag=new NbtCompound();c.assertTrue(actor.saveSelfNbt(tag),"actual independent factory save");
            var pending=new NbtCompound();pending.putInt("schema",1);pending.putString("phase","WARN");pending.putInt("remaining",1);pending.putInt("cooldown",0);tag.put("u07Teleport",pending);
            var parent=TeleportFlyerTestProbe.proofDirectory(c.getWorld().getServer());
            var file=parent.resolve("pending-flyer-"+actor.getUuid()+".dat");c.assertTrue(!Files.exists(file),"compressed save is fresh");NbtIo.writeCompressed(tag,file);
            var disk=NbtIo.readCompressed(file,NbtSizeTracker.of(2*1024*1024));var copy=(TeleportFlyerEntity)EntityType.getEntityFromNbt(disk,c.getWorld()).orElseThrow();owned.add(copy);
            c.assertTrue(copy.getType()==ModEntities.TELEPORT_FLYER&&copy.getUuid().equals(actor.getUuid())&&copy.getRole()==AiRole.INTERCEPTOR&&copy.getVelocity().equals(actor.getVelocity())
                &&copy.getFlightRoll()==45&&copy.getModEnergy()==100&&copy.ability().phase()==TeleportAbilityState.Phase.RECOVERY&&copy.ability().remaining()==20&&copy.ability().cooldown()==100
                &&copy.lockedDestination()==null&&copy.teleportCount()==0,"saved pending warning cancels with preserved identity/physical energy, no silent load teleport");
            var future=pending.copy();future.putInt("schema",37);future.putString("opaque","retain exactly");tag.put("u07Teleport",future);
            var unknown=(TeleportFlyerEntity)EntityType.getEntityFromNbt(tag,c.getWorld()).orElseThrow();owned.add(unknown);var saved=new NbtCompound();unknown.writeCustomDataToNbt(saved);
            c.assertTrue(unknown.ability().phase()==TeleportAbilityState.Phase.REFUSED&&future.equals(saved.get("u07Teleport"))&&unknown.ability().mayFire(),"future metadata preserved; only relocation authority is denied");
            var corrupt=pending.copy();corrupt.putInt("remaining",0);tag.put("u07Teleport",corrupt);var broken=(TeleportFlyerEntity)EntityType.getEntityFromNbt(tag,c.getWorld()).orElseThrow();owned.add(broken);saved=new NbtCompound();broken.writeCustomDataToNbt(saved);
            c.assertTrue(broken.ability().phase()==TeleportAbilityState.Phase.REFUSED&&corrupt.equals(saved.get("u07Teleport")),"corrupt pending data cannot become a free ready transition");
            var before=broken.getPos();float energy=broken.getModEnergy();c.assertTrue(!broken.commitTeleport(null)&&broken.getPos().equals(before)&&broken.getModEnergy()==energy,"no public pose mutation without a genuine same-END admission permit");
        }finally{for(var e:owned)if(e!=null)e.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-unknown-exit")
    public void fullVolumeAdmissionCannotLoadAnUnknownDestination(TestContext c){
        var actor=ModEntities.TELEPORT_FLYER.create(c.getWorld());var point=new Vec3d(1940000.5,130,2910000.5);var pos=BlockPos.ofFloored(point);
        try{
            c.assertTrue(c.getWorld().getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)==null,"actual unknown remote column premise");
            var result=TeleportDestination.query(c.getWorld(),actor,point);
            c.assertTrue(!result.clear()&&result.reason()==TeleportDestination.Reason.UNAVAILABLE&&result.volumes()<=64
                &&c.getWorld().getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)==null,"unavailable full volume refuses without synchronous generation: "+result);
        }finally{actor.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="u07-resource-rollback")
    public void actualRegisteredResourceReservationRefundsRefusalAndException(TestContext c)throws Exception{
        var actor=ModEntities.TELEPORT_FLYER.create(c.getWorld());
        try{
            var method=DroneEntity.class.getDeclaredMethod("withModEnergy",float.class,BooleanSupplier.class);method.setAccessible(true);int[] calls={0};
            c.assertTrue(!(Boolean)method.invoke(actor,20f,(BooleanSupplier)()->{calls[0]++;return false;})&&actor.getModEnergy()==100&&calls[0]==1,"refused actual operation refunds its reservation exactly");
            try{method.invoke(actor,20f,(BooleanSupplier)()->{throw new IllegalStateException("u07_refusal_fixture");});c.assertTrue(false,"throwing callback must remain an error");}
            catch(InvocationTargetException expected){c.assertTrue(expected.getCause() instanceof IllegalStateException&&actor.getModEnergy()==100,"actual exception refunds without disguising callback failure");}
            var tag=new NbtCompound();actor.writeCustomDataToNbt(tag);tag.putFloat("modEnergy",0);actor.readCustomDataFromNbt(tag);
            c.assertTrue(!(Boolean)method.invoke(actor,20f,(BooleanSupplier)()->{calls[0]++;return true;})&&actor.getModEnergy()==0&&calls[0]==1,"empty actual capacitor cannot execute an operation");
            tag.putFloat("modEnergy",100);actor.readCustomDataFromNbt(tag);
            c.assertTrue((Boolean)method.invoke(actor,20f,(BooleanSupplier)()->true)&&actor.getModEnergy()==80,"accepted transaction charges one20energy cost");
            c.assertTrue(!(Boolean)method.invoke(actor,Float.NaN,(BooleanSupplier)()->true)&&actor.getModEnergy()==80,"nonfinite costs grant no authority");
        }finally{actor.discard();}c.complete();
    }
}
