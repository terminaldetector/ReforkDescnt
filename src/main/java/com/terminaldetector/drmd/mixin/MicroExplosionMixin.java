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
        if (!(world instanceof ServerWorld server) || !com.terminaldetector.drmd.physics.PhysicsProfileState.expanded(server)) return;
        Explosion explosion=(Explosion)(Object)this;
        var type=explosion.getDestructionType();
        if(type!=Explosion.DestructionType.DESTROY && type!=Explosion.DestructionType.DESTROY_WITH_DECAY) return;
        // Moving local grids are not world blocks in vanilla's affected-block list. Snapshot
        // the hull index once, so fragments created by this blast cannot be hit a second time.
        double radius=explosion.getPower()*2.;
        if(Double.isFinite(radius)&&radius>0) {
            var centre=explosion.getPosition();
            for(var body:com.terminaldetector.drmd.world.contraption.BodySpatialIndex.query(server,
                new net.minecraft.util.math.Box(centre,centre).expand(radius)))
                body.weaponBlast(centre,explosion.getPower()*24, radius);
        }
        com.terminaldetector.drmd.world.contraption.StructureCollapse.explosion(
            server,explosion.getAffectedBlocks(),explosion.getPosition(),explosion.getPower());
        explosion.getAffectedBlocks().removeIf(pos -> BlockDamage.fractureExplosion(
            server,pos,explosion.getPosition(),explosion.getPower()));
    }
}
