package com.terminaldetector.drmd.gate.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MinecraftClient.class)
public interface FlightItemColoursAccess { @Accessor("itemColors") ItemColors drmd$itemColours(); }
