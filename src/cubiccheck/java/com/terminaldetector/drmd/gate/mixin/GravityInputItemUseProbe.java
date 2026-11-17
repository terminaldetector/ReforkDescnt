package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.GravityInputRouteTrace;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=DescentWeaponItem.class,remap=false)
public class GravityInputItemUseProbe {
    @Inject(method="use",at=@At("HEAD"),remap=false)
    private void before(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<TypedActionResult<ItemStack>> cir){GravityInputRouteTrace.beforeUse(world,player,hand);}
    @Inject(method="use",at=@At("RETURN"),remap=false)
    private void after(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<TypedActionResult<ItemStack>> cir){GravityInputRouteTrace.afterUse(world,player,hand,cir.getReturnValue().getResult().isAccepted());}
}
