package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.cloud.CloudRenderCompatibility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CloudRenderCompatibilityTest {
    @Test void actualObservedLayoutsSelectDistinctTypedPrograms(){assertEquals(CloudRenderCompatibility.Layout.SODIUM_POSITION_COLOR,CloudRenderCompatibility.layout(16,0,-1,12,-1,2));assertEquals(CloudRenderCompatibility.Layout.VANILLA_TEXTURED,CloudRenderCompatibility.layout(28,0,12,20,24,4));}
    @Test void nearMatchOffsetsStrideOrMissingNativeAttributeAreRejected(){assertNull(CloudRenderCompatibility.layout(28,0,12,16,24,4));assertNull(CloudRenderCompatibility.layout(28,0,12,20,-1,3));assertNull(CloudRenderCompatibility.layout(32,0,12,20,24,4));assertNull(CloudRenderCompatibility.layout(16,0,8,12,-1,3));assertNull(CloudRenderCompatibility.layout(28,0,12,20,24,5));}
    @Test void nativeTextureUnitZeroCannotAliasFarSemanticTexture(){assertEquals(0,CloudRenderCompatibility.farSurfaceUnit(CloudRenderCompatibility.Layout.SODIUM_POSITION_COLOR));assertEquals(1,CloudRenderCompatibility.farSurfaceUnit(CloudRenderCompatibility.Layout.VANILLA_TEXTURED));assertThrows(IllegalArgumentException.class,()->CloudRenderCompatibility.farSurfaceUnit(null));}
    @Test void coreAndArbCapabilityMatrixNeverRequestsAbsentSamplerApi(){assertEquals(CloudRenderCompatibility.SamplerApi.NONE,CloudRenderCompatibility.samplerApi(false,false));assertEquals(CloudRenderCompatibility.SamplerApi.ARB,CloudRenderCompatibility.samplerApi(false,true));assertEquals(CloudRenderCompatibility.SamplerApi.CORE_33,CloudRenderCompatibility.samplerApi(true,false));assertEquals(CloudRenderCompatibility.SamplerApi.CORE_33,CloudRenderCompatibility.samplerApi(true,true));}
}
