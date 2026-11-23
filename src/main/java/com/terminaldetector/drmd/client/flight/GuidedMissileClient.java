package com.terminaldetector.drmd.client.flight;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.weapon.projectile.GuidedMissileControl;
import com.terminaldetector.drmd.weapon.projectile.GuidedMissileControl.Input;
import com.terminaldetector.drmd.weapon.projectile.GuidedMissileControl.Phase;
import com.terminaldetector.drmd.weapon.projectile.GuidedMissileControl.State;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/** Missile video from authoritative poses. The main camera remains attached to the local player. */
public final class GuidedMissileClient {
    private static final int VIDEO_WIDTH = 320;
    private static final int VIDEO_HEIGHT = 180;
    private static final long FRAME_NANOS = 50_000_000L;
    private static final long SNAPSHOT_NANOS = FRAME_NANOS * GuidedMissileControl.SNAPSHOT_INTERVAL;
    private static final float MOUSE_GAIN = 0.025f;
    private static KeyBinding controlKey;
    private static Link link;
    private static Framebuffer video;
    private static ClientWorld videoWorld;
    private static int cameraProjectileId = -1;
    private static long lastFrame;
    private static boolean hasFrame;

    private GuidedMissileClient() {}

    public static void register() {
        controlKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.drmd.guided_control",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.category.drmd"));
        ClientTickEvents.END_CLIENT_TICK.register(GuidedMissileClient::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> reset());
        ClientPlayNetworking.registerGlobalReceiver(State.ID, (state, context) -> accept(context.client(), state));
        WorldRenderEvents.END.register(GuidedMissileClient::capture);
        HudRenderCallback.EVENT.register((draw, tick) -> drawHud(draw));
    }

    public static boolean isCameraProjectile(int id) {
        return id == cameraProjectileId;
    }

    /** True only while the pilot has explicitly transferred their mouse look to this link. */
    public static boolean mouse(double mouseX, double mouseY) {
        var client = MinecraftClient.getInstance();
        if (link == null || !link.steering || !link.isCurrent(client) || client.currentScreen != null
                || !client.isWindowFocused() || client.options.hudHidden) return false;
        link.yaw = MathHelper.clamp(link.yaw - mouseX * MOUSE_GAIN, -1, 1);
        link.pitch = MathHelper.clamp(link.pitch - mouseY * MOUSE_GAIN, -1, 1);
        return true;
    }

    private static void accept(MinecraftClient client, State state) {
        if (client.world == null || !client.world.getRegistryKey().getValue().equals(state.dimension())) return;
        if (!state.isOpen()) {
            if (link != null && link.state.token().equals(state.token())) {
                clearLink();
                if (client.player != null && state.phase() == Phase.LOST) {
                    client.player.sendMessage(Text.translatable("hud.drmd.guided_lost"), true);
                }
            }
            return;
        }
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator()
                || !validPose(state)) return;
        if (state.phase() == Phase.OPEN) {
            clearLink();
            link = new Link(client.player, client.world, state);
        } else if (link != null && link.isCurrent(client) && link.state.token().equals(state.token())) {
            link.update(state);
        }
    }

    private static boolean validPose(State state) {
        Vec3d p = state.position();
        var q = state.attitude();
        return state.lifeTicks() > 0 && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                && Double.isFinite(q.x()) && Double.isFinite(q.y()) && Double.isFinite(q.z()) && Double.isFinite(q.w());
    }

    private static void tick(MinecraftClient client) {
        if (video != null && videoWorld != client.world) deleteVideo();
        boolean pressed = false;
        while (controlKey.wasPressed()) pressed = true;
        if (link == null) return;
        if (!link.isCurrent(client) || !ClientPlayNetworking.canSend(Input.ID)) {
            clearLink();
            return;
        }
        if (link.steering && (client.currentScreen != null || !client.isWindowFocused() || client.options.hudHidden)) {
            release();
            return;
        }
        if (client.isPaused()) return;
        if (++link.ticksWithoutState > GuidedMissileControl.LINK_TIMEOUT) {
            release();
            client.player.sendMessage(Text.translatable("hud.drmd.guided_lost"), true);
            return;
        }
        if (pressed && link.steering) {
            release();
            return;
        }
        if (pressed && client.currentScreen == null && client.isWindowFocused() && !client.options.hudHidden && hasFrame) {
            link.steering = true;
        }
        ClientPlayNetworking.send(new Input(link.state.token(), link.sequence++, false,
                (float) link.yaw, (float) link.pitch));
        link.yaw = link.pitch = 0;
    }

    private static void release() {
        ClientPlayNetworking.send(new Input(link.state.token(), link.sequence++, true, 0, 0));
        clearLink();
    }

    private static void clearLink() {
        link = null;
        hasFrame = false;
        lastFrame = 0;
        cameraProjectileId = -1;
    }

    private static void deleteVideo() {
        if (video != null) video.delete();
        video = null;
        videoWorld = null;
        hasFrame = false;
    }

    private static void reset() {
        clearLink();
        deleteVideo();
    }

    private static void capture(WorldRenderContext context) {
        if (link == null || OffscreenWorldView.busy()) return;
        var mc = MinecraftClient.getInstance();
        if (!link.isCurrent(mc) || mc.options.hudHidden || mc.isPaused() || mc.currentScreen != null) return;
        long now = System.nanoTime();
        if (hasFrame && now - lastFrame < FRAME_NANOS) return;

        Pose pose = link.poseAt(now);
        var access = WorldAccess.of(link.world);
        if (!access.isVolumeAvailable(access.cubeAt(BlockPos.ofFloored(pose.position)))) {
            hasFrame = false;
            return;
        }
        Quaternionf attitude = pose.attitude;
        var look = attitude.transform(new Vector3f(0, 0, 1));
        Vec3d nose = pose.position.add(look.x * .25, look.y * .25, look.z * .25);
        if (access.isVolumeAvailable(access.cubeAt(BlockPos.ofFloored(nose)))) {
            var hit = link.world.raycast(new RaycastContext(pose.position, nose,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, link.owner));
            if (hit.getType() != HitResult.Type.MISS) nose = pose.position;
        } else {
            nose = pose.position;
        }
        // Minecraft's camera looks down -Z, while the physical attitude's forward is +Z.
        Quaternionf rotation = new Quaternionf(attitude).rotateY((float) Math.PI);
        float far = Math.max(32, context.gameRenderer().getViewDistance() * 2);
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(64),
                (float) VIDEO_WIDTH / VIDEO_HEIGHT, .05f, far);

        cameraProjectileId = link.state.entityId();
        try {
            if (video == null) {
                video = new VideoTarget();
                video.resize(VIDEO_WIDTH, VIDEO_HEIGHT, MinecraftClient.IS_SYSTEM_MAC);
                videoWorld = link.world;
                mc.getFramebuffer().beginWrite(true);
            }
            hasFrame = OffscreenWorldView.capture(context, video, nose, rotation, projection);
            lastFrame = now;
        } catch (RuntimeException failure) {
            DescentMod.LOGGER.warn("Could not render the guided missile camera", failure);
            if (ClientPlayNetworking.canSend(Input.ID)) release();
            else clearLink();
            deleteVideo();
            mc.getFramebuffer().beginWrite(true);
        } finally {
            cameraProjectileId = -1;
        }
    }

    private static void drawHud(DrawContext draw) {
        var mc = MinecraftClient.getInstance();
        if (link == null || !link.isCurrent(mc) || mc.options.hudHidden || mc.currentScreen != null
                || draw.getScaledWindowWidth() < 150 || draw.getScaledWindowHeight() < 120) return;
        int y = 18;
        int heightBudget = Math.max(47, draw.getScaledWindowHeight() / 2 - y - 34);
        int width = Math.min(Math.min(192, draw.getScaledWindowWidth() - 24),
                heightBudget * VIDEO_WIDTH / VIDEO_HEIGHT);
        int height = width * VIDEO_HEIGHT / VIDEO_WIDTH;
        int x = (draw.getScaledWindowWidth() - width) / 2;
        int signalColor = link.steering ? 0xFFB3D991 : 0xFFCED5C4;
        draw.fill(x - 2, y - 12, x + width + 2, y + height + 25, 0xE5111617);
        draw.fill(x - 2, y - 12, x + width + 2, y - 11, signalColor);
        draw.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.drmd.guided_camera"), x + 2, y - 10, signalColor);
        if (hasFrame && video != null) {
            draw.draw();
            drawVideo(draw, x, y, width, height);
            int centerX = x + width / 2;
            int centerY = y + height / 2;
            draw.fill(centerX - 4, centerY, centerX + 5, centerY + 1, signalColor);
            draw.fill(centerX, centerY - 4, centerX + 1, centerY + 5, signalColor);
        } else {
            draw.fill(x, y, x + width, y + height, 0xFF1A2121);
            draw.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.drmd.guided_wait"), x + 3, y + 4, signalColor);
        }

        int distance = (int) Math.round(link.state.position().distanceTo(mc.player.getPos()));
        String life = String.format(Locale.ROOT, "%.1f", link.state.lifeTicks() / 20.0);
        draw.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.drmd.guided_range", distance, life),
                x + 2, y + height + 2, 0xFFDDE0D7);
        String action = link.steering ? "hud.drmd.guided_release" : "hud.drmd.guided_steer";
        draw.drawTextWithShadow(mc.textRenderer, Text.translatable(action, controlKey.getBoundKeyLocalizedText()),
                x + 2, y + height + 13, signalColor);
    }

    private static void drawVideo(DrawContext draw, int x, int y, int width, int height) {
        var matrix = draw.getMatrices().peek().getPositionMatrix();
        var shader = RenderSystem.getShader();
        int texture = RenderSystem.getShaderTexture(0);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        float[] color = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderTexture(0, video.getColorAttachment());
            RenderSystem.setShaderColor(1, 1, 1, 1);
            var quad = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            quad.vertex(matrix, x, y + height, 0).texture(0, 0);
            quad.vertex(matrix, x + width, y + height, 0).texture(1, 0);
            quad.vertex(matrix, x + width, y, 0).texture(1, 1);
            quad.vertex(matrix, x, y, 0).texture(0, 1);
            BufferRenderer.drawWithGlobalProgram(quad.end());
        } finally {
            if (depth) RenderSystem.enableDepthTest();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }

    private static final class VideoTarget extends Framebuffer {
        private VideoTarget() { super(true); }
    }

    private record Pose(Vec3d position, Quaternionf attitude) {
        static Pose from(State state) {
            var q = state.attitude();
            return new Pose(state.position(), new Quaternionf(q.x(), q.y(), q.z(), q.w()).normalize());
        }
    }

    private static final class Link {
        private final ClientPlayerEntity owner;
        private final ClientWorld world;
        private State state;
        private Pose from;
        private Pose to;
        private long updatedAt;
        private int sequence;
        private int ticksWithoutState;
        private boolean steering;
        private double yaw;
        private double pitch;

        Link(ClientPlayerEntity owner, ClientWorld world, State initial) {
            this.owner = owner;
            this.world = world;
            this.state = initial;
            from = to = Pose.from(initial);
            updatedAt = System.nanoTime();
        }

        boolean isCurrent(MinecraftClient client) {
            return client.player == owner && client.world == world && owner.isAlive()
                    && !owner.isSpectator() && client.getCameraEntity() == owner;
        }

        void update(State next) {
            long now = System.nanoTime();
            from = poseAt(now);
            to = Pose.from(next);
            state = next;
            updatedAt = now;
            ticksWithoutState = 0;
        }

        Pose poseAt(long now) {
            float progress = MathHelper.clamp((float) (now - updatedAt) / SNAPSHOT_NANOS, 0f, 1f);
            return new Pose(from.position.lerp(to.position, progress),
                    new Quaternionf(from.attitude).slerp(to.attitude, progress));
        }
    }
}
