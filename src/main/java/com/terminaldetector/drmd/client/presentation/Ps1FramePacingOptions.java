package com.terminaldetector.drmd.client.presentation;

import java.util.Properties;

/** Independent opt-in render cap. The vanilla render loop remains the only scheduler. */
public record Ps1FramePacingOptions(int fps) {
    public Ps1FramePacingOptions {fps=fps<=0?0:Math.max(10,Math.min(120,fps));}
    public int limit(int vanillaLimit,PresentationProfile profile,boolean mainWorld,boolean offscreen){
        if(fps==0||vanillaLimit<=0||profile!=PresentationProfile.PS1_HYBRID||!mainWorld||offscreen)return vanillaLimit;
        return Math.min(vanillaLimit,fps);
    }
    public int nextFps(){for(int value:new int[]{0,20,24,30,60})if(value>fps)return value;return 0;}
    public static Ps1FramePacingOptions read(Properties p){
        try{return new Ps1FramePacingOptions(Integer.parseInt(p.getProperty("ps1FramePacingFps","0").trim()));}
        catch(NumberFormatException invalid){return new Ps1FramePacingOptions(0);}
    }
    public void write(Properties p){p.setProperty("ps1FramePacingFps",Integer.toString(fps));}
}
