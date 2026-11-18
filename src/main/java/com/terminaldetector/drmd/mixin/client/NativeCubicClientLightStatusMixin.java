package com.terminaldetector.drmd.mixin.client;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Refresh only the geometry bit of the existing optional native deferred light call. */
@Pseudo
@Mixin(targets="dev.fallingcloud.bettercubicchunks.client.ClientCubeHandler",remap=false)
public abstract class NativeCubicClientLightStatusMixin {
    // One-for-one forwarding keeps the actual receiver available for the foreign-provider guard.
    // No extra light invocation, payload replay, task, array or world load is introduced.
    @Redirect(method="lambda$handleCubeData$5",at=@At(value="INVOKE",target="Lnet/minecraft/class_3568;method_15551(Lnet/minecraft/class_4076;Z)V",remap=false),remap=false,require=1)
    private static void drmd$currentSectionStatus(LightingProvider provider,ChunkSectionPos section,boolean originalEmpty,
            ClientWorld world,int cubeX,int cubeY,int cubeZ,@Coerce Object payload,boolean capturedEmpty,long cubeKey){
        provider.setSectionStatus(section,drmd$currentSectionEmpty(provider,section,originalEmpty,world,cubeX,cubeY,cubeZ));
    }
    @Unique
    private static boolean drmd$currentSectionEmpty(LightingProvider provider,ChunkSectionPos section,boolean originalEmpty,
            ClientWorld world,int cubeX,int cubeY,int cubeZ){
        if(world==null||provider==null||section==null)return originalEmpty;
        var client=MinecraftClient.getInstance();
        if(client.world!=world||!client.isOnThread()||!CubicAvailability.onWorldThread(world))return originalEmpty;
        if(provider!=world.getLightingProvider()||CubicAvailability.backend(world)!=CubicAvailability.Backend.NATIVE_CUBIC)return originalEmpty;
        if(section.getSectionX()!=cubeX||section.getSectionY()!=cubeY||section.getSectionZ()!=cubeZ)return originalEmpty;
        if(!CubicAvailability.available(world,new D6ChunkPos(cubeX,cubeY,cubeZ)))return originalEmpty;
        var column=CubicAvailability.loadedColumn(world,cubeX,cubeZ);
        if(column==null||column.getPos().x!=cubeX||column.getPos().z!=cubeZ)return originalEmpty;
        int index=world.sectionCoordToIndex(cubeY);
        if(index<0||index>=column.countVerticalSections())return originalEmpty;
        var current=column.getSection(index);
        return current==null?originalEmpty:current.isEmpty();
    }
}
