package com.terminaldetector.drmd.physics;

import java.util.Locale;

/** Optional destruction/wreck conversion policy; core flight and existing collision stay valid. */
public enum PhysicsProfile {
    STANDARD,EXPANDED;
    public static PhysicsProfile parse(String text) {
        if(text==null)return EXPANDED;
        return switch(text.trim().toUpperCase(Locale.ROOT)) {
            case "STANDARD","FALSE","OFF","0" -> STANDARD;
            case "EXPANDED","TRUE","ON","1" -> EXPANDED;
            default -> EXPANDED;
        };
    }
}
