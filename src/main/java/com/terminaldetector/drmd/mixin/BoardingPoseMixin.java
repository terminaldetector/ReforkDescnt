package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spawn/late-join metadata publishes the actual passenger shape to other clients. No persisted lease. */
@Mixin(PlayerEntity.class)
public abstract class BoardingPoseMixin implements BoardingPose.Access {
    @Unique private static final TrackedData<NbtCompound> DRMD_BOARDING_POSE=DataTracker.registerData(PlayerEntity.class,TrackedDataHandlerRegistry.NBT_COMPOUND);
    @Inject(method="initDataTracker",at=@At("TAIL"))
    private void pose(DataTracker.Builder builder,CallbackInfo ci){builder.add(DRMD_BOARDING_POSE,new NbtCompound());}
    @Override public boolean drmd$boardingPoseActive(){var tracker=((PlayerEntity)(Object)this).getDataTracker();return tracker!=null&&tracker.get(DRMD_BOARDING_POSE).getBoolean("active");}
    @Override public Quat drmd$boardingFrame(){
        var tag=((PlayerEntity)(Object)this).getDataTracker().get(DRMD_BOARDING_POSE);
        return tag.getBoolean("active")?new Quat(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z"),tag.getDouble("w")).normalized():Quat.IDENTITY;
    }
    @Override public void drmd$setBoardingFrame(Quat q,boolean active){
        var player=(PlayerEntity)(Object)this;if(player.getWorld().isClient)throw new IllegalStateException("Passenger metadata is server-owned");
        var tag=new NbtCompound();tag.putBoolean("active",active);
        tag.putDouble("x",q.x());tag.putDouble("y",q.y());tag.putDouble("z",q.z());tag.putDouble("w",q.w());
        player.getDataTracker().set(DRMD_BOARDING_POSE,tag);
    }
    @Override public boolean drmd$boardingFrameData(TrackedData<?> data){return DRMD_BOARDING_POSE.equals(data);}
}
