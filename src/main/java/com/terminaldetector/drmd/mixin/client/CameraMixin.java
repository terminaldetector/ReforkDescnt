package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.BoardingCamera;
import com.terminaldetector.drmd.client.flight.DescentCamera;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingPose;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply the active movement frame, then clip the outer F5 camera against physical hulls. */
@Mixin(Camera.class)
public class CameraMixin {
    @Inject(method="update",at=@At("RETURN"))
    private void drmd$descentCalcView(BlockView area,Entity focusedEntity,boolean thirdPerson,
                                    boolean inverseView,float tickDelta,CallbackInfo ci) {
        Camera self=(Camera)(Object)this;CameraAccessor access=(CameraAccessor)(Object)this;
        boolean outer=self==MinecraftClient.getInstance().gameRenderer.getCamera();
        if(!DescentClientState.enabled&&focusedEntity instanceof PlayerEntity player&&BoardingPose.active(player)&&outer) {
            BoardingCamera.apply(self,access,player,thirdPerson,inverseView,tickDelta);return;
        }
        if(!DescentClientState.enabled&&focusedEntity instanceof PlayerEntity player&&FootGravitySystem.isActive(player)) {
            Vec3d up=FootGravitySystem.getUp(player);double height=player.getEyeHeight(player.getPose());
            access.drmd$invokeSetPos(self.getPos().add(up.multiply(height)).subtract(0,height,0));
        }else if(DescentClientState.enabled&&DescentClientState.attitudeValid) {
            DescentCamera.apply(self,new DescentCamera.CameraAccess() {
                @Override public void drmd$setPos(Vec3d pos){access.drmd$invokeSetPos(pos);}
                @Override public void drmd$setRotation(float yaw,float pitch){access.drmd$invokeSetRotation(yaw,pitch);}
            },thirdPerson,inverseView,tickDelta);
        }
        if(outer&&thirdPerson&&focusedEntity instanceof PlayerEntity player)BoardingCamera.clipHulls(self,access,player,tickDelta);
    }
}
