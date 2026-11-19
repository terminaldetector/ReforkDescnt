package com.terminaldetector.drmd.mixin;
import com.terminaldetector.drmd.world.micro.CarvedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Vanilla still chooses fluid levels/decay/flow; its two internal state writes retain the carved solid. */
@Mixin(FlowableFluid.class)
public class CarvedFluidTickMixin {
    @Redirect(method="onScheduledTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean keepCarrier(World world,BlockPos pos,BlockState next,int flags){
        var old=world.getBlockState(pos);
        if(CarvedBlock.water((FlowableFluid)(Object)this)&&old.getBlock()instanceof CarvedBlock&&old.get(Properties.WATERLOGGED)
            &&(next.isAir()||CarvedBlock.water(next.getFluidState().getFluid())))next=CarvedBlock.withFluid(old,next.getFluidState());
        return world.setBlockState(pos,next,flags);
    }
}
