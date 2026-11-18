package com.terminaldetector.drmd;

import com.google.gson.JsonObject;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.physics.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual default actor and durable enemy; ordinary engine ticks and the production END relay only. */
final class TeleportFlyerFixture implements AutoCloseable {
    final TestContext c;final ServerWorld world;final BlockPos base;final Vec3d start;
    final TeleportFlyerEntity actor;final CowEntity enemy;final AiFixtureChunks chunks;
    final List<Entity> extra=new ArrayList<>();final Map<BlockPos,net.minecraft.block.BlockState> blocks=new LinkedHashMap<>();
    final Set<UUID> shots=new HashSet<>();final Map<UUID,Vec3d> firstShot=new HashMap<>();
    final PhysicsProfile oldProfile;final TeleportAbilityRelay.Stats initialStats;
    final boolean diagnostic=Boolean.getBoolean("drmd.test.u07NaturalDiagnostic");
    int ticks,admission;boolean spawned,closed,movingShot,ownAcquired,freshOwn,actualDamage;
    com.google.gson.JsonArray sampledProjectiles=new com.google.gson.JsonArray();int sampledProjectileCount,projectileSampleOverflow;
    final float initialEnemyHealth;
    TeleportFlyerFixture(TestContext c,int offset,boolean expanded){
        this.c=c;world=c.getWorld();base=c.getAbsolutePos(new BlockPos(210000+offset,150,26000));start=Vec3d.ofBottomCenter(base);
        oldProfile=PhysicsProfileState.get(world).profile();if(expanded)PhysicsProfileState.get(world).set(PhysicsProfile.EXPANDED);
        initialStats=TeleportAbilityRelay.stats(world);chunks=new AiFixtureChunks(world,base,2);
        actor=ModEntities.TELEPORT_FLYER.create(world);enemy=EntityType.COW.create(world);
        try{
            c.assertTrue(actor!=null&&enemy!=null&&TeleportAbilityRelay.registered(),"real registered prototype and production lifecycle are active, no test bootstrap");
            actor.setPosition(start);actor.setPersistent();enemy.setPosition(start.add(20,0,0));enemy.setNoGravity(true);enemy.setAiDisabled(true);enemy.setPersistent();
            enemy.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1024);enemy.setHealth(1024);
            enemy.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
            initialEnemyHealth=enemy.getHealth();TeleportFlyerTestProbe.attach(this);
        }catch(Throwable failure){close();throw failure;}
    }
    boolean admit(){
        if(spawned)return true;
        if(!chunks.entityTicking(actor.getBlockPos(),enemy.getBlockPos())){
            c.assertTrue(++admission<80,"actual entity-ticking fixture admission: "+chunks.readiness(actor.getBlockPos(),enemy.getBlockPos()));return false;
        }
        c.assertTrue(world.spawnEntity(enemy)&&world.spawnEntity(actor),"real actor/enemy spawn");spawned=true;return false;
    }
    void sample(){
        ticks++;freshOwn=actor.getTarget()==enemy&&actor.canSee(enemy);ownAcquired|=freshOwn;actualDamage|=enemy.getHealth()<initialEnemyHealth&&enemy.getAttacker()==actor;
        if(diagnostic){sampledProjectiles=new com.google.gson.JsonArray();sampledProjectileCount=0;projectileSampleOverflow=0;}
        for(var e:world.iterateEntities())if(e instanceof ProjectileEntity shot&&shot.getOwnerLiving()==actor){
            shots.add(shot.getUuid());var previous=firstShot.putIfAbsent(shot.getUuid(),shot.getPos());if(previous!=null&&shot.getPos().squaredDistanceTo(previous)>.01)movingShot=true;
            if(diagnostic){
                sampledProjectileCount++;
                if(sampledProjectiles.size()<48){
                    var sample=new com.google.gson.JsonObject();sample.addProperty("uuid",shot.getUuid().toString());sample.addProperty("owner",actor.getUuid().toString());sample.addProperty("age",shot.age);
                    sample.addProperty("x",shot.getX());sample.addProperty("y",shot.getY());sample.addProperty("z",shot.getZ());sample.addProperty("vx",shot.getVelocity().x);sample.addProperty("vy",shot.getVelocity().y);sample.addProperty("vz",shot.getVelocity().z);sampledProjectiles.add(sample);
                }else projectileSampleOverflow++;
            }
        }
    }
    @FunctionalInterface interface Step {void run(TeleportFlyerFixture fixture)throws Exception;}
    void drive(Step action){c.runAtEveryTick(()->{
        if(closed)return;try{world.resetIdleTimeout();if(!admit())return;sample();TeleportFlyerTestProbe.beforeAssertions(this);action.run(this);}
        catch(Throwable failure){close();if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;throw new RuntimeException(failure);}
    });}
    void block(BlockPos pos,net.minecraft.block.BlockState state){
        c.assertTrue(com.terminaldetector.drmd.world.edit.WorldEditSafety.mayChange(world,pos,actor),"known loaded fixture edit before state read");
        blocks.putIfAbsent(pos,world.getBlockState(pos));c.assertTrue(world.setBlockState(pos,state,2)||world.getBlockState(pos).equals(state),"actual material edit");
    }
    TeleportFlyerTestProbe.Record record(){return TeleportFlyerTestProbe.record(this);}
    String state(){return "ticks="+ticks+" age="+actor.age+" phase="+actor.ability().phase()+" remaining="+actor.ability().remaining()+" cooldown="+actor.ability().cooldown()
        +" blinks="+actor.teleportCount()+" pos="+actor.getPos()+" energy="+actor.getModEnergy()+" shield="+actor.getDroneShield()+" hp="+actor.getHealth()
        +" own="+ownAcquired+" shots="+shots.size()+" moving="+movingShot+" enemyHp="+enemy.getHealth()+" actualDamage="+actualDamage+" relay="+TeleportAbilityRelay.stats(world);}
    void complete(){close();c.complete();}
    public void close(){
        if(closed)return;closed=true;var proof=TeleportFlyerTestProbe.record(this);
        if(proof!=null&&proof.corpse!=null&&!extra.contains(proof.corpse))extra.add(proof.corpse);
        TeleportFlyerTestProbe.close(this);
        for(var entity:java.util.stream.StreamSupport.stream(world.iterateEntities().spliterator(),false).toList())
            if(entity instanceof ProjectileEntity shot&&(shot.getOwnerEntity()==actor||extra.contains(shot.getOwnerEntity())))entity.discard();
        if(actor!=null)actor.discard();if(enemy!=null)enemy.discard();for(var e:extra)e.discard();
        for(var entry:blocks.entrySet())world.setBlockState(entry.getKey(),entry.getValue(),2);chunks.close();PhysicsProfileState.get(world).set(oldProfile);
    }
}
