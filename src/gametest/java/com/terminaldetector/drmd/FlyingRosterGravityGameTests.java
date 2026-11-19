package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.world.gravity.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;
import java.util.UUID;

public final class FlyingRosterGravityGameTests implements FabricGameTest {
    private static final EntityType<?>[] VANILLA_FLYERS={EntityType.BEE,EntityType.PHANTOM,
        EntityType.GHAST,EntityType.ALLAY,EntityType.VEX};
    private static final Direction[] FIELD_AXES={Direction.EAST,Direction.UP,Direction.DOWN,
        Direction.NORTH,Direction.SOUTH,Direction.WEST};

    /** The ordinary flight AI owns attitude/position; entering a field only adds its force. */
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="vanilla-flyer-roster")
    public void vanillaFlyersNeverBorrowGroundMountingOnAnyGravityAxis(TestContext context) {
        var world=context.getWorld();var centre=context.getAbsolutePos(new BlockPos(6,80,6));
        for(var p:BlockPos.iterate(centre.add(-8,-8,-8),centre.add(8,8,8)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(var p:BlockPos.iterate(centre.add(3,-4,-4),centre.add(3,6,4)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        UUID field=UUID.randomUUID();
        try {
            for(var type:VANILLA_FLYERS) {
                var actor=(MobEntity)type.create(world);
                try {
                    context.assertTrue(!actor.isAiDisabled(),"vanilla flight AI starts enabled: "+type);
                    var navigation=actor.getNavigation();var control=actor.getMoveControl();boolean originalNoGravity=actor.hasNoGravity();
                    actor.setAiDisabled(true);actor.setPosition(Vec3d.ofCenter(centre));world.spawnEntity(actor);
                    actor.setYaw(37);actor.setPitch(-60);actor.setOnGround(false);
                    var start=actor.getPos();var propulsion=new Vec3d(.17,.03,-.04);
                    for(var direction:FIELD_AXES) {
                        var down=Vec3d.of(direction.getVector());actor.setVelocity(propulsion);
                        GravityFields.put(new GravityFields.Field(field,world.getRegistryKey(),centre,down,32,1,FieldShape.SPHERE,"vanilla flight adapter"));
                        EntityGravitySystem.tick(world.getServer());
                        context.assertTrue(actor.getPos().distanceTo(start)<1e-9,"a vanilla flyer must not be teleported onto the torch wall: "+type+" "+direction);
                        context.assertTrue(actor.getVelocity().distanceTo(propulsion.add(down.multiply(.06)))<1e-8,"one field force retains vanilla propulsion without ground damping: "+type+" "+direction);
                        context.assertTrue(actor.getYaw()==37&&actor.getPitch()==-60&&!actor.isOnGround(),"a field cannot manufacture a ground/attitude state: "+type);
                        context.assertTrue(!ActorFrames.active(actor)&&actor.hasNoGravity()==originalNoGravity,"flight does not acquire a foot frame or another owner's flag: "+type);
                    }
                    var velocity=actor.getVelocity();GravityFields.remove(field);EntityGravitySystem.tick(world.getServer());
                    actor.setAiDisabled(false);
                    context.assertTrue(actor.getVelocity().equals(velocity)&&!actor.isAiDisabled()
                        &&actor.getNavigation()==navigation&&actor.getMoveControl()==control,"field exit leaves native flight controllers and momentum intact: "+type);
                } finally {actor.discard();GravityFields.remove(field);EntityGravitySystem.tick(world.getServer());}
            }
        } finally {
            GravityFields.remove(field);
            for(var p:BlockPos.iterate(centre.add(3,-4,-4),centre.add(3,6,4)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        }
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="vanilla-flyer-persistence")
    public void vanillaFlightFieldLeaseNeverBecomesASavedNoGravityFlagOrTransfersToAnNbtAlias(TestContext context) {
        var world=context.getWorld();var centre=context.getAbsolutePos(new BlockPos(6,80,6));
        for(var p:BlockPos.iterate(centre.add(-6,-6,-6),centre.add(6,6,6)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        UUID field=UUID.randomUUID();
        try {
            for(var type:VANILLA_FLYERS)for(boolean originalNoGravity:new boolean[]{false,true}) {
                var actor=(MobEntity)type.create(world);MobEntity copy=null;
                try {
                    actor.setAiDisabled(true);actor.setNoGravity(originalNoGravity);actor.setPosition(Vec3d.ofCenter(centre));world.spawnEntity(actor);
                    var before=actor.getFinalGravity();var propulsion=new Vec3d(.12,.05,-.03);actor.setVelocity(propulsion);
                    var down=new Vec3d(1,0,0);GravityFields.put(new GravityFields.Field(field,world.getRegistryKey(),centre,down,32,.5f,FieldShape.SPHERE,"vanilla saved ownership"));
                    EntityGravitySystem.tick(world.getServer());
                    context.assertTrue(actor.getVelocity().distanceTo(propulsion.add(.03,0,0))<1e-8,"half-strength field adds one flight impulse without ground drag: "+type);
                    var nbt=actor.writeNbt(new NbtCompound());copy=(MobEntity)type.create(world);copy.readNbt(nbt);
                    context.assertTrue(nbt.getBoolean("NoGravity")==originalNoGravity&&copy.hasNoGravity()==originalNoGravity,"NBT retains the actual external gravity-flag owner: "+type);
                    context.assertTrue(copy.getUuid().equals(actor.getUuid())&&!EntityGravitySystem.suppressesVanillaGravity(copy),"saved-UUID alias cannot inherit a live object's field lease: "+type);
                    var velocity=actor.getVelocity();GravityFields.remove(field);EntityGravitySystem.tick(world.getServer());
                    context.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(actor)&&actor.hasNoGravity()==originalNoGravity
                        &&Math.abs(actor.getFinalGravity()-before)<1e-9&&actor.getVelocity().equals(velocity),"field release restores native gravity without resetting propulsion: "+type);
                } finally {if(copy!=null)copy.discard();actor.discard();GravityFields.remove(field);EntityGravitySystem.tick(world.getServer());}
            }
        } finally {GravityFields.remove(field);}
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="flying-roster")
    public void entireFlyingLivingRosterKeepsPropulsionAndAttitudeOnAllSixGravityAxes(TestContext context) {
        var world=context.getWorld();BlockPos centre=context.getAbsolutePos(new BlockPos(6,80,6));
        for(BlockPos p:BlockPos.iterate(centre.add(-8,-8,-8),centre.add(8,8,8)))
            world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        // Old foot-mount code found this wall, teleported a flyer and zeroed its propulsion.
        for(BlockPos p:BlockPos.iterate(centre.add(3,-4,-4),centre.add(3,6,4)))
            world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        EntityType<?>[] roster={ModEntities.DRONE,ModEntities.SCANNER,ModEntities.OBLIVION_SEEKER,
            ModEntities.REACTOR_KEEPER,ModEntities.END_REACTOR_BOSS,ModEntities.AIR_MINE};
        UUID field=UUID.randomUUID();
        try {
            for(EntityType<?> type:roster) {
                LivingEntity actor=(LivingEntity)type.create(world);
                try {
                    context.assertTrue(actor instanceof MobEntity mob && !mob.isAiDisabled(),"AI enabled out of the box: "+type);
                    context.assertTrue(actor.hasNoGravity() && ActorPhysicsProfile.flying(actor),"explicit flight adapter: "+type);
                    ((MobEntity)actor).setAiDisabled(true);actor.setPosition(Vec3d.ofCenter(centre));world.spawnEntity(actor);
                    Vec3d feet=actor.getPos(),propulsion=new Vec3d(.17,.03,-.04);
                    actor.setYaw(37);actor.setPitch(-60);actor.setOnGround(false);
                    for(Direction direction:Direction.values()) {
                        Vec3d down=Vec3d.of(direction.getVector());actor.setVelocity(propulsion);
                        GravityFields.put(new GravityFields.Field(field,world.getRegistryKey(),centre,down,32,1,FieldShape.SPHERE,"flight adapter"));
                        EntityGravitySystem.tick(world.getServer());
                        context.assertTrue(actor.getPos().distanceTo(feet)<1e-9,"no foot teleport: "+type+" "+direction);
                        context.assertTrue(actor.getVelocity().distanceTo(propulsion.add(down.multiply(.06)))<1e-8,"one force preserves propulsion: "+type+" "+direction);
                        context.assertTrue(actor.getYaw()==37 && actor.getPitch()==-60 && !actor.isOnGround(),"field cannot ground/steer flyer: "+type);
                        context.assertTrue(!ActorFrames.active(actor) && actor.hasNoGravity(),"flight never borrows a foot frame or mutates owner flag");
                    }
                    GravityFields.remove(field);EntityGravitySystem.tick(world.getServer());
                    context.assertTrue(actor.hasNoGravity() && actor.writeNbt(new NbtCompound()).getBoolean("NoGravity"),"authored flight flag survives field exit: "+type);
                } finally {actor.discard();GravityFields.remove(field);EntityGravitySystem.tick(world.getServer());}
            }
        } finally {
            GravityFields.remove(field);
            for(BlockPos p:BlockPos.iterate(centre.add(3,-4,-4),centre.add(3,6,4)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        }
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-family-fallback")
    public void nativeAerialSpecialAndUnknownFamiliesKeepTheirControllersWithoutGroundMounts(TestContext c) {
        var world=c.getWorld();var centre=c.getAbsolutePos(new BlockPos(6,80,6));
        for(var p:BlockPos.iterate(centre.add(-8,-8,-8),centre.add(8,8,8)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(var p:BlockPos.iterate(centre.add(3,-4,-4),centre.add(3,6,4)))world.setBlockState(p,Blocks.BRICKS.getDefaultState(),2);
        var id=UUID.randomUUID();
        EntityType<?>[] types={EntityType.BAT,EntityType.PARROT,EntityType.WITHER,EntityType.VILLAGER,EntityType.RABBIT,EntityType.SLIME};
        try {
            for(var type:types)for(boolean originalNoGravity:new boolean[]{false,true}) {
                var actor=(MobEntity)type.create(world);MobEntity copy=null;
                c.assertTrue(!actor.isAiDisabled(),"native AI enabled before the disclosed force-only fixture: "+type);
                actor.setAiDisabled(true);actor.setNoGravity(originalNoGravity);actor.setPosition(Vec3d.ofCenter(centre));
                if(actor instanceof net.minecraft.entity.passive.BatEntity bat)bat.setRoosting(false);
                if(actor instanceof net.minecraft.entity.passive.ParrotEntity parrot)parrot.setInSittingPose(false);
                actor.setOnGround(false);actor.setYaw(37);actor.setPitch(-23);world.spawnEntity(actor);
                var navigation=actor.getNavigation();var controller=actor.getMoveControl();var start=actor.getPos();var propulsion=new Vec3d(.17,.03,-.04);
                try {
                    for(var direction:FIELD_AXES) {
                        var down=Vec3d.of(direction.getVector());actor.setVelocity(propulsion);
                        GravityFields.put(new GravityFields.Field(id,world.getRegistryKey(),centre,down,32,.5f,FieldShape.SPHERE,"native family fallback"));
                        EntityGravitySystem.tick(world.getServer());
                        c.assertTrue(actor.getPos().distanceTo(start)<1e-9&&actor.getVelocity().distanceTo(propulsion.add(down.multiply(.03)))<1e-8,
                            "actual native actor receives only one force and never a foot teleport/snap/drag: "+type+" "+direction);
                        c.assertTrue(actor.getYaw()==37&&actor.getPitch()==-23&&!actor.isOnGround()&&!ActorFrames.active(actor)
                            &&actor.hasNoGravity()==originalNoGravity,"native pose/attitude/external flag preserved: "+type);
                    }
                    var saved=actor.writeNbt(new NbtCompound());copy=(MobEntity)type.create(world);copy.readNbt(saved);
                    c.assertTrue(copy.getUuid().equals(actor.getUuid())&&!EntityGravitySystem.suppressesVanillaGravity(copy)
                        &&copy.hasNoGravity()==originalNoGravity,"saved identity cannot acquire another live actor's lease: "+type);
                    var velocity=actor.getVelocity();GravityFields.remove(id);EntityGravitySystem.tick(world.getServer());actor.setAiDisabled(false);
                    c.assertTrue(!actor.isAiDisabled()&&actor.getNavigation()==navigation&&actor.getMoveControl()==controller
                        &&actor.getVelocity().equals(velocity)&&actor.hasNoGravity()==originalNoGravity,
                        "exit retains original goals/controllers/momentum instead of replacing native AI: "+type);
                } finally {GravityFields.remove(id);if(copy!=null)copy.discard();actor.discard();EntityGravitySystem.tick(world.getServer());}
            }
        } finally {GravityFields.remove(id);for(var p:BlockPos.iterate(centre.add(3,-4,-4),centre.add(3,6,4)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);}
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="native-family-constraints")
    public void roostAttachmentAndDragonMultipartConstraintsNeverBorrowAGroundLease(TestContext c) {
        var world=c.getWorld();var centre=c.getAbsolutePos(new BlockPos(6,80,6));var id=UUID.randomUUID();
        for(var p:BlockPos.iterate(centre.add(-8,-8,-8),centre.add(8,8,8)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(var type:new EntityType<?>[]{EntityType.BAT,EntityType.SHULKER,EntityType.ENDER_DRAGON}) {
            var actor=(MobEntity)type.create(world);actor.setAiDisabled(true);actor.setPosition(Vec3d.ofCenter(centre));
            if(actor instanceof net.minecraft.entity.passive.BatEntity bat)bat.setRoosting(true);
            var attached=actor instanceof net.minecraft.entity.mob.ShulkerEntity shulker?shulker.getAttachedFace():null;
            var parts=actor instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity dragon?dragon.getBodyParts().clone():null;
            world.spawnEntity(actor);var start=actor.getPos();var propulsion=new Vec3d(.11,.03,-.04);actor.setVelocity(propulsion);
            try {
                for(var direction:FIELD_AXES) {
                    GravityFields.put(new GravityFields.Field(id,world.getRegistryKey(),centre,Vec3d.of(direction.getVector()),32,1,FieldShape.SPHERE,"native constraint"));
                    EntityGravitySystem.tick(world.getServer());
                    c.assertTrue(actor.getPos().equals(start)&&actor.getVelocity().equals(propulsion)&&!ActorFrames.active(actor)
                        &&!EntityGravitySystem.suppressesVanillaGravity(actor),"native constraint refuses unsupported local frame/force ownership: "+type+" "+direction);
                }
                if(actor instanceof net.minecraft.entity.mob.ShulkerEntity shulker)c.assertTrue(shulker.getAttachedFace()==attached,"actual native support face is untouched");
                if(actor instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity dragon)
                    c.assertTrue(java.util.Arrays.equals(parts,dragon.getBodyParts()),"actual multipart identities/controllers are never replaced by a generic body");
                if(actor instanceof net.minecraft.entity.passive.BatEntity bat) {
                    bat.setRoosting(false);EntityGravitySystem.tick(world.getServer());
                    c.assertTrue(EntityGravitySystem.suppressesVanillaGravity(bat)&&bat.getVelocity().distanceTo(propulsion)>.02,"leaving the actual native roost enables the ordinary aerial force adapter");
                    var velocity=bat.getVelocity();bat.setRoosting(true);EntityGravitySystem.tick(world.getServer());
                    c.assertTrue(!EntityGravitySystem.suppressesVanillaGravity(bat)&&bat.isRoosting()&&bat.getVelocity().equals(velocity),"re-entry into a native constraint retires the earlier live force lease");
                }
            } finally {GravityFields.remove(id);actor.discard();EntityGravitySystem.tick(world.getServer());}
        }
        c.complete();
    }
}
