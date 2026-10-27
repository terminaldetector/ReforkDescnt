package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Built-in metadata provides spawn/late-join synchronization without a UUID client cache. */
@Mixin(LivingEntity.class)
public abstract class ActorFrameTrackerMixin implements ActorFrameAccess {
    @Unique private static final TrackedData<Vector3f> DRMD_ACTOR_UP =
        DataTracker.registerData(LivingEntity.class,TrackedDataHandlerRegistry.VECTOR3F);
    @Unique private static final String DRMD_FRAME_NBT="drmd:actor_frame";

    @Inject(method="initDataTracker",at=@At("TAIL"))
    private void drmd$initFrame(DataTracker.Builder builder,CallbackInfo ci) {
        builder.add(DRMD_ACTOR_UP,new Vector3f());
    }

    @Override public Vec3d drmd$actorUp() {
        Vector3f value=((LivingEntity)(Object)this).getDataTracker().get(DRMD_ACTOR_UP);
        return ActorFrameMath.decode(value.x,value.y,value.z);
    }

    @Override public void drmd$setActorUp(Vec3d up) {
        LivingEntity entity=(LivingEntity)(Object)this;
        if(entity.getWorld().isClient)throw new IllegalStateException("Actor frames are server-owned");
        entity.getDataTracker().set(DRMD_ACTOR_UP,new Vector3f((float)up.x,(float)up.y,(float)up.z));
    }

    @Inject(method="onTrackedDataSet",at=@At("TAIL"))
    private void drmd$refreshFrame(TrackedData<?> data,CallbackInfo ci) {
        if(data.equals(DRMD_ACTOR_UP)) {
            LivingEntity entity=(LivingEntity)(Object)this;
            entity.setPosition(entity.getPos());
            ActorFrames.frameChanged(entity);
        }
    }

    @Inject(method="writeCustomDataToNbt",at=@At("TAIL"))
    private void drmd$saveFrame(NbtCompound nbt,CallbackInfo ci) {
        Vec3d up=ActorFrames.up((LivingEntity)(Object)this);
        if(up==Vec3d.ZERO) {nbt.remove(DRMD_FRAME_NBT);return;}
        var frame=new NbtCompound();frame.putInt("version",1);
        frame.putFloat("x",(float)up.x);frame.putFloat("y",(float)up.y);frame.putFloat("z",(float)up.z);
        nbt.put(DRMD_FRAME_NBT,frame);
    }

    @Inject(method="readCustomDataFromNbt",at=@At("TAIL"))
    private void drmd$loadFrame(NbtCompound nbt,CallbackInfo ci) {
        LivingEntity entity=(LivingEntity)(Object)this;
        if(entity.getWorld().isClient || !ActorFrames.supported(entity))return;
        var frame=nbt.getCompound(DRMD_FRAME_NBT);
        Vec3d up=frame.getInt("version")==1
            ? ActorFrameMath.decode(frame.getFloat("x"),frame.getFloat("y"),frame.getFloat("z")) : Vec3d.ZERO;
        // Restoring a saved physical pose does not restore the old runtime acceleration lease.
        drmd$setActorUp(up);
    }
}
