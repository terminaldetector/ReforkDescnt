package com.terminaldetector.drmd.gate;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.BackupPromptScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Nameable;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;

/** Isolated production-classpath client gate, not a replacement world backend or a gameplay mod. */
public final class CubicClientGate implements ClientModInitializer {
    private static final BlockPos HIGH=new BlockPos(8,2150,8),LOW=new BlockPos(8,-512,8);
    private static CubicClientGate instance;
    private boolean started,forward,pendingLowTeleport,serverCrossRequested;
    private volatile boolean serverCrossed;
    private volatile double serverCrossY,serverVelocityY;
    private boolean fluidSetup,lightRemoval;
    private volatile boolean serverFluidReady;
    private volatile int serverLight,serverSky;
    private boolean roofPlaced,roofRemoved;
    private static final BlockPos SKY_PROBE=new BlockPos(-8,2160,-8);
    private static final BlockPos WATER_SOURCE=new BlockPos(16,2162,0),WATER_PROBE=new BlockPos(15,2158,-1),
        LIGHT_SOURCE=new BlockPos(16,2160,-3),LIGHT_PROBE=new BlockPos(15,2159,-3);
    private int renderedFrames,captureFrames;
    private volatile boolean signWritten;
    private volatile boolean serverReady;
    private int phase,ticks,age;
    private String capture;
    private final List<String> results=new ArrayList<>();
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        instance=this;
    }
    public static void frameFinished() {if(instance!=null)instance.rendered();}
    private void rendered() {
        renderedFrames++;
        if(capture==null) {captureFrames=0;return;}
        if(++captureFrames<60)return;
        var client=MinecraftClient.getInstance();
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            Path dir=client.runDirectory.toPath().resolve("gate-frames");Files.createDirectories(dir);
            image.writeTo(dir.resolve(capture+".png"));
            if(capture.equals("high-client") || capture.equals("negative-client")) {
                int red=0;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                    int color=image.getColor(x,y),r=color&255,g=color>>8&255,b=color>>16&255;
                    if(r>60 && r>g*2.3 && r>b*2.3)red++;
                }
                if(red<100)throw new IllegalStateException("Native cube marker has no visible geometry: "+capture+" red="+red);
                System.out.println("CUBIC_PIXELS "+capture+" red="+red+" camera="+client.gameRenderer.getCamera().getPos()
                    +" yaw="+client.gameRenderer.getCamera().getYaw()+" pitch="+client.gameRenderer.getCamera().getPitch());
            }
            capture=null;captureFrames=0;
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private void tick(MinecraftClient client) {
        if(phase==4)return;
        if(++ticks>4200)throw new IllegalStateException("Cubic client gate timeout phase="+phase+" age="+age);
        if(ticks%100==0)System.out.println("CUBIC_GATE phase="+phase+" age="+age+" screen="+(client.currentScreen==null?"none":client.currentScreen.getClass().getSimpleName())
                +" player="+(client.player==null?"none":client.player.getPos()));
        if(client.currentScreen instanceof BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof ButtonWidget button && button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(client.player==null || client.world==null || client.getServer()==null)return;
        if(!started) {
            if(!client.runDirectory.toPath().getFileName().toString().equals("cubic-client-automation"))throw new IllegalStateException("Disposable cubic runtime required");
            if(!FabricLoader.getInstance().isModLoaded("bettercubicchunks"))throw new IllegalStateException("No actual cubic backend");
            if(client.world.getBottomY()!=-16384 || client.world.getHeight()!=32768)
                throw new IllegalStateException("Wrong authoritative dimension bounds: "+client.world.getBottomY()+"/"+client.world.getHeight());
            started=true;client.setScreen(null);client.options.hudHidden=true;
            client.getToastManager().clear();
            for(String option:new String[]{"hud","cockpit","weaponView","planetFloor","levelSky"}) {
                try {Class.forName("com.terminaldetector.drmd.client.config.DescentConfig").getField(option).setBoolean(null,false);}
                catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            }client.inGameHud.getChatHud().clear(false);
            client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
            teleport(client,2152);return;
        }
        if(!serverReady || capture!=null)return;
        age++;
        if(age%100==0 && (phase==0 || phase==2)) {
            var pos=phase==0?HIGH:LOW;var be=client.world.getBlockEntity(pos);
            System.out.println("CUBIC_DATA pos="+pos+" block="+client.world.getBlockState(pos)+" be="+(be==null?"null":be.getClass().getName())
                +" name="+(be instanceof Nameable n?n.getName().getString():"none"));
        }
        if(phase==0) {
            if(Math.abs(client.player.getY()-2152)>5 || !(client.world.getBlockEntity(HIGH) instanceof ChestBlockEntity))return;
            if(!signWritten) {publishSign(client,HIGH,"gate-v5-Y2150");return;}
            if(!signArrived(client,HIGH))return;
            orient(client,new Vec3d(0,-1,-1),new Vec3d(0,1,-1));
            checkRaycast(client,HIGH);results.add("high_container_type_and_synced_sign_nbt");capture="high-client";phase=1;age=0;
            if(!flightEnabled())toggleFlight();
        }else if(phase==1) {
            if(!flightEnabled())return;
            // Actual normal forward input and DRMD's public orientation API, no position stepping.
            orientUp(client);client.options.forwardKey.setPressed(true);forward=true;
            if(client.player.getY()<2165) {
                if(age>300)throw new IllegalStateException("6DoF did not cross vertical cube seam: "+client.player.getPos());
                return;
            }
            if(!serverCrossRequested) {
                serverCrossRequested=true;
                client.getServer().execute(()->{
                    var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                    serverCrossY=player.getY();
                    try {
                        var type=Class.forName("com.terminaldetector.drmd.DescentPlayerData");
                        var data=type.getMethod("get",net.minecraft.entity.player.PlayerEntity.class).invoke(null,player);
                        serverVelocityY=((Vec3d)type.getMethod("getFlightVelocity").invoke(data)).y;
                    }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                    if(serverCrossY<2160 || serverVelocityY<=0)throw new IllegalStateException("Server did not confirm upward 6DoF seam crossing: y="+serverCrossY+" velocity="+serverVelocityY);
                    System.out.println("CUBIC_SERVER_FLIGHT y="+serverCrossY+" velocityY="+serverVelocityY);serverCrossed=true;
                });
            }
            if(!serverCrossed)return;
            client.options.forwardKey.setPressed(false);forward=false;
            if(!client.world.getBlockState(new BlockPos(1,2159,1)).isOf(Blocks.GOLD_BLOCK)
                    || !client.world.getBlockState(new BlockPos(1,2160,1)).isOf(Blocks.DIAMOND_BLOCK))
                throw new IllegalStateException("Adjacent native cube marker packets missing after flight");
            results.add("six_dof_vertical_seam");capture="seam-flight";phase=10;age=0;
            signWritten=false;
        }else if(phase==10) {
            if(!fluidSetup) {
                if(!cubeLoaded(client,WATER_SOURCE) || !cubeLoaded(client,WATER_PROBE) || !cubeLoaded(client,LIGHT_PROBE))return;
                fluidSetup=true;serverReady=false;
                client.getServer().execute(()->{
                    var world=client.getServer().getOverworld();
                    // The movement sample was recorded before this fixture-only stabilization.
                    var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                    try {
                        var type=Class.forName("com.terminaldetector.drmd.DescentPlayerData");
                        var data=type.getMethod("get",net.minecraft.entity.player.PlayerEntity.class).invoke(null,player);
                        type.getMethod("setFlightVelocity",Vec3d.class).invoke(data,Vec3d.ZERO);
                    }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                    player.setVelocity(Vec3d.ZERO);
                    for(int x=14;x<=18;x++)for(int z=-2;z<=2;z++)for(int y=2157;y<=2163;y++) {
                        boolean boundary=y==2157 || x==14 || x==18 || z==-2 || z==2;
                        world.setBlockState(new BlockPos(x,y,z),boundary?Blocks.GLASS.getDefaultState():Blocks.AIR.getDefaultState(),3);
                    }
                    world.setBlockState(WATER_SOURCE,Blocks.WATER.getDefaultState(),3);
                    world.setBlockState(LIGHT_SOURCE,Blocks.GLOWSTONE.getDefaultState(),3);
                    serverReady=true;
                });return;
            }
            if(age%10==0)client.getServer().execute(()->{
                var world=client.getServer().getOverworld();
                serverFluidReady=world.getFluidState(WATER_PROBE).isIn(net.minecraft.registry.tag.FluidTags.WATER);
                serverLight=world.getLightLevel(net.minecraft.world.LightType.BLOCK,LIGHT_PROBE);
            });
            int light=client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,LIGHT_PROBE);
            boolean water=client.world.getFluidState(WATER_PROBE).isIn(net.minecraft.registry.tag.FluidTags.WATER);
            if(age%100==0)System.out.println("CUBIC_BOUNDARY water="+water+" serverWater="+serverFluidReady+" light="+light+" serverLight="+serverLight);
            if(!water || !serverFluidReady || light!=13 || serverLight!=13) {
                if(age>400)throw new IllegalStateException("Native fluid/light cross-cube propagation failed: water="+water+" serverWater="+serverFluidReady+" light="+light+" serverLight="+serverLight);
                return;
            }
            results.add("native_water_cross_xyz_cube_boundaries");results.add("native_blocklight_cross_xy_boundary");
            Vec3d direction=new Vec3d(16.5,2160.5,.5).subtract(client.player.getEyePos()).normalize();
            orient(client,direction,new Vec3d(0,1,0).subtract(direction.multiply(direction.y)));
            capture="fluid-boundaries";phase=11;age=0;
        }else if(phase==11) {
            if(!lightRemoval) {
                lightRemoval=true;serverLight=15;
                client.getServer().execute(()->client.getServer().getOverworld().setBlockState(LIGHT_SOURCE,Blocks.AIR.getDefaultState(),3));
                return;
            }
            if(age%10==0)client.getServer().execute(()->serverLight=client.getServer().getOverworld().getLightLevel(net.minecraft.world.LightType.BLOCK,LIGHT_PROBE));
            int light=client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,LIGHT_PROBE);
            if(light>=13 || serverLight>=13) {
                if(age>400)throw new IllegalStateException("Removed blocklight persisted across cube boundary: "+light+"/"+serverLight);
                return;
            }
            results.add("native_blocklight_removal");phase=12;age=0;
        }else if(phase==12) {
            if(!roofPlaced) {
                if(!cubeLoaded(client,SKY_PROBE))return;
                if(!client.world.getBlockState(SKY_PROBE).isAir())throw new IllegalStateException("Sky probe fixture is not air before roof");
                if(client.world.getLightLevel(net.minecraft.world.LightType.SKY,SKY_PROBE)!=15)
                    throw new IllegalStateException("Native open-sky cube has no full skylight");
                roofPlaced=true;serverSky=15;
                client.getServer().execute(()->setRoof(client,true));return;
            }
            if(age%10==0)client.getServer().execute(()->serverSky=client.getServer().getOverworld().getLightLevel(net.minecraft.world.LightType.SKY,SKY_PROBE));
            int sky=client.world.getLightLevel(net.minecraft.world.LightType.SKY,SKY_PROBE);
            if(sky>=15 || serverSky>=15) {
                if(age>400)throw new IllegalStateException("Opaque roof did not shadow native cube: "+sky+"/"+serverSky);
                return;
            }
            results.add("native_skylight_occlusion");phase=13;age=0;
        }else if(phase==13) {
            if(!roofRemoved) {
                roofRemoved=true;serverSky=0;
                client.getServer().execute(()->setRoof(client,false));return;
            }
            if(age%10==0)client.getServer().execute(()->serverSky=client.getServer().getOverworld().getLightLevel(net.minecraft.world.LightType.SKY,SKY_PROBE));
            if(Boolean.getBoolean("drmd.gate.tryNativeRelight") && (age==100 || age==200 || age==300 || age==400)) {
                int step=age;
                client.getServer().execute(()->probeNativeRelight(client,step));
            }
            int sky=client.world.getLightLevel(net.minecraft.world.LightType.SKY,SKY_PROBE);
            if(sky!=15 || serverSky!=15) {
                if(age>(Boolean.getBoolean("drmd.gate.tryNativeRelight")?600:400))throw new IllegalStateException("Removed roof left stale native skylight: "+sky+"/"+serverSky+" probe="+client.world.getBlockState(SKY_PROBE)
                    +" above="+client.world.getBlockState(SKY_PROBE.up()));
                return;
            }
            results.add("native_skylight_restoration");phase=2;age=0;pendingLowTeleport=true;
        }else if(phase==2) {
            if(pendingLowTeleport) {
                pendingLowTeleport=false;if(flightEnabled())toggleFlight();teleport(client,-509);return;
            }
            if(Math.abs(client.player.getY()+509)>5 || !(client.world.getBlockEntity(LOW) instanceof BarrelBlockEntity))return;
            if(!signWritten) {publishSign(client,LOW,"gate-v5-negative");return;}
            if(!signArrived(client,LOW))return;
            checkRaycast(client,LOW);results.add("negative_container_type_and_synced_sign_nbt");capture="negative-client";phase=3;age=0;
        }else if(phase==3) {
            // Allow the backend's normal unload grace period. Never inject /bcc load tickets.
            if(age<100)return;
            if(cubeLoaded(client,HIGH) || client.world.getBlockEntity(HIGH)!=null) {
                if(age>1500)throw new IllegalStateException("Far vertical cube's BE still retained while the negative cube is loaded");
                return;
            }
            if(!cubeLoaded(client,LOW) || !(client.world.getBlockEntity(LOW) instanceof BarrelBlockEntity))throw new IllegalStateException("Near negative cube unloaded with distant cube");
            results.add("independent_vertical_be_unload");
            finish(client);
        }
    }
    private void teleport(MinecraftClient client,double y) {
        serverReady=false;age=0;
        client.getServer().execute(()->{
            var server=client.getServer();var player=server.getPlayerManager().getPlayer(client.player.getUuid());
            if(player==null)throw new IllegalStateException("No authoritative player");
            player.changeGameMode(GameMode.CREATIVE);
            var world=player.getServerWorld();world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,server);world.setTimeOfDay(6000);
            player.teleport(world,8.5,y,11.5,Set.of(),180,45);
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),"bcc status");
            serverReady=true;
        });
    }
    /** Experimental API probe only; a passing probe is not a shipped lighting fix. */
    private static void probeNativeRelight(MinecraftClient client,int step) {
        var world=client.getServer().getOverworld();var chunk=world.getWorldChunk(SKY_PROBE);
        var sky=chunk.getChunkSkyLight();
        try {
            var api=Class.forName("dev.fallingcloud.bettercubicchunks.chunk.ISkyLightSourcesExt");
            var source=api.getMethod("bcc$loadedSourceY",int.class,int.class);
            Object before=source.invoke(sky,SKY_PROBE.getX()&15,SKY_PROBE.getZ()&15);
            Object state=api.getMethod("bcc$columnState").invoke(sky);
            var stateType=state.getClass();int x=SKY_PROBE.getX()&15,z=SKY_PROBE.getZ()&15;
            Object occlusion=stateType.getMethod("occlusionHeight",int.class,int.class).invoke(state,x,z);
            boolean above=(Boolean)stateType.getMethod("allGeneratedCubesLoadedAbove",int.class).invoke(state,world.getSectionIndex(SKY_PROBE.getY()));
            System.out.println("CUBIC_SKY_SOURCE step="+step+" loaded="+before+" raw="+sky.get(x,z)+" occlusion="+occlusion
                +" max="+sky.getMaxSurfaceY()+" allGeneratedAboveLoaded="+above);
            if(step==400) {
                int cached=(Integer)occlusion,index=world.getSectionIndex(cached-1);
                Object status=stateType.getMethod("status",int.class).invoke(state,index);
                boolean emptyTop=world.getBlockState(new BlockPos(SKY_PROBE.getX(),cached-1,SKY_PROBE.getZ())).isAir();
                int conservativeFloor=Math.floorDiv(cached-1,16)*16;
                System.out.println("CUBIC_OCCLUSION_REPAIR status="+status+" emptyTop="+emptyTop+" floor="+conservativeFloor);
                if(String.valueOf(status).equals("FULL") && emptyTop) {
                    int bound=Math.max((Integer)before,conservativeFloor);
                    if(bound<cached)stateType.getMethod("setOcclusion",int.class,int.class,int.class).invoke(state,x,z,bound);
                }
            }
            api.getMethod("bcc$sectionChanged",net.minecraft.world.chunk.Chunk.class,int.class)
                .invoke(sky,chunk,world.getSectionIndex(SKY_PROBE.getY()));
            Object after=source.invoke(sky,SKY_PROBE.getX()&15,SKY_PROBE.getZ()&15);
            var light=world.getChunkManager().getLightingProvider();
            light.checkBlock(SKY_PROBE.up());light.checkBlock(SKY_PROBE);
            if(step>=200)light.propagateLight(chunk.getPos());
            if(step>=300) {
                light.setSectionStatus(net.minecraft.util.math.ChunkSectionPos.from(SKY_PROBE),false);
                light.propagateLight(chunk.getPos());
            }
            System.out.println("CUBIC_RELIGHT_PROBE step="+step+" sourceBefore="+before+" sourceAfter="+after+" level="+world.getLightLevel(net.minecraft.world.LightType.SKY,SKY_PROBE));
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void setRoof(MinecraftClient client,boolean opaque) {
        var world=client.getServer().getOverworld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setBlockState(SKY_PROBE.add(x,1,z),opaque?Blocks.REDSTONE_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),3);
    }
    private void publishSign(MinecraftClient client,BlockPos container,String expectedName) {
        // Vanilla containers do not include their private name/inventory in initial chunk NBT.
        // Validate the stored name on the server and use a sign, which actually syncs its NBT,
        // to verify a real client-bound BE update at the same native cube height.
        signWritten=true;serverReady=false;
        client.getServer().execute(()->{
            var world=client.getServer().getOverworld();var be=world.getBlockEntity(container);
            if(!(be instanceof Nameable n) || !n.getName().getString().equals(expectedName))
                throw new IllegalStateException("Saved native container name missing: "+container);
            var sign=container.east();
            if(container.getY()<0) {
                for(var point:BlockPos.iterate(container.add(-2,1,-2),container.add(3,7,4)))
                    world.setBlockState(point,Blocks.AIR.getDefaultState(),3);
            }
            for(int x=container.getX()-2;x<=container.getX()+3;x++)for(int y=container.getY();y<=container.getY()+5;y++)
                world.setBlockState(new BlockPos(x,y,container.getZ()-2),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            world.setBlockState(sign.down(),Blocks.IRON_BLOCK.getDefaultState(),3);
            world.setBlockState(sign,Blocks.OAK_SIGN.getDefaultState(),3);
            var entity=(SignBlockEntity)world.getBlockEntity(sign);
            if(entity==null)throw new IllegalStateException("Native sign BE not created");
            entity.setText(entity.getFrontText().withMessage(0,Text.literal("native-Y="+container.getY())),true);
            entity.markDirty();world.getChunkManager().markForUpdate(sign);
            world.setBlockState(container.add(-2,2,2),Blocks.GLOWSTONE.getDefaultState(),3);
            serverReady=true;
        });
    }
    private static boolean signArrived(MinecraftClient client,BlockPos container) {
        return client.world.getBlockEntity(container.east()) instanceof SignBlockEntity sign
            && sign.getFrontText().getMessage(0,false).getString().equals("native-Y="+container.getY());
    }
    private static boolean cubeLoaded(MinecraftClient client,BlockPos pos) {
        try {
            return (Boolean)Class.forName("dev.fallingcloud.bettercubicchunks.client.ClientCubes")
                .getMethod("isCubeLoaded",net.minecraft.world.World.class,BlockPos.class).invoke(null,client.world,pos);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void checkRaycast(MinecraftClient client,BlockPos container) {
        if(!cubeLoaded(client,container))throw new IllegalStateException("BE present without an authoritative loaded native cube");
        Vec3d from=new Vec3d(container.getX()+.5,container.getY()+1.5,container.getZ()+3.5);
        Vec3d to=new Vec3d(container.getX()+.5,container.getY()+1.5,container.getZ()-2.5);
        var hit=client.world.raycast(new net.minecraft.world.RaycastContext(from,to,
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,client.player));
        if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK || !client.world.getBlockState(hit.getBlockPos()).isOf(Blocks.REDSTONE_BLOCK))
            throw new IllegalStateException("Native cube collider raycast missed marker at "+container);
    }
    private static boolean flightEnabled() {
        try{return Class.forName("com.terminaldetector.drmd.client.DescentClientState").getField("enabled").getBoolean(null);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void toggleFlight() {
        try {
            var payload=(CustomPayload)Class.forName("com.terminaldetector.drmd.network.ModNetworking$ActionPayload").getConstructor(String.class).newInstance("toggle");
            ClientPlayNetworking.send(payload);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void orientUp(MinecraftClient client) {orient(client,new Vec3d(0,1,0),new Vec3d(0,0,1));}
    private static void orient(MinecraftClient client,Vec3d forward,Vec3d up) {
        try {
            Class.forName("com.terminaldetector.drmd.client.flight.ShipAttitudeClient")
                    .getMethod("acceptPortalFrame",net.minecraft.client.network.ClientPlayerEntity.class,Vec3d.class,Vec3d.class)
                    .invoke(null,client.player,forward.normalize(),up.normalize());
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private void finish(MinecraftClient client) {
        phase=4;client.options.forwardKey.setPressed(false);
        try {
            Files.writeString(client.runDirectory.toPath().resolve("cubic-client-ok.json"),
                "{\"status\":\""+(Boolean.getBoolean("drmd.gate.tryNativeRelight")?"PASS_WITH_EXPERIMENTAL_RELIGHT_PROBE_NOT_SHIPPED":"CLIENT_SUBSET_PASS_FULL_GATE_PENDING")+"\",\"serverFlightY\":"+serverCrossY+",\"serverFlightVelocityY\":"+serverVelocityY+",\"renderedFrames\":"+renderedFrames+",\"checks\":[\""+String.join("\",\"",results)+"\"]}\n");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        client.scheduleStop();
    }
}
