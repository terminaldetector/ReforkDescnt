package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClient;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.planet.HorizonLandmark;
import com.terminaldetector.drmd.client.presentation.*;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.planet.AuthoredOrbitalCatalogue;
import com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.util.*;

/** Read an explicitly restored closed native donor; no world authoring, certification or mining. */
public final class NativeIntactAsteroidCheck {
    public static final boolean STATION=Boolean.getBoolean("drmd.gate.intactStation");
    public static final boolean ENABLED=STATION||Boolean.getBoolean("drmd.gate.intactAsteroid");
    private static final boolean FOG_PROOF=!STATION&&Boolean.getBoolean("drmd.gate.intactAsteroidFog");
    private static boolean fogProbe;
    private static final String PLAN="b0c0be5305a458b766aea230a700f435b95e2279328a5afb6a9b2588e4fef8d9";
    private static final Vec3d CENTRE=STATION?new Vec3d(-1112,1416,1206):new Vec3d(-1072,1444,1176);
    private static final int TOTAL_CELLS=STATION?7680:2304;
    private static final String[] STATION_EXPECTED=stationExpected();
    private static String[] stationExpected(){
        if(!STATION)return new String[0];
        var states=new String[7680];Arrays.fill(states,"minecraft:air");
        for(var v:OrbitalObjectGeometry.cells(OrbitalObjectGeometry.Shape.STATION,24,16,20))states[v.x()+24*(v.z()+20*v.y())]=v.state();
        return states;
    }
    private static final List<Map<String,Object>> ROWS=new ArrayList<>();
    private static UUID playerId;
    private static volatile int serverStage=-1,verified;
    private static volatile boolean loading,serverReady;
    private static volatile String failure;
    private static boolean started,done;
    private static volatile int stage;
    private static int ticks,phaseTicks,setupTeleports;
    private static long deadline;
    private static int renderedBits;
    private NativeIntactAsteroidCheck(){}
    public static void register(){
        deadline=System.nanoTime()+260_000_000_000L;
        ClientTickEvents.END_CLIENT_TICK.register(NativeIntactAsteroidCheck::tick);
    }
    static boolean fogProbe(){return FOG_PROOF&&fogProbe&&!done;}
    public static Vec3d centre(){return CENTRE;}
    public static boolean selected(HorizonLandmark mark){
        return mark.kind()==(STATION?MacroEntry.Kind.ORBITAL_STATION:MacroEntry.Kind.ASTEROID)
            &&mark.x()==CENTRE.x&&mark.y()==CENTRE.y&&mark.z()==CENTRE.z
            &&mark.sizeX()==(STATION?24:16)&&mark.sizeY()==(STATION?16:12)&&mark.sizeZ()==(STATION?20:12);
    }
    public static int viewCase(){
        if(!ENABLED||done||!serverReady||serverStage!=stage)return -1;
        var c=MinecraftClient.getInstance();if(c.player==null||c.currentScreen!=null||com.terminaldetector.drmd.client.portal.OffscreenWorldView.busy())return -1;
        if(DescentClientState.enabled||!c.player.getAbilities().flying||c.player.getPos().distanceTo(goal())>.02)return -1;
        var eye=c.gameRenderer.getCamera().getPos();
        float pitch=(float)Math.toDegrees(Math.atan2(c.player.getEyeY()-(CENTRE.y+.5),CENTRE.z-c.player.getZ()));
        var camera=c.gameRenderer.getCamera();
        return eye.distanceTo(new Vec3d(c.player.getX(),c.player.getEyeY(),c.player.getZ()))<.02
            &&Math.abs(camera.getYaw())<=.1&&Math.abs(camera.getPitch()-pitch)<=.1?stage:-1;
    }
    private static Vec3d goal(){return CENTRE.add(.5,0,.5-(stage==0?24:stage==1?512:3072));}
    private static void require(boolean condition,String why){if(!condition)throw new IllegalStateException(why);}
    private static void serverPulse(MinecraftClient c,int expectedStage){
        loading=true;
        c.getServer().execute(()->{
            try{
                var p=c.getServer().getPlayerManager().getPlayer(playerId);require(p!=null,"same connected reader");
                var w=p.getServerWorld();require(CubicAvailability.backend(w)==CubicAvailability.Backend.NATIVE_CUBIC,"real BCC donor backend");
                require(w.getSeed()==271026,"original native donor seed");
                var entries=AuthoredOrbitalCatalogue.visible(w);
                require(entries.size()==3&&entries.stream().allMatch(e->e.planHash().equals(PLAN)),"all three original intact donor certificates, no mining or substitute catalogue");
                var e=entries.stream().filter(q->q.object().id().equals(STATION?"abandoned_station":"outer_asteroid")).findFirst().orElseThrow();var o=e.object();
                require(o.shape()==(STATION?OrbitalObjectGeometry.Shape.STATION:OrbitalObjectGeometry.Shape.ASTEROID)
                    &&o.origin().x()==(STATION?-1124:-1080)&&o.origin().y()==(STATION?1408:1438)&&o.origin().z()==(STATION?1196:1170)
                    &&o.sizeX()==(STATION?24:16)&&o.sizeY()==(STATION?16:12)&&o.sizeZ()==(STATION?20:12),"same original selected donor object");
                if(serverStage!=expectedStage){
                    var g=goal();p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));
                    p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();
                    p.teleport(w,g.x,g.y,g.z,Set.of(),0,0);p.setVelocity(Vec3d.ZERO);
                    serverStage=expectedStage;serverReady=false;setupTeleports++;return;
                }
                if(p.getPos().distanceTo(goal())>.02)return;
                if(expectedStage==0){
                    var access=WorldAccess.of(w);int end=Math.min(TOTAL_CELLS,verified+64);
                    while(verified<end){
                        int sx=STATION?24:16,sz=STATION?20:12;
                        int x=verified%sx,z=(verified/sx)%sz,y=verified/(sx*sz);String expected;
                        if(STATION)expected=STATION_EXPECTED[verified];
                        else{
                            long dx=2L*x+1-16,dy=2L*y+1-12,dz=2L*z+1-12;
                            boolean solid=dx*dx*144*144+dy*dy*256*144+dz*dz*256*144<=256L*144*144;
                            expected=solid?"minecraft:stone":"minecraft:air";
                        }
                        var state=access.readIfLoaded(new BlockPos((STATION?-1124:-1080)+x,(STATION?1408:1438)+y,(STATION?1196:1170)+z));if(state.isEmpty())return;
                        require(Registries.BLOCK.getId(state.get().getBlock()).toString().equals(expected),"loaded-only original donor cell "+verified);
                        verified++;
                    }
                    if(verified<TOTAL_CELLS)return;
                }
                require(!DescentPlayerData.get(p).isEnabled()&&p.isCreative()&&p.getAbilities().flying,"ordinary actual Creative hover, H off");
                serverReady=true;
            }catch(Throwable problem){failure=problem.toString();}finally{loading=false;}
        });
    }
    private static void tick(MinecraftClient c){
        if(done)return;
        try{
            require(++ticks<=2200&&System.nanoTime()<deadline,"finite reader deadline");
            if(failure!=null)throw new IllegalStateException(failure);
            DescentClient.markUserFlightChoice();
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
                for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(c.player==null||c.world==null||c.getServer()==null)return;
            if(!started){
                require(c.runDirectory.getName().equals("cubic-client-automation")&&NativeOrbitalMaskCheck.ENABLED,"isolated native donor and existing mask observer required");
                require(!NativeOrbitalMissionCheck.ENABLED&&!NativeBdhCubicCheck.ENABLED,"reader cannot run author/mining/BDH drivers");
                require(!(STATION&&Boolean.getBoolean("drmd.gate.intactAsteroid")),"select exactly one intact donor reader");
                playerId=c.player.getUuid();started=true;c.options.setPerspective(Perspective.FIRST_PERSON);c.options.getViewDistance().setValue(4);
                c.options.getCloudRenderMode().setValue(CloudRenderMode.FANCY);c.options.getFov().setValue(70);c.getToastManager().clear();
                DescentConfig.hybridRender=DescentConfig.planetFloor=DescentConfig.levelSky=true;
                DescentConfig.presentationProfile=PresentationProfile.PS1_HYBRID;DescentConfig.ps1SceneHeight=360;
                DescentConfig.ps1FarFog=new Ps1FarFogOptions(0); // Original default; this case proves geometry, not enabled haze.
            }
            require(++phaseTicks<=320,"same original320tick view admission bound: "+NativeOrbitalMaskCheck.fieldViewDiagnostic());
            if(!loading&&!serverReady)serverPulse(c,stage);
            c.player.setYaw(0);double ahead=CENTRE.z-c.player.getZ();
            c.player.setPitch((float)Math.toDegrees(Math.atan2(c.player.getEyeY()-(CENTRE.y+.5),ahead)));
            require(NativeOrbitalMaskCheck.fieldViewFailure()==null,"actual orbital draw failure: "+NativeOrbitalMaskCheck.fieldViewFailure());
            if((renderedBits&(1<<stage))==0)return;
            if(stage==2){
                if(FOG_PROOF&&!NativeOrbitalMaskCheck.asteroidFogObserved()){
                    if(!fogProbe){fogProbe=true;phaseTicks=0;}
                    return;
                }
                finish(c,"PASS",FOG_PROOF
                    ?"same closed native asteroid near/MID/FAR and real FAR paired fog/neutral colour"
                    :"same intact native "+(STATION?"original station":"outer asteroid")+" near/MID/FAR in actual PS1 world; not an atmosphere or physical-flight proof");
                return;
            }
            stage++;phaseTicks=0;serverReady=false;
        }catch(Throwable problem){finish(c,"FAIL",problem.toString());}
    }
    /** Actual GameRenderer RETURN, after the existing independent colour+semantic observer. */
    public static void rendered(){
        if(!ENABLED||done||viewCase()<0||!NativeOrbitalMaskCheck.fieldViewObserved(stage)||(renderedBits&(1<<stage))!=0)return;
        var c=MinecraftClient.getInstance();
        try{
            var stats=ScenePresentation.stats();require(stats.profile()==PresentationProfile.PS1_HYBRID&&stats.sceneHeight()==360
                &&stats.sceneWidth()<stats.outputWidth()&&stats.fallbackFrames()==0&&!stats.inWorldPass(),"actual low-resolution PS1 scene completed without fallback");
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"actual completed scene GL error");
            var eye=c.gameRenderer.getCamera().getPos();ROWS.add(Map.of("stage",stage,"eye",List.of(eye.x,eye.y,eye.z),"verifiedOriginalCells",verified,
                "completedMaskFrames",8,"yaw",c.gameRenderer.getCamera().getYaw(),"pitch",c.gameRenderer.getCamera().getPitch(),"scene",stats.toString(),"actualAdmission",NativeOrbitalMaskCheck.fieldViewDiagnostic(),"setupTeleports",setupTeleports));
            renderedBits|=1<<stage;
        }catch(Throwable problem){finish(c,"FAIL",problem.toString());}
    }
    private static void finish(MinecraftClient c,String status,String why){
        if(done)return;done=true;
        try{var report=new LinkedHashMap<String,Object>();report.put("status",status);report.put("intactStationMode",STATION);report.put("reason",why);report.put("stage",stage);report.put("verifiedOriginalCells",verified);
            report.put("intactAsteroidFogMode",FOG_PROOF);report.put("asteroidFogPairObserved",NativeOrbitalMaskCheck.asteroidFogObserved());
            report.put("rows",ROWS);report.put("setupTeleports",setupTeleports);report.put("fixtureBlockWrites",0);report.put("fixtureCatalogueWrites",0);report.put("actualReadback",NativeOrbitalMaskCheck.fieldViewDiagnostic());
            Files.writeString(c.runDirectory.toPath().resolve(STATION?"intact-station-outcome.json":"intact-asteroid-outcome.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception problem){throw new IllegalStateException(problem);}finally{c.scheduleStop();}
    }
}
