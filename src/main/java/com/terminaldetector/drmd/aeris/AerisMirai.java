package com.terminaldetector.drmd.aeris;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Preserve the original experimental codec/dimension type for saved dimension options.
 * Its exact world definition lives in an explicitly selected demo data pack, never the default
 * ordinary world registry. No existing save/chunk/profile is migrated, removed or rewritten. */
public final class AerisMirai {
    private AerisMirai() {}
    public static void register() {
        Registry.register(Registries.CHUNK_GENERATOR,
            Identifier.of(DescentMod.MOD_ID,"aeris_density_generator"),AerisDensityChunkGenerator.CODEC);
        var mod=FabricLoader.getInstance().getModContainer(DescentMod.MOD_ID).orElseThrow();
        boolean registered=ResourceManagerHelper.registerBuiltinResourcePack(
            Identifier.of(DescentMod.MOD_ID,"aeris_demo"),mod,Text.literal("DRMD legacy ÆRis demo"),ResourcePackActivationType.NORMAL);
        if(!registered)throw new IllegalStateException("Legacy ÆRis demo pack resource missing; keep old codec/type and explicit profile available");
        DescentMod.LOGGER.info("Legacy ÆRis codec retained; aeris_demo world definition requires explicit data-pack selection");
    }
}
