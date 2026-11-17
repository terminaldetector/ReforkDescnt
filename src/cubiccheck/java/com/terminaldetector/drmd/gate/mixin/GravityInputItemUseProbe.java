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
@Mixin(DescentWeaponItem.class)
public class GravityInputItemUseProbe {
    // This override has Item's mapped method name in a remapped native JAR.
    // Keep both injections required; a missing observer must fail the gate.
    @Inject(method="use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;",at=@At("HEAD"),require=1)
    private void before(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<TypedActionResult<ItemStack>> cir){GravityInputRouteTrace.beforeUse(world,player,hand);}
    @Inject(method="use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;",at=@At("RETURN"),require=1)
    private void after(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<TypedActionResult<ItemStack>> cir){GravityInputRouteTrace.afterUse(world,player,hand,cir.getReturnValue().getResult().isAccepted());}
}
