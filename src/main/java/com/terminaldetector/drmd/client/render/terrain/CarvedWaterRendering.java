package com.terminaldetector.drmd.client.render.terrain;
import com.terminaldetector.drmd.world.geometry.*;
import com.terminaldetector.drmd.world.micro.CarvedBlock;
import net.fabricmc.fabric.api.client.render.fluid.v1.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.fluid.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

/** Fabric's existing fluid-material route supports Vanilla/Sodium; only carved water gains clipped geometry. */
public final class CarvedWaterRendering {
    private static boolean registered;
    private CarvedWaterRendering(){}
    public static void register(){
        if(registered)return;registered=true;
        var registry=FluidRenderHandlerRegistry.INSTANCE;
        for(var fluid:new Fluid[]{Fluids.WATER,Fluids.FLOWING_WATER}){
            var original=registry.get(fluid);if(original!=null)registry.register(fluid,new Handler(original));
        }
    }
    private record Handler(FluidRenderHandler original) implements FluidRenderHandler {
        public Sprite[] getFluidSprites(BlockRenderView world,BlockPos pos,FluidState state){return original.getFluidSprites(world,pos,state);}
        public int getFluidColor(BlockRenderView world,BlockPos pos,FluidState state){return original.getFluidColor(world,pos,state);}
        public void reloadTextures(SpriteAtlasTexture atlas){original.reloadTextures(atlas);}
        public void renderFluid(BlockPos pos,BlockRenderView world,VertexConsumer vertices,BlockState state,FluidState fluid){
            if(!(state.getBlock()instanceof CarvedBlock)||!state.get(Properties.WATERLOGGED)){original.renderFluid(pos,world,vertices,state,fluid);return;}
            // Chunk workers read only the immutable attachment, never a live block entity.
            if(!(world.getBlockEntityRenderData(pos)instanceof HybridTerrain.Data data))return;
            var volume=MicroWaterVolume.of(HybridTerrain.snapshot(world,pos,data));var sprites=getFluidSprites(world,pos,fluid);
            if(sprites.length==0||sprites[0]==null)return;var sprite=sprites[0];int colour=getFluidColor(world,pos,fluid),light=WorldRenderer.getLightmapCoordinates(world,pos);
            float r=((colour>>>16)&255)/255f,g=((colour>>>8)&255)/255f,b=(colour&255)/255f;
            double height=fluid.getHeight(world,pos);
            for(var face:volume.faces(height,(x,y,z)->neighbourWater(world,pos,x,y,z))){
                int dir=face.direction();float shade=dir==1?1:dir==0?.5f:dir<4?.8f:.6f;
                float nx=dir==4?-1:dir==5?1:0,ny=dir==0?-1:dir==1?1:0,nz=dir==2?-1:dir==3?1:0;
                for(var p:face.points()){
                    float u=(float)(dir<4?p.x():p.z()),v=(float)(dir<2?p.z():1-p.y());
                    vertices.vertex((float)((pos.getX()&15)+p.x()),(float)((pos.getY()&15)+p.y()),(float)((pos.getZ()&15)+p.z()))
                        .color(r*shade,g*shade,b*shade,1).texture(sprite.getFrameU(u),sprite.getFrameV(v)).light(light).normal(nx,ny,nz);
                }
            }
        }
    }
    private static boolean neighbourWater(BlockRenderView world,BlockPos origin,int x,int y,int z){
        var pos=origin.add(Math.floorDiv(x,16),Math.floorDiv(y,16),Math.floorDiv(z,16));var state=world.getBlockState(pos);var fluid=state.getFluidState();
        int a=Math.floorMod(x,16),b=Math.floorMod(y,16),c=Math.floorMod(z,16);
        if(!fluid.isIn(FluidTags.WATER)||b/16d>=fluid.getHeight(world,pos))return false;
        if(!(state.getBlock()instanceof CarvedBlock))return true;
        return world.getBlockEntityRenderData(pos)instanceof HybridTerrain.Data data
            &&MicroWaterVolume.of(HybridTerrain.snapshot(world,pos,data)).empty(a,b,c);
    }
}
