package com.terminaldetector.drmd.client.render.terrain;

import com.terminaldetector.drmd.world.geometry.HybridMesh;
import com.terminaldetector.drmd.world.geometry.HybridTerrain;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Chunk-batched damage/organic geometry. No per-frame BER or live block-entity reads. */
public final class HybridTerrainModel extends ForwardingBakedModel {
    private static final Identifier MODEL = Identifier.of("drmd", "block/carved");
    private static final Direction[] DIRECTIONS={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private static final int[] QUAD={0,1,2,3}, TRIANGLE_A={0,1,2,2}, TRIANGLE_B={0,2,3,3};
    private HybridTerrainModel(BakedModel delegate) { super(delegate); }

    public static void register() {
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model,context) ->
            (MODEL.equals(context.resourceId()) || context.resourceId()!=null && context.resourceId().getNamespace().equals("drmd") && context.resourceId().getPath().startsWith("block/organic_")) && model!=null ? new HybridTerrainModel(model) : model));
    }
    @Override public boolean isVanillaAdapter() { return false; }
    @Override public boolean isBuiltin() { return false; }
    @Override public boolean useAmbientOcclusion() { return true; }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos,
            Supplier<Random> random, RenderContext context) {
        Object attachment=world.getBlockEntityRenderData(pos);
        HybridTerrain.Data data=state.getBlock() instanceof com.terminaldetector.drmd.world.geometry.OrganicBlock organic ?
            new HybridTerrain.Data(organic.source(),MicroGrid.FULL,true) : attachment instanceof HybridTerrain.Data d ? d :
            new HybridTerrain.Data(Blocks.STONE.getDefaultState(),MicroGrid.FULL,false);
        if (data.mask()==0) return;
        MinecraftClient client=MinecraftClient.getInstance();
        BakedModel source=client.getBlockRenderManager().getModel(data.source());
        var renderer=RendererAccess.INSTANCE.getRenderer();
        if (renderer==null) throw new IllegalStateException("DRMD hybrid terrain requires Fabric Renderer API support");
        RenderMaterial material=renderer.materialFinder()
            .blendMode(BlendMode.fromRenderLayer(RenderLayers.getBlockLayer(data.source())))
            .disableColorIndex(true).find();
        List<List<BakedQuad>> templates=new ArrayList<>(6);
        long seed=data.source().getRenderingSeed(pos);
        List<BakedQuad> unculled=source.getQuads(data.source(),null,Random.create(seed));
        for (Direction direction:DIRECTIONS) {
            var quads=new ArrayList<>(source.getQuads(data.source(),direction,Random.create(seed)));
            for (var q:unculled) if (q.getFace()==direction) quads.add(q);
            templates.add(quads);
        }
        QuadEmitter emitter=context.getEmitter();
        for (var face:new HybridMesh(HybridTerrain.snapshot(world,pos,data)).faces()) {
            var quads=templates.get(face.direction());
            if (quads.isEmpty()) {
                if (data.organic()) {
                    emit(emitter,face,TRIANGLE_A,null,material,-1,source);
                    emit(emitter,face,TRIANGLE_B,null,material,-1,source);
                } else emit(emitter,face,QUAD,null,material,-1,source);
            } else for (var template:quads) {
                int colour=template.hasColor() ? client.getBlockColors().getColor(data.source(),world,pos,template.getColorIndex()) : -1;
                if (data.organic()) {
                    emit(emitter,face,TRIANGLE_A,template,material,colour,source);
                    emit(emitter,face,TRIANGLE_B,template,material,colour,source);
                } else emit(emitter,face,QUAD,template,material,colour,source);
            }
        }
    }

    private static void emit(QuadEmitter out, HybridMesh.Face face, int[] order, BakedQuad template,
            RenderMaterial material, int colour, BakedModel source) {
        var a=face.vertices().get(order[0]).position();
        var b=face.vertices().get(order[1]).position();
        var c=face.vertices().get(order[2]).position();
        double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z();
        double vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if (length<1e-10) return;
        out.material(material).cullFace(null).nominalFace(DIRECTIONS[face.direction()]).colorIndex(-1);
        for (int i=0;i<4;i++) {
            var vertex=face.vertices().get(order[i]); var p=vertex.position(); var t=vertex.texturePosition();
            out.pos(i,(float)p.x(),(float)p.y(),(float)p.z());
            out.normal(i,(float)(nx/length),(float)(ny/length),(float)(nz/length));
            out.color(i,0xff000000 | colour);
            if (template!=null) {
                // Original face UVs preserve texture phase across neighbouring fragments.
                int[] packed=template.getVertexData(); int stride=packed.length/4;
                double s=face.direction()<4?t.x():t.z(),v=face.direction()<2?t.z():t.y();
                double texU=0,texV=0;
                for (int j=0;j<4;j++) {
                    double js=Float.intBitsToFloat(packed[j*stride+(face.direction()<4?0:2)]);
                    double jv=Float.intBitsToFloat(packed[j*stride+(face.direction()<2?2:1)]);
                    double weight=(js>.5?s:1-s)*(jv>.5?v:1-v);
                    texU+=weight*Float.intBitsToFloat(packed[j*stride+4]);
                    texV+=weight*Float.intBitsToFloat(packed[j*stride+5]);
                }
                out.uv(i,(float)texU,(float)texV);
            } else {
                var sprite=source.getParticleSprite();
                double u=face.direction()<4?t.x():t.z(),v=face.direction()<2?t.z():1-t.y();
                out.uv(i,(float)(sprite.getMinU()+u*(sprite.getMaxU()-sprite.getMinU())),
                    (float)(sprite.getMinV()+v*(sprite.getMaxV()-sprite.getMinV())));
            }
        }
        out.emit();
    }
}
