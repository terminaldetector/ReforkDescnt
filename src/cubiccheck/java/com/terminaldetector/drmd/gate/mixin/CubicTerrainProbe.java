package com.terminaldetector.drmd.gate.mixin;

import com.terminaldetector.drmd.client.render.terrain.HybridTerrainModel;
import com.terminaldetector.drmd.world.geometry.HybridMesh;
import com.terminaldetector.drmd.gate.CubicTerrainCheck;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=HybridTerrainModel.class,remap=false)
public abstract class CubicTerrainProbe {
    @Inject(method="emitBlockQuads",at=@At("HEAD"),remap=false)
    private void begin(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context,CallbackInfo ci) {
        CubicTerrainCheck.begin(state,pos);
    }
    @Inject(method="emitBlockQuads",at=@At("RETURN"),remap=false)
    private void end(CallbackInfo ci){CubicTerrainCheck.end();}
    @Inject(method="emit",at=@At("HEAD"),remap=false)
    private static void emitted(QuadEmitter out,HybridMesh.Face face,int[] order,BakedQuad template,
            RenderMaterial material,int colour,BakedModel source,CallbackInfo ci) {
        CubicTerrainCheck.face(face);
    }
}
