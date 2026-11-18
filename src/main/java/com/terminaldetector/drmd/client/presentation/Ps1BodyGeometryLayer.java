package com.terminaldetector.drmd.client.presentation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import java.util.Optional;

/** Built-in geometry shader for solid BlockBody faces and the two authored mob cutout materials. */
public final class Ps1BodyGeometryLayer extends RenderLayer {
    public record Stats(long shaderSelections, long fallbacks, int viewportWidth, int viewportHeight,
                        float snapPixels, float affineAmount) {}
    private static net.minecraft.client.gl.ShaderProgram program;
    private static Ps1BodyGeometryLayer wrapped;
    private static final Identifier DRONE = Identifier.of("drmd", "textures/entity/drone.png");
    private static final Identifier SPIDER = Identifier.of("drmd", "textures/entity/spider_turret.png");
    // Only these two material variants are cached. A queued wrapper always owns its immutable native phase.
    private static Ps1BodyGeometryLayer drone, spider;
    private static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0);
    private static long selections, fallbacks;
    private static Stats last = EMPTY;
    private final State state;

    private Ps1BodyGeometryLayer(State state) {
        super("drmd_ps1_body_cutout", state.original.getVertexFormat(), state.original.getDrawMode(),
                state.original.getExpectedBufferSize(), state.original.hasCrumbling(), state.original.isTranslucent(),
                state::start, state::end);
        this.state = state;
    }

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                Identifier.of("drmd", "ps1_body_cutout"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                shader -> program = shader));
    }

    public static Stats stats() { return last; }

    /** Actual shader starts for this authored material, independent of BlockBody batches. */
    public static Stats stats(Identifier texture) {
        var layer = materialLayer(texture);
        return layer == null ? EMPTY : layer.state.last;
    }

    /** Completed world draws using this material's PS1 shader, not model/selector callback counts. */
    public static long completedDraws(Identifier texture) {
        var layer = materialLayer(texture);
        return layer == null ? 0 : layer.state.completedDraws;
    }

    private static Ps1BodyGeometryLayer materialLayer(Identifier texture) {
        return DRONE.equals(texture) ? drone : SPIDER.equals(texture) ? spider : null;
    }

    private static boolean eligible(Ps1GeometryOptions options) {
        return options.eligible(DescentConfig.presentationProfile, ScenePresentation.inLowResolutionWorld(),
                OffscreenWorldView.busy());
    }

    /** Native/Debug/overlays and every unsupported material retain the exact existing layer instance. */
    public static RenderLayer select(RenderLayer original) {
        if (!eligible(DescentConfig.ps1Geometry) || original != TexturedRenderLayers.getEntityCutout()) return original;
        if (wrapped == null || wrapped.state.original != original) wrapped = new Ps1BodyGeometryLayer(new State(original));
        return wrapped;
    }

    /** Keep invisible/translucent/outline/feature layers native; only the actual authored rig layer is eligible. */
    public static RenderLayer selectMob(RenderLayer original, Identifier texture) {
        if (!eligible(DescentConfig.ps1Geometry) || (!DRONE.equals(texture) && !SPIDER.equals(texture))) return original;
        if (original != RenderLayer.getEntityCutoutNoCull(texture)
                || original.getVertexFormat() != VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
                || original.getDrawMode() != VertexFormat.DrawMode.QUADS) return original;
        var layer = materialLayer(texture);
        if (layer == null || layer.state.original != original) layer = new Ps1BodyGeometryLayer(new State(original));
        if (DRONE.equals(texture)) drone = layer;
        else spider = layer;
        return layer;
    }

    @Override public Optional<RenderLayer> getAffectedOutline() { return state.original.getAffectedOutline(); }
    @Override public boolean isOutline() { return state.original.isOutline(); }

    @Override public void draw(BuiltBuffer buffer) {
        long before = state.selections;
        try {
            super.draw(buffer);
            if (state.selections > before) state.completedDraws++;
        }
        finally { state.end(); } // RenderLayer's ordinary end path and an exceptional draw both restore the native phase.
    }

    private static final class State {
        private final RenderLayer original;
        private net.minecraft.client.gl.ShaderProgram originalShader;
        private boolean started;
        private long selections, fallbacks, completedDraws;
        private Stats last = EMPTY;
        private State(RenderLayer original) { this.original = original; }

        private void start() {
            original.startDrawing();
            originalShader = RenderSystem.getShader();
            started = true;
            try {
                var options = DescentConfig.ps1Geometry;
                // A queued batch may flush after profile/world phase changed; admission is checked again here.
                if (!eligible(options)) return;
                int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                if (program == null || viewport[2] <= 0 || viewport[3] <= 0) {
                    fallbacks++;
                    Ps1BodyGeometryLayer.fallbacks++;
                    record(viewport, options);
                    return;
                }
                program.getUniform("GeometryViewport").set((float)viewport[0], (float)viewport[1], (float)viewport[2], (float)viewport[3]);
                program.getUniform("VertexSnapPixels").set(options.snapPixels());
                program.getUniform("AffineAmount").set(options.affineAmount());
                RenderSystem.setShader(() -> program);
                selections++;
                Ps1BodyGeometryLayer.selections++;
                record(viewport, options);
            } catch (RuntimeException | Error failure) { end(); throw failure; }
        }

        private void record(int[] viewport, Ps1GeometryOptions options) {
            last = new Stats(selections, fallbacks, viewport[2], viewport[3], options.snapPixels(), options.affineAmount());
            Ps1BodyGeometryLayer.last = new Stats(Ps1BodyGeometryLayer.selections, Ps1BodyGeometryLayer.fallbacks,
                    viewport[2], viewport[3], options.snapPixels(), options.affineAmount());
        }

        private void end() {
            if (!started) return;
            started = false;
            var restore = originalShader; originalShader = null;
            try { original.endDrawing(); }
            finally { RenderSystem.setShader(() -> restore); }
        }
    }
}
