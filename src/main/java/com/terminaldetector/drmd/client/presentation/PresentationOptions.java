package com.terminaldetector.drmd.client.presentation;

import java.util.Properties;

/** Persistent client choices, with no Fabric/Minecraft or world-state dependency. */
public record PresentationOptions(PresentationProfile profile, int sceneHeight) {
    public static final int DEFAULT_HEIGHT = 360, MIN_HEIGHT = 90, MAX_HEIGHT = 1080;

    public PresentationOptions {
        if (profile == null) profile = PresentationProfile.HYBRID_NATIVE;
        sceneHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, sceneHeight));
    }

    public static PresentationOptions read(Properties properties) {
        int height = DEFAULT_HEIGHT;
        try { height = Integer.parseInt(properties.getProperty("ps1SceneHeight", "360").trim()); }
        catch (NumberFormatException ignored) { }
        return new PresentationOptions(PresentationProfile.parse(properties.getProperty("presentationProfile")), height);
    }

    public void write(Properties properties) {
        properties.setProperty("presentationProfile", profile.name());
        properties.setProperty("ps1SceneHeight", Integer.toString(sceneHeight));
    }

    public int nextHeight() {
        for (int height : new int[]{180, 270, 360, 540, 720, 1080}) if (height > sceneHeight) return height;
        return 180;
    }
}
