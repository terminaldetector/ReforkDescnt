package com.terminaldetector.drmd.client.presentation;

import java.util.Locale;
import java.util.Properties;

/** Supported world-model variants only; the default retains every existing native asset. */
public enum Ps1AssetProfile {
    NATIVE, LOW_POLY;
    public Ps1AssetProfile next(){return this==NATIVE?LOW_POLY:NATIVE;}
    public boolean eligible(PresentationProfile profile,boolean lowResolutionWorld,boolean offscreen){
        return this==LOW_POLY&&profile==PresentationProfile.PS1_HYBRID&&lowResolutionWorld&&!offscreen;
    }
    public static Ps1AssetProfile read(Properties p){
        try{return valueOf(p.getProperty("ps1AssetProfile","NATIVE").trim().toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException invalid){return NATIVE;}
    }
    public void write(Properties p){p.setProperty("ps1AssetProfile",name());}
}
