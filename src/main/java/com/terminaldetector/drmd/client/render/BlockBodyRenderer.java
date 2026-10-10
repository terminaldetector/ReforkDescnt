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
        var before = body.previousRotation(); var after = body.rotation();
        Quaternionf q = new Quaternionf((float)before.x(),(float)before.y(),(float)before.z(),(float)before.w());
        q.slerp(new Quaternionf((float)after.x(),(float)after.y(),(float)after.z(),(float)after.w()), delta);
        matrices.multiply(q);
        var centre = body.localCentre();
        matrices.translate(-centre.x(), -centre.y(), -centre.z());
        for (var cell : body.cells()) {
            matrices.push(); matrices.translate(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ());
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(
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
