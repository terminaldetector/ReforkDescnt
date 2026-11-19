package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.presentation.PresentationProfile;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.geometry.*;
import com.terminaldetector.drmd.world.micro.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** One real cold cavity across XYZ section boundaries, then genuine flowing water in retained solid. */
public final class NativeHybridCavityCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.hybridCavity");
    private static final BlockPos MIN=new BlockPos(10,2157,12),MAX=new BlockPos(21,2164,22),SOURCE=new BlockPos(14,2159,19);
    private static final Vec3d START=new Vec3d(10.5,2163,23.5),LOOK=new Vec3d(16,2160,17);
    private static final long FLOOR=MicroGrid.build((x,y,z)->y==0);
    private static final Map<BlockPos,Long> MASKS=new LinkedHashMap<>();
    private static volatile Map<BlockPos,List<HybridMesh.Point>> expected=Map.of();
    private static final Set<BlockPos> SEEN=ConcurrentHashMap.newKeySet();
    private static final AtomicInteger FACES=new AtomicInteger(),CURVED=new AtomicInteger(),WATER_FACES=new AtomicInteger(),WATER_CALLS=new AtomicInteger();
    private record Water(BlockPos pos,int faces){}
    private static final ThreadLocal<BlockPos> BLOCK=new ThreadLocal<>();
    private static final ThreadLocal<Water> WATER=new ThreadLocal<>();
    private static volatile UUID viewer;private static volatile boolean staged,authored,viewerReady,waterRequest,waterReady,done;
    private static volatile String failure;private static volatile int sourceTick=-1,flowTick=-1;private static volatile Map<String,Object> serverProof=Map.of();
    private static long boot;private static int ticks,frames;private static volatile int phase;private static volatile boolean armed;
    private static final List<Map<String,Object>> STATES=new ArrayList<>();
    private NativeHybridCavityCheck(){}
    public static void register(){boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(NativeHybridCavityCheck::server);ClientTickEvents.END_CLIENT_TICK.register(NativeHybridCavityCheck::tick);}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static boolean wetCell(BlockPos p){return p.equals(new BlockPos(15,2159,19))||p.equals(new BlockPos(16,2159,19));}
    private static long mask(BlockPos p){final int y=p.getY()-2160,z=p.getZ()-16;return MicroGrid.build((x,a,b)->Math.pow(y+(a+.5)*.25,2)+Math.pow(z+(b+.5)*.25,2)>.9*.9);}
    private static List<HybridMesh.Point> vertices(HybridMesh mesh){var out=new ArrayList<HybridMesh.Point>(125);for(int y=0;y<=4;y++)for(int z=0;z<=4;z++)for(int x=0;x<=4;x++)out.add(mesh.vertex(x,y,z));return List.copyOf(out);}
    private static HybridMesh mesh(net.minecraft.world.BlockView w,BlockPos p){return new HybridMesh(HybridTerrain.snapshot(w,p,((CarvedBlockEntity)w.getBlockEntity(p)).getRenderData()));}
    private static double seam(HybridMesh a,HybridMesh b,int axis){double max=0;for(int u=0;u<=4;u++)for(int v=0;v<=4;v++){
        var x=axis==0?a.vertex(4,u,v):axis==1?a.vertex(u,4,v):a.vertex(u,v,4);
        var y=axis==0?b.vertex(0,u,v):axis==1?b.vertex(u,0,v):b.vertex(u,v,0);
        max=Math.max(max,Math.max(Math.abs(x.x()-y.x()-(axis==0?1:0)),Math.max(Math.abs(x.y()-y.y()-(axis==1?1:0)),Math.abs(x.z()-y.z()-(axis==2?1:0)))));
    }return max;}
    private static void server(MinecraftServer s){
        if(!ENABLED||done||viewer==null||failure!=null)return;
        try{var p=s.getPlayerManager().getPlayer(viewer);if(p==null)return;var w=p.getServerWorld();var access=WorldAccess.of(w);
            require(access.backend()==CubicAvailability.Backend.NATIVE_CUBIC,"real native BCC required");
            if(!staged){staged=true;p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.teleport(w,START.x,START.y,START.z,Set.of(),0,0);p.setVelocity(Vec3d.ZERO);return;}
            if(!authored){if(!viewerReady)return;for(var q:BlockPos.iterate(MIN,MAX))if(!access.isVolumeAvailable(D6ChunkPos.ofBlock(q.getX(),q.getY(),q.getZ())))return;
                for(var q:BlockPos.iterate(MIN,MAX))w.setBlockState(q,Blocks.AIR.getDefaultState(),3);
                for(int x=15;x<=16;x++)for(int y=2158;y<=2161;y++)for(int z=14;z<=17;z++){
                    var at=new BlockPos(x,y,z);long bits=mask(at);if(bits==0)continue;w.setBlockState(at,Blocks.IRON_BLOCK.getDefaultState(),3);
                    if(bits!=MicroGrid.FULL){require(CarvedBlock.replace(w,at,Blocks.IRON_BLOCK.getDefaultState(),bits)!=null,"real cold cut retains its source material");MASKS.put(at,bits);}
                }
                for(int x=13;x<=19;x++){w.setBlockState(new BlockPos(x,2158,19),Blocks.IRON_BLOCK.getDefaultState(),3);for(int z:new int[]{18,20})w.setBlockState(new BlockPos(x,2159,z),Blocks.GLASS.getDefaultState(),3);}
                w.setBlockState(new BlockPos(13,2159,19),Blocks.GLASS.getDefaultState(),3);w.setBlockState(new BlockPos(19,2159,19),Blocks.GLASS.getDefaultState(),3);
                for(int x=15;x<=16;x++){var at=new BlockPos(x,2159,19);w.setBlockState(at,Blocks.IRON_BLOCK.getDefaultState(),3);require(CarvedBlock.replace(w,at,Blocks.IRON_BLOCK.getDefaultState(),FLOOR)!=null,"retained flow-channel floor");MASKS.put(at,FLOOR);}
                require(MASKS.size()<=64,"bounded real BE fixture");var shapes=new LinkedHashMap<BlockPos,List<HybridMesh.Point>>();for(var at:MASKS.keySet())shapes.put(at,vertices(mesh(w,at)));expected=Map.copyOf(shapes);
                double sx=seam(mesh(w,new BlockPos(15,2159,15)),mesh(w,new BlockPos(16,2159,15)),0),sy=seam(mesh(w,new BlockPos(15,2159,15)),mesh(w,new BlockPos(15,2160,15)),1),sz=seam(mesh(w,new BlockPos(15,2159,15)),mesh(w,new BlockPos(15,2159,16)),2);
                require(Math.max(sx,Math.max(sy,sz))<=1e-12,"actual authored XYZ section seam vertices agree");
                var clear=new Box(15,2159.8,15.8,17,2160.2,16.2);require(!w.getBlockCollisions(null,clear).iterator().hasNext(),"actual collision has no invisible centre wall");
                require(w.getBlockCollisions(null,new Box(15.2,2159.05,15.05,15.4,2159.2,15.2)).iterator().hasNext(),"retained rim has real collision");
                serverProof=Map.of("nativeBackend",true,"partialCarriers",MASKS.size(),"xSeamError",sx,"ySeamError",sy,"zSeamError",sz,"centreCollisionClear",true,"rimCollisionPresent",true);authored=true;return;
            }
            for(var e:MASKS.entrySet())require(w.getBlockEntity(e.getKey())instanceof CarvedBlockEntity c&&c.mask()==e.getValue()&&c.source().isOf(Blocks.IRON_BLOCK),"original source/mask/BE retained");
            if(waterRequest&&sourceTick<0){w.setBlockState(SOURCE,Blocks.WATER.getDefaultState(),3);sourceTick=s.getTicks();}
            if(sourceTick>=0){boolean flowing=true;for(var at:MASKS.keySet())if(wetCell(at))flowing&=w.getBlockState(at).get(Properties.WATERLOGGED)&&w.getFluidState(at).isIn(FluidTags.WATER)&&!w.getFluidState(at).isStill();
                waterReady=flowing&&w.getFluidState(new BlockPos(17,2159,19)).isIn(FluidTags.WATER);if(waterReady&&flowTick<0)flowTick=s.getTicks();require(s.getTicks()-sourceTick<300||waterReady,"normal water did not flow through both retained carriers and out");}
        }catch(Throwable error){failure=error.toString();}
    }
    private static boolean clientReady(MinecraftClient c){
        if(!authored)return false;for(var e:MASKS.entrySet())if(!(c.world.getBlockEntity(e.getKey())instanceof CarvedBlockEntity be)||be.mask()!=e.getValue()||!be.source().isOf(Blocks.IRON_BLOCK))return false;
        if(phase==1){if(!waterReady)return false;for(var at:MASKS.keySet())if(wetCell(at)&&(!c.world.getFluidState(at).isIn(FluidTags.WATER)||c.world.getFluidState(at).isStill()))return false;}
        return true;
    }
    private static void tick(MinecraftClient c){
        if(!ENABLED||done)return;try{
            if(++ticks>3200||System.nanoTime()-boot>160_000_000_000L)throw new IllegalStateException("finite160s cavity/water admission deadline");
            if(viewer==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.ConfirmScreen p&&p.getTitle().getString().equals("Warning! Low disk space!"))for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("Continue"))b.onPress();
            if(viewer==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(failure!=null)throw new IllegalStateException(failure);if(c.player==null||c.world==null||c.getServer()==null)return;DescentClient.markUserFlightChoice();
            if(viewer==null){viewer=c.player.getUuid();return;}
            viewerReady=c.currentScreen==null&&c.getOverlay()==null&&c.player.getPos().distanceTo(START)<.03&&!DescentClientState.enabled;
            if(!viewerReady||!clientReady(c))return;
            c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.inGameHud.getChatHud().clear(false);c.options.hudHidden=false;DescentConfig.presentationProfile=PresentationProfile.HYBRID_NATIVE;
            Vec3d direction=LOOK.subtract(c.player.getEyePos());c.player.setYaw((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));c.player.setPitch((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
            if(!armed){FACES.set(0);CURVED.set(0);SEEN.clear();WATER_FACES.set(0);WATER_CALLS.set(0);frames=0;armed=true;c.worldRenderer.scheduleBlockRenders(MIN.getX(),MIN.getY(),MIN.getZ(),MAX.getX(),MAX.getY(),MAX.getZ());}
        }catch(Throwable error){finish("FAIL",error.toString());}
    }
    public static void begin(net.minecraft.world.BlockRenderView world,BlockState state,BlockPos pos){
        if(!ENABLED)return;BLOCK.remove();if(!armed||done||!expected.containsKey(pos))return;
        // Render-region copies taken before the admitted BE packets are not the admitted fixture.
        for(var q:BlockPos.iterate(pos.add(-1,-1,-1),pos.add(1,1,1))){Long mask=MASKS.get(q);if(mask!=null&&(!(world.getBlockEntityRenderData(q)instanceof HybridTerrain.Data data)||data.mask()!=mask))return;}
        BLOCK.set(pos.toImmutable());
    }
    public static void end(){if(ENABLED)BLOCK.remove();}
    /** Called only after normal HybridTerrainModel.emit RETURN; nondegenerate path has submitted out.emit(). */
    public static void emitted(HybridMesh.Face face,int[] order){if(!ENABLED)return;var at=BLOCK.get();if(at==null||!armed||done)return;var points=expected.get(at);if(points==null)return;
        var a=face.vertices().get(order[0]).position();var b=face.vertices().get(order[1]).position();var c=face.vertices().get(order[2]).position();double nx=(b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y()),ny=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z()),nz=(b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());if(nx*nx+ny*ny+nz*nz<1e-20)return;
        for(var vertex:face.vertices()){var p=vertex.position();boolean found=points.stream().anyMatch(v->Math.abs(v.x()-p.x())<1e-9&&Math.abs(v.y()-p.y())<1e-9&&Math.abs(v.z()-p.z())<1e-9);if(!found)failure="actual chunk emitted a vertex outside the authoritative collision mesh at "+at;if(Math.abs(p.x()*4-Math.rint(p.x()*4))>.01||Math.abs(p.y()*4-Math.rint(p.y()*4))>.01||Math.abs(p.z()*4-Math.rint(p.z()*4))>.01)CURVED.incrementAndGet();}FACES.incrementAndGet();SEEN.add(at);
    }
    public static void waterBegin(BlockPos p,BlockState state,FluidState fluid){if(!ENABLED)return;if(armed&&phase==1&&!done&&wetCell(p)&&state.getBlock()instanceof CarvedBlock&&state.get(Properties.WATERLOGGED)&&fluid.isIn(FluidTags.WATER)&&!fluid.isStill())WATER.set(new Water(p.toImmutable(),0));else WATER.remove();}
    public static void waterFaces(int count){if(!ENABLED)return;var w=WATER.get();if(w!=null){if(count<0||count>32768)failure="unbounded fluid geometry receipt";else WATER.set(new Water(w.pos(),count));}}
    public static void waterEnd(){if(!ENABLED)return;var w=WATER.get();if(w!=null&&w.faces()>0){WATER_CALLS.incrementAndGet();WATER_FACES.addAndGet(w.faces());}WATER.remove();}
    public static void rendered(){if(!ENABLED||done||!armed)return;try{if(failure!=null)throw new IllegalStateException(failure);var c=MinecraftClient.getInstance();if(c.currentScreen!=null||c.getOverlay()!=null)return;
        int error=GL11.glGetError();require(error==GL11.GL_NO_ERROR,"actual completed-frame GL error "+error);if(++frames<30||FACES.get()==0||CURVED.get()==0)return;
        for(int x=15;x<=16;x++)for(int y=2159;y<=2160;y++)for(int z=15;z<=16;z++)if(!SEEN.contains(new BlockPos(x,y,z)))return;
        if(phase==1&&(WATER_FACES.get()==0||WATER_CALLS.get()==0))return;
        var folder=c.runDirectory.toPath().resolve("hybrid-cavity-frames");Files.createDirectories(folder);String file=phase==0?"dry-xyz-cavity.png":"real-retained-flow.png";
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},saved=new int[4];for(int i=0;i<4;i++){saved[i]=GL11.glGetInteger(names[i]);GL11.glPixelStorei(names[i],i==0?4:0);}try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){require(image.getWidth()>=640&&image.getHeight()>=360,"actual full-frame screenshot");image.writeTo(folder.resolve(file));}finally{com.mojang.blaze3d.platform.GlStateManager._activeTexture(active);com.mojang.blaze3d.systems.RenderSystem.bindTexture(texture);for(int i=0;i<4;i++)GL11.glPixelStorei(names[i],saved[i]);}
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"capture restores GL state without error");STATES.add(Map.of("phase",phase,"frame",file,"actualHybridFaces",FACES.get(),"curvedVertices",CURVED.get(),"actualPartialCarriersEmitted",SEEN.size(),"actualFluidHandlerCalls",WATER_CALLS.get(),"actualClippedFluidFaces",WATER_FACES.get()));
        if(phase==0){phase=1;armed=false;waterRequest=true;}else finish("PASS","actual XYZ cold-cavity mesh/collision and retained flowing-water client witness; no universal navigation/fluid or pixelbeauty claim");
    }catch(Throwable error){finish("FAIL",error.toString());}}
    private static void finish(String status,String reason){if(done)return;done=true;var c=MinecraftClient.getInstance();try{var data=new LinkedHashMap<String,Object>();data.put("status",status);data.put("reason",reason);data.put("phase",phase);data.put("states",STATES);data.put("server",serverProof);data.put("sourceWaterTick",sourceTick);data.put("firstActualFlowTick",flowTick);data.put("ticks",ticks);data.put("frames",frames);data.put("expectedCarriers",expected.size());data.put("actualEmittedCarriers",SEEN.size());data.put("sodiumLoaded",net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium"));data.put("schema",1);Files.writeString(c.runDirectory.toPath().resolve("hybrid-cavity-outcome.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception error){error.printStackTrace();}System.out.println("NATIVE_HYBRID_CAVITY "+status+" "+reason);c.scheduleStop();}
}
