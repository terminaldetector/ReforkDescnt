package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.LegacyGeneration;
import com.terminaldetector.drmd.world.gen.ModWorldgen;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Routing regression only: test-only fail-fast observation prevents the forbidden old
 * generator from materializing a huge arena on the negative; no fake block/height is returned. */
public final class LegacyFeatureAdmissionGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="retired-feature-admission")
    public void savedConfiguredFeatureIdIsAnInertPlaceholderBeforeAnyWorldRead(TestContext c){
        c.assertTrue(LegacyGeneration.retired(),"fixture requires the actual retired policy");
        var feature=Registries.FEATURE.get(Identifier.of("drmd","industrial_underground"));
        c.assertTrue(feature==ModWorldgen.INDUSTRIAL,"the real compatibility registry ID remains loadable");
        var calls=new AtomicInteger();
        StructureWorldAccess observed=(StructureWorldAccess)Proxy.newProxyInstance(StructureWorldAccess.class.getClassLoader(),new Class<?>[]{StructureWorldAccess.class},(proxy,method,args)->{
            calls.incrementAndGet();throw new AssertionError("Retired feature touched actual generation context via "+method.getName());
        });
        // Chunk0 is an actual accepted legacy hash branch, not a rejected-density false control.
        var context=new FeatureContext<DefaultFeatureConfig>(Optional.empty(),observed,c.getWorld().getChunkManager().getChunkGenerator(),c.getWorld().getRandom(),BlockPos.ORIGIN,DefaultFeatureConfig.INSTANCE);
        c.assertTrue(!ModWorldgen.INDUSTRIAL.generate(context),"old placed-feature entry returns vanilla false without authoring");
        c.assertTrue(calls.get()==0,"no world read/write, random generation, entity or pending NBT work admitted");
        c.complete();
    }
}
