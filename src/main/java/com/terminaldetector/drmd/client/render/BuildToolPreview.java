package com.terminaldetector.drmd.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.terminaldetector.drmd.client.build.ScaffoldClientState;
import com.terminaldetector.drmd.world.build.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/** One bounded read-only plan per client tick. No world or inventory writes from a preview. */
public final class BuildToolPreview {
    private static BuildToolItem.Proposal current;
    private static net.minecraft.client.world.ClientWorld snapshotWorld;
    private static net.minecraft.client.network.ClientPlayerEntity snapshotPlayer;
    private BuildToolPreview() {}
    public static BuildToolItem.Proposal current(){return current;}
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BuildToolPreview::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BuildToolPreview::render);
    }
    private static boolean visible(MinecraftClient client) {
        return client.world!=null && client.player!=null && client.currentScreen==null && !client.options.hudHidden
            && client.player.isAlive() && !client.player.isSpectator() && !client.player.isSneaking()
            && client.player.getMainHandStack().getItem() instanceof BuildToolItem
            && !com.terminaldetector.drmd.client.portal.OffscreenWorldView.busy();
    }
    private static void tick(MinecraftClient client) {
        current=null;snapshotWorld=client.world;snapshotPlayer=client.player;
        if(!visible(client) || !ScaffoldClientState.INSTANCE.modeKnown())return;
        if(!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK)return;
        current=BuildToolItem.propose(client.world,client.player,client.player.getMainHandStack(),hit.getBlockPos(),hit.getSide(),
            ScaffoldClientState.INSTANCE.constructionMode());
    }
    private static void render(WorldRenderContext context) {
        var client=MinecraftClient.getInstance();var plan=current;
        if(plan==null || snapshotWorld!=client.world || snapshotPlayer!=client.player || !visible(client) || context.matrixStack()==null)return;
        Vec3d camera=context.camera().getPos();var matrix=context.matrixStack().peek().getPositionMatrix();
        boolean ready=plan.plan().ready();float red=ready?.2f:1f,green=ready?1f:.25f;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            var buffer=Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
            if(plan.plan().cells().isEmpty()) {
                var pos=plan.anchor();ConstructScaffoldRenderer.wireBox(buffer,matrix,(float)(pos.getX()-camera.x),(float)(pos.getY()-camera.y),(float)(pos.getZ()-camera.z),red,green,.25f,.85f);
            } else for(var cell:plan.plan().cells()) {
                var pos=cell.position();ConstructScaffoldRenderer.wireBox(buffer,matrix,(float)(pos.getX()-camera.x),(float)(pos.getY()-camera.y),(float)(pos.getZ()-camera.z),red,green,.25f,.85f);
            }
            Vec3d origin=plan.anchor().toCenterPos().subtract(camera);
            Vec3d normal=plan.mode()==BuildMode.SURFACE?Vec3d.of(plan.face().getVector()):AdaptivePlacement.localUp(client.player);
            normal=normal.normalize();Vec3d right=AdaptivePlacement.aim(client.player).crossProduct(normal);
            if(right.lengthSquared()<1e-8)right=com.terminaldetector.drmd.flight.ShipAttitude.levelRightOf(normal);
            right=right.normalize();Vec3d forward=normal.crossProduct(right).normalize();
            axis(buffer,matrix,origin,right,1,.3f,.3f);axis(buffer,matrix,origin,forward,.3f,1,.3f);axis(buffer,matrix,origin,normal,.3f,.6f,1);
            var built=buffer.endNullable();if(built!=null)BufferRenderer.drawWithGlobalProgram(built);
        } finally {RenderSystem.depthMask(true);RenderSystem.disableBlend();}
    }
    private static void axis(BufferBuilder buffer,org.joml.Matrix4f matrix,Vec3d origin,Vec3d direction,float r,float g,float b) {
        Vec3d end=origin.add(direction.multiply(.8));
        ConstructScaffoldRenderer.line(buffer,matrix,(float)origin.x,(float)origin.y,(float)origin.z,(float)end.x,(float)end.y,(float)end.z,r,g,b,.9f);
    }
    public static void caption(DrawContext context) {
        var client=MinecraftClient.getInstance();var plan=current;
        if(plan==null || snapshotWorld!=client.world || snapshotPlayer!=client.player || !visible(client))return;
        Text mode=Text.translatable("message.drmd.build_mode."+plan.mode().name().toLowerCase(java.util.Locale.ROOT));
        Text material=plan.material()==null?Text.translatable("message.drmd.build_reason.no_material"):Text.translatable(plan.material().getTranslationKey());
        String first=client.textRenderer.trimToWidth(Text.translatable("message.drmd.build_preview",mode,material).getString(),client.getWindow().getScaledWidth()-24);
        int x=client.getWindow().getScaledWidth()/2,y=Math.min(client.getWindow().getScaledHeight()/2+26,client.getWindow().getScaledHeight()-62);
        context.drawCenteredTextWithShadow(client.textRenderer,first,x,y,0xdde8dd);
        Text status=plan.plan().ready()?Text.translatable("message.drmd.build_preview.ready",plan.plan().cells().size()):
            Text.translatable("message.drmd.build_reason."+plan.plan().reason().name().toLowerCase(java.util.Locale.ROOT));
        String second=client.textRenderer.trimToWidth(status.getString(),client.getWindow().getScaledWidth()-24);
        context.drawCenteredTextWithShadow(client.textRenderer,second,x,y+10,plan.plan().ready()?0x55e088:0xff7755);
    }
}
