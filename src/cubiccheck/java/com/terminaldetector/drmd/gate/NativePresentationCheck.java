package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.presentation.*;
import com.terminaldetector.drmd.client.render.WeaponViewRenderer;
import com.terminaldetector.drmd.client.portal.PortalViewDiagnostics;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.mixin.client.WorldViewTargetsAccess;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.access.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;

/** Actual world raster/profile changes, with a one-physical-pixel HUD witness and real viewmodels.
 * The fixture edits only its disposable copied world and never runs in a normal game directory. */
public final class NativePresentationCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.presentation");
    private static final BlockPos BASE=new BlockPos(8,2200,8);
    private static final String[] SCENES={"native-baseline","ps1-360","native-restored","debug-wireframe","ps1-180-resized","native-window-restored"};
    private static final PresentationProfile[] PROFILES={PresentationProfile.HYBRID_NATIVE,PresentationProfile.PS1_HYBRID,PresentationProfile.HYBRID_NATIVE,PresentationProfile.RENDER_DEBUG,PresentationProfile.PS1_HYBRID,PresentationProfile.HYBRID_NATIVE};
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static volatile ServerPlayerEntity player;
    private static volatile boolean located,ready,requestedFixture,done;
    private static boolean requested,witness,originalHybrid,exceptionRestored;
    private static volatile int phase;
    private static volatile int outlineEntity;
    private static int ticks,frames;
    private static int samplePhase=-1;
    private static double worldGrid=-1;
    private static NativeImage beforeOverlays;
    private static long frameEpoch,mirrorEpoch=-1,sampleEpoch=-1;
    private static int mirrorPhase=-1;
    private static List<double[][]> mirrorPolygons=List.of();
    private static List<double[][]> debugWallEdges=List.of();
    private static long debugProjectionEpoch=-1;
    private static boolean debugExceptionRestored;
    private static Map<String,Object> debugEdgeResult=Map.of();
    private static long geometryHash;
    private NativePresentationCheck(){}
    private static void require(boolean value,String why){if(!value){done=true;MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException("PRESENTATION_NATIVE "+why);}}
    public static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(NativePresentationCheck::tick);
        WorldRenderEvents.START.register(context->{
            if(!ready||done||com.terminaldetector.drmd.client.portal.OffscreenWorldView.busy())return;
            if(DescentConfig.presentationProfile==PresentationProfile.RENDER_DEBUG){if(!debugExceptionRestored)checkDebugPostprocessException();}
            else require(ScenePresentation.fillDebugPostprocess()==null,"Native/PS1 never borrow the Debug composite raster guard");
            mirrorEpoch=frameEpoch;mirrorPhase=phase;mirrorPolygons=List.of();
            var c=MinecraftClient.getInstance();var faces=new ArrayList<com.terminaldetector.drmd.client.portal.MirrorScanner.MirrorFace>();
            for(int x=-2;x<=-1;x++)for(int y=0;y<=3;y++){
                var pos=BASE.add(x,y,6);var state=c.world.getBlockState(pos);if(!state.isOf(ModWorldBlocks.MIRROR))return;
                var facing=state.get(net.minecraft.state.property.Properties.FACING);var normal=Vec3d.of(facing.getVector());
                faces.add(new com.terminaldetector.drmd.client.portal.MirrorScanner.MirrorFace(pos,facing,Vec3d.ofCenter(pos).add(normal.multiply(.5)),normal,state.getBlock()));
            }
            // Snapshot the real primary pass before the shared context can be reused by a mirror.
            var matrix=new org.joml.Matrix4f(context.projectionMatrix()).mul(context.positionMatrix());var camera=context.camera().getPos();
            var polygons=new ArrayList<double[][]>();int width=c.getWindow().getFramebufferWidth(),height=c.getWindow().getFramebufferHeight();
            for(var aperture:new com.terminaldetector.drmd.client.portal.MirrorSurface(faces).apertures()){
                var polygon=new double[4][2];boolean front=true;
                for(int i=0;i<4;i++){
                    var p=aperture[i];var clip=new org.joml.Vector4f((float)(p.x()-camera.x),(float)(p.y()-camera.y),(float)(p.z()-camera.z),1).mul(matrix);
                    if(clip.w<=1e-6){front=false;break;}
                    polygon[i][0]=(clip.x/clip.w*.5+.5)*width;polygon[i][1]=(.5-clip.y/clip.w*.5)*height;
                }if(front)polygons.add(polygon);
            }mirrorPolygons=List.copyOf(polygons);
            debugWallEdges=List.of();debugProjectionEpoch=frameEpoch;
            if(DescentConfig.presentationProfile==PresentationProfile.RENDER_DEBUG){
                // Actual exposed checker-wall block edges, above the husk and apart from GUI.
                var edges=new ArrayList<double[][]>();
                for(int y=3;y<=8;y++)for(int x=-7;x<=7;x++)projectWallEdge(edges,matrix,camera,width,height,BASE.getX()+x,BASE.getY()+y,BASE.getX()+x+1,BASE.getY()+y);
                for(int x=-7;x<=8;x++)for(int y=3;y<8;y++)projectWallEdge(edges,matrix,camera,width,height,BASE.getX()+x,BASE.getY()+y,BASE.getX()+x,BASE.getY()+y+1);
                debugWallEdges=List.copyOf(edges);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{if(ready&&!done){require(hash()==geometryHash,"profile toggle preserves the actual blockstore");require(DescentPlayerData.get(player).isEnabled(),"presentation preserves authoritative 6DoF mode");}});
        HudRenderCallback.EVENT.register((draw,counter)->{
            if(!ready||done)return;
            // This explicit test witness must remain one PHYSICAL pixel after GUI scaling.
            float inverse=(float)(1/MinecraftClient.getInstance().getWindow().getScaleFactor());var matrices=draw.getMatrices();matrices.push();matrices.scale(inverse,inverse,1);
            draw.fill(17,20,18,40,0xff00ffff);matrices.pop();witness=true;
        });
    }
    private static boolean loaded(net.minecraft.world.World world){
        var access=WorldAccess.of(world);
        for(int x=-1;x<=1;x++)for(int y=(BASE.getY()-2)>>4;y<=(BASE.getY()+9)>>4;y++)for(int z=-1;z<=1;z++)
            if(!access.isVolumeAvailable(new com.terminaldetector.drmd.d6.D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void fixture(){
        var world=player.getServerWorld();if(!loaded(world)){requestedFixture=false;return;}
        require(CubicAvailability.backend(world)==CubicAvailability.Backend.NATIVE_CUBIC,"actual native cubic backend");
        for(int x=-7;x<=7;x++)for(int y=0;y<=8;y++)world.setBlockState(BASE.add(x,y,8),((x+y)%3==0?Blocks.IRON_BLOCK:(x+y)%3==1?Blocks.BLUE_CONCRETE:Blocks.ORANGE_CONCRETE).getDefaultState(),2);
        for(int x=-7;x<=7;x++)for(int z=0;z<=8;z++)world.setBlockState(BASE.add(x,-1,z),Blocks.IRON_BLOCK.getDefaultState(),2);
        for(int x=-7;x<=7;x++)for(int y=0;y<=8;y++)world.setBlockState(BASE.add(x,y,-4),Blocks.GOLD_BLOCK.getDefaultState(),2);
        for(int x=-2;x<=-1;x++)for(int y=0;y<=3;y++)world.setBlockState(BASE.add(x,y,6),ModWorldBlocks.MIRROR.getDefaultState().with(net.minecraft.state.property.Properties.FACING,Direction.NORTH),2);
        var husk=new net.minecraft.entity.mob.HuskEntity(net.minecraft.entity.EntityType.HUSK,world);husk.setAiDisabled(true);husk.setGlowing(true);husk.refreshPositionAndAngles(BASE.getX()+3.5,BASE.getY(),BASE.getZ()+5.5,180,0);
        require(world.spawnEntity(husk),"real glowing actor for outline target coverage");outlineEntity=husk.getId();
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.LASER));player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
        var data=DescentPlayerData.get(player);FlightSystem.enableWithoutMount(player,data);data.setFlightVelocity(Vec3d.ZERO);data.setShipAttitude(0,0,1,0,1,0);
        geometryHash=hash();ready=true;
    }
    private static long hash(){long hash=1;var world=player.getServerWorld();for(int x=-7;x<=7;x++)for(int y=0;y<=8;y++)hash=hash*31+Block.getRawIdFromState(world.getBlockState(BASE.add(x,y,8)));return hash;}
    private static void tick(MinecraftClient c){
        if(done)return;require(++ticks<1600,"timeout phase="+phase+" ready="+ready);
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(c.player==null||c.world==null||c.getServer()==null)return;
        if(!requested){
            require(c.runDirectory.getName().equals("cubic-client-automation"),"disposable runtime required");requested=true;c.setScreen(null);originalHybrid=DescentConfig.hybridRender;
            DescentConfig.presentationProfile=PROFILES[0];DescentConfig.ps1SceneHeight=360;DescentConfig.cockpit=true;DescentConfig.weaponView=true;
            DescentConfig.mirrorReflection=true;DescentConfig.portalSeeThrough=false;
            c.options.hudHidden=false;c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);c.inGameHud.getChatHud().clear(false);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            c.getServer().execute(()->{player=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());player.stopRiding();player.changeGameMode(GameMode.CREATIVE);player.teleport(player.getServerWorld(),BASE.getX()+.5,BASE.getY(),BASE.getZ()+.5,Set.of(),0,0);player.getServerWorld().setTimeOfDay(6000);located=true;});return;
        }
        if(located&&!requestedFixture&&loaded(c.world)){requestedFixture=true;c.getServer().execute(NativePresentationCheck::fixture);}
        if(ready)require(DescentConfig.hybridRender==originalHybrid,"PS1 profiles preserve the independent hybrid/mesher choice");
    }
    public static void beforeFrame(){
        if(!ENABLED)return;witness=false;samplePhase=-1;sampleEpoch=-1;worldGrid=-1;frameEpoch++;
        if(beforeOverlays!=null){beforeOverlays.close();beforeOverlays=null;}
    }
    public static void beforeOverlay(){
        if(!ready||done||samplePhase>=0||DescentConfig.presentationProfile==PresentationProfile.HYBRID_NATIVE)return;
        var c=MinecraftClient.getInstance();samplePhase=phase;sampleEpoch=frameEpoch;
        beforeOverlays=ScreenshotRecorder.takeScreenshot(c.getFramebuffer());
        int factor=DescentConfig.presentationProfile==PresentationProfile.PS1_HYBRID?beforeOverlays.getHeight()/DescentConfig.ps1SceneHeight:1;
        require(factor>=1,"bounded scale in composite observation");worldGrid=pixelGrid(beforeOverlays,factor);
    }
    private static boolean cyan(int value){return (value&255)<8&&((value>>>8)&255)>247&&((value>>>16)&255)>247;}
    private static void projectWallEdge(List<double[][]> edges,org.joml.Matrix4f matrix,Vec3d camera,int width,int height,double x1,double y1,double x2,double y2){
        var edge=new double[2][2];double[] xs={x1,x2},ys={y1,y2};
        for(int i=0;i<2;i++){
            var clip=new org.joml.Vector4f((float)(xs[i]-camera.x),(float)(ys[i]-camera.y),(float)(BASE.getZ()+8-camera.z),1).mul(matrix);
            if(clip.w<=1e-6)return;
            edge[i][0]=(clip.x/clip.w*.5+.5)*width;edge[i][1]=(.5-clip.y/clip.w*.5)*height;
        }edges.add(edge);
    }
    private static boolean insideMirror(double x,double y){
        for(var p:mirrorPolygons){
            double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
            for(var v:p){minX=Math.min(minX,v[0]);minY=Math.min(minY,v[1]);maxX=Math.max(maxX,v[0]);maxY=Math.max(maxY,v[1]);}
            if(x>=minX-5&&x<=maxX+5&&y>=minY-5&&y<=maxY+5)return true;
        }return false;
    }
    private static Map<String,Object> wallEdgePixels(NativeImage image){
        int samples=0,hits=0;int[] bins=new int[3];var misses=new ArrayList<Map<String,Object>>();
        for(var edge:debugWallEdges)for(double t:new double[]{.2,.5,.8}){
            double px=edge[0][0]*(1-t)+edge[1][0]*t,py=edge[0][1]*(1-t)+edge[1][1]*t;
            int x=(int)Math.round(px),y=(int)Math.round(py);
            if(x<4||x>=image.getWidth()-4||y<4||y>=image.getHeight()-4||insideMirror(px,py))continue;
            samples++;boolean found=false;
            for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
                int c=image.getColor(x+dx,y+dy),r=c&255,g=(c>>>8)&255,b=(c>>>16)&255;
                // Include the dark-blue authored material; dark background still fails.
                found|=Math.max(r,Math.max(g,b))>=60&&r+g+b>=100;
            }
            if(found){hits++;bins[Math.min(2,x*3/image.getWidth())]++;}else misses.add(Map.of("x",x,"y",y));
        }
        var result=new LinkedHashMap<String,Object>();result.put("samples",samples);result.put("hits",hits);result.put("ratio",samples==0?0:(double)hits/samples);result.put("horizontalBins",bins);result.put("misses",misses);return result;
    }
    private static void checkDebugPostprocessException(){
        int[] before=new int[2];GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,before);
        require(before[0]==GL11.GL_LINE&&before[1]==GL11.GL_LINE,"actual Debug world raster is wireframe before composite guard");
        class ExpectedFailure extends RuntimeException{}
        try(var filled=ScenePresentation.fillDebugPostprocess()){
            require(filled!=null,"real Debug compositor raster guard");
            int[] inside=new int[2];GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,inside);
            require(inside[0]==GL11.GL_FILL&&inside[1]==GL11.GL_FILL,"compositor guard fills both polygon faces");throw new ExpectedFailure();
        }catch(ExpectedFailure expected){ }
        int[] after=new int[2];GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,after);
        require(Arrays.equals(before,after)&&GL11.glGetError()==GL11.GL_NO_ERROR,"Debug guard restores both polygon modes even when its pass throws");debugExceptionRestored=true;
    }
    private static void checkDebugWorldEdges(MinecraftClient c,NativeImage finalImage)throws java.io.IOException{
        require(beforeOverlays!=null&&samplePhase==phase&&sampleEpoch==frameEpoch&&debugProjectionEpoch==frameEpoch,"fresh main-world Debug raster and authored-edge projection");
        var measured=wallEdgePixels(beforeOverlays);int samples=(int)measured.get("samples");double ratio=(double)measured.get("ratio");var bins=(int[])measured.get("horizontalBins");
        debugEdgeResult=Map.of("samples",samples,"hits",measured.get("hits"),"ratio",ratio,"horizontalBins",bins,"frameEpoch",frameEpoch,"sampleEpoch",sampleEpoch,"projectionEpoch",debugProjectionEpoch);
        boolean useful=samples>=100&&ratio>=.70&&bins[0]>=20&&bins[1]>=20&&bins[2]>=20;
        if(!useful){
            var folder=c.runDirectory.toPath().resolve("presentation-failure");Files.createDirectories(folder);
            finalImage.writeTo(folder.resolve("final-debug-wireframe.png"));beforeOverlays.writeTo(folder.resolve("world-before-overlays-debug-wireframe.png"));
            var evidence=new LinkedHashMap<String,Object>(measured);evidence.put("frameEpoch",frameEpoch);evidence.put("sampleEpoch",sampleEpoch);evidence.put("projectionEpoch",debugProjectionEpoch);evidence.put("projectedEdges",debugWallEdges);evidence.put("mirrorPolygons",mirrorPolygons);evidence.put("threshold",Map.of("minSamples",100,"minHitRatio",.70,"minHitsEachHorizontalThird",20,"minMaxRgb",60,"minRgbSum",100,"pixelRadius",2));
            Files.writeString(folder.resolve("debug-world-edges.json"),new com.google.gson.Gson().toJson(evidence));
        }
        require(useful,"Debug must show distributed authored 3D wall edges, not only a fullscreen-quad diagonal: "+measured);
    }
    private static double pixelGrid(NativeImage image,int factor){
        int same=0,total=0;
        for(int y=(image.getHeight()/6/factor)*factor;y<image.getHeight()/3;y+=factor)for(int x=(image.getWidth()*3/8/factor)*factor;x<image.getWidth()*5/8;x+=factor){
            int color=image.getColor(x,y);boolean matches=true;for(int dy=0;dy<factor;dy++)for(int dx=0;dx<factor;dx++)matches&=image.getColor(x+dx,y+dy)==color;
            if(matches)same++;total++;
        }return total==0?0:(double)same/total;
    }
    private static List<Map<String,Object>> mismatchedGroups(NativeImage image,int factor){
        var groups=new ArrayList<Map<String,Object>>();
        for(int y=(image.getHeight()/6/factor)*factor;y<image.getHeight()/3;y+=factor)for(int x=(image.getWidth()*3/8/factor)*factor;x<image.getWidth()*5/8;x+=factor){
            int color=image.getColor(x,y);boolean same=true;var pixels=new ArrayList<String>();
            for(int dy=0;dy<factor;dy++)for(int dx=0;dx<factor;dx++){int value=image.getColor(x+dx,y+dy);same&=value==color;pixels.add(Integer.toUnsignedString(value,16));}
            if(!same)groups.add(Map.of("x",x,"y",y,"pixels",pixels));
        }return groups;
    }
    private static void captureGridFailure(MinecraftClient c,NativeImage finalImage,int factor,double finalGrid)throws java.io.IOException{
        var dir=c.runDirectory.toPath().resolve("presentation-failure");Files.createDirectories(dir);
        finalImage.writeTo(dir.resolve("final-"+SCENES[phase]+".png"));
        if(beforeOverlays!=null)beforeOverlays.writeTo(dir.resolve("world-before-overlays-"+SCENES[phase]+".png"));
        var data=new LinkedHashMap<String,Object>();data.put("scene",SCENES[phase]);data.put("phase",phase);data.put("samplePhase",samplePhase);data.put("sampleEpoch",sampleEpoch);data.put("factor",factor);
        data.put("finalGrid",finalGrid);data.put("worldGrid",worldGrid);data.put("finalMismatches",mismatchedGroups(finalImage,factor));
        data.put("frameEpoch",frameEpoch);data.put("mirrorEpoch",mirrorEpoch);data.put("mirrorPhase",mirrorPhase);data.put("mirrorPolygons",mirrorPolygons);
        data.put("legacyGoldPixels",gold(finalImage));data.put("projectedApertureGoldPixels",projectedGold(finalImage));
        if(beforeOverlays!=null)data.put("worldMismatches",mismatchedGroups(beforeOverlays,factor));
        Files.writeString(dir.resolve("grid-"+SCENES[phase]+".json"),new com.google.gson.Gson().toJson(data));
    }
    private static int gold(NativeImage image){int pixels=0;for(int y=image.getHeight()/5;y<image.getHeight()*2/3;y++)for(int x=image.getWidth()/4;x<image.getWidth()*7/16;x++){int c=image.getColor(x,y);if((c&255)>170&&((c>>>8)&255)>145&&((c>>>16)&255)<110)pixels++;}return pixels;}
    private static int projectedGold(NativeImage image){
        if(mirrorEpoch!=frameEpoch||mirrorPhase!=phase)return 0;int pixels=0;
        for(var polygon:mirrorPolygons){
            double area=0,minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
            for(int i=0;i<4;i++){var a=polygon[i];var b=polygon[(i+1)%4];area+=a[0]*b[1]-b[0]*a[1];minX=Math.min(minX,a[0]);minY=Math.min(minY,a[1]);maxX=Math.max(maxX,a[0]);maxY=Math.max(maxY,a[1]);}
            for(int y=Math.max(0,(int)Math.ceil(minY));y<Math.min(image.getHeight(),(int)Math.floor(maxY));y++)for(int x=Math.max(0,(int)Math.ceil(minX));x<Math.min(image.getWidth(),(int)Math.floor(maxX));x++){
                boolean inside=true;for(int i=0;i<4;i++){var a=polygon[i];var b=polygon[(i+1)%4];double dx=b[0]-a[0],dy=b[1]-a[1];if(Math.signum(area)*(dx*(y+.5-a[1])-dy*(x+.5-a[0]))<3*Math.hypot(dx,dy)){inside=false;break;}}
                if(inside){int c=image.getColor(x,y);if((c&255)>170&&((c>>>8)&255)>145&&((c>>>16)&255)<110)pixels++;}
            }
        }return pixels;
    }
    private static Object[] auxiliary(WorldViewTargetsAccess a){return new Object[]{a.drmd$transparency(),a.drmd$outlines(),a.drmd$translucent(),a.drmd$entities(),a.drmd$particles(),a.drmd$weather(),a.drmd$clouds(),a.drmd$outlineTarget()};}
    private static void checkExceptionRestore(MinecraftClient c){
        var original=c.getFramebuffer();var access=(WorldViewTargetsAccess)c.worldRenderer;var saved=auxiliary(access);
        class ExpectedFailure extends RuntimeException{}
        try(var frame=ScenePresentation.begin(c.worldRenderer)){require(frame!=null,"real presentation frame exists for exception cleanup");throw new ExpectedFailure();}
        catch(ExpectedFailure expected){ }
        require(c.getFramebuffer()==original&&Arrays.equals(saved,auxiliary(access))&&!ScenePresentation.stats().inWorldPass(),"exception cleanup restores all original FBO references");
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);require(viewport[2]==original.textureWidth&&viewport[3]==original.textureHeight&&GL11.glGetError()==GL11.GL_NO_ERROR,"exception cleanup restores full viewport without GL errors");exceptionRestored=true;
    }
    public static void rendered(){
        if(!ENABLED||!ready||done)return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null||!DescentClientState.enabled||!WeaponViewRenderer.replacesVanillaHand(c)||!witness)return;
        var outlined=c.world.getEntityById(outlineEntity);if(outlined==null||!outlined.isGlowing())return;
        var state=ScenePresentation.stats();var wanted=PROFILES[phase];int width=phase==4?960:1280,height=phase==4?540:720;
        if(state.profile()!=wanted||state.outputWidth()!=width||state.outputHeight()!=height)return;
        require(!state.inWorldPass()&&c.getFramebuffer().textureWidth==width&&c.getFramebuffer().textureHeight==height,"main framebuffer restored before HUD/frame capture");
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);require(Arrays.equals(viewport,new int[]{0,0,width,height}),"full-resolution viewport restored");
        int error=GL11.glGetError();require(error==GL11.GL_NO_ERROR,"GL error after depth/colour upscale or overlay: "+error);
        require(state.fallbackFrames()==0,"no failed-target fallback may count as PS1 proof");
        int factor=wanted==PresentationProfile.PS1_HYBRID?height/(phase==4?180:360):1;
        if(wanted==PresentationProfile.PS1_HYBRID)require(state.sceneWidth()==width/factor&&state.sceneHeight()==height/factor&&state.overlayPasses()>=2,"real low-resolution scene and eligible full-resolution cockpit/weapon passes");
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){
            require(cyan(image.getColor(17,24))&&!cyan(image.getColor(18,24)),"one-physical-pixel HUD witness survives scene nearest upscale");
            if(wanted==PresentationProfile.RENDER_DEBUG)checkDebugWorldEdges(c,image);
            double finalGrid=factor>1?pixelGrid(image,factor):1,grid=factor>1?worldGrid:1;
            if(factor>1){
                require(samplePhase==phase&&sampleEpoch==frameEpoch&&beforeOverlays!=null,"fresh pure-world composite observed before this frame's first native overlay");
                if(grid<=.97)captureGridFailure(c,image,factor,finalGrid);
                require(grid>.97,"world colour groups use nearest-neighbour upscale: "+grid);
            }
            int gold=projectedGold(image);String mirror=PortalViewDiagnostics.lastSummary("mirror");
            int surfaces=com.terminaldetector.drmd.client.portal.MirrorReflectionRenderer.scannedCount();
            if(wanted!=PresentationProfile.RENDER_DEBUG&&(mirrorEpoch!=frameEpoch||mirrorPhase!=phase||mirrorPolygons.size()!=8||surfaces!=1||mirror==null||!mirror.startsWith("drawing")||gold<30)){frames=0;return;}
            if(++frames<3)return;frames=0;var folder=c.runDirectory.toPath().resolve("presentation-frames");Files.createDirectories(folder);image.writeTo(folder.resolve(SCENES[phase]+".png"));
            if(factor>1)beforeOverlays.writeTo(folder.resolve(SCENES[phase]+"-world-before-overlays.png"));
            var result=new LinkedHashMap<String,Object>();result.put("scene",SCENES[phase]);result.put("profile",wanted.name());result.put("sceneWidth",state.sceneWidth());result.put("sceneHeight",state.sceneHeight());result.put("outputWidth",width);result.put("outputHeight",height);result.put("deferredOverlayPasses",state.overlayPasses());result.put("worldNearestGridRatio",grid);result.put("finalCompositeGridRatio",finalGrid);result.put("nativeHudPixel",true);result.put("mirror",mirror);result.put("reflectedGoldPixels",gold);result.put("mergedMirrorSurfaces",surfaces);result.put("projectedMirrorPanes",mirrorPolygons.size());result.put("frameEpoch",frameEpoch);result.put("mirrorEpoch",mirrorEpoch);result.put("sampleEpoch",sampleEpoch);result.put("glError",error);RESULTS.add(result);
            if(wanted==PresentationProfile.RENDER_DEBUG)result.put("debugWorldEdges",debugEdgeResult);
            if(phase==1)checkExceptionRestore(c);
            if(++phase==SCENES.length){var report=new LinkedHashMap<String,Object>();report.put("runtimeToggleWithoutReload",true);report.put("actualWindowResizeAndRestore",true);report.put("exceptionTargetsRestored",exceptionRestored);report.put("debugPostprocessExceptionRestored",debugExceptionRestored);report.put("blockstoreHashUnchanged",true);report.put("independentHybridChoiceUnchanged",true);report.put("scenes",RESULTS);Files.writeString(c.runDirectory.toPath().resolve("presentation-ok.json"),new com.google.gson.Gson().toJson(report));done=true;c.scheduleStop();return;}
            DescentConfig.presentationProfile=PROFILES[phase];DescentConfig.ps1SceneHeight=phase==4?180:360;
            if(phase==4)c.getWindow().setWindowedSize(960,540);if(phase==5)c.getWindow().setWindowedSize(1280,720);
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
}
