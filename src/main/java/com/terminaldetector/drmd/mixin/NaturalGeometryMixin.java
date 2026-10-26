package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.geometry.NaturalGeometry;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The same warped volume supplies picking, camera and server/client collision. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public class NaturalGeometryMixin {
    @Inject(method="getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;",at=@At("HEAD"),cancellable=true)
    private void drmd$uncachedNaturalShape(BlockView world,BlockPos pos,CallbackInfoReturnable<VoxelShape> cir) {
        BlockState state=(BlockState)(Object)this;
        if(world!=net.minecraft.world.EmptyBlockView.INSTANCE && NaturalGeometry.applies(state))
            cir.setReturnValue(NaturalGeometry.shape(state,world,pos));
    }
    @Inject(method={"getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
        "getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
        "getCameraCollisionShape"},at=@At("HEAD"),cancellable=true)
    private void drmd$naturalShape(BlockView world,BlockPos pos,ShapeContext context,CallbackInfoReturnable<VoxelShape> cir) {
        BlockState state=(BlockState)(Object)this;
        // Bootstrap caches must describe the vanilla block, not an isolated block in an empty world.
        if(world!=net.minecraft.world.EmptyBlockView.INSTANCE && NaturalGeometry.applies(state))
            cir.setReturnValue(NaturalGeometry.shape(state,world,pos));
    }
}
