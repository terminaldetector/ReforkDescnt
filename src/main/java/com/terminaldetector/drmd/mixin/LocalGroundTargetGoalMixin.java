package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Existing native C1 goals keep predicates, chance, radius and lifecycle; their frame is physical. */
@Mixin(ActiveTargetGoal.class)
public abstract class LocalGroundTargetGoalMixin extends TrackTargetGoal {
    protected LocalGroundTargetGoalMixin(MobEntity actor,boolean visibility){super(actor,visibility);}
    @Unique private boolean physicalC1(){
        return ActorPhysicsProfile.family(mob)==ActorPhysicsProfile.Family.GROUND&&LocalSurfaceNavigation.framed(mob);
    }
    @Inject(method="getSearchBox",at=@At("HEAD"),cancellable=true)
    private void physicalMobCandidates(double range,CallbackInfoReturnable<Box> ci){
        if(physicalC1()&&Double.isFinite(range)&&range>=0)ci.setReturnValue(LocalTargetVolume.expand(mob.getBoundingBox(),ActorFrames.up(mob),range));
    }
    @Redirect(method="findClosestTarget",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;getClosestPlayer(Lnet/minecraft/entity/ai/TargetPredicate;Lnet/minecraft/entity/LivingEntity;DDD)Lnet/minecraft/entity/player/PlayerEntity;"),require=1,allow=1)
    private PlayerEntity physicalClosestPlayer(World world,TargetPredicate predicate,LivingEntity actor,double x,double y,double z){
        if(physicalC1()&&actor==mob){var eye=mob.getEyePos();x=eye.x;y=eye.y;z=eye.z;}
        return world.getClosestPlayer(predicate,actor,x,y,z);
    }
    @Redirect(method="findClosestTarget",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;getClosestEntity(Ljava/util/List;Lnet/minecraft/entity/ai/TargetPredicate;Lnet/minecraft/entity/LivingEntity;DDD)Lnet/minecraft/entity/LivingEntity;"),require=1,allow=1)
    private LivingEntity physicalClosestMob(World world,List<? extends LivingEntity> candidates,TargetPredicate predicate,LivingEntity actor,double x,double y,double z){
        if(physicalC1()&&actor==mob){var eye=mob.getEyePos();x=eye.x;y=eye.y;z=eye.z;}
        return world.getClosestEntity(candidates,predicate,actor,x,y,z);
    }
}
