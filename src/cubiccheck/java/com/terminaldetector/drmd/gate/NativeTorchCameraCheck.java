package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.DescentCamera;
import com.terminaldetector.drmd.client.gravity.FootGravityCamera;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;
import com.terminaldetector.drmd.client.presentation.PresentationProfile;
import com.terminaldetector.drmd.client.render.HandRenderFrame;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.gravity.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real first-person lens, real torch/player and actual baked held mesh. No pose follower. */
public final class NativeTorchCameraCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.torchCamera");
    public static final boolean DEATH=Boolean.getBoolean("drmd.gate.torchCameraDeath");
    private static final Direction[] AXES={Direction.UP,Direction.EAST,Direction.DOWN};
    private static final float[][] LOOKS={{0,0},{90,35},{180,-35},{-90,85}};
    private static final Vec3d CENTRE=new Vec3d(16,2208,16);
    private record View(int tick,int age,int surface,boolean flight,boolean vehicle,boolean foot,boolean grounded,
        boolean clear,Vec3d position,Vec3d eye,Vec3d up,UUID source,boolean matchedSource,boolean blocker,boolean blockerHit,boolean noGravity){}
    private enum Step { SETUP, H_ON, H_OFF, SURFACE, LOOK, ADD_BLOCK, REMOVE_BLOCK, RELEASE, KILL, RESPAWN }
    private static volatile UUID viewer;
    private static volatile View view;
    private static volatile String failure;
    private static volatile int requestedSurface=-2,actualSurface=-2,blockRequest;
    private static volatile Vec3d blockerLook;
    private static boolean staged,built,done,pressed,ownerChecked;
    private static boolean killed,respawnRequested;
    private static Object oldClientPlayer;
    private static volatile Map<String,Object> deathReceipt,respawnReceipt;
    private static int ticks,epoch,frames,look,axis,lookTick,foreignId=-1;
    private static volatile int surfaceStarted;
    private static volatile Step step=Step.SETUP;
    private static long boot;
    private static BlockPos source,blocker;
    private static boolean actualBlocker;
    private static Vec3d blockerStart;
    private static Matrix4f itemMatrix;
    private static double[][] points,baseline;
    private static int baked,handTilt,handBob,worldTilt;
    private static double handDelta,worldDelta;
    private static NativeImage handBefore,handAfter;
    private static int handBeforeEpoch=-1,handAfterEpoch=-1;
    private static boolean captureStateRestored;
    private static boolean failureDumped;
    private static int beforeHandDrawFbo=-1,afterHandDrawFbo=-1;
    private static final int[][] FACES={{0,2,3,1},{4,5,7,6},{0,1,5,4},{2,6,7,3},{0,4,6,2},{1,3,7,5}};
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static final List<Map<String,Object>> EVENTS=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<Map<String,Object>> MATRICES=new ArrayList<>();
    private NativeTorchCameraCheck(){}
    private static void require(boolean yes,String why){if(!yes)throw new IllegalStateException(why);}
    private static List<Double> xyz(Vec3d v){return List.of(v.x,v.y,v.z);}
    public static void register(){
        require(MinecraftClient.getInstance().runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"),"disposable actual native runtime only");
        boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(NativeTorchCameraCheck::server);
        ClientTickEvents.END_CLIENT_TICK.register(NativeTorchCameraCheck::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,damage)->{
            if(ENABLED&&DEATH&&entity.getUuid().equals(viewer))deathReceipt=Map.of("event","real AFTER_DEATH","entityId",entity.getId(),"uuid",entity.getUuid().toString(),"alive",entity.isAlive(),"noGravity",entity.hasNoGravity());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->{
            if(ENABLED&&DEATH&&newPlayer.getUuid().equals(viewer))respawnReceipt=Map.of("event","real AFTER_RESPAWN","oldId",oldPlayer.getId(),"newId",newPlayer.getId(),"sameObject",oldPlayer==newPlayer,
                "sameUuid",oldPlayer.getUuid().equals(newPlayer.getUuid()),"aliveTransfer",alive,"nativeNewAlive",newPlayer.isAlive(),"flight",DescentPlayerData.get(newPlayer).isEnabled(),"foot",FootGravitySystem.isActive(newPlayer),"noGravity",newPlayer.hasNoGravity());
        });
    }
    private static boolean full(net.minecraft.server.world.ServerWorld w){
        var access=WorldAccess.of(w);for(int x=0;x<=1;x++)for(int y=137;y<=138;y++)for(int z=0;z<=1;z++)
            if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;return true;
    }
    private static boolean clientFull(MinecraftClient c,Box box){
        int x0=(int)Math.floor(box.minX)>>4,x1=(int)Math.floor(box.maxX)>>4;
        int y0=(int)Math.floor(box.minY)>>4,y1=(int)Math.floor(box.maxY)>>4;
        int z0=(int)Math.floor(box.minZ)>>4,z1=(int)Math.floor(box.maxZ)>>4;
        if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>8)return false;
        var access=WorldAccess.of(c.world);for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)
            if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;return true;
    }
    private static void server(MinecraftServer server){
        if(done||viewer==null||failure!=null)return;
        try{
            var p=server.getPlayerManager().getPlayer(viewer);if(p==null)return;var w=p.getServerWorld();
            require(WorldAccess.of(w).backend()==CubicAvailability.Backend.NATIVE_CUBIC,"actual BCC backend required");
            if(step==Step.KILL||step==Step.RESPAWN){
                if(!killed){require(DEATH&&FootGravitySystem.isActive(p)&&p.isAlive(),"actual living ceiling-frame death premise");
                    EVENTS.add(Map.of("event","one disclosed native kill","uuid",p.getUuid().toString(),"entityId",p.getId(),"up",xyz(FootGravitySystem.getUp(p)),"noGravity",p.hasNoGravity()));
                    killed=true;p.kill();
                }
                view=new View(server.getTicks(),p.age,actualSurface,DescentPlayerData.get(p).isEnabled(),p.hasVehicle(),FootGravitySystem.isActive(p),false,true,p.getPos(),p.getEyePos(),FootGravitySystem.getUp(p),null,false,false,false,p.hasNoGravity());return;
            }
            if(!staged){staged=true;p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));
                p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.teleport(w,CENTRE.x,CENTRE.y,CENTRE.z,Set.of(),0,0);return;}
            if(!full(w))return;
            if(!built){
                for(var b:BlockPos.iterate(new BlockPos(8,2200,8),new BlockPos(23,2215,23))){
                    boolean shell=b.getX()==8||b.getX()==23||b.getY()==2200||b.getY()==2215||b.getZ()==8||b.getZ()==23;
                    w.setBlockState(b,shell?Blocks.SEA_LANTERN.getDefaultState():Blocks.AIR.getDefaultState(),2);
                }
                var cow=EntityType.COW.create(w);require(cow!=null,"registered foreign-lens fixture");cow.setAiDisabled(true);cow.setPersistent();
                cow.setPosition(19.5,2201,20.5);require(w.spawnEntity(cow),"actual foreign-lens entity spawn");foreignId=cow.getId();
                p.getInventory().setStack(0,new ItemStack(Items.GOLD_BLOCK));p.getInventory().selectedSlot=0;
                p.getInventory().markDirty();p.playerScreenHandler.sendContentUpdates();built=true;
            }
            if(requestedSurface!=actualSurface){
                if(source!=null)w.setBlockState(source,Blocks.AIR.getDefaultState(),3);
                if(blocker!=null&&actualBlocker)w.setBlockState(blocker,Blocks.AIR.getDefaultState(),3);
                actualBlocker=false;actualSurface=requestedSurface;
                surfaceStarted=server.getTicks();
                if(actualSurface>=0){
                    Vec3d up=Vec3d.of(AXES[actualSurface].getVector());
                    // Keep both versions of the physical eye on the same voxel lane for the
                    // obstruction ray. The client interpolates vanilla eye height as a float.
                    Vec3d tangent=com.terminaldetector.drmd.world.gravity.ActorFrameMath.toWorld(new Vec3d(.5,0,.5),up);
                    Vec3d contact=CENTRE.subtract(up.multiply(7)).add(tangent);
                    Vec3d spawn=contact.add(up.multiply(up.y<-.5?2.5:.7));
                    source=BlockPos.ofFloored(contact.add(up.multiply(.3)).add(Math.abs(up.x)>.5?new Vec3d(0,2,2):new Vec3d(2,0,2)));
                    w.setBlockState(source,ModWorldBlocks.GRAVITY_TORCH.getDefaultState().with(GravityTorchBlock.FACING,AXES[actualSurface]),3);
                    p.teleport(w,spawn.x,spawn.y,spawn.z,Set.of(),0,0);p.setVelocity(Vec3d.ZERO);
                }else if(actualSurface==-1){source=null;}
                EVENTS.add(Map.of("event","one-time surface setup","surface",actualSurface,"serverTick",server.getTicks()));
            }
            if(blockRequest!=0){
                if(blockRequest>0){
                    require(blockerLook!=null,"native current camera look requested");blockerStart=p.getEyePos();
                    blocker=BlockPos.ofFloored(blockerStart.add(blockerLook.multiply(2.25)));
                    require(w.getBlockState(blocker).isAir()&&!new Box(blocker).intersects(p.getBoundingBox()),"dynamic obstruction is clear of actual player body");
                    w.setBlockState(blocker,Blocks.AMETHYST_BLOCK.getDefaultState(),3);actualBlocker=true;
                }else{require(blocker!=null&&actualBlocker,"remove the actual added obstruction");w.setBlockState(blocker,Blocks.AIR.getDefaultState(),3);actualBlocker=false;}
                blockRequest=0;
            }
            var foot=FootGravitySystem.get(p);var sampled=GravityFields.sample(w,p.getPos());
            boolean matched=source!=null&&sampled!=null&&GravityFields.all().stream().anyMatch(f->f.id().equals(sampled.sourceId())
                &&w.getRegistryKey().equals(f.worldKey())&&f.origin().equals(source));
            boolean hit=false;if(blocker!=null&&blockerLook!=null){var h=w.raycast(new RaycastContext(p.getEyePos(),p.getEyePos().add(blockerLook.multiply(4)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));hit=h.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(blocker);}
            boolean contact=false;if(foot!=null&&foot.active()){
                Vec3d from=p.getPos().add(foot.up().multiply(.08));var h=w.raycast(new RaycastContext(from,from.subtract(foot.up().multiply(.2)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
                if(h.getType()==HitResult.Type.BLOCK){double gap=p.getPos().subtract(h.getPos()).dotProduct(foot.up());contact=gap>=-1e-6&&gap<=.08&&Vec3d.of(h.getSide().getVector()).dotProduct(foot.up())>=.95;}
            }
            view=new View(server.getTicks(),p.age,actualSurface,DescentPlayerData.get(p).isEnabled(),p.hasVehicle(),foot!=null&&foot.active(),
                foot==null?p.isOnGround():foot.grounded()&&contact,w.isSpaceEmpty(p,p.getBoundingBox().contract(1e-6)),p.getPos(),p.getEyePos(),FootGravitySystem.getUp(p),
                sampled==null?null:sampled.sourceId(),matched,actualBlocker,hit,p.hasNoGravity());
        }catch(Throwable error){failure=error.toString();}
    }
    private static void pulse(){require(!pressed,"single H pulse outstanding");KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(DescentKeybinds.toggle));DescentKeybinds.toggle.setPressed(true);pressed=true;
        EVENTS.add(Map.of("event","real H binding pulse","clientTick",ticks,"priorClientFlight",DescentClientState.enabled,"priorServerFlight",view!=null&&view.flight));}
    private static void tick(MinecraftClient c){
        if(!ENABLED||done)return;
        try{
            if(pressed){DescentKeybinds.toggle.setPressed(false);pressed=false;}
            if(++ticks>3000||System.nanoTime()-boot>180_000_000_000L){finish("UNOBSERVED","finite native physical/render deadline");return;}
            if(failure!=null){finish("FAIL",failure);return;}
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen p)
                for(var child:p.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player==null||c.world==null||c.getServer()==null)return;
            DescentClient.markUserFlightChoice();if(viewer==null){viewer=c.player.getUuid();c.options.setPerspective(Perspective.FIRST_PERSON);c.options.hudHidden=false;
                DescentConfig.presentationProfile=PresentationProfile.HYBRID_NATIVE;DescentConfig.hud=false;DescentConfig.cockpit=false;c.inGameHud.getChatHud().clear(false);return;}
            if(DEATH&&(step==Step.KILL||step==Step.RESPAWN)&&c.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen screen){
                if(!respawnRequested)for(var child:screen.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.active&&button.getMessage().getString().equals(net.minecraft.text.Text.translatable("deathScreen.respawn").getString())){
                    EVENTS.add(Map.of("event","actual native Respawn button","clientTick",ticks));respawnRequested=true;button.onPress();step=Step.RESPAWN;break;
                }return;
            }
            View v=view;if(v==null||c.currentScreen!=null||c.getOverlay()!=null)return;
            switch(step){
                case SETUP->{if(built&&!v.flight&&!DescentClientState.enabled&&c.player.getMainHandStack().isOf(Items.GOLD_BLOCK)){pulse();step=Step.H_ON;}}
                case H_ON->{if(v.flight&&DescentClientState.enabled&&ownerChecked){pulse();step=Step.H_OFF;}}
                case H_OFF->{if(!v.flight&&!DescentClientState.enabled&&!v.vehicle){axis=0;requestedSurface=0;step=Step.SURFACE;}}
                case SURFACE->{
                    if(v.surface==axis&&v.tick-surfaceStarted>120){finish("UNOBSERVED","actual source/support/client frame admission exceeded120 server ticks");return;}
                    if(v.surface==axis&&v.matchedSource&&v.foot&&v.grounded&&v.clear&&v.up.dotProduct(Vec3d.of(AXES[axis].getVector()))>=.999999
                        &&DescentClientState.footGravity&&FootGravitySystem.getUp(c.player).dotProduct(v.up)>=.999999
                        &&FootGravityCamera.visualUp().dotProduct(v.up)>=.99999){look=0;setLook(c,v.up);step=Step.LOOK;}
                }
                case ADD_BLOCK->{if(v.blocker&&v.blockerHit){lookTick=ticks;frames=0;step=Step.LOOK;}}
                case REMOVE_BLOCK->{if(!v.blocker&&!v.blockerHit){lookTick=ticks;frames=0;step=Step.LOOK;}}
                case RELEASE->{if(!v.foot&&!DescentClientState.footGravity&&FootGravityCamera.settled()&&!v.noGravity&&!v.vehicle){
                    require(c.player.isAlive(),"ordinary released player remains alive");}}
                default->{}
            }
        }catch(Throwable error){finish("FAIL",error.toString());}
    }
    private static void setLook(MinecraftClient c,Vec3d up){
        Vec3d f=ActorFrameMath.toWorld(Vec3d.fromPolar(LOOKS[look][1],LOOKS[look][0]),up);
        c.player.setYaw((float)Math.toDegrees(Math.atan2(-f.x,f.z)));c.player.setPitch((float)-Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,f.y)))));
        lookTick=ticks;frames=0;
        EVENTS.add(Map.of("event","one-time look sample","surface",axis,"look",look,"localYaw",LOOKS[look][0],"localPitch",LOOKS[look][1],"clientTick",ticks));
    }
    public static void beforeFrame(){
        if(!ENABLED||done)return;closeHandImages();epoch++;baked=handTilt=handBob=worldTilt=0;handDelta=worldDelta=0;itemMatrix=null;points=null;MATRICES.clear();
        handBeforeEpoch=handAfterEpoch=-1;beforeHandDrawFbo=afterHandDrawFbo=-1;captureStateRestored=true;
    }
    private static void closeHandImages(){if(handBefore!=null){handBefore.close();handBefore=null;}if(handAfter!=null){handAfter.close();handAfter=null;}}
    /** Actual native renderHand boundaries, before/after its real Immediate.draw. */
    public static void hand(boolean before){
        if(!ENABLED||done||failure!=null||step!=Step.LOOK||ticks-lookTick<20||OffscreenWorldView.busy())return;
        var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||c.currentScreen!=null||!DescentCamera.ownsView(c.gameRenderer.getCamera()))return;
        try{
            int actualDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            require(actualDraw==c.getFramebuffer().fbo,"UNOBSERVED: actual hand must draw to the sampled native framebuffer");
            NativeImage image=captureActualFramebuffer();
            if(before){if(handBefore!=null)handBefore.close();handBefore=image;handBeforeEpoch=epoch;beforeHandDrawFbo=actualDraw;}
            else{if(handAfter!=null)handAfter.close();handAfter=image;handAfterEpoch=epoch;afterHandDrawFbo=actualDraw;}
        }catch(Throwable error){failure=error.toString();dumpRenderFailure(error);}
    }
    private static NativeImage captureActualFramebuffer(){
        var c=MinecraftClient.getInstance();int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},values=new int[4];
        for(int i=0;i<4;i++)values[i]=GL11.glGetInteger(names[i]);
        try{for(int i=0;i<4;i++)GL11.glPixelStorei(names[i],i==0?4:0);return ScreenshotRecorder.takeScreenshot(c.getFramebuffer());}
        finally{
            GlStateManager._activeTexture(active);RenderSystem.bindTexture(bound);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            for(int i=0;i<4;i++)GL11.glPixelStorei(names[i],values[i]);
            captureStateRestored&=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==active&&GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==bound
                &&GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)==read&&GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==draw;
            for(int i=0;i<4;i++)captureStateRestored&=GL11.glGetInteger(names[i])==values[i];
        }
    }
    private static double difference(Matrix4f a,Matrix4f b){float[] x=a.get(new float[16]),y=b.get(new float[16]);double max=0;for(int i=0;i<16;i++)max=Math.max(max,Math.abs(x[i]-y[i]));return max;}
    public static void transform(boolean hand,boolean bob,Matrix4f before,Matrix4f after){
        if(!ENABLED||done||step!=Step.LOOK||OffscreenWorldView.busy())return;
        if(hand){require(HandRenderFrame.active,"actual production renderHand ownership flag");if(bob)handBob++;else handTilt++;handDelta=Math.max(handDelta,difference(before,after));}
        else if(!bob){worldTilt++;worldDelta=Math.max(worldDelta,difference(before,after));}
        if(MATRICES.size()<8)MATRICES.add(Map.of("domain",hand?"renderHand":"renderWorld","operator",bob?"bobView":"tiltViewWhenHurt","before",before.get(new float[16]),"after",after.get(new float[16])));
    }
    public static void baked(BakedModel model,ItemStack stack,MatrixStack matrices){
        if(!ENABLED||done||step!=Step.LOOK||!HandRenderFrame.active||OffscreenWorldView.busy()||!stack.isOf(Items.GOLD_BLOCK))return;
        var c=MinecraftClient.getInstance();require(model!=c.getBakedModelManager().getMissingModel(),"actual held baked model exists");
        baked++;itemMatrix=new Matrix4f(matrices.peek().getPositionMatrix());var combined=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(itemMatrix);
        points=new double[8][2];for(int i=0;i<8;i++){var p=new Vector4f((i&1)==0?0:1,(i&2)==0?0:1,(i&4)==0?0:1,1).mul(combined);
            require(Float.isFinite(p.w)&&p.w>1e-6,"actual emitted held cube in front of lens");points[i]=new double[]{(p.x/p.w*.5+.5)*c.getWindow().getFramebufferWidth(),(.5-p.y/p.w*.5)*c.getWindow().getFramebufferHeight()};}
    }
    private static int gold(NativeImage image){
        if(points==null)return 0;double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(var p:points){minX=Math.min(minX,p[0]);minY=Math.min(minY,p[1]);maxX=Math.max(maxX,p[0]);maxY=Math.max(maxY,p[1]);}
        int count=0;for(int y=Math.max(0,(int)Math.floor(minY));y<Math.min(image.getHeight(),(int)Math.ceil(maxY));y++)for(int x=Math.max(0,(int)Math.floor(minX));x<Math.min(image.getWidth(),(int)Math.ceil(maxX));x++){
            int color=image.getColor(x,y),r=color&255,g=(color>>>8)&255,b=(color>>>16)&255;if(r>=130&&g>=90&&b<=145&&r>=g+15&&g>=b+15)count++;
        }return count;
    }
    private static boolean inFace(double x,double y,int[] face){
        double area=0;for(int i=0;i<4;i++){var a=points[face[i]];var b=points[face[(i+1)%4]];area+=a[0]*b[1]-b[0]*a[1];}
        if(Math.abs(area)<1e-7)return false;
        for(int i=0;i<4;i++){var a=points[face[i]];var b=points[face[(i+1)%4]];
            if(Math.signum(area)*((b[0]-a[0])*(y-a[1])-(b[1]-a[1])*(x-a[0]))<-.8*Math.hypot(b[0]-a[0],b[1]-a[1]))return false;
        }return true;
    }
    private static int colorDelta(int a,int b){return Math.max(Math.abs((a&255)-(b&255)),Math.max(Math.abs(((a>>>8)&255)-((b>>>8)&255)),Math.abs(((a>>>16)&255)-((b>>>16)&255))));}
    private static double scalarFit(int reference,int actual){
        double r=reference&255,g=(reference>>>8)&255,b=(reference>>>16)&255;
        double length=r*r+g*g+b*b;
        return length==0?Double.NaN:(r*(actual&255)+g*((actual>>>8)&255)+b*((actual>>>16)&255))/length;
    }
    private static double scalarResidual(int reference,int actual,double scale){
        if(!Double.isFinite(scale))return Double.POSITIVE_INFINITY;
        return Math.max(Math.abs((actual&255)-scale*(reference&255)),Math.max(Math.abs(((actual>>>8)&255)-scale*((reference>>>8)&255)),Math.abs(((actual>>>16)&255)-scale*((reference>>>16)&255))));
    }
    private static Map<String,Object> contribution(NativeImage image){
        require(handBefore!=null&&handAfter!=null&&handBeforeEpoch==epoch&&handAfterEpoch==epoch,"same actual frame has both native renderHand image boundaries");
        require(handBefore.getWidth()==image.getWidth()&&handAfter.getWidth()==image.getWidth()&&handBefore.getHeight()==image.getHeight()&&handAfter.getHeight()==image.getHeight(),"actual before/after/final framebuffer dimensions agree");
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(var p:points){minX=Math.min(minX,p[0]);minY=Math.min(minY,p[1]);maxX=Math.max(maxX,p[0]);maxY=Math.max(maxY,p[1]);}
        int changed=0,retained=0,exact=0,roi=0;long red=0,green=0,blue=0;double minScale=1,maxScale=0;
        for(int y=Math.max(0,(int)Math.floor(minY)-1);y<Math.min(image.getHeight(),(int)Math.ceil(maxY)+1);y++)for(int x=Math.max(0,(int)Math.floor(minX)-1);x<Math.min(image.getWidth(),(int)Math.ceil(maxX)+1);x++){
            boolean inside=false;for(var f:FACES)if(inFace(x+.5,y+.5,f)){inside=true;break;}if(!inside)continue;roi++;
            int a=handBefore.getColor(x,y),b=handAfter.getColor(x,y),last=image.getColor(x,y);
            if(colorDelta(a,b)>=8){
                changed++;red+=b&255;green+=(b>>>8)&255;blue+=(b>>>16)&255;
                if(colorDelta(b,last)<=2)exact++;
                // Native HUD vignette multiplies destination RGB after renderHand.
                // Require SAME-pixel post-hand chroma, while excluding ANY scalar fit
                // of the pre-hand background. Neither a moved mesh nor a black/erased
                // hand can qualify merely because its final pixels are dark.
                double scale=scalarFit(b,last),residual=scalarResidual(b,last,scale);
                double beforeResidual=scalarResidual(a,last,scalarFit(a,last));
                if(scale>=.1&&scale<=1.01&&residual<=2&&beforeResidual>=8){retained++;minScale=Math.min(minScale,scale);maxScale=Math.max(maxScale,scale);}
            }
        }
        var out=new LinkedHashMap<String,Object>();out.put("actualEpoch",epoch);out.put("projectedRoiPixels",roi);out.put("actualHandChangedPixels",changed);out.put("actualFinalRetainedPixels",retained);
        out.put("actualFinalExactRetainedPixels",exact);out.put("minimumRequired",100);out.put("minChannelChange",8);out.put("maxFinalScaledRgbResidual",2);out.put("minimumBeforeScaledRgbResidual",8);
        out.put("allowedAchromaticScale",List.of(.1,1.01));out.put("observedRetainedScaleRange",retained==0?List.of():List.of(minScale,maxScale));out.put("captureStateRestored",captureStateRestored);
        out.put("oracle","same-frame native hand draw in emitted projected mesh, retained at SAME final pixels under native achromatic vignette; pre-hand background scalar fit excluded");
        out.put("changedMeanAfterRgb",List.of(changed==0?0:(double)red/changed,changed==0?0:(double)green/changed,changed==0?0:(double)blue/changed));
        out.put("previousF256ExactRetentionStatus","FAIL");return out;
    }
    public static void rendered(float delta){
        if(!ENABLED||done)return;var c=MinecraftClient.getInstance();
        if(failure!=null){finish("FAIL",failure);return;}
        try{
            if(c.player==null||c.world==null||c.currentScreen!=null||c.getOverlay()!=null||OffscreenWorldView.busy())return;
            if(step==Step.RELEASE&&view!=null&&view.surface==-1&&!view.foot&&!DescentClientState.footGravity&&FootGravityCamera.settled()&&!view.noGravity&&!view.vehicle){
                if(!view.grounded||!c.player.isOnGround()||c.player.getPos().squaredDistanceTo(view.position)>.01)return;
                var camera=c.gameRenderer.getCamera();double error=camera.getPos().squaredDistanceTo(c.player.getLerpedPos(delta).add(0,c.player.getEyeHeight(c.player.getPose()),0));
                if(!clientFull(c,Box.of(camera.getPos(),2,2,2)))return;
                boolean clear=c.world.isSpaceEmpty(c.player,Box.of(camera.getPos(),.02,.02,.02));
                require(DescentCamera.ownsView(camera)&&Double.isFinite(error)&&error<=1e-4&&clear,
                    "ordinary released eye: actual="+camera.getPos()+" player="+c.player.getPos()+" server="+view.position+" squaredError="+error+" clear="+clear);
                if(++frames<3)return;try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(c.runDirectory.toPath().resolve("torch-camera-released.png"));}
                finish("PASS","actual H-off, three torch supports/four local look samples, added/removed obstruction, held mesh and ordinary upright release");return;
            }
            if(DEATH&&step==Step.RESPAWN&&respawnReceipt!=null&&deathReceipt!=null&&c.player!=oldClientPlayer&&c.player.isAlive()&&view!=null&&!view.flight&&!view.vehicle&&!view.foot&&!view.noGravity
                &&!DescentClientState.enabled&&!DescentClientState.footGravity&&FootGravityCamera.settled()){
                require(Boolean.FALSE.equals(respawnReceipt.get("sameObject"))&&Boolean.TRUE.equals(respawnReceipt.get("sameUuid"))&&Boolean.FALSE.equals(respawnReceipt.get("aliveTransfer")),"actual death creates a replacement player through native respawn");
                var camera=c.gameRenderer.getCamera();double error=camera.getPos().squaredDistanceTo(c.player.getLerpedPos(delta).add(0,c.player.getEyeHeight(c.player.getPose()),0));
                if(!clientFull(c,Box.of(camera.getPos(),2,2,2)))return;
                require(DescentCamera.ownsView(camera)&&Double.isFinite(error)&&error<=1e-4&&c.world.isSpaceEmpty(c.player,Box.of(camera.getPos(),.02,.02,.02)),"replacement player's actual native first-person eye is finite, upright and clear");
                if(++frames<3)return;try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){image.writeTo(c.runDirectory.toPath().resolve("torch-camera-respawn.png"));}
                finish("PASS","actual H-off/torch/obstruction/held sweep plus real active-ceiling death and native Respawn replacement eye; cold player restart remains separate");return;
            }
            if(step==Step.H_ON&&!ownerChecked&&view!=null&&view.flight&&DescentClientState.enabled){
                var foreign=c.world.getEntityById(foreignId);if(foreign==null)return;NativeCameraOwnershipProbe.check(foreign,delta);ownerChecked=true;return;
            }
            if(step!=Step.LOOK||ticks-lookTick<20||view==null||view.surface!=axis||!view.grounded||!view.clear||!view.foot)return;
            require(!DescentClientState.enabled&&!view.flight&&!view.vehicle,"actual H-off free player, no proxy");
            require(DescentCamera.ownsView(c.gameRenderer.getCamera())&&c.options.getPerspective().isFirstPerson(),"actual owned first-person lens");
            Vec3d camera=c.gameRenderer.getCamera().getPos(),up=FootGravitySystem.getUp(c.player);
            if(!clientFull(c,Box.of(camera,2,2,2)))return;
            Vec3d expected=c.player.getLerpedPos(delta).add(up.multiply(c.player.getEyeHeight(c.player.getPose())));
            double eyeError=camera.squaredDistanceTo(expected);require(Double.isFinite(eyeError)&&eyeError<=1e-4,"real camera follows actual local interpolated eye within .01m");
            require(c.world.isSpaceEmpty(c.player,Box.of(camera,.02,.02,.02)),"actual first-person lens is outside solid material");
            if(look==0&&blocker!=null&&blockerLook!=null){
                var block=WorldAccess.of(c.world).readIfLoaded(blocker);if(block.isEmpty())return;
                if(view.blocker?!block.get().isOf(Blocks.AMETHYST_BLOCK):!block.get().isAir())return;
                Vec3d end=camera.add(blockerLook.multiply(4));if(!clientFull(c,new Box(camera,end).expand(1)))return;
                var hit=c.world.raycast(new RaycastContext(camera,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,c.player));
                require((hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(blocker))==view.blocker,"actual loaded client obstruction ray matches genuine added/removed material");
            }
            require(baked>0&&handTilt>0&&worldTilt>0&&handDelta<=1e-6,"actual native held mesh and stabilized hand callbacks, no second bank/bob");
            float bank=FootGravityCamera.viewRoll(delta);if(axis>0&&look==0)require(Math.abs(bank)>=45&&worldDelta>.1,"actual sideways/ceiling bank exercised in world, retained outside held model");
            double projected=0;if(baseline!=null){for(int i=0;i<8;i++)projected=Math.max(projected,Math.hypot(points[i][0]-baseline[i][0],points[i][1]-baseline[i][1]));require(projected<=1,"actual emitted held vertices remain within one physical pixel across view samples");}
            try(var image=captureActualFramebuffer()){
                int pixels=gold(image);var witness=contribution(image);
                // The first88ce gold-hue FAIL is immutable. This independently measures
                // actual same-frame draw contribution; it does not rename that hue as PASS.
                if(!captureStateRestored||((Number)witness.get("actualHandChangedPixels")).intValue()<100||((Number)witness.get("actualFinalRetainedPixels")).intValue()<100){
                    var dir=c.runDirectory.toPath().resolve("torch-camera-draw-failure");Files.createDirectories(dir);
                    handBefore.writeTo(dir.resolve("before-native-hand.png"));handAfter.writeTo(dir.resolve("after-native-hand.png"));image.writeTo(dir.resolve("actual-final.png"));
                    Files.writeString(dir.resolve("actual-witness.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("surface",axis,"look",look,"epoch",epoch,"originalGoldPixels",pixels,"originalGoldHuePass",pixels>=100,"witness",witness,"actualVertices",points,"actualMatrices",List.copyOf(MATRICES)))+"\n");
                }
                require(captureStateRestored&&((Number)witness.get("actualHandChangedPixels")).intValue()>=100&&((Number)witness.get("actualFinalRetainedPixels")).intValue()>=100,"actual native hand contributes at least100 real changed mesh pixels retained in the final frame");
                int worldPixels=0,worldArea=0;
                for(int y=image.getHeight()/5;y<image.getHeight()/2;y++)for(int x=image.getWidth()*3/10;x<image.getWidth()*7/10;x++){
                    int rgb=image.getColor(x,y);worldArea++;if(Math.max(rgb&255,Math.max((rgb>>>8)&255,(rgb>>>16)&255))>=32)worldPixels++;
                }
                require(worldPixels>=100&&worldPixels>=worldArea*.05,"actual central world image is visible beyond the held mesh");
                require(GL11.glGetError()==0,"completed native frame GL success");if(++frames<3)return;
                if(baseline==null)baseline=points;
                var row=new LinkedHashMap<String,Object>();row.put("surface",axis);row.put("up",xyz(up));row.put("look",look);row.put("epoch",epoch);row.put("serverAge",view.age);
                row.put("camera",xyz(camera));row.put("serverEye",xyz(view.eye));row.put("cameraEyeSquaredError",eyeError);row.put("worldBank",bank);row.put("worldMatrixDelta",worldDelta);
                row.put("handMatrixDelta",handDelta);row.put("handTiltCalls",handTilt);row.put("handBobCalls",handBob);row.put("actualBakedReturns",baked);row.put("actualVertices",points);
                row.put("actualCallMatrices",List.copyOf(MATRICES));row.put("actualEmittedItemMatrix",itemMatrix.get(new float[16]));row.put("actualSourceUUID",view.source==null?null:view.source.toString());
                row.put("maximumVertexDriftPixels",projected);row.put("finalGoldPixels",pixels);row.put("obstructed",view.blocker);row.put("actualBlockRayHit",view.blockerHit);RESULTS.add(row);
                row.put("originalGoldHuePass",pixels>=100);row.put("actualDrawWitness",witness);
                row.put("actualHandFramebuffers",List.of(beforeHandDrawFbo,afterHandDrawFbo,c.getFramebuffer().fbo));
                row.put("centralWorldVisiblePixels",worldPixels);row.put("centralWorldPixelArea",worldArea);
                if(look==0){var dir=c.runDirectory.toPath().resolve("torch-camera-frames");Files.createDirectories(dir);String label="surface-"+axis+"-obstructed-"+view.blocker+"-sample-"+RESULTS.size();
                    handBefore.writeTo(dir.resolve(label+"-before-hand.png"));handAfter.writeTo(dir.resolve(label+"-after-hand.png"));image.writeTo(dir.resolve(label+".png"));}
            }
            frames=0;
            if(look==0&&blockerLook==null){blockerLook=c.player.getRotationVec(1).normalize();blockRequest=1;step=Step.ADD_BLOCK;return;}
            if(look==0&&view.blocker){blockRequest=-1;step=Step.REMOVE_BLOCK;return;}
            if(++look<LOOKS.length){setLook(c,up);return;}
            if(++axis<AXES.length){blockerLook=null;requestedSurface=axis;step=Step.SURFACE;return;}
            frames=0;if(DEATH){oldClientPlayer=c.player;step=Step.KILL;}else{requestedSurface=-1;step=Step.RELEASE;}
        }catch(Throwable error){dumpRenderFailure(error);finish("FAIL",error.toString());}
    }
    private static void dumpRenderFailure(Throwable original){
        if(failureDumped)return;failureDumped=true;
        try{
            var c=MinecraftClient.getInstance();var dir=c.runDirectory.toPath().resolve("torch-camera-first-render-failure");Files.createDirectories(dir);
            var out=new LinkedHashMap<String,Object>();out.put("originalFailure",original.toString());out.put("epoch",epoch);out.put("surface",axis);out.put("look",look);
            out.put("actualMatrices",List.copyOf(MATRICES));out.put("actualVertices",points);out.put("actualBakedReturns",baked);out.put("handDelta",handDelta);out.put("worldDelta",worldDelta);
            out.put("beforeHandEpoch",handBeforeEpoch);out.put("afterHandEpoch",handAfterEpoch);out.put("actualFramebuffers",List.of(beforeHandDrawFbo,afterHandDrawFbo,c.getFramebuffer().fbo));
            if(handBefore!=null)handBefore.writeTo(dir.resolve("before-hand.png"));if(handAfter!=null)handAfter.writeTo(dir.resolve("after-hand.png"));
            try(var finalImage=captureActualFramebuffer()){finalImage.writeTo(dir.resolve("actual-final.png"));if(points!=null)out.put("originalGoldPixels",gold(finalImage));}
            Files.writeString(dir.resolve("actual-raw-frame.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out)+"\n");
        }catch(Throwable captureError){System.err.println("TORCH_CAMERA_FAILURE_CAPTURE_UNOBSERVED "+captureError+" original="+original);}
    }
    private static void finish(String status,String why){
        if(done)return;done=true;DescentKeybinds.toggle.setPressed(false);
        try{var c=MinecraftClient.getInstance();var out=new LinkedHashMap<String,Object>();out.put("status",status);out.put("reason",why);out.put("step",step.name());out.put("samples",RESULTS);out.put("setupAndInput",EVENTS);
            out.put("ownerProbeCompleted",ownerChecked);out.put("surface",axis);out.put("look",look);out.put("latestActualServer",view);out.put("ordinaryDismountScope","separate actual connected-player GameTests; this observer has no vehicle");
            out.put("deathProfile",DEATH);out.put("nativeDeath",deathReceipt);out.put("nativeRespawn",respawnReceipt);out.put("nativeRespawnButton",respawnRequested);
            out.put("manualFrameAdoptions",0);out.put("perFramePoseWrites",0);out.put("sourceBinding",System.getProperty("drmd.gate.torchCameraSource","UNBOUND"));
            out.put("visibilityOracle","same-frame native hand contribution >=100 and same-pixel final achromatic correspondence >=100, maxRGBresidual2, pre-hand scalar residual>=8; previous88ce hue FAIL and f256 exact-retention FAIL remain separate");
            out.put("originalGoldHueAllSamplesPass",!RESULTS.isEmpty()&&RESULTS.stream().allMatch(r->Boolean.TRUE.equals(r.get("originalGoldHuePass"))));
            out.put("originalFirstHueSubject","88ce0587e4ba78fb7a8f67b656028bc362a3c08c");out.put("originalFirstHueStatus","FAIL");
            Files.writeString(c.runDirectory.toPath().resolve("torch-camera-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out)+"\n");
        }catch(Exception error){error.printStackTrace();}finally{closeHandImages();}System.out.println("TORCH_CAMERA_OUTCOME "+status+" "+why);MinecraftClient.getInstance().scheduleStop();
    }
}
