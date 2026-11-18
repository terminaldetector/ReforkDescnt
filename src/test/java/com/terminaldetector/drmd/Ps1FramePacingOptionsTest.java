package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.presentation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Ps1FramePacingOptionsTest {
    @Test void capNeverRaisesVanillaAndEveryExcludedModeReturnsTheExactOriginal(){
        for(int fps:new int[]{0,30})for(int vanilla:new int[]{0,10,20,75,260})for(var profile:PresentationProfile.values())for(boolean world:new boolean[]{false,true})for(boolean offscreen:new boolean[]{false,true}){
            boolean active=fps>0&&vanilla>0&&profile==PresentationProfile.PS1_HYBRID&&world&&!offscreen;
            assertEquals(active?Math.min(vanilla,fps):vanilla,new Ps1FramePacingOptions(fps).limit(vanilla,profile,world,offscreen));
        }
    }
    @Test void invalidSettingsAreDisabledAndExplicitValuesStayWithinTheBoundedRange(){
        for(String value:new String[]{"NaN","Infinity","bad","","-30","999999999999999999"}){
            var p=new Properties();p.setProperty("ps1FramePacingFps",value);assertEquals(0,Ps1FramePacingOptions.read(p).fps());
        }
        assertEquals(10,new Ps1FramePacingOptions(1).fps());assertEquals(120,new Ps1FramePacingOptions(Integer.MAX_VALUE).fps());
        var p=new Properties();new Ps1FramePacingOptions(0).write(p);assertEquals("0",p.getProperty("ps1FramePacingFps"));
    }
    @Test void liveCycleIsIndependentOfAssetChoiceAndTheVanillaFpsProperty(){
        var p=new Properties();p.setProperty("maxFps","90");Ps1AssetProfile.LOW_POLY.write(p);
        var options=new Ps1FramePacingOptions(0);var seen=new HashSet<Integer>();
        for(int i=0;i<5;i++){seen.add(options.fps());options.write(p);assertEquals(Ps1AssetProfile.LOW_POLY,Ps1AssetProfile.read(p));assertEquals("90",p.getProperty("maxFps"));options=new Ps1FramePacingOptions(options.nextFps());}
        assertEquals(Set.of(0,20,24,30,60),seen);assertEquals(0,options.fps());
    }
}
