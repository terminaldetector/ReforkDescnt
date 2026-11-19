package com.terminaldetector.drmd.aerisfixture;
import java.util.*;
/** Test-only actual pre-registry pack selection; never substitutes world/registry values. */
public final class AerisProfileState {
    public static final String MODE=System.getProperty("drmd.test.aerisProfile","");
    public static final String DEMO="drmd:aeris_demo",ADVANCED="drmd:advanced_column";
    public static final boolean ENABLED=MODE.equals("ordinary")||MODE.equals("explicit");
    public static volatile boolean selected;
    public static volatile List<String> before=List.of(),after=List.of(),disabled=List.of();
    private AerisProfileState(){}
    public static void record(List<String> a,List<String> b,List<String> c){before=List.copyOf(a);after=List.copyOf(b);disabled=List.copyOf(c);selected=true;System.out.println("AERIS_REAL_PROFILE mode="+MODE+" before="+before+" after="+after+" disabled="+disabled);}
}
