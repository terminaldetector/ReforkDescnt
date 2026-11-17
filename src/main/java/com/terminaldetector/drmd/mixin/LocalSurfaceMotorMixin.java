package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Native MOVE_TO still owns state/yaw/speed; only its desired displacement uses the walking chart. */
@Mixin(MoveControl.class)
public abstract class LocalSurfaceMotorMixin {
    @Shadow @Final protected MobEntity entity;
    @Shadow protected double targetX;
    @Shadow protected double targetY;
    @Shadow protected double targetZ;
    @Unique private Vec3d localWanted(){return LocalSurfaceNavigation.localWanted(entity,new Vec3d(targetX,targetY,targetZ));}
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/minecraft/entity/ai/control/MoveControl;targetX:D",opcode=Opcodes.GETFIELD))
    private double localX(MoveControl control){return LocalSurfaceNavigation.localMotor(entity)?entity.getX()+localWanted().x:targetX;}
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/minecraft/entity/ai/control/MoveControl;targetY:D",opcode=Opcodes.GETFIELD))
    private double localY(MoveControl control){return LocalSurfaceNavigation.localMotor(entity)?entity.getY()+localWanted().y:targetY;}
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/minecraft/entity/ai/control/MoveControl;targetZ:D",opcode=Opcodes.GETFIELD))
    private double localZ(MoveControl control){return LocalSurfaceNavigation.localMotor(entity)?entity.getZ()+localWanted().z:targetZ;}
}
