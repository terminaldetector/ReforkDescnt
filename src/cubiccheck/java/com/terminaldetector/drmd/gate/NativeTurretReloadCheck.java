package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.client.flight.*;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.world.mega.*;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Actual closed-world writer/reader, never creates a body from the saved verification data. */
public final class NativeTurretReloadCheck {
    public static final boolean WRITE=Boolean.getBoolean("drmd.gate.turretReloadWrite"),READ=Boolean.getBoolean("drmd.gate.turretReloadRead");
    private static SkyUfoEntity root;private static BlockBodyEntity head;private static ServerPlayerEntity pilot;
    private static UUID rootId,headId,jointId;private static volatile boolean registryExact,rootExact,headExact,writerActive,writerStop,writerClosed;
    private static volatile int phase,writerSamples,lasers,rockets,aimPackets,firePackets,postSamples,barrierSamples,headDraws,exteriorDraws;
    private static int ticks,controlAge,imageFrames,aimTicks;private static boolean walkingDone,exteriorAimed,done;
    private static double maxAnchor,muzzleError,velocityError,angleTravel,brakedSpeed;private static Vec3d camera;private static int subjectPixels;
    private static Quat relativeStart;private static NbtCompound expectedRoot;private static final List<String> scenes=new ArrayList<>();
    private static Vec3d idleLocal;private static int restoredOwnerSamples;private static double idleDrift,observerPositionError;
    private static float frameDelta;private static boolean projectedHead;private static double projectedX,projectedY;
    private static net.minecraft.entity.Entity entity(net.minecraft.entity.Entity e){return e;}
    private static Path file(String n){return MinecraftClient.getInstance().runDirectory.toPath().resolve(n);}
    private static void require(boolean b,String why){if(!b){MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException("Turret restart: "+why);}}
    private static void write(String name,String data){try{Files.writeString(file(name),data);}catch(Exception e){throw new IllegalStateException(e);}}
    private static NbtCompound read(String name){try{return StringNbtReader.parse(Files.readString(file(name)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static boolean playing(){return READ&&walkingDone;}
    public static void watch(SkyUfoEntity b,ServerPlayerEntity p){
        if(!WRITE)return;root=b;pilot=p;rootId=entity(b).getUuid();
        p.getInventory().selectedSlot=0;p.getInventory().setStack(0,new ItemStack(ModWorldBlocks.SHIP_TURRET_KIT));p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(root.turretModule().isEmpty())return;var t=root.turretModule().orElseThrow();head=t.body(root).orElseThrow();headId=entity(head).getUuid();jointId=t.jointId();
            if(!writerActive||writerClosed)return;writerSamples++;require(t.connected(root)&&UfoControlSystem.controls(pilot,root),"writer remains physically attached and piloted");
            if(writerSamples>=24&&lasers>=4&&rockets>=2&&root.physics().linearVelocity().length()>.1&&root.physics().angularVelocity().length()>.01){
                var n=t.write();if(n.getInt("laserCooldown")>0&&n.getInt("rocketCooldown")>0){writerStop=true;closeWriter(MinecraftClient.getInstance());s.stop(false);}
            }
        });
    }
    public static boolean writerTick(MinecraftClient c){
        if(!writerActive){writerActive=true;DescentKeybinds.turretRight.setPressed(true);DescentKeybinds.auxiliary.setPressed(true);return true;}return false;
    }
    public static boolean writerReady(){return writerStop;}
    public static void closeWriter(MinecraftClient c){
        require(writerStop&&head!=null,"writer saves after real mounted fire");
        write("turret-restart-ids.json","{\"root\":\""+rootId+"\",\"head\":\""+headId+"\",\"joint\":\""+jointId+"\"}");
        NativeUfoReloadCheck.watch(rootId);writerClosed=true;c.scheduleStop();
    }
    public static void savedRoot(SkyUfoEntity b,NbtCompound n){
        if(!WRITE||!writerStop||!entity(b).getUuid().equals(rootId)||entity(b).getWorld().isClient)return;
        write("turret-restart-root.snbt",n.toString());write("ufo-reload-expected.snbt",n.toString());var t=n.getCompound("turret");
        write("turret-restart-write-ok.json","{\"actualSerializedRoot\":true,\"laserCooldown\":"+t.getInt("laserCooldown")+",\"rocketCooldown\":"+t.getInt("rocketCooldown")+",\"lasers\":"+lasers+",\"rockets\":"+rockets+",\"samples\":"+writerSamples+",\"aimPackets\":"+aimPackets+",\"firePackets\":"+firePackets+"}");
    }
    public static void savedBody(BlockBodyEntity b,NbtCompound n){if(WRITE&&writerStop&&!(b instanceof SkyUfoEntity)&&entity(b).getUuid().equals(headId)&&!entity(b).getWorld().isClient)write("turret-restart-child.snbt",n.toString());}
    public static void savedRegistry(NbtCompound n){if(WRITE&&writerStop&&jointId!=null)write("turret-restart-registry.snbt",n.toString());}
    private static void compare(BlockBodyEntity b,NbtCompound saved,NbtCompound expected){
        require(!b.hasSimulatedPhysics(),"actual stored body observed before its first physical tick");
        require(expected.getCompound("body").equals(saved.getCompound("body")),"exact serialized physical data on disk");
        var actual=entity(b).writeNbt(new NbtCompound());var before=expected.getCompound("body");var after=actual.getCompound("body");
        for(String k:new String[]{"qx","qy","qz","qw","vx","vy","vz","lx","ly","lz","passengerGravityx","passengerGravityy","passengerGravityz"})require(Math.abs(before.getDouble(k)-after.getDouble(k))<=1e-10,"restored physical component "+k);
        require(before.getBoolean("gravity")==after.getBoolean("gravity")&&expected.getList("Pos",NbtElement.DOUBLE_TYPE).equals(actual.getList("Pos",NbtElement.DOUBLE_TYPE))&&expected.getCompound("shape").equals(actual.getCompound("shape")),"restored gravity, world position and exact material grid");
    }
    public static void loadedRoot(SkyUfoEntity b,NbtCompound n){
        if(!READ||entity(b).getWorld().isClient||!entity(b).getUuid().equals(rootId))return;
        expectedRoot=read("turret-restart-root.snbt");compare(b,n,expectedRoot);var actual=entity(b).writeNbt(new NbtCompound());require(expectedRoot.getCompound("turret").equals(actual.getCompound("turret")),"same child/joint UUIDs and both firing cooldowns before simulation");root=b;rootExact=true;System.out.println("TURRET_RESTART_ROOT_EXACT "+rootId);
    }
    public static void loadedBody(BlockBodyEntity b,NbtCompound n){
        if(!READ||b instanceof SkyUfoEntity||entity(b).getWorld().isClient||!entity(b).getUuid().equals(headId))return;
        compare(b,n,read("turret-restart-child.snbt"));head=b;headExact=true;System.out.println("TURRET_RESTART_CHILD_EXACT "+headId);
    }
    public static void acceptedAim(ServerPlayerEntity p,UfoControlSystem.TurretInput in){if((WRITE&&writerActive||READ&&phase==2)&&p==pilot&&in.yaw()>.5)aimPackets++;}
    public static void acceptedFire(ServerPlayerEntity p,UfoControlSystem.Fire in){if((WRITE&&writerActive||READ&&phase==2)&&p==pilot&&(in.mask()&4)!=0)firePackets++;}
    public static void fired(WeaponCore.FireConfig cfg,ProjectileEntity shot){
        if((!WRITE&&!READ)||WRITE&&writerClosed||shot==null||head==null||cfg.owner!=pilot)return;
        if((WRITE&&!writerActive||READ&&phase>=1&&phase<=2)&&UfoControlSystem.controls(pilot,root)&&SkyUfoWeapons.ROCKETS.stream().anyMatch(slot->SkyUfoWeapons.muzzle(root,slot).distanceTo(cfg.pos)<1e-8))return;
        require(WRITE&&writerActive||READ&&(phase==2||phase==3),"no phantom trigger or stale pilot after restart");
        var slot=cfg.meshKind==1?SkyUfoTurret.ROCKET:SkyUfoTurret.LASER;var at=SkyUfoWeapons.muzzle(head,slot);var d=head.rotation().rotate(new Vec3(0,0,1));
        muzzleError=Math.max(muzzleError,at.distanceTo(cfg.pos));velocityError=Math.max(velocityError,entity(shot).getVelocity().distanceTo(new Vec3d(d.x(),d.y(),d.z()).multiply(cfg.speed).add(head.surfaceVelocityAt(at).multiply(.05))));require(muzzleError<1e-8&&velocityError<1e-8,"loaded module uses real transformed muzzle and surface velocity");if(cfg.meshKind==1)rockets++;else lasers++;
    }
    public static void afterWalking(){if(READ){walkingDone=true;phase=1;}}
    public static void register(){
        try{var j=com.google.gson.JsonParser.parseString(Files.readString(file("turret-restart-ids.json"))).getAsJsonObject();rootId=UUID.fromString(j.get("root").getAsString());headId=UUID.fromString(j.get("head").getAsString());jointId=UUID.fromString(j.get("joint").getAsString());}catch(Exception e){throw new IllegalStateException(e);}
        ServerLifecycleEvents.SERVER_STARTED.register(s->{var w=s.getOverworld();var actual=((net.minecraft.world.PersistentState)BodyJointRegistry.get(w)).writeNbt(new NbtCompound(),w.getRegistryManager());require(read("turret-restart-registry.snbt").equals(actual),"same actual persistent joint registry before simulation");registryExact=true;});
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(root==null||head==null||done)return;var w=s.getOverworld();if(s.getPlayerManager().getPlayerList().isEmpty())return;pilot=s.getPlayerManager().getPlayerList().getFirst();var t=root.turretModule().orElseThrow();
            require(BodySpatialIndex.loaded(w).stream().filter(b->entity(b).getUuid().equals(rootId)).count()==1&&BodySpatialIndex.loaded(w).stream().filter(b->entity(b).getUuid().equals(headId)).count()==1,"exactly one root and child per saved UUID");
            require(t.bodyId().equals(headId)&&t.jointId().equals(jointId)&&t.connected(root),"actual saved module and original joint remain connected");
            var link=BodyJointRegistry.get(w).find(jointId).orElseThrow();require(!link.collideConnected(),"bearing collision mask restored");
            if(!BoardingSystem.ready(pilot)){
                var resume=com.terminaldetector.drmd.DescentPlayerData.get(pilot).pendingBoardingResume();require(resume!=null&&resume.body().equals(rootId),"saved passenger still names the real carrier, never its earlier-loaded module");
                if(idleLocal==null)idleLocal=root.worldToLocal(pilot.getPos());
                require(t.write().equals(expectedRoot.getCompound("turret")),"admission barrier retains saved cooldowns");barrierSamples++;return;
            }
            if(phase==0){
                var state=BoardingSystem.state(pilot);require(BoardingSystem.drives(pilot)&&state!=null&&state.gravityOwner()==entity(root).getId(),"first and subsequent restored contacts keep the original carrier gravity owner");restoredOwnerSamples++;
                if(!NativeUfoReloadCheck.walkingStarted()){
                    var local=root.worldToLocal(pilot.getPos());idleDrift=Math.max(idleDrift,local.distanceTo(idleLocal));require(local.y>-.03&&idleDrift<.4,"idle returning passenger cannot sink through or slide off the rotating deck");
                }
            }
            maxAnchor=Math.max(maxAnchor,root.localToWorld(new Vec3d(.5,4,.5)).distanceTo(head.localToWorld(new Vec3d(.5,0,.5))));require(maxAnchor<.03&&!BodyContactSystem.stats(w).budgetPaused(),"loaded anchor/contact continuity");
            if(phase==0||phase==1&&!UfoControlSystem.controls(pilot,root)){require(!UfoControlSystem.controls(pilot)&&link.speed()==0,"no stale pilot or saved motor command resumes without new ownership");}
            if(phase==2){var relative=com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.hamiltonProduct(root.rotation().inverse(),head.rotation()).normalized();if(relativeStart==null)relativeStart=relative;postSamples++;angleTravel=Math.max(angleTravel,2*Math.acos(Math.min(1,Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(relativeStart,relative)))));}
            if(phase==3)brakedSpeed=Math.abs(head.physics().angularVelocity().minus(root.physics().angularVelocity()).dot(root.rotation().rotate(new Vec3(0,1,0))));
        });
        ClientTickEvents.END_CLIENT_TICK.register(NativeTurretReloadCheck::tick);
    }
    private static void aimConsole(MinecraftClient c){
        var e=c.world.getEntityById(entity(root).getId());if(!(e instanceof SkyUfoEntity b))return;var view=BoardingClient.bodyView(b,1);var off=new Vec3(3.5,-.001,.5).minus(b.localCentre());
        var at=view==null?b.localToWorld(new Vec3d(3.5,-.001,.5)):BoardingSystem.mc(view.position().plus(view.rotation().rotate(off)));var a=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(BoardingSystem.pure(at.subtract(c.player.getEyePos())));c.player.setYaw((float)a.yawDegrees());c.player.setPitch((float)a.pitchDegrees());
    }
    private static Vec3d renderTarget(BlockBodyEntity b,float delta){var view=BoardingClient.bodyView(b,delta);var at=view==null?b.renderMassPosition(delta):view.position();var q=view==null?b.renderRotation(delta):view.rotation();return BoardingSystem.mc(at.plus(q.rotate(new Vec3(.5,1.5,.5).minus(b.localCentre()))));}
    public static void beforeFrame(float delta){
        if(!READ)return;frameDelta=delta;projectedHead=false;var c=MinecraftClient.getInstance();
        if(done||phase!=5||camera==null||c.player==null||c.world==null||c.player.getPos().distanceTo(camera)>=.5||BoardingPose.active(c.player))return;
        var e=c.world.getEntityById(entity(head).getId());if(!(e instanceof BlockBodyEntity b))return;
        var a=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(BoardingSystem.pure(renderTarget(b,delta).subtract(c.player.getCameraPosVec(delta))));
        c.player.setYaw((float)a.yawDegrees());c.player.prevYaw=c.player.getYaw();c.player.setPitch((float)a.pitchDegrees());c.player.prevPitch=c.player.getPitch();exteriorAimed=true;aimTicks++;
    }
    /** Observe the actual model/view transform after its real quaternion is applied. */
    public static void renderedHead(BlockBodyEntity b,net.minecraft.client.util.math.MatrixStack matrices){
        if(!READ||phase!=5||!entity(b).getUuid().equals(headId))return;var p=new Vec3(.5,1.5,.5).minus(b.localPivot());
        var clip=new org.joml.Vector4f((float)p.x(),(float)p.y(),(float)p.z(),1).mul(matrices.peek().getPositionMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        projectedHead=clip.w>0;projectedX=clip.x/clip.w;projectedY=clip.y/clip.w;
    }
    private static void tick(MinecraftClient c){
        if(done)return;if(++ticks>2400)require(false,"reader timeout phase="+phase+" root="+rootExact+" head="+headExact+" registry="+registryExact+" samples="+postSamples);
        if(c.player==null||c.world==null||!walkingDone)return;controlAge++;
        if(phase==1){aimConsole(c);c.options.useKey.setPressed(true);if(UfoControlClient.active()&&UfoControlSystem.controls(pilot,root)){c.options.useKey.setPressed(false);phase=2;controlAge=0;DescentKeybinds.turretRight.setPressed(true);DescentKeybinds.auxiliary.setPressed(true);}}
        else if(phase==2&&postSamples>=32&&lasers>=3&&rockets>=1&&aimPackets>=8&&firePackets>=8&&angleTravel>.25){DescentKeybinds.turretRight.setPressed(false);DescentKeybinds.auxiliary.setPressed(false);capture("post-load-controlled-fire");phase=3;controlAge=0;}
        else if(phase==3&&controlAge>=24&&brakedSpeed<.08){ClientPlayNetworking.send(new com.terminaldetector.drmd.network.ModNetworking.ActionPayload("toggle"));phase=4;controlAge=0;}
        else if(phase==4&&controlAge>=8&&!UfoControlClient.active()&&!UfoControlSystem.controls(pilot)){
            require(BoardingClient.drives(c.player)&&BoardingSystem.drives(pilot),"actual H release returns to walking on the saved carrier");capture("post-load-control-release");phase=5;c.options.hudHidden=false;c.options.setPerspective(Perspective.FIRST_PERSON);
            c.getServer().execute(()->{pilot.changeGameMode(net.minecraft.world.GameMode.CREATIVE);pilot.getAbilities().flying=true;pilot.sendAbilitiesUpdate();pilot.getInventory().selectedSlot=0;pilot.getInventory().setStack(0,new ItemStack(ModWorldBlocks.SHIP_TURRET_KIT));pilot.getInventory().markDirty();pilot.currentScreenHandler.sendContentUpdates();camera=head.localToWorld(new Vec3d(4,3,8));pilot.teleport(pilot.getServerWorld(),camera.x,camera.y,camera.z,Set.of(),180,0);pilot.setVelocity(Vec3d.ZERO);});
        }else if(phase==5&&camera!=null&&c.player.getPos().distanceTo(camera)<.5&&!BoardingPose.active(c.player)){
            if(!c.player.getAbilities().flying){c.player.getAbilities().flying=true;c.player.sendAbilitiesUpdate();}
        }
    }
    public static void drew(BlockBodyEntity b){if(READ&&entity(b).getUuid().equals(headId)){headDraws++;if(exteriorAimed)exteriorDraws++;}}
    private static void capture(String name){var c=MinecraftClient.getInstance();try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){Files.createDirectories(file("turret-restart-frames"));image.writeTo(file("turret-restart-frames/"+name+".png"));scenes.add(name);}catch(Exception e){throw new IllegalStateException(e);}}
    public static void rendered(){
        if(!READ||done||phase!=5||aimTicks<4||exteriorDraws<6)return;var c=MinecraftClient.getInstance();if(c.currentScreen!=null||!c.player.getMainHandStack().isOf(ModWorldBlocks.SHIP_TURRET_KIT))return;
        // The asynchronous vanilla teleport must actually reach the client before
        // old deck pixels and cumulative draw calls can qualify an exterior frame.
        observerPositionError=c.player.getPos().distanceTo(camera);
        if(observerPositionError>=.5||BoardingPose.active(c.player)||c.gameRenderer.getCamera().getPos().distanceTo(c.player.getEyePos())>=.5){imageFrames=0;return;}
        if(!projectedHead||Math.abs(projectedX)>.08||Math.abs(projectedY)>.08){imageFrames=0;return;}
        var e=c.world.getEntityById(entity(head).getId());if(!(e instanceof BlockBodyEntity b))return;var f=c.gameRenderer.getCamera().getHorizontalPlane();var d=renderTarget(b,frameDelta).subtract(c.gameRenderer.getCamera().getPos()).normalize();if(d.dotProduct(new Vec3d(f.x,f.y,f.z))<.98)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){int n=0;for(int y=image.getHeight()/2-110;y<image.getHeight()/2+110;y++)for(int x=image.getWidth()/2-110;x<image.getWidth()/2+110;x++){int col=image.getColor(x,y),r=col&255,g=(col>>>8)&255,blue=(col>>>16)&255;if(r>50&&g>50&&blue>50&&Math.abs(r-g)<25&&Math.abs(g-blue)<25)n++;}if(n<256){imageFrames=0;return;}subjectPixels=n;if(++imageFrames<3)return;image.writeTo(file("turret-restart-module.png"));}
        catch(Exception failure){throw new IllegalStateException(failure);}require(rootExact&&headExact&&registryExact&&barrierSamples>=20,"all actual reload observations required");
        var j=new com.google.gson.JsonObject();j.addProperty("actualHeadProjectionVerified",true);j.addProperty("headNdcX",projectedX);j.addProperty("headNdcY",projectedY);j.addProperty("observerPositionVerified",true);j.addProperty("observerPositionError",observerPositionError);j.addProperty("actualTwoProcessRestart",true);j.addProperty("rootExact",rootExact);j.addProperty("headExact",headExact);j.addProperty("registryExact",registryExact);j.addProperty("barrierCooldownSamples",barrierSamples);j.addProperty("restoredCarrierOwnerSamples",restoredOwnerSamples);j.addProperty("idleLocalDrift",idleDrift);j.addProperty("postLoadSamples",postSamples);j.addProperty("lasers",lasers);j.addProperty("rockets",rockets);j.addProperty("aimPackets",aimPackets);j.addProperty("firePackets",firePackets);j.addProperty("maxAnchorError",maxAnchor);j.addProperty("muzzleError",muzzleError);j.addProperty("launchVelocityError",velocityError);j.addProperty("relativeAngle",angleTravel);j.addProperty("brakedRelativeSpeed",brakedSpeed);j.addProperty("headRenderedFrames",headDraws);j.addProperty("exteriorRenderedFrames",exteriorDraws);j.addProperty("subjectPixels",subjectPixels);j.addProperty("noDuplicates",true);j.addProperty("postLoadControlRelease",true);write("turret-restart-ok.json",new com.google.gson.Gson().toJson(j));done=true;c.scheduleStop();
    }
}
