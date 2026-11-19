package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.presentation.Ps1FogRange;
import com.terminaldetector.drmd.client.presentation.Ps1WorldFog;
import net.minecraft.client.render.FogShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Ps1FogRangeTest {
    @Test void noStyleAndMalformedInputsStayOutOfTheNativeWorldFog(){
        assertTrue(Ps1FogRange.at(600,0,64).isEmpty());assertTrue(Ps1FogRange.at(Double.NaN,1,64).isEmpty());
        assertTrue(Ps1FogRange.at(600,1,Float.POSITIVE_INFINITY).isEmpty());assertTrue(Ps1FogRange.at(600,1,-1).isEmpty());
        assertTrue(Float.isFinite(Ps1FogRange.at(600,Float.MIN_VALUE,64).orElseThrow().end()));
    }
    @Test void theSameTrueDistanceHasExactlyTheSameSmoothstepAtNearFarAndCloudConsumers(){
        var range=Ps1FogRange.at(600,1,64).orElseThrow();assertEquals(32,range.start());
        assertEquals(0,range.amount(range.start(),1));assertEquals(.5,range.amount((range.start()+range.end())*.5,1),1e-6);
        assertEquals(1,range.amount(range.end(),1));assertEquals(.5,range.amount(range.end(),.5));
        double previous=0;for(int i=0;i<=1000;i++){double amount=range.amount(range.start()+(range.end()-range.start())*i/1000.0,1);assertTrue(amount>=previous&&amount<=1);previous=amount;}
        for(double distance:new double[]{range.start(),range.end()})assertEquals(range.amount(distance-.001,1),range.amount(distance+.001,1),1e-6);
    }
    @Test void orbitalAirKeepsMoreDistantRealGeometryWhileTheActualNearExtentControlsTheJoin(){
        var ground=Ps1FogRange.at(600,1,64).orElseThrow();var orbit=Ps1FogRange.at(1700,1,64).orElseThrow();
        assertTrue(orbit.end()>ground.end());assertTrue(orbit.amount(5000,1)<ground.amount(5000,1));
        assertEquals(128,Ps1FogRange.at(600,1,256).orElseThrow().start());
        assertTrue(ground.amount(64,1)<.01,"near geometry is not fully erased at the vanilla chunk extent");
    }
    @Test void nestedViewSnapshotsCannotOverwriteTheOuterNativeColourThroughAnAliasedArray(){
        float[] rgb={.1f,.2f,.3f,.75f};var state=new Ps1WorldFog.State(8,64,rgb,FogShape.CYLINDER);
        rgb[0]=1;assertEquals(.1f,state.colour()[0]);var borrowed=state.colour();borrowed[1]=1;
        assertEquals(.2f,state.colour()[1]);assertEquals(.75f,state.colour()[3]);assertEquals(8,state.start());assertEquals(64,state.end());
    }
}
