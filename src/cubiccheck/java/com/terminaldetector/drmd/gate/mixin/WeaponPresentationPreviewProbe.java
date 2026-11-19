package com.terminaldetector.drmd.gate.mixin;
import com.terminaldetector.drmd.gate.NativeWeaponPresentationCheck;
import com.terminaldetector.drmd.client.render.DroneLoadoutPreview;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=DroneLoadoutPreview.class,remap=false)
public class WeaponPresentationPreviewProbe {
    @Inject(method="draw",at=@At("RETURN"),remap=false)
    private void actual(DrawContext context,ItemStack stack,int bank,int x,int y,int width,int height,CallbackInfo ci){NativeWeaponPresentationCheck.preview(stack,bank);}
}
