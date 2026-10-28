package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Predicate;

/** Preserve vanilla picking for ordinary entities, replace only the hull's broadphase hit. */
@Mixin(ProjectileUtil.class)
public class BodyPickingMixin {
    private static final ThreadLocal<Boolean> drmd$picking=ThreadLocal.withInitial(()->false);
    @Inject(method="raycast",at=@At("RETURN"),cancellable=true)
    private static void drmd$localGrid(Entity owner,Vec3d from,Vec3d to,Box area,Predicate<Entity> allowed,double limit,CallbackInfoReturnable<EntityHitResult> ci) {
        if(drmd$picking.get())return;
        var bodies=owner.getWorld().getEntitiesByClass(BlockBodyEntity.class,area,b->allowed.test(b)&&!b.isRemoved());
        if(bodies.isEmpty())return;
        EntityHitResult best;
        drmd$picking.set(true);
        try {best=ProjectileUtil.raycast(owner,from,to,area,e->!(e instanceof BlockBodyEntity)&&allowed.test(e),limit);}
        finally{drmd$picking.remove();}
        double nearest=best==null?limit:from.squaredDistanceTo(best.getPos());
        for(var body:bodies) {
            if(body.getRootVehicle()==owner.getRootVehicle())continue;
            var hit=body.raycast(from,to);
            if(hit.isPresent()) {double distance=from.squaredDistanceTo(hit.get());if(distance<nearest){nearest=distance;best=new EntityHitResult(body,hit.get());}}
        }
        ci.setReturnValue(best);
    }
}
