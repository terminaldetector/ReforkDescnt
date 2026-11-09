package com.terminaldetector.drmd.client.presentation;

/** Preserve the window aspect/FOV and never supersample a smaller window. */
public record PresentationSize(int width, int height) {
    public static final long MAX_PIXELS = 4L * 1024 * 1024;
    public boolean valid() { return width > 0 && height > 0; }

    public static PresentationSize fit(int windowWidth, int windowHeight, int requestedHeight, int textureLimit) {
        if (windowWidth <= 0 || windowHeight <= 0 || textureLimit <= 0) return new PresentationSize(0, 0);
        int height = Math.min(windowHeight, Math.max(PresentationOptions.MIN_HEIGHT,
                Math.min(PresentationOptions.MAX_HEIGHT, requestedHeight)));
        double scale = (double) height / windowHeight;
        scale = Math.min(scale, (double) textureLimit / Math.max(windowWidth, windowHeight));
        scale = Math.min(scale, Math.sqrt((double) MAX_PIXELS / ((long) windowWidth * windowHeight)));
        return new PresentationSize(Math.max(1, (int) Math.floor(windowWidth * scale)),
                Math.max(1, (int) Math.floor(windowHeight * scale)));
    }
}
