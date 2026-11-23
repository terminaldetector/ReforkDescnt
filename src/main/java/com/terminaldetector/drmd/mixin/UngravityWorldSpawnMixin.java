package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.profile.UngravityChunkGenerator;
import com.terminaldetector.drmd.world.profile.WorldProfile;
import com.terminaldetector.drmd.world.profile.WorldProfileNative;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.level.ServerWorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class UngravityWorldSpawnMixin {
    @Inject(method = "setupSpawn", at = @At("HEAD"), cancellable = true)
    private static void drmd$spawnAtDock(ServerWorld world, ServerWorldProperties properties,
            boolean bonusChest, boolean debug, CallbackInfo callback) {
        if (!WorldProfile.isUngravity(world)) return;
        world.setSpawnPos(new BlockPos(0, UngravityChunkGenerator.SPAWN_Y, 0), 0);
        world.getGameRules().get(GameRules.SPAWN_RADIUS).set(0, world.getServer());
        callback.cancel();
    }

    @Inject(method = "prepareStartRegion", at = @At("HEAD"))
    private void drmd$validateLoadedProfile(net.minecraft.server.WorldGenerationProgressListener listener, CallbackInfo callback) {
        ServerWorld world = ((MinecraftServer) (Object) this).getWorld(World.OVERWORLD);
        if (WorldProfile.isUngravity(world)) WorldProfileNative.validate(world, WorldProfile.get(world));
    }
}
