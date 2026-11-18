package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.boarding.LooseItemBodyAccess;
import com.terminaldetector.drmd.world.contraption.boarding.LooseItemBodySystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native dry item lifecycle stays intact; only its near-body physical move is deferred. */
@Mixin(ItemEntity.class)
public abstract class LooseItemBodyMixin extends Entity implements LooseItemBodyAccess {
    @Unique private LooseItemBodySystem.Phase drmd$itemPhase = LooseItemBodySystem.Phase.NATIVE;
    protected LooseItemBodyMixin(EntityType<?> type, World world) {super(type, world);}
    @Invoker("canMerge") public abstract boolean drmd$nativeCanMerge();
    @Invoker("tryMerge") public abstract void drmd$nativeTryMerge();

    @Inject(method = "tick", at = @At("HEAD"))
    private void drmd$beginNativeItem(CallbackInfo callback) {drmd$itemPhase = LooseItemBodySystem.Phase.NATIVE;}

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;applyGravity()V"))
    private void drmd$deferDryForce(ItemEntity item) {
        drmd$itemPhase = LooseItemBodySystem.prepare(item);
        if (drmd$itemPhase == LooseItemBodySystem.Phase.NATIVE) super.applyGravity();
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"))
    private void drmd$deferNativeMove(ItemEntity item, MovementType type, Vec3d delta) {
        if (drmd$itemPhase == LooseItemBodySystem.Phase.NATIVE) item.move(type, delta);
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;isOnGround()Z"))
    private boolean drmd$deferredMoveHasNoPrematureWorldBounce(ItemEntity item) {
        return drmd$itemPhase == LooseItemBodySystem.Phase.NATIVE && item.isOnGround();
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isSpaceEmpty(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Z"))
    private boolean drmd$keepUnprovedWorldShapesNative(World world, Entity entity, net.minecraft.util.math.Box bounds) {
        return drmd$itemPhase != LooseItemBodySystem.Phase.NATIVE || world.isSpaceEmpty(entity, bounds);
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;multiply(DDD)Lnet/minecraft/util/math/Vec3d;", ordinal = 0))
    private Vec3d drmd$recordActualNativeDrag(Vec3d velocity, double x, double y, double z) {
        if (drmd$itemPhase == LooseItemBodySystem.Phase.DEFERRED)
            LooseItemBodySystem.drag((ItemEntity)(Object)this, x, y, z);
        return drmd$itemPhase == LooseItemBodySystem.Phase.HOLD ? velocity : velocity.multiply(x, y, z);
    }
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;canMerge()Z"))
    private boolean drmd$mergeAfterRealMovement(ItemEntity item) {
        return drmd$itemPhase != LooseItemBodySystem.Phase.DEFERRED && drmd$nativeCanMerge();
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void drmd$nativeItemComplete(CallbackInfo callback) {
        if (drmd$itemPhase == LooseItemBodySystem.Phase.DEFERRED)
            LooseItemBodySystem.nativeFinished((ItemEntity)(Object)this);
    }
    @Override public void drmd$finishBodyMove() {
        drmd$itemPhase = LooseItemBodySystem.Phase.NATIVE;
        velocityDirty |= updateWaterState();
    }
}
