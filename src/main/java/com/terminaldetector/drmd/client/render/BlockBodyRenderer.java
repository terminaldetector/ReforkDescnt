package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

public final class BlockBodyRenderer extends EntityRenderer<BlockBodyEntity> {
    public BlockBodyRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public void render(BlockBodyEntity body, float yaw, float delta, MatrixStack matrices,
            VertexConsumerProvider consumers, int light) {
        matrices.push();
        var predicted=com.terminaldetector.drmd.client.flight.BoardingClient.bodyView(body,delta);
        if(predicted!=null) {
            var actual=body.getLerpedPos(delta);var position=predicted.position();
            matrices.translate(position.x()-actual.x,position.y()-actual.y,position.z()-actual.z);
        }
        var pose = predicted==null?body.renderRotation(delta):predicted.rotation();
        Quaternionf q = new Quaternionf((float)pose.x(),(float)pose.y(),(float)pose.z(),(float)pose.w());
        matrices.multiply(q);
        var centre = body.localCentre();
        matrices.translate(-centre.x(), -centre.y(), -centre.z());
        for (var cell : body.cells()) {
            matrices.push(); matrices.translate(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ());
            if(body.surface(cell)!=null) SmoothBodySurface.render(body,cell,matrices,consumers,light);
            else MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(
                cell.state(), matrices, consumers, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
        matrices.pop();
        super.render(body, yaw, delta, matrices, consumers, light);
    }
    @Override public Identifier getTexture(BlockBodyEntity body) {
        return Identifier.ofVanilla("textures/atlas/blocks.png");
    }
}
