package com.terminaldetector.drmd.config;

import java.util.Locale;
import java.util.Properties;

/** Migration rules for profile keys; explicit choices are never overwritten by a restart. */
public final class ProfileDefaults {
    private ProfileDefaults() {}
    public static boolean hybrid(Properties props) {
        String value=props.getProperty("hybridRender");
        if(value==null)return true;
        return switch(value.trim().toLowerCase(Locale.ROOT)) {
            case "false","off","0" -> false;
            case "true","on","1" -> true;
            default -> true;
        };
    }
}
