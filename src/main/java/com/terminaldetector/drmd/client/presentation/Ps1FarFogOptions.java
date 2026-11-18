package com.terminaldetector.drmd.client.presentation;

import java.util.Properties;

/** Optional colour-only haze for the actual PS1 far-world pass; old configurations remain neutral. */
public record Ps1FarFogOptions(float density) {
    public Ps1FarFogOptions {
        density=Float.isFinite(density)?Math.max(0,Math.min(2,density)):0;
    }
    public float effective(PresentationProfile profile,boolean lowResolutionWorld,boolean offscreen){
        return profile==PresentationProfile.PS1_HYBRID&&lowResolutionWorld&&!offscreen?density:0;
    }
    public static Ps1FarFogOptions read(Properties properties){
        float value=0;
        try{value=Float.parseFloat(properties.getProperty("ps1FarFogDensity","0").trim());}
        catch(NumberFormatException ignored){}
        return new Ps1FarFogOptions(value);
    }
    public void write(Properties properties){properties.setProperty("ps1FarFogDensity",Float.toString(density));}
    public float nextDensity(){for(float value:new float[]{0,.25f,.5f,1,2})if(value>density)return value;return 0;}
}
