package com.terminaldetector.drmd.gate.mixin;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(MinecraftClient.class)
public interface ProjectorInputAccess {
    @Invoker("doAttack") boolean drmd$attack();
    @Invoker("doItemUse") void drmd$use();
    @Invoker("handleBlockBreaking") void drmd$breakHeld(boolean breaking);
}
