package com.terminaldetector.drmd.legacy;

/** Focused test observations only; never part of the installable mod JAR. */
public final class LegacyRetirementObservation {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.test.legacyRetirement");
    public static int forbiddenArenaEntries,vanillaRespawnEntries;
    private LegacyRetirementObservation(){}
    public static void reset(){forbiddenArenaEntries=0;vanillaRespawnEntries=0;}
    public static void forbiddenArena(){
        forbiddenArenaEntries++;
        // Fail before the forbidden ~60k writes. ensureBase catches this sentinel; the
        // actual GameTest subsequently asserts that entry count was zero, so it fails.
        throw new IllegalStateException("LEGACY_RETIREMENT_TEST forbidden automatic Citadel entry observed; test sentinel prevents bulk writes");
    }
}
