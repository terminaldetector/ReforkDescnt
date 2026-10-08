package com.terminaldetector.drmd.client.render;

import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.physics.ragdoll.RagdollRig;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

import java.util.List;

/** Draws every authoritative ragdoll segment as a separately transformed low-poly wreck part. */
public final class RagdollRenderer extends EntityRenderer<RagdollEntity> {
	private static final Identifier WHITE = Identifier.ofVanilla("textures/misc/white.png");

	public RagdollRenderer(EntityRendererFactory.Context context) {
		super(context);
		shadowRadius = .55f;
	}

	@Override
	public void render(RagdollEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		List<RagdollEntity.RenderPart> previous = entity.previousRenderParts();
		List<RagdollEntity.RenderPart> current = entity.currentRenderParts();
		List<RagdollRig.Segment> segments = entity.rig().segments();
		int count = Math.min(segments.size(), Math.min(previous.size(), current.size()));
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntitySolid(WHITE));

		for (int i = 0; i < count; i++) {
			RagdollEntity.RenderPart a = previous.get(i);
			RagdollEntity.RenderPart b = current.get(i);
			RagdollRig.Segment segment = segments.get(i);
			matrices.push();
			matrices.translate(
					MathHelper.lerp(tickDelta, a.position().x(), b.position().x()),
					MathHelper.lerp(tickDelta, a.position().y(), b.position().y()),
					MathHelper.lerp(tickDelta, a.position().z(), b.position().z()));
			Quaternionf rotation = new Quaternionf(
					(float) a.rotation().x(), (float) a.rotation().y(),
					(float) a.rotation().z(), (float) a.rotation().w());
			rotation.slerp(new Quaternionf(
					(float) b.rotation().x(), (float) b.rotation().y(),
					(float) b.rotation().z(), (float) b.rotation().w()), tickDelta);
			matrices.multiply(rotation);

			int colour = i == 0 ? 0xFF33434B : (i % 2 == 0 ? 0xFF9B382E : 0xFF6F2B2B);
			drawBox(vertices, matrices.peek(),
					(float) segment.halfExtents().x(), (float) segment.halfExtents().y(),
					(float) segment.halfExtents().z(), colour, light);
			matrices.pop();
		}
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	private static void drawBox(VertexConsumer vertices, MatrixStack.Entry entry,
			float hx, float hy, float hz, int argb, int light) {
		float a = ((argb >>> 24) & 255) / 255f;
		float r = ((argb >>> 16) & 255) / 255f;
		float g = ((argb >>> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		float x0 = -hx, y0 = -hy, z0 = -hz, x1 = hx, y1 = hy, z1 = hz;
		quad(vertices, entry, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, r,g,b,a, 0,-1,0, light);
		quad(vertices, entry, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, r,g,b,a, 0,1,0, light);
		quad(vertices, entry, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, r,g,b,a, 0,0,-1, light);
		quad(vertices, entry, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, r,g,b,a, 0,0,1, light);
		quad(vertices, entry, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, r,g,b,a, -1,0,0, light);
		quad(vertices, entry, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, r,g,b,a, 1,0,0, light);
	}

	private static void quad(VertexConsumer vertices, MatrixStack.Entry entry,
			float x0,float y0,float z0, float x1,float y1,float z1,
			float x2,float y2,float z2, float x3,float y3,float z3,
			float r,float g,float b,float a, float nx,float ny,float nz, int light) {
		var matrix = entry.getPositionMatrix();
		vertex(vertices,matrix,x0,y0,z0,r,g,b,a,0,0,nx,ny,nz,light);
		vertex(vertices,matrix,x1,y1,z1,r,g,b,a,1,0,nx,ny,nz,light);
		vertex(vertices,matrix,x2,y2,z2,r,g,b,a,1,1,nx,ny,nz,light);
		vertex(vertices,matrix,x3,y3,z3,r,g,b,a,0,1,nx,ny,nz,light);
		// Both winding orders keep the tiny parts visible under render pipelines with culling changes.
		vertex(vertices,matrix,x0,y0,z0,r,g,b,a,0,0,nx,ny,nz,light);
		vertex(vertices,matrix,x3,y3,z3,r,g,b,a,0,1,nx,ny,nz,light);
		vertex(vertices,matrix,x2,y2,z2,r,g,b,a,1,1,nx,ny,nz,light);
		vertex(vertices,matrix,x1,y1,z1,r,g,b,a,1,0,nx,ny,nz,light);
	}

	private static void vertex(VertexConsumer vertices, org.joml.Matrix4f matrix,
			float x,float y,float z, float r,float g,float b,float a, float u,float v,
			float nx,float ny,float nz, int light) {
		vertices.vertex(matrix,x,y,z).color(r,g,b,a).texture(u,v).overlay(0).light(light).normal(nx,ny,nz);
	}

	@Override
	public Identifier getTexture(RagdollEntity entity) {
		return WHITE;
	}
}
