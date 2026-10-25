package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.screen.DescentSettingsScreen;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.gravity.*;
import com.terminaldetector.drmd.world.level.LowerWorldRecovery;
import com.terminaldetector.drmd.client.planet.PlanetClientState;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.*;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;

/** Disposable connected-client regressions for the supplied video audit, never a production entrypoint. */
public final class AuditRenderCheck {
    private static final Vec3d BASE=new Vec3d(12.5,205.5,30.5);
    private static final Vec3d[] AXES={new Vec3d(1,0,0),new Vec3d(-1,0,0),new Vec3d(0,1,0),new Vec3d(0,-1,0),new Vec3d(0,0,1),new Vec3d(0,0,-1)};
    private record HorizonCase(int height,int bank,int speed,boolean withDh) {}
    private static final List<HorizonCase> HORIZON=new ArrayList<>();
    private static boolean active,cameraOverride,settingsCaptured;
    private static volatile boolean ready,serverPassed;
    private static int phase,ticks,total,axis,scaleIndex,horizonIndex,horizonCases,frames,environmentIndex,profileIndex;
    private static String pending;
    private static Runnable after;
    private static UUID field;
    private static long horizonDrawStamp,horizonDraws,offBaselineDraws,profileBaselineDraws;
    private static int[] onPixels;
    private static Vec3d cameraPosition=BASE;
    private static final Set<String> SETTINGS_SEEN=new HashSet<>();
    private static final List<String> SETTINGS_VIEWPORTS=new ArrayList<>();
    private static final int[] SCALES={2,3,4};
    private AuditRenderCheck() {}
    public static boolean active() { return active; }
    public static void horizonDrawn() { if(active) {horizonDrawStamp=System.nanoTime();horizonDraws++;} }
    public static void overrideCamera(Camera camera) {
        if(active&&cameraOverride) ((CameraAccessor)camera).drmd$invokeSetPos(cameraPosition);
    }
    public static void start(MinecraftClient client) {
        if(!client.runDirectory.toPath().getFileName().toString().equals("shootcheck"))
            throw new IllegalStateException("Audit must run in its disposable world");
        active=true;phase=0;ticks=0;
        DescentConfig.cockpit=false;DescentConfig.hud=false;DescentConfig.weaponView=false;
        DescentConfig.cameraShake=false;client.options.setPerspective(Perspective.FIRST_PERSON);
        for(int y:new int[]{100,320,640,880,1024,1500,2150}) for(int bank:new int[]{0,90,180})
            for(int speed:new int[]{0,50,300,628}) for(boolean dh:new boolean[]{false,true}) HORIZON.add(new HorizonCase(y,bank,speed,dh));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!active || client.player==null) return;
            var player=server.getPlayerManager().getPlayer(client.player.getUuid());
            if(player==null) return;
            if(phase>=3) {player.setPosition(BASE);player.setVelocity(Vec3d.ZERO);DescentPlayerData.get(player).setFlightVelocity(Vec3d.ZERO);}
        });
        prepareLifecycle(client);
    }
    private static void prepareLifecycle(MinecraftClient client) {
        ready=false;
        client.getServer().execute(()->{
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            var world=player.getServerWorld();
            player.stopRiding();player.changeGameMode(GameMode.CREATIVE);player.setPosition(BASE);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,client.getServer());world.setTimeOfDay(6000);
            for(var entity:world.iterateEntities()) if(entity instanceof ProjectileEntity||entity instanceof DroneEntity||entity instanceof PyroShipEntity||entity instanceof RagdollEntity) entity.discard();
            var data=DescentPlayerData.get(player);FlightSystem.disable(player,data);
            for(int i=0;i<8;i++) {FlightSystem.toggle(player);if(!data.isEnabled()||player.hasVehicle())throw new IllegalStateException("Native H-on spawned a hull");FlightSystem.toggle(player);}
            var ship=ModEntities.PYRO_SHIP.create(world);ship.setPosition(BASE);world.spawnEntity(ship);
            data.setEnabled(true);player.startRiding(ship,true);FlightSystem.toggle(player);ship.tick();
            if(data.isEnabled()||player.hasVehicle()||ship.hasPassengers())throw new IllegalStateException("H-off rearmed by hull");
            ship.discard();serverPassed=true;ready=true;
        });
    }
    public static void tick(MinecraftClient client) {
        if(client.player==null||client.world==null||client.getServer()==null)throw new IllegalStateException("Audit lost connected player");
        if(++total>18000)throw new IllegalStateException("Audit timeout phase="+phase+" case="+horizonIndex);
        if(!ready||pending!=null)return;
        ticks++;
        if(phase==0) {
            if(ticks<25)return;
            if(!serverPassed||DescentClientState.enabled||client.player.hasVehicle()||DescentClientState.attitudeValid)
                throw new IllegalStateException("Server H-off did not restore client controls");
            queue("audit-flight-off",()->{phase=1;ticks=0;prepareGravity(client);});
        }else if(phase==1) {
            if(ticks<25)return;
            Vec3d up=FootGravitySystem.getUp(client.player);
            if(!FootGravitySystem.isActive(client.player)||up.dotProduct(AXES[axis])<.98)
                throw new IllegalStateException("Client did not adopt gravity axis "+axis);
            Vec3d eye=client.player.getEyePos().subtract(client.player.getPos());
            if(eye.distanceTo(up.multiply(client.player.getEyeHeight(client.player.getPose())))>.02)
                throw new IllegalStateException("Interaction eyes and gravity frame disagree");
            if(client.gameRenderer.getCamera().getPos().distanceTo(client.player.getCameraPosVec(1))>.2)
                throw new IllegalStateException("Rendered eye differs from interaction eye");
            queue("audit-gravity-"+axis,()->{phase=2;ticks=0;ClientPlayNetworking.send(new ModNetworking.ActionPayload("toggle"));});
        }else if(phase==2) {
            if(ticks<20)return;
            if(!DescentClientState.enabled||FootGravitySystem.isActive(client.player)||!client.world.isSpaceEmpty(client.player,client.player.getBoundingBox()))
                throw new IllegalStateException("Leaving local gravity did not restore free body clearance");
            queue("audit-gravity-release-"+axis,()->{if(++axis<AXES.length){phase=1;ticks=0;prepareGravity(client);}else prepareBounds(client);});
        }else if(phase==3) {
            if(ticks<25)return;
            if(!serverPassed||!DescentClientState.enabled||client.player.hasVehicle())throw new IllegalStateException("Lower-world recovery did not synchronize control");
            queue("audit-lower-recovery",()->{phase=4;ticks=0;prepareSettings(client);});
        }else if(phase==4) {
            if(ticks<5)return;
            if(client.getWindow().getScaleFactor()!=SCALES[scaleIndex]) {
                if(ticks<80)return;
                throw new IllegalStateException("Requested GUI scale was clamped: "+SCALES[scaleIndex]+" actual="+client.getWindow().getScaleFactor());
            }
            DescentConfig.hud=true;DescentConfig.cockpit=true;
            var screen=client.currentScreen;
            for(var child:screen.children())if(child instanceof ClickableWidget widget){
                if(widget.getX()<0||widget.getX()+widget.getWidth()>screen.width||widget.getY()<0||widget.getY()+widget.getHeight()>screen.height)
                    throw new IllegalStateException("Settings widget escaped scaled viewport");
                SETTINGS_SEEN.add(widget.getMessage().getString());
            }
            if(!settingsCaptured) {
                settingsCaptured=true;ticks=5;
                SETTINGS_VIEWPORTS.add("{\"requested\":"+SCALES[scaleIndex]+",\"actual\":"+client.getWindow().getScaleFactor()+",\"width\":"+client.getWindow().getFramebufferWidth()+",\"height\":"+client.getWindow().getFramebufferHeight()+"}");
                queue("audit-settings-scale-"+SCALES[scaleIndex],()->{});
            }
            if(ticks>5&&ticks<40)screen.mouseScrolled(screen.width/2.0,screen.height/2.0,0,-1);
            if(ticks==40)queue("audit-settings-bottom-"+SCALES[scaleIndex],()->{
                if(!SETTINGS_SEEN.contains(Text.translatable("options.drmd.controls").getString()))throw new IllegalStateException("Settings controls row unreachable");
                SETTINGS_SEEN.clear();client.setScreen(null);
                if(++scaleIndex<SCALES.length){ticks=0;prepareSettings(client);}else{
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),1280,720);
                    client.options.getGuiScale().setValue(2);client.onResolutionChanged();
                    phase=5;ticks=0;prepareHorizon(client);
                }
            });
        }else if(phase==5) {
            var spec=HORIZON.get(horizonIndex);
            if(ticks<=20)cameraPosition=new Vec3d(BASE.x+spec.speed()/20.0*ticks,spec.height(),BASE.z);
            if(ticks<25)return;
            boolean expected=spec.height()>com.terminaldetector.drmd.world.level.WorldLevels.SURFACE_TOP+10
                    && (!com.terminaldetector.drmd.world.compat.DistantHorizonsCompat.isPresent()||spec.withDh());
            if(expected&&(!PlanetClientState.INSTANCE.hasSeed()||System.nanoTime()-horizonDrawStamp>1_000_000_000L)) {
                if(ticks<160)return;
                throw new IllegalStateException("Enabled horizon failed to render "+spec);
            }
            queue(horizonName("on"),()->{DescentConfig.planetFloor=false;offBaselineDraws=horizonDraws;phase=6;ticks=0;});
        }else if(phase==6) {
            if(ticks<3)return;
            if(horizonDraws!=offBaselineDraws)throw new IllegalStateException("Disabled horizon still submitted geometry");
            queue(horizonName("off"),()->{
                horizonCases++;if(++horizonIndex<HORIZON.size()){phase=5;ticks=0;prepareHorizon(client);}else prepareInventory(client);
            });
        }else if(phase==7) {
            if(ticks==6) queue("audit-inventory",()->prepareSwarm(client));
        }else if(phase==8) {
            AuditSwarmProfile.tick(client);
        }else if(phase==9) {
            if(ticks==15)queue("audit-environment-"+environmentIndex,()->{
                if(++environmentIndex<4){ticks=0;prepareEnvironment(client);}else prepareProfile(client);
            });
        }else if(phase==10) {
            if(ticks<15)return;
            boolean hybrid=(profileIndex&2)!=0;
            if(!hybrid && horizonDraws!=profileBaselineDraws)throw new IllegalStateException("Hybrid OFF still submitted horizon geometry");
            if(hybrid && horizonDraws==profileBaselineDraws) {
                if(ticks<100)return;
                throw new IllegalStateException("Hybrid ON failed to resume composed horizon");
            }
            var desired=(profileIndex&1)==0?com.terminaldetector.drmd.physics.PhysicsProfile.STANDARD:com.terminaldetector.drmd.physics.PhysicsProfile.EXPANDED;
            if(DescentClientState.physicsProfile!=desired) {
                if(ticks<100)return;
                throw new IllegalStateException("Effective server physics did not synchronize: "+desired);
            }
            queue("audit-profile-"+profileIndex,()->{if(++profileIndex<4)prepareProfile(client);else finish(client);});
        }
    }
    private static void prepareGravity(MinecraftClient client) {
        ready=false;
        client.getServer().execute(()->{
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());var world=player.getServerWorld();
            if(field!=null)GravityFields.remove(field);
            FlightSystem.disable(player,DescentPlayerData.get(player));FootGravitySystem.forget(player.getUuid());
            for(var p:BlockPos.iterate(new BlockPos(9,202,27),new BlockPos(15,208,33))) {
                boolean wall=p.getX()==9||p.getX()==15||p.getY()==202||p.getY()==208||p.getZ()==27||p.getZ()==33;
                world.setBlockState(p,wall?Blocks.IRON_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),2);
            }
            Vec3d up=AXES[axis];
            Vec3d feet=BASE.add(up.x>0?-2.44:up.x<0?2.44:0,up.y>0?-2.44:up.y<0?2.44:0,up.z>0?-2.44:up.z<0?2.44:0);
            player.requestTeleport(feet.x,feet.y,feet.z);player.setYaw(Math.abs(up.z)>.5?-90:180);player.setPitch(0);
            field=UUID.randomUUID();GravityFields.put(new GravityFields.Field(field,world.getRegistryKey(),BlockPos.ofFloored(BASE),up.negate(),20,100,FieldShape.SPHERE,"audit gravity"));
            FootGravitySystem.tick(player);ModNetworking.syncPlayer(player,DescentPlayerData.get(player));ready=true;
        });
    }
    private static void prepareBounds(MinecraftClient client) {
        phase=3;ticks=0;ready=false;serverPassed=false;
        client.getServer().execute(()->{
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            if(field!=null)GravityFields.remove(field);FootGravitySystem.forget(player.getUuid());
            for(var p:BlockPos.iterate(new BlockPos(9,202,27),new BlockPos(15,208,33)))player.getWorld().setBlockState(p,Blocks.AIR.getDefaultState(),2);
            player.setPosition(BASE);player.calculateDimensions();LowerWorldRecovery.recoverOrRemember(player);
            player.setPosition(BASE.x,player.getWorld().getBottomY()-80,BASE.z);
            serverPassed=LowerWorldRecovery.recoverOrRemember(player)&&player.isAlive();ready=true;
        });
    }
    private static void prepareSettings(MinecraftClient client) {
        settingsCaptured=false;
        // Minecraft clamps scale 4 at 720p. Use 1080p for a real scale-4 check.
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(),scaleIndex==2?1920:1280,scaleIndex==2?1080:720);
        client.options.getGuiScale().setValue(SCALES[scaleIndex]);client.onResolutionChanged();client.setScreen(new DescentSettingsScreen(null));
        // Screen.init reloads saved settings; assert modal suppression with overlays enabled afterwards.
        DescentConfig.hud=true;DescentConfig.cockpit=true;
    }
    private static void prepareSwarm(MinecraftClient client) {
        client.setScreen(null);phase=8;ticks=0;cameraOverride=false;
        DescentConfig.planetFloor=false;DescentConfig.levelSky=false;DescentConfig.hud=false;DescentConfig.cockpit=false;
        ShipAttitudeClient.acceptPortalFrame(client.player,new Vec3d(0,0,-1),new Vec3d(0,1,0));
        AuditSwarmProfile.start(client,()->{phase=9;ticks=0;prepareEnvironment(client);});
    }
    private static void prepareEnvironment(MinecraftClient client) {
        cameraOverride=true;cameraPosition=new Vec3d(BASE.x,880,BASE.z);
        DescentConfig.planetFloor=true;DescentConfig.planetFloorWithDistantHorizons=true;
        DescentConfig.levelSky=(environmentIndex&1)!=0;
        client.options.getCloudRenderMode().setValue((environmentIndex&2)!=0?CloudRenderMode.FANCY:CloudRenderMode.OFF);
        var attitude=new com.terminaldetector.drmd.flight.ShipAttitude();
        attitude.setForwardUp(new Vec3d(0,-.5,-Math.sqrt(.75)),new Vec3d(0,Math.sqrt(.75),-.5));attitude.rollLocal(90);
        ShipAttitudeClient.acceptPortalFrame(client.player,attitude.forward(),attitude.up());
    }
    private static void prepareProfile(MinecraftClient client) {
        phase=10;ticks=0;
        profileBaselineDraws=horizonDraws;
        boolean hybrid=(profileIndex&2)!=0;
        DescentConfig.hybridRender=hybrid;DescentConfig.save();DescentConfig.hybridRender=!hybrid;DescentConfig.reload();
        if(DescentConfig.hybridRender!=hybrid)throw new IllegalStateException("Explicit Hybrid choice did not persist");
        ClientPlayNetworking.send(new ModNetworking.ActionPayload((profileIndex&1)==0?"physics_standard":"physics_expanded"));
    }
    private static void prepareInventory(MinecraftClient client) {
        phase=7;ticks=0;cameraOverride=false;
        client.options.getGuiScale().setValue(2);client.onResolutionChanged();client.setScreen(new AuditInventoryScreen());
    }
    private static void prepareHorizon(MinecraftClient client) {
        client.setScreen(null);cameraOverride=true;
        DescentConfig.cockpit=false;DescentConfig.hud=false;DescentConfig.weaponView=false;
        DescentConfig.hybridRender=true;DescentConfig.planetFloor=true;DescentConfig.levelSky=false;DescentConfig.skyUfoVirtualHull=false;
        DescentConfig.planetFloorWithDistantHorizons=HORIZON.get(horizonIndex).withDh();
        client.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);client.options.getFov().setValue(80);
        Vec3d forward=new Vec3d(0,-.5,-Math.sqrt(.75));Vec3d up=new Vec3d(0,Math.sqrt(.75),-.5);
        var attitude=new com.terminaldetector.drmd.flight.ShipAttitude();attitude.setForwardUp(forward,up);
        attitude.rollLocal(HORIZON.get(horizonIndex).bank());
        ShipAttitudeClient.acceptPortalFrame(client.player,attitude.forward(),attitude.up());
        cameraPosition=new Vec3d(BASE.x,HORIZON.get(horizonIndex).height(),BASE.z);onPixels=null;horizonDrawStamp=0;
    }
    private static String horizonName(String mode) {
        var c=HORIZON.get(horizonIndex);return "audit-horizon-"+c.height()+"-"+c.bank()+"-"+c.speed()+"-"+(c.withDh()?1:0)+"-"+mode;
    }
    static void queue(String name,Runnable next) {
        if(pending!=null)throw new IllegalStateException("Overlapping audit captures");
        pending=name;after=next;
    }
    public static void rendered() {
        if(!active||pending==null)return;
        var client=MinecraftClient.getInstance();
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var dir=client.runDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);image.writeTo(dir.resolve(pending+".png"));
            if(phase==7 && client.currentScreen instanceof AuditInventoryScreen atlas) {
                double factor=client.getWindow().getScaleFactor();
                var prints=new java.util.HashMap<String,Long>();
                for(int i=0;i<AuditInventoryScreen.IDS.length;i++) {
                    long hash=0xcbf29ce484222325L;
                    for(int y=0;y<(int)(32*factor);y++) for(int x=0;x<(int)(32*factor);x++) {
                        hash^=image.getColor((int)(atlas.iconX(i)*factor)+x,(int)(atlas.iconY(i)*factor)+y);
                        hash*=0x100000001b3L;
                    }
                    prints.put(AuditInventoryScreen.IDS[i],hash);
                }
                var eggPrints=new HashSet<Long>();
                for(var entry:prints.entrySet())if(entry.getKey().startsWith("spawn_egg"))eggPrints.add(entry.getValue());
                if(eggPrints.size()!=14)throw new IllegalStateException("Spawn egg model/tint rendering is duplicated: "+eggPrints.size());
                for(String[] pair:new String[][]{{"bomb_cluster","bomb_cluster_laser"},{"bomb_cluster_laser","bomb_cluster_virus_sculk"},
                        {"bomb_cluster_virus_sculk","bomb_cluster_virus_nether"},{"energy_cell","energy_orb_pickup"},{"shield_orb","targeting_core"},
                        {"portal_gun","portal_stabilizer"},{"weapon_d6_concussion","weapon_d6_flash"},{"weapon_d6_flak","weapon_d6_helix"},
                        {"weapon_d6_fusion","weapon_d6_omega"},{"weapon_d6_homing","weapon_d6_guided"},{"weapon_d6_plasma","weapon_d6_phoenix"},
                        {"weapon_d6_vulcan","weapon_d6_gauss"},{"weapon_d6_mega_missile","weapon_d6_earthshaker"},{"weapon_d6_smart_missile","weapon_d6_mercury"}})
                    if(prints.get(pair[0]).equals(prints.get(pair[1])))throw new IllegalStateException("Indistinguishable rendered item pair: "+java.util.Arrays.toString(pair));
                var legend=new StringBuilder("index\titem\tpixel_hash\n");
                for(int i=0;i<AuditInventoryScreen.IDS.length;i++)legend.append(i+1).append('\t').append(AuditInventoryScreen.IDS[i]).append('\t').append(Long.toHexString(prints.get(AuditInventoryScreen.IDS[i]))).append('\n');
                Files.writeString(client.runDirectory.toPath().resolve("inventory-models.tsv"),legend);
            }
            if(phase==5) {
                onPixels=new int[image.getWidth()*image.getHeight()];
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)onPixels[y*image.getWidth()+x]=image.getColor(x,y);
            }else if(phase==6) {
                var spec=HORIZON.get(horizonIndex);
                boolean expected=spec.height()>com.terminaldetector.drmd.world.level.WorldLevels.SURFACE_TOP+10&&(!com.terminaldetector.drmd.world.compat.DistantHorizonsCompat.isPresent()||spec.withDh());
                if(expected) {
                    int changed=0;
                    for(int y=0;y<image.getHeight();y+=2)for(int x=0;x<image.getWidth();x+=2) {
                        int a=onPixels[y*image.getWidth()+x],b=image.getColor(x,y);
                        if(Math.abs((a&255)-(b&255))+Math.abs(((a>>8)&255)-((b>>8)&255))+Math.abs(((a>>16)&255)-((b>>16)&255))>35)changed++;
                    }
                    if(changed<200)throw new IllegalStateException("Enabled horizon has no visible image contribution: "+spec+" changed="+changed);
                }
            }
            frames++;
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        pending=null;after.run();
    }
    private static void finish(MinecraftClient client) {
        try {
            Files.writeString(client.runDirectory.toPath().resolve("audit-ok.json"),"{\"lifecycleRoundTrips\":8,\"gravityAxes\":6,\"gravityRelease\":true,\"lowerRecovery\":true,\"modalHud\":true,\"inventoryDistinct\":true,\"swarmProfiles\":[128,256,512,1024],\"environmentComparisons\":4,\"profileCombinations\":4,\"settingsScales\":[2,3,4],\"settingsViewports\":["+String.join(",",SETTINGS_VIEWPORTS)+"],\"horizonCases\":"+horizonCases+",\"horizonOnPixels\":true,\"dhInstalled\":"+com.terminaldetector.drmd.world.compat.DistantHorizonsCompat.isPresent()+",\"frames\":"+frames+"}\n");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        active=false;cameraOverride=false;client.scheduleStop();
    }
}
