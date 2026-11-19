package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.layer.WorldLayer;
import com.terminaldetector.drmd.world.planet.PlanetaryZones;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/** Real server-world metadata/HUD integration only; no terrain/navigation acceptance. */
public final class PlanetaryZoneContextGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void actualWorldContextResolvesTheSameZoneVocabularyUsedByHud(TestContext context){
        var world=context.getWorld();
        var layout=PlanetaryZones.fit(world.getBottomY(),world.getTopY(),world.getSeaLevel()).orElseThrow();
        for(var band:layout.bands()){
            double y=(long)band.bottom()+((long)band.top()-band.bottom())*.5;
            var display=WorldLayer.display(world,y);
            context.assertTrue(display.insideWorld()&&display.label().equals(band.zone().label),"actual world bounds/sea-level feed the live HUD resolver: "+band.zone());
        }
        context.assertTrue(!WorldLayer.display(world,(double)world.getBottomY()-1).insideWorld(),"real lower dimension bound is not a fabricated Core continuation");
        context.assertTrue(!WorldLayer.display(world,(double)world.getTopY()).insideWorld(),"exclusive actual upper bound is not a fabricated orbit continuation");
        context.complete();
    }
}
