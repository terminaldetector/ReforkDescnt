package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.util.UUID;

public final class EntityFieldGravityGameTests implements FabricGameTest {
    private static final EntityType<?>[] NATIVE_FISH = {
        EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH, EntityType.PUFFERFISH
    };

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="fish-field-wall")
    public void submergedNativeFishNeverBorrowStandingMountingOnSixAxes(TestContext context) {
        var world=context.getWorld();var centre=context.getAbsolutePos(new BlockPos(6,80,6));
        prepareFishWater(context,centre,true);var id=UUID.randomUUID();
        try {
            for(var type:NATIVE_FISH) {
                var fish=submergedFish(context,type,centre,false);
                var navigation=fish.getNavigation();var controller=fish.getMoveControl();
                var origin=fish.getPos();var propulsion=new Vec3d(.17,.03,-.04);
                boolean swimming=fish.isSwimming();fish.setYaw(37);fish.setPitch(-20);fish.setOnGround(false);
                try {
                    for(var direction:new Direction[]{Direction.EAST,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST}) {
                        var down=Vec3d.of(direction.getVector());fish.setVelocity(propulsion);
                        field(context,id,centre,down,1);var sample=GravityFields.sample(world,origin);
                        context.assertTrue(sample!=null&&sample.strength()>.99,"fixture samples full field at submerged fish: "+type);
                        tick(context);
                        context.assertTrue(fish.getPos().distanceTo(origin)<1e-9,"submerged native fish must not be teleported onto a standing surface: "+type+" "+direction);
                        context.assertTrue(fish.getVelocity().distanceTo(propulsion.add(down.multiply(.06*sample.strength())))<1e-8,
                            "field pass adds one force; native water drag belongs to later travel: "+type+" "+direction);
                        context.assertTrue(!fish.isOnGround()&&!ActorFrames.active(fish)&&fish.isSwimming()==swimming,
                            "field cannot manufacture standing contact or erase native swimming state: "+type);
                        context.assertTrue(fish.isTouchingWater()&&fish.isSubmergedInWater()&&!fish.hasNoGravity()
                            &&fish.getYaw()==37&&fish.getPitch()==-20,"native fluid/attitude/gravity ownership retained: "+type);
                    }
                    var velocity=fish.getVelocity();GravityFields.remove(id);tick(context);fish.setAiDisabled(false);
                    context.assertTrue(!fish.isAiDisabled()&&fish.getNavigation()==navigation&&fish.getMoveControl()==controller
                        &&fish.getVelocity().equals(velocity),"field exit retains native controllers and propulsion: "+type);
                } finally {GravityFields.remove(id);fish.discard();tick(context);}
            }
        } finally {GravityFields.remove(id);clearFishWater(context,centre);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="fish-field-lease")
    public void submergedFishFieldForceExitAndSavedFlagsRemainSeparateFromNativeWaterTravel(TestContext context) {
        var world=context.getWorld();var centre=context.getAbsolutePos(new BlockPos(6,80,6));
        prepareFishWater(context,centre,false);var id=UUID.randomUUID();
        try {
            for(var type:NATIVE_FISH)for(boolean originalNoGravity:new boolean[]{false,true}) {
                var fish=submergedFish(context,type,centre,originalNoGravity);FishEntity copy=null;
                var origin=fish.getPos();var propulsion=new Vec3d(.12,.05,-.03);
                var navigation=fish.getNavigation();var controller=fish.getMoveControl();double originalGravity=fish.getFinalGravity();
                try {
                    // Measure actual native fluid travel without assuming its damping formula.
                    fish.setAiDisabled(false);fish.getNavigation().stop();fish.setVelocity(propulsion);
                    context.assertTrue(fish.canMoveVoluntarily()&&fish.isSubmergedInWater(),"ordinary native water travel enabled: "+type);
                    fish.travel(Vec3d.ZERO);var nativeVelocity=fish.getVelocity();var nativeDisplacement=fish.getPos().subtract(origin);
                    context.assertTrue(nativeVelocity.distanceTo(propulsion)>1e-5&&nativeDisplacement.length()>.01,
                        "no-field control exercises real fluid travel/drag, not a synthetic velocity calculation: "+type);
                    fish.setPosition(origin);fish.setAiDisabled(true);fish.setOnGround(false);
                    for(float power:new float[]{.5f,1f}) {
                        fish.setVelocity(propulsion);field(context,id,centre,new Vec3d(1,0,0),power);
                        var sample=GravityFields.sample(world,origin);
                        context.assertTrue(sample!=null&&Math.abs(sample.strength()-power)<1e-6,"deep-water strength fixture ready: "+type);
                        tick(context);
                        context.assertTrue(fish.getPos().distanceTo(origin)<1e-9&&fish.getVelocity().distanceTo(propulsion.add(.06*sample.strength(),0,0))<1e-8,
                            "submerged half/full field force must not apply standing or ground drag: "+type+" "+power);
                    }
                    var saved=fish.writeNbt(new NbtCompound());copy=(FishEntity)type.create(world);copy.readNbt(saved);
                    context.assertTrue(saved.getBoolean("NoGravity")==originalNoGravity&&copy.hasNoGravity()==originalNoGravity
                        &&copy.getUuid().equals(fish.getUuid())&&!EntityGravitySystem.suppressesVanillaGravity(copy),
                        "NBT alias keeps saved flag but cannot acquire live field ownership: "+type);
                    var velocity=fish.getVelocity();GravityFields.remove(id);tick(context);
                    context.assertTrue(fish.hasNoGravity()==originalNoGravity&&!EntityGravitySystem.suppressesVanillaGravity(fish)
                        &&Math.abs(fish.getFinalGravity()-originalGravity)<1e-9&&fish.getVelocity().equals(velocity),
                        "field exit restores ordinary acceleration ownership without resetting propulsion: "+type);
                    fish.setPosition(origin);fish.setVelocity(propulsion);fish.setAiDisabled(false);
                    fish.travel(Vec3d.ZERO);
                    context.assertTrue(fish.getVelocity().distanceTo(nativeVelocity)<1e-8
                        &&fish.getPos().subtract(origin).distanceTo(nativeDisplacement)<1e-8
                        &&fish.getNavigation()==navigation&&fish.getMoveControl()==controller,
                        "after exit the same actual native water travel/drag still owns movement: "+type);
                } finally {GravityFields.remove(id);if(copy!=null)copy.discard();fish.discard();tick(context);}
            }
        } finally {GravityFields.remove(id);clearFishWater(context,centre);}
        context.complete();
    }

    private static FishEntity submergedFish(TestContext context,EntityType<?> type,BlockPos centre,boolean noGravity) {
        var fish=(FishEntity)type.create(context.getWorld());
        context.assertTrue(fish!=null&&!fish.isAiDisabled(),"native fish AI starts enabled: "+type);
        fish.setNoGravity(noGravity);fish.setAiDisabled(true);fish.setPosition(Vec3d.ofCenter(centre));
        context.assertTrue(context.getWorld().spawnEntity(fish),"real fish registered: "+type);
        boolean ready=false;
        try {
            // Populate fluid contact through ordinary entity lifecycle, never set a synthetic water flag.
            for(int i=0;i<3&&!fish.isSubmergedInWater();i++)((Entity)fish).tick();
            context.assertTrue(fish.isTouchingWater()&&fish.isSubmergedInWater()
                &&context.getWorld().getFluidState(fish.getBlockPos()).isIn(FluidTags.WATER),
                "real native water contact ready: "+type+" touching="+fish.isTouchingWater()+" submerged="+fish.isSubmergedInWater());
            fish.setPosition(Vec3d.ofCenter(centre));ready=true;return fish;
        } finally {if(!ready)fish.discard();}
    }

    private static void prepareFishWater(TestContext context,BlockPos centre,boolean walls) {
        for(var pos:BlockPos.iterate(centre.add(-6,-4,-6),centre.add(6,4,6)))
            context.getWorld().setBlockState(pos,Blocks.WATER.getDefaultState(),2);
        if(walls)for(var pos:BlockPos.iterate(centre.add(-3,-3,-3),centre.add(3,3,3)))
            if(Math.abs(pos.getX()-centre.getX())==3||Math.abs(pos.getY()-centre.getY())==3||Math.abs(pos.getZ()-centre.getZ())==3)
                context.getWorld().setBlockState(pos,Blocks.STONE.getDefaultState(),2);
    }

    private static void clearFishWater(TestContext context,BlockPos centre) {
        for(var pos:BlockPos.iterate(centre.add(-6,-4,-6),centre.add(6,4,6)))
            context.getWorld().setBlockState(pos,Blocks.AIR.getDefaultState(),2);
    }

    private static CowEntity actor(TestContext context,boolean noGravity) {
        var world=context.getWorld();var centre=context.getAbsolutePos(new BlockPos(4,80,4));
        for(var p:BlockPos.iterate(centre.add(-6,-6,-6),centre.add(6,6,6)))
            if(!world.getBlockState(p).isAir())world.setBlockState(p,net.minecraft.block.Blocks.AIR.getDefaultState(),2);
        var cow=EntityType.COW.create(world);cow.setAiDisabled(true);cow.setNoGravity(noGravity);
        cow.setPosition(Vec3d.ofCenter(centre));world.spawnEntity(cow);return cow;
    }
    private static void field(TestContext context,UUID id,BlockPos origin,Vec3d down,float power) {
        GravityFields.put(new GravityFields.Field(id,context.getWorld().getRegistryKey(),origin,down,32,power,FieldShape.SPHERE,"actor test"));
    }
    private static void tick(TestContext context){EntityGravitySystem.tick(context.getWorld().getServer());}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void ordinaryActorNeverSerializesTheTemporaryGravityOverride(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();double original=cow.getFinalGravity();
        try {
            context.assertTrue(original>0,"ordinary cow starts with standard acceleration");
            field(context,id,cow.getBlockPos(),new Vec3d(1,0,0),1);tick(context);
            context.assertTrue(EntityGravitySystem.suppressesVanillaGravity(cow) && cow.getFinalGravity()==0,"only standard acceleration suppressed");
            context.assertTrue(!cow.hasNoGravity(),"saved flag still belongs to the actor");
            var nbt=cow.writeNbt(new NbtCompound());
            context.assertTrue(!nbt.getBoolean("NoGravity"),"save contains original gravity");
            var copy=EntityType.COW.create(context.getWorld());copy.readNbt(nbt);
            context.assertTrue(copy.getUuid().equals(cow.getUuid()),"test copy retains saved identity");
            context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(copy) && copy.getFinalGravity()>0,"reloaded object cannot inherit an old runtime lease");
            copy.discard();GravityFields.remove(id);tick(context);
            context.assertTrue(!cow.hasNoGravity() && Math.abs(cow.getFinalGravity()-original)<1e-9,"leaving the field restores standard acceleration");
        } finally {GravityFields.remove(id);cow.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void naturallyWeightlessActorAndExternalFlagOwnerSurviveFieldExit(TestContext context) {
        var cow=actor(context,true);var id=UUID.randomUUID();
        try {
            field(context,id,cow.getBlockPos(),new Vec3d(-1,0,0),1);tick(context);
            context.assertTrue(cow.hasNoGravity() && cow.writeNbt(new NbtCompound()).getBoolean("NoGravity"),"original weightlessness saved inside field");
            GravityFields.remove(id);tick(context);
            context.assertTrue(cow.hasNoGravity() && cow.getFinalGravity()==0,"field exit preserves weightlessness");
            cow.setNoGravity(false);field(context,id,cow.getBlockPos(),new Vec3d(-1,0,0),1);tick(context);
            cow.setNoGravity(true);GravityFields.remove(id);tick(context);
            context.assertTrue(cow.hasNoGravity(),"another owner's flag change survives release");
        } finally {GravityFields.remove(id);cow.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void airborneGravityAccumulatesAndScalesAlongAllSixAxes(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();var origin=cow.getBlockPos();var initial=cow.getPos();
        try {
            for(var direction:Direction.values()) {
                Vec3d down=Vec3d.of(direction.getVector());cow.setPosition(initial);cow.setVelocity(Vec3d.ZERO);
                field(context,id,origin,down,.5f);tick(context);double first=cow.getVelocity().dotProduct(down);
                context.assertTrue(first>.02 && first<.04,"half-strength acceleration on "+direction);
                tick(context);double second=cow.getVelocity().dotProduct(down);
                context.assertTrue(second>first*1.8,"normal velocity accumulates on "+direction);
                cow.setPosition(initial);cow.setVelocity(Vec3d.ZERO);field(context,id,origin,down,1);tick(context);
                context.assertTrue(Math.abs(cow.getVelocity().dotProduct(down)/first-2)<.01,"field strength scales acceleration on "+direction);
            }
        } finally {GravityFields.remove(id);cow.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void ridingAndClearingReleaseOnlyRuntimeGravity(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();var cart=EntityType.MINECART.create(context.getWorld());
        cart.setPosition(cow.getPos());context.getWorld().spawnEntity(cart);
        try {
            field(context,id,cow.getBlockPos(),new Vec3d(1,0,0),1);tick(context);
            context.assertTrue(EntityGravitySystem.suppressesVanillaGravity(cow),"field owns acceleration");
            context.assertTrue(cow.startRiding(cart,true),"actor mounted the vehicle");tick(context);
            context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(cow) && !cow.hasNoGravity(),"rider releases runtime override without flag mutation");
            cow.stopRiding();tick(context);EntityGravitySystem.clear();
            context.assertTrue(cow.getFinalGravity()>0 && !cow.hasNoGravity(),"clear releases acceleration immediately");
        } finally {GravityFields.remove(id);cow.stopRiding();cow.discard();cart.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=40,batchId="entity-field-live")
    public void actualLivingTravelDoesNotAddWorldDownInsideASidewaysField(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();var start=cow.getPos();
        cow.setAiDisabled(false);cow.getNavigation().stop();cow.setMovementSpeed(0);
        var world=context.getWorld();long chunk=cow.getChunkPos().toLong();
        boolean forced=world.getForcedChunks().contains(chunk);
        world.setChunkForced(cow.getChunkPos().x,cow.getChunkPos().z,true);
        field(context,id,cow.getBlockPos(),new Vec3d(1,0,0),.5f);tick(context);
        context.runAtTick(8,()->{
            try {
                context.assertTrue(cow.getX()>start.x+.3 && cow.getVelocity().x>.05,"actual entity ticks accelerate along field down");
                context.assertTrue(Math.abs(cow.getY()-start.y)<.01,"vanilla travel adds no second world-Y gravity");
                context.assertTrue(!cow.hasNoGravity(),"actual ticks leave the original flag unchanged");
            } finally {
                GravityFields.remove(id);cow.discard();tick(context);
                if(!forced)world.setChunkForced(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),false);
            }
            context.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-basic")
    public void airborneFieldPassCannotAdvanceTheActorASecondTime(TestContext context) {
        var cow=actor(context,false);var id=UUID.randomUUID();var start=cow.getPos();
        try {
            field(context,id,cow.getBlockPos(),new Vec3d(1,0,0),.5f);tick(context);
            context.assertTrue(cow.getPos().distanceTo(start)<1e-9 && cow.getVelocity().x>.02,"field changes velocity without moving the actor");
            cow.setAiDisabled(false);
            context.assertTrue(cow.canMoveVoluntarily(),"ordinary travel is enabled for this actor");
            cow.travel(Vec3d.ZERO);var moved=cow.getPos();
            context.assertTrue(moved.x>start.x+.02 && moved.x<start.x+.04,"ordinary travel integrates one movement step");
            tick(context);
            context.assertTrue(cow.getPos().distanceTo(moved)<1e-9,"next field pass does not integrate a duplicate step");
        } finally {GravityFields.remove(id);cow.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="entity-field-interest")
    public void spatialGravityDiscoverySkipsUnrelatedActorsAndRetiresItsLastLease(TestContext context) {
        var cow=actor(context,false);var world=context.getWorld();var id=UUID.randomUUID();
        var unrelated=EntityType.COW.create(world);unrelated.setAiDisabled(true);unrelated.setNoGravity(true);
        unrelated.setPosition(cow.getPos().add(0,96,0));var propulsion=new Vec3d(.13,.04,-.08);unrelated.setVelocity(propulsion);
        context.assertTrue(world.spawnEntity(unrelated),"ordinary unrelated cow registered in the same loaded column");
        try {
            GravityFields.put(new GravityFields.Field(id,world.getRegistryKey(),cow.getBlockPos(),new Vec3d(1,0,0),6,.5f,FieldShape.SPHERE,"interest control"));
            tick(context);var stats=GravityClusterInterest.stats(world);
            context.assertTrue(EntityGravitySystem.suppressesVanillaGravity(cow)&&cow.getVelocity().x>.02,
                "regional discovery gives the actual near actor its ordinary force lease");
            context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(unrelated)&&unrelated.getVelocity().equals(propulsion),
                "distant actor outside the source's spatial cluster retains native velocity and gravity ownership");
            context.assertTrue(stats.queries()>0&&stats.queries()<=GravityClusterInterest.QUERIES_PER_WORLD
                &&stats.maxQueryCandidates()<=GravityClusterInterest.CANDIDATES_PER_QUERY,"actual native query work stays within published budgets");
            GravityFields.remove(id);tick(context);
            context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(cow)&&!cow.hasNoGravity()&&!ActorFrames.active(cow),
                "the final source disappearing still services the actor's release without another discovery query");
            context.assertTrue(unrelated.getVelocity().equals(propulsion),"source retirement does not touch unrelated native movement");
        } finally {GravityFields.remove(id);cow.discard();unrelated.discard();tick(context);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=40,batchId="entity-field-interest-live")
    public void denseGravityInterestKeepsOwnedForcesHotAcrossDiscoveryBudget(TestContext context) {
        var world=context.getWorld();var centre=context.getAbsolutePos(new BlockPos(4,80,4));var id=UUID.randomUUID();
        var bees=new java.util.ArrayList<net.minecraft.entity.passive.BeeEntity>();
        long chunk=new ChunkPos(centre).toLong();boolean forced=world.getForcedChunks().contains(chunk);
        world.setChunkForced(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),true);
        GravityFields.put(new GravityFields.Field(id,world.getRegistryKey(),centre,new Vec3d(1,0,0),6,.5f,FieldShape.SPHERE,"dense interest"));
        for(int i=0;i<GravityClusterInterest.CANDIDATES_PER_QUERY+8;i++) {
            var bee=EntityType.BEE.create(world);bee.setAiDisabled(true);bee.setNoGravity(true);bee.setPosition(Vec3d.ofCenter(centre));
            context.assertTrue(world.spawnEntity(bee),"native flyer registered, not a synthetic cluster member");bees.add(bee);
        }
        tick(context);var first=GravityClusterInterest.stats(world);
        context.assertTrue(first.maxQueryCandidates()==GravityClusterInterest.CANDIDATES_PER_QUERY,"actual dense native query exercises the result cap");
        int[] ages=bees.stream().mapToInt(bee->bee.age).toArray();
        context.runAtTick(8,()->{
            try {
                var stats=GravityClusterInterest.stats(world);
                for(int i=0;i<bees.size();i++) {
                    var bee=bees.get(i);
                    context.assertTrue(bee.age>=ages[i]+2,"real ordinary world ticks advance every acquired actor");
                    context.assertTrue(EntityGravitySystem.suppressesVanillaGravity(bee)&&bee.hasNoGravity()&&bee.getVelocity().x>.02,
                        "discovery overflow cannot starve later actors or decimate an owned force: index="+i);
                }
                context.assertTrue(stats.essentialActors()>=bees.size(),"all acquired actors remain in the mandatory full-rate set");
                context.assertTrue(stats.queries()<=GravityClusterInterest.QUERIES_PER_WORLD&&stats.maxQueryCandidates()<=GravityClusterInterest.CANDIDATES_PER_QUERY,
                    "subsequent real ticks preserve discovery work bounds");
            } finally {
                GravityFields.remove(id);for(var bee:bees)bee.discard();tick(context);
                if(!forced)world.setChunkForced(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),false);
            }
            context.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=60,batchId="aquatic-native-family-medium")
    public void ordinaryWetAquaticFamiliesKeepNativeMediumMotionAcrossAllFieldAxes(TestContext c) {
        var world=c.getWorld();var centre=c.getAbsolutePos(new BlockPos(6,80,6));var id=UUID.randomUUID();
        var actors=new java.util.ArrayList<net.minecraft.entity.mob.MobEntity>();
        for(var p:BlockPos.iterate(centre.add(-6,-4,-6),centre.add(6,4,6)))world.setBlockState(p,Blocks.WATER.getDefaultState(),2);
        long chunk=new ChunkPos(centre).toLong();boolean forced=world.getForcedChunks().contains(chunk);
        world.setChunkForced(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),true);
        for(var type:new EntityType<?>[]{EntityType.DOLPHIN,EntityType.SQUID,EntityType.GLOW_SQUID,EntityType.GUARDIAN,EntityType.ELDER_GUARDIAN,EntityType.AXOLOTL}) {
            var actor=(net.minecraft.entity.mob.MobEntity)type.create(world);actor.setAiDisabled(true);actor.setNoGravity(true);
            actor.setPosition(Vec3d.ofCenter(centre));c.assertTrue(world.spawnEntity(actor),"actual aquatic actor registered: "+type);actors.add(actor);
        }
        c.runAtTick(5,()->{
            try {
                for(var actor:actors) {
                    c.assertTrue(actor.age>=2&&actor.isTouchingWater()&&actor.isSubmergedInWater(),"admission: actual ordinary world ticks establish native water contact: "+actor.getType());
                    var navigation=actor.getNavigation();var control=actor.getMoveControl();var start=actor.getPos();var propulsion=new Vec3d(.17,.03,-.04);boolean swimming=actor.isSwimming();
                    actor.setOnGround(false);actor.setYaw(37);actor.setPitch(-23);
                    for(var direction:Direction.values()) {
                        var down=Vec3d.of(direction.getVector());actor.setVelocity(propulsion);
                        GravityFields.put(new GravityFields.Field(id,world.getRegistryKey(),centre,down,32,.5f,FieldShape.SPHERE,"actual aquatic medium"));
                        var sample=GravityFields.sample(world,start);c.assertTrue(sample!=null,"actual native medium actor is inside the source");
                        tick(c);
                        c.assertTrue(actor.getPos().equals(start)&&actor.getVelocity().distanceTo(propulsion.add(down.multiply(.06*sample.strength())))<1e-8,
                            "one force preserves actual native fluid propulsion without standing snap or drag: "+actor.getType()+" "+direction);
                        c.assertTrue(!actor.isOnGround()&&!ActorFrames.active(actor)&&actor.isSwimming()==swimming&&actor.isTouchingWater()&&actor.isSubmergedInWater()
                            &&actor.getYaw()==37&&actor.getPitch()==-23,"native fluid/attitude/contact ownership remains unchanged: "+actor.getType());
                    }
                    var velocity=actor.getVelocity();GravityFields.remove(id);tick(c);
                    c.assertTrue(actor.hasNoGravity()&&actor.getVelocity().equals(velocity)&&actor.getNavigation()==navigation&&actor.getMoveControl()==control,
                        "aquatic field exit restores native controllers and saved gravity ownership: "+actor.getType());
                }
            } finally {
                GravityFields.remove(id);for(var actor:actors)actor.discard();tick(c);
                for(var p:BlockPos.iterate(centre.add(-6,-4,-6),centre.add(6,4,6)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
                if(!forced)world.setChunkForced(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),false);
            }
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=40,batchId="native-standing-medium-transition")
    public void nativeCowAndSpiderReleaseStandingFramesInWaterAndReacquireThemOnDryTicks(TestContext c) {
        var world=c.getWorld();var centre=c.getAbsolutePos(new BlockPos(6,80,6));var id=UUID.randomUUID();
        var min=centre.add(-4,-3,-4);var max=centre.add(4,3,4);
        var actors=new java.util.ArrayList<net.minecraft.entity.mob.MobEntity>();
        var navigators=new java.util.ArrayList<net.minecraft.entity.ai.pathing.EntityNavigation>();
        var controls=new java.util.ArrayList<net.minecraft.entity.ai.control.MoveControl>();
        long chunk=new ChunkPos(centre).toLong();boolean forced=world.getForcedChunks().contains(chunk);
        world.setChunkForced(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),true);
        Runnable dry=()->{for(var p:BlockPos.iterate(min,max))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);};
        Runnable clean=()->{
            GravityFields.remove(id);for(var actor:actors)actor.discard();tick(c);dry.run();
            if(!forced)world.setChunkForced(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),false);
        };
        try {
            dry.run();
            actors.add(EntityType.COW.create(world));actors.add(EntityType.SPIDER.create(world));
            for(int i=0;i<actors.size();i++) {
                var actor=actors.get(i);actor.setAiDisabled(true);actor.setPersistent();actor.setPosition(Vec3d.ofCenter(centre).add(0,0,i==0?-2:2));
                navigators.add(actor.getNavigation());controls.add(actor.getMoveControl());
                c.assertTrue(world.spawnEntity(actor),"actual standing species registered before dry contact");
            }
            field(c,id,centre,new Vec3d(-1,0,0),1);tick(c);
            for(var actor:actors)c.assertTrue(ActorFrames.active(actor)&&ActorFrames.up(actor).dotProduct(new Vec3d(1,0,0))>.99999,
                "dry standing species begins with the actual wall frame");
        } catch(Throwable failure) {clean.run();throw failure;}
        c.runAtTick(2,()->{for(var p:BlockPos.iterate(min,max))world.setBlockState(p,Blocks.WATER.getDefaultState(),2);});
        c.runAtTick(8,()->{
            try {
                var propulsion=new Vec3d(.17,.03,-.04);
                var wetPositions=actors.stream().map(Entity::getPos).toList();
                for(var actor:actors) {
                    c.assertTrue(actor.age>=3&&actor.isTouchingWater()&&!ActorFrames.active(actor),
                        "ordinary native water ticks release the rotated standing-only body");
                    actor.setVelocity(propulsion);
                }
                tick(c);
                for(int i=0;i<actors.size();i++) {
                    var actor=actors.get(i);var position=wetPositions.get(i);var sample=GravityFields.sample(world,position);
                    c.assertTrue(sample!=null&&actor.getPos().equals(position)
                        &&actor.getVelocity().distanceTo(propulsion.add(sample.upDir().normalize().multiply(-.06*sample.strength())))<1e-8,
                        "a wet standing species receives only the sampled force, without a dry snap or damping");
                    c.assertTrue(!actor.hasNoGravity()&&EntityGravitySystem.suppressesVanillaGravity(actor),"saved native flag and force lease have separate owners");
                }
                dry.run();
            } catch(Throwable failure) {clean.run();throw failure;}
        });
        c.runAtTick(15,()->{
            try {
                for(int i=0;i<actors.size();i++) {
                    var actor=actors.get(i);
                    c.assertTrue(actor.age>=8&&!actor.isTouchingWater()&&ActorFrames.active(actor)&&!actor.hasNoGravity()
                        &&actor.getNavigation()==navigators.get(i)&&actor.getMoveControl()==controls.get(i),
                        "ordinary dry ticks reacquire the safe standing frame and preserve native controllers");
                }
            } finally {clean.run();}
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-family-real-torch-nbt")
    public void sevenActualFamiliesRetainNativeIdentityAndPoseAcrossRealTorchTurnsAndNbt(TestContext c) {
        var world=c.getWorld();var centre=c.getAbsolutePos(new BlockPos(6,80,6));
        for(var p:BlockPos.iterate(centre.add(-8,-8,-8),centre.add(8,8,8)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        EntityType<?>[] types={EntityType.COW,EntityType.SPIDER,EntityType.BAT,EntityType.DOLPHIN,
            com.terminaldetector.drmd.entity.ModEntities.ORB,EntityType.WITHER,EntityType.SHULKER};
        for(var type:types)for(var facing:Direction.values()) {
            var actor=(net.minecraft.entity.mob.MobEntity)type.create(world);net.minecraft.entity.mob.MobEntity copy=null;
            actor.setAiDisabled(true);actor.setNoGravity(true);actor.setPosition(Vec3d.ofCenter(centre));
            if(actor instanceof net.minecraft.entity.passive.BatEntity bat)bat.setRoosting(false);
            c.assertTrue(world.spawnEntity(actor),"real species registered before native torch entry: "+type);
            var source=centre.offset(facing.getOpposite(),3);var original=world.getBlockState(source);var position=actor.getPos();
            try {
                world.setBlockState(source,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState().with(GravityTorchBlock.FACING,facing),3);
                var field=GravityFields.sample(world,position);c.assertTrue(field!=null&&field.upDir().dotProduct(Vec3d.of(facing.getVector()))>.99999,"real native emitter/front-side vector admitted");
                tick(c);
                boolean framed=ActorFrames.supported(actor),constrained=ActorPhysicsProfile.nativeConstraint(actor);
                c.assertTrue(actor.getPos().equals(position),"no family receives a hidden proxy or torch-wall teleport: "+type);
                c.assertTrue(ActorFrames.active(actor)==framed&&EntityGravitySystem.suppressesVanillaGravity(actor)!=constrained,"each actual family selects its declared frame/constraint policy: "+type);
                if(framed)c.assertTrue(ActorFrames.up(actor).dotProduct(Vec3d.of(facing.getVector()))>.99999
                    &&actor.getEyePos().subtract(actor.getPos()).normalize().dotProduct(Vec3d.of(facing.getVector()))>.99999,"actual local body and eye share the torch frame");
                var saved=actor.writeNbt(new NbtCompound());copy=(net.minecraft.entity.mob.MobEntity)type.create(world);copy.readNbt(saved);
                c.assertTrue(copy.getUuid().equals(actor.getUuid())&&copy.hasNoGravity()&&!EntityGravitySystem.suppressesVanillaGravity(copy),"actual NBT reload retains identity and authored flag, never another runtime lease: "+type);
                c.assertTrue(ActorFrames.up(copy).distanceTo(ActorFrames.up(actor))<1e-6&&copy.getPos().equals(position),"actual saved pose and position survive native NBT decoding: "+type);
                world.setBlockState(source,Blocks.AIR.getDefaultState(),3);tick(c);
                c.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(actor)&&!ActorFrames.active(actor)&&actor.hasNoGravity(),"real source removal releases physical orientation and acceleration without changing saved flags: "+type);
            } finally {world.setBlockState(source,original,3);if(copy!=null)copy.discard();actor.discard();tick(c);}
        }
        c.complete();
    }
}
