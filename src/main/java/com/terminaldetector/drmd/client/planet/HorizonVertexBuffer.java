package com.terminaldetector.drmd.client.planet;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * Keeps the horizon field on the GPU between rebuilds instead of re-sending it every frame.
 *
 * <p><b>Why.</b> The field is a rigid body between rebuilds — {@link PlanetSurfaceMesh} is cached and
 * only rebuilt when the ship wanders off it — but the renderer was walking all of it into a
 * {@code BufferBuilder} on every single frame, transforming four vertices per quad by the model matrix
 * on the CPU and uploading the lot. At the current budget that is 144,000 vertices a frame for geometry
 * that did not change, and it is the ceiling on how fine the grid can be: cell count grows as the
 * square of the refinement, so every step toward a VOXY-like field multiplies work that was already
 * being repeated for nothing.
 *
 * <p>Uploaded once per mesh, drawn with two matrices. The parallax the ship has flown since the build
 * is the model-view matrix rather than something baked into the vertices, which is what makes the
 * upload survive movement at all.
 *
 * <p><b>The fade is a uniform, not a rebuild.</b> The map fades in across the climb, so its alpha
 * changes every frame — baking it into vertex colours would defeat the whole point. Each quad's own
 * alpha is baked; the climb's is applied with {@code RenderSystem.setShaderColor}, which vanilla's
 * {@code position_color} shader multiplies in as {@code ColorModulator}.
 *
 * <p>Everything here runs on the render thread, called from inside the world-render callback, which is
 * what makes plain statics safe. The buffer outlives a world change deliberately: it is re-uploaded
 * from the first mesh built in the new world, and a GL buffer is not worth tearing down and recreating
 * for that.
 */
public final class HorizonVertexBuffer {
	private HorizonVertexBuffer() {}

	private static VertexBuffer buffer;
	/** Identity of the mesh currently on the GPU — the cache key, since a rebuild makes a new object. */
	private static PlanetSurfaceMesh uploaded;
	private static boolean hasGeometry;

	/**
	 * Make sure {@code mesh} is the one on the GPU, uploading it if it is not.
	 *
	 * @return false when there is nothing to draw — every quad was fully transparent, or the mesh is
	 *         empty. The caller should skip the draw entirely rather than bind an empty buffer.
	 */
	public static boolean prepare(PlanetSurfaceMesh mesh) {
		if (mesh == uploaded && buffer != null && !buffer.isClosed()) return hasGeometry;

		if (buffer == null || buffer.isClosed()) {
			buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
		}

		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float[] positions = mesh.positions;
		int[] colours = mesh.colours;
		for (int q = 0; q < mesh.quads; q++) {
			int argb = colours[q];
			float a = ((argb >>> 24) & 0xFF) / 255f;
			// The quad's own alpha only. The climb's fade is a uniform, so a quad invisible now but
			// visible later is still not worth storing: this one is transparent at every altitude.
			if (a <= 0.01f) continue;
			float r = ((argb >> 16) & 0xFF) / 255f;
			float g = ((argb >> 8) & 0xFF) / 255f;
			float b = (argb & 0xFF) / 255f;
			int i = q * 12;
			// No matrix: vertices stay in the mesh's own space and the model-view carries the parallax.
			builder.vertex(positions[i], positions[i + 1], positions[i + 2]).color(r, g, b, a);
			builder.vertex(positions[i + 3], positions[i + 4], positions[i + 5]).color(r, g, b, a);
			builder.vertex(positions[i + 6], positions[i + 7], positions[i + 8]).color(r, g, b, a);
			builder.vertex(positions[i + 9], positions[i + 10], positions[i + 11]).color(r, g, b, a);
		}

		BuiltBuffer built = builder.endNullable();
		uploaded = mesh;
		hasGeometry = built != null;
		if (!hasGeometry) return false;

		buffer.bind();
		buffer.upload(built); // takes ownership of built, including closing it
		return true;
	}

	/**
	 * Draw what {@link #prepare} put on the GPU.
	 *
	 * @param modelView the field's placement this frame — the world render's own matrix with the
	 *                  parallax since the build folded in. Passed here rather than baked into the
	 *                  vertices, which is the whole reason the upload can be reused.
	 */
	public static void draw(Matrix4f modelView) {
		buffer.bind();
		buffer.draw(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
		// Through the instance rather than the type on purpose: this is static in vanilla, and calling
		// a static method through a reference is legal, so this compiles either way. Naming the type
		// would not, if it ever stops being static.
		buffer.unbind();
	}
}
