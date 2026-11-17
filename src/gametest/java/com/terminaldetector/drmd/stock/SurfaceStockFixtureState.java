package com.terminaldetector.drmd.stock;

import java.util.ArrayList;
import java.util.List;

/** Actual selected pack lists, captured before registry loading by the focused test only. */
public final class SurfaceStockFixtureState {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.test.surfaceStock");
    public static final String LEGACY="drmd:advanced_column";
    public static volatile boolean selected;
    public static volatile List<String> beforeEnabled=List.of(),beforeDisabled=List.of(),enabled=List.of(),disabled=List.of();
    private SurfaceStockFixtureState(){}
    public static void record(List<String> oldEnabled,List<String> oldDisabled,List<String> nextEnabled,List<String> nextDisabled){
        var expected=new ArrayList<>(oldEnabled);expected.removeIf(LEGACY::equals);
        var expectedDisabled=new ArrayList<>(oldDisabled);if(!expectedDisabled.contains(LEGACY))expectedDisabled.add(LEGACY);
        if(!expected.equals(nextEnabled)||!expectedDisabled.equals(nextDisabled))throw new IllegalStateException("STOCK fixture may select only the legacy compatibility pack; preserve all other profiles/order");
        beforeEnabled=List.copyOf(oldEnabled);beforeDisabled=List.copyOf(oldDisabled);enabled=List.copyOf(nextEnabled);disabled=List.copyOf(nextDisabled);selected=true;
        System.out.println("SURFACE_STOCK_PACK_SELECTION before="+beforeEnabled+" disabledBefore="+beforeDisabled+" enabled="+enabled+" disabled="+disabled);
    }
}
