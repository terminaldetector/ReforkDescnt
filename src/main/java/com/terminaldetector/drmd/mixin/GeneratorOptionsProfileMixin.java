package com.terminaldetector.drmd.mixin;

import com.mojang.serialization.MapCodec;
import com.terminaldetector.drmd.world.profile.WorldProfile;
import com.terminaldetector.drmd.world.profile.WorldProfiles;
import net.minecraft.world.gen.GeneratorOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalLong;

@Mixin(GeneratorOptions.class)
public abstract class GeneratorOptionsProfileMixin implements WorldProfile.Options {
    @Shadow @Final @Mutable public static MapCodec<GeneratorOptions> CODEC;
    @Unique private WorldProfile drmd$profile;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void drmd$extendGeneratorCodec(CallbackInfo ci) {
        CODEC = WorldProfiles.extendGeneratorOptionsCodec(CODEC);
    }

    @Inject(method = {"withBonusChest", "withStructures"}, at = @At("RETURN"))
    private void drmd$copyProfile(boolean enabled, CallbackInfoReturnable<GeneratorOptions> callback) {
        if (drmd$profile != null) WorldProfile.attach(callback.getReturnValue(), drmd$profile);
    }

    @Inject(method = "withSeed", at = @At("RETURN"))
    private void drmd$copyProfileWithSeed(OptionalLong selectedSeed, CallbackInfoReturnable<GeneratorOptions> callback) {
        WorldProfile profile = drmd$profile;
        if (profile == null) return;
        GeneratorOptions result = callback.getReturnValue();
        WorldProfile.attach(result, new WorldProfile(profile.schema(), profile.mode(), profile.preset(),
                profile.scale(), profile.performance(), profile.start(), result.getSeed()));
    }

    @Override public WorldProfile drmd$getWorldProfile() { return drmd$profile; }
    @Override public void drmd$setWorldProfile(WorldProfile profile) { drmd$profile = profile; }
}
