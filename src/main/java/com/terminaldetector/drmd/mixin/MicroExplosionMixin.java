package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.micro.BlockDamage;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses vanilla's affected-block selection, retaining shielding and mob-griefing decisions. */
@Mixin(Explosion.class)
public abstract class MicroExplosionMixin {
    @Shadow @Final private World world;
    @Inject(method="affectWorld", at=@At("HEAD"))
    private void drmd$fractureRim(boolean particles, CallbackInfo ci) {
        if (!(world instanceof ServerWorld server)) return;
        Explosion explosion=(Explosion)(Object)this;
        var type=explosion.getDestructionType();
        if(type!=Explosion.DestructionType.DESTROY && type!=Explosion.DestructionType.DESTROY_WITH_DECAY) return;
        explosion.getAffectedBlocks().removeIf(pos -> BlockDamage.fractureExplosion(
            server,pos,explosion.getPosition(),explosion.getPower()));
    }
}
