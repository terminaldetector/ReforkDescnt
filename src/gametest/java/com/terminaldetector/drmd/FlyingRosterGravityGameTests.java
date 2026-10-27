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
}
