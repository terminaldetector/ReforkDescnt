package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.world.micro.CarvedBlockEntity;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Draws a block that has had pieces taken out of it.
 *
 * <p>There is no model for this — the block looks like whatever it used to be, minus what is gone,
 * and that is a different shape for every mask. So the source block's own model is drawn once per
 * box of the merged shape, squashed into that box.
 *
 * <p>Per <em>box</em>, not per cell, is what makes it affordable: the merge in
 * {@link MicroGrid#boxes} turns the usual shapes into two or three boxes where the cells would have
 * been dozens, and the same merge is already what the collision shape is built from — so what is
 * drawn and what you bump into come from one description rather than two that can disagree.
 *
 * <p>The stretch is a real approximation: a texture pulled over a box that is not a cube is not what
 * the block would look like if you sawed it. At the sizes involved — quarter-block chunks of wall,
 * usually seen while something is shooting at them — it reads as broken masonry, which is the point.
 */
public class CarvedBlockRenderer implements BlockEntityRenderer<CarvedBlockEntity> {
	private final MinecraftClient client = MinecraftClient.getInstance();

	public CarvedBlockRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(CarvedBlockEntity entity, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light, int overlay) {
		long mask = entity.mask();
		if (MicroGrid.isEmpty(mask)) return;
		var source = entity.source();
		if (source.isAir()) return;
		if (client.world == null) return;

		BakedModel model = client.getBlockRenderManager().getModel(source);
		var layer = RenderLayers.getMovingBlockLayer(source);
		var consumer = vertexConsumers.getBuffer(layer);
		List<MicroGrid.Box> boxes = MicroGrid.boxes(mask);

		for (MicroGrid.Box box : boxes) {
			matrices.push();
			matrices.translate(box.minX(), box.minY(), box.minZ());
			matrices.scale(
					(float) (box.maxX() - box.minX()),
					(float) (box.maxY() - box.minY()),
					(float) (box.maxZ() - box.minZ()));
			client.getBlockRenderManager().getModelRenderer().render(
					entity.getWorld(), model, source, entity.getPos(), matrices, consumer,
					false, Random.create(), source.getRenderingSeed(entity.getPos()), overlay);
			matrices.pop();
		}
	}

	/**
	 * Only worth drawing properly up close.
	 *
	 * <p>A carved block a hundred metres away is a few pixels, and the boxes it would cost are the
	 * same however far away it is. Past this it falls back to nothing drawn, which reads as the hole
	 * it is rather than as a block that should not be there.
	 */
	@Override
	public int getRenderDistance() {
		return 48;
	}
}
