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
    @Shadow @Final private net.minecraft.world.explosion.ExplosionBehavior behavior;
    @Unique private java.util.List<net.minecraft.util.math.BlockPos> drmd$structuralCuts=java.util.List.of();
    @Unique private boolean drmd$unknownVolume;
    @Inject(method="collectBlocksAndDamageEntities",at=@At("HEAD"),cancellable=true)
    private void drmd$loadedExplosionQueries(CallbackInfo ci) {
        drmd$unknownVolume=world instanceof ServerWorld server
            &&!com.terminaldetector.drmd.world.access.ExplosionWorldAccess.canCollect(server,(Explosion)(Object)this);
        if(drmd$unknownVolume)ci.cancel();
    }
    @Inject(method="affectWorld", at=@At("HEAD"),cancellable=true)
    private void drmd$fractureRim(boolean particles, CallbackInfo ci) {
        drmd$structuralCuts=java.util.List.of();
        if(drmd$unknownVolume){ci.cancel();return;}
        if(world instanceof ServerWorld server) {
            // Also protect explicit/preselected affected lists which bypass collect().
            if(!com.terminaldetector.drmd.world.access.CubicAvailability.onWorldThread(server)){ci.cancel();return;}
            ((Explosion)(Object)this).getAffectedBlocks().removeIf(pos->
                com.terminaldetector.drmd.world.access.WorldAccess.of(server).readIfLoaded(pos).isEmpty()
                ||!com.terminaldetector.drmd.world.edit.WorldEditSafety.neighbourhoodLoaded(server,pos));
        }
        if (!(world instanceof ServerWorld server) || !com.terminaldetector.drmd.world.contraption.BlastStructuralCollapse.enabled(server)) return;
        Explosion explosion=(Explosion)(Object)this;
        var type=explosion.getDestructionType();
        if(type!=Explosion.DestructionType.DESTROY && type!=Explosion.DestructionType.DESTROY_WITH_DECAY) return;
        drmd$structuralCuts=com.terminaldetector.drmd.world.contraption.BlastStructuralCollapse.capture(server,explosion.getAffectedBlocks());
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
            server,explosion.getAffectedBlocks(),explosion.getPosition(),explosion.getPower(),p->
                com.terminaldetector.drmd.world.edit.WorldEditSafety.mayChange(server,p,explosion.getCausingEntity())
                &&com.terminaldetector.drmd.world.access.WorldAccess.of(server).readIfLoaded(p)
                    .filter(s->behavior.canDestroyBlock(explosion,server,p,s,explosion.getPower())).isPresent());
        explosion.getAffectedBlocks().removeIf(pos -> BlockDamage.fractureExplosion(
            server,pos,explosion.getPosition(),explosion.getPower()));
    }
    @Inject(method="affectWorld",at=@At("TAIL"))
    private void drmd$unsupportedFragments(boolean particles,CallbackInfo ci){
        var cuts=drmd$structuralCuts;drmd$structuralCuts=java.util.List.of();
        if(world instanceof ServerWorld server&&!cuts.isEmpty()){
            var blast=(Explosion)(Object)this;
            com.terminaldetector.drmd.world.contraption.BlastStructuralCollapse.finish(server,cuts,blast.getPosition(),blast.getPower(),p->
                com.terminaldetector.drmd.world.edit.WorldEditSafety.mayChange(server,p,blast.getCausingEntity())
                &&com.terminaldetector.drmd.world.access.WorldAccess.of(server).readIfLoaded(p)
                    .filter(s->behavior.canDestroyBlock(blast,server,p,s,blast.getPower())).isPresent());
        }
    }
}
