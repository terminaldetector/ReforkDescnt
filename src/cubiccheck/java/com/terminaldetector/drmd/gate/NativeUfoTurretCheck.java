package com.terminaldetector.drmd.gate;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.mega.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
public final class NativeUfoTurretCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.ufoTurret");
    private static SkyUfoEntity root;private static ServerPlayerEntity pilot;private static BlockBodyEntity head;private static SkyUfoTurret module;
    private static volatile int stage,samples,brakeSamples,freeSamples,aimPackets,firePackets,lasers,rockets,draws;
    private static volatile double anchorError,relativeAngle,relativeSpeed,muzzleError,velocityError,momentumError,angularError,separationError,freeTravel;
    private static volatile boolean installed,exterior;private static volatile Vec3d camera;
    private static Vec3 preP,preL,expectedP,expectedL,cutOffset;private static int beforeFree;
    private static net.minecraft.entity.Entity entity(net.minecraft.entity.Entity e){return e;}
    private static void require(boolean b,String why){if(!b){MinecraftClient.getInstance().scheduleStop();throw new IllegalStateException("Native turret: "+why);}}
    private static Vec3 pure(Vec3d p){return new Vec3(p.x,p.y,p.z);}
    private static Vec3 momentum(BlockBodyEntity b){return b.physics().linearVelocity().scaled(b.physicsMass());}
    private static Vec3 angular(BlockBodyEntity b){return b.physics().angularMomentum().plus(b.massPosition().cross(momentum(b)));}
    public static void watch(SkyUfoEntity b,ServerPlayerEntity p){
        if(!ENABLED)return;root=b;pilot=p;
        p.getInventory().selectedSlot=0;p.getInventory().setStack(0,new ItemStack(ModWorldBlocks.SHIP_TURRET_KIT));p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();
        ServerTickEvents.END_WORLD_TICK.register(w->{if(w!=entity(root).getWorld()||stage>=5)return;
            if(module==null&&root.turretModule().isPresent()){module=root.turretModule().orElseThrow();head=module.body(root).orElseThrow();installed=true;require(pilot.getMainHandStack().isEmpty(),"real survival console use consumes the installation kit");}
            if(stage<1)return;
            if(stage==4||!UfoControlSystem.controls(pilot,root))System.out.println("TURRET_DETACH_TRACE stage="+stage+" free="+freeSamples+" local="+root.worldToLocal(pilot.getPos())+" player="+pilot.isAlive()+"/"+pilot.getHealth()+" contains="+root.containsPos(pilot.getPos())+" console="+root.hasControlConsole()+" rootRemoved="+entity(root).isRemoved()+" crashing="+root.isCrashing()+" boarded="+com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem.drives(pilot)+" contacts="+BodyContactSystem.stats(w)+" offset="+root.worldToLocal(head.physicsCentre())+" rootV="+root.physics().linearVelocity()+" childV="+head.physics().linearVelocity());
            require(UfoControlSystem.controls(pilot,root),"pilot remains aboard its moving carrier");
            if(stage==1||stage==2){require(module.connected(root),"real motor remains connected");anchorError=Math.max(anchorError,root.localToWorld(new Vec3d(.5,4,.5)).distanceTo(head.localToWorld(new Vec3d(.5,0,.5))));require(anchorError<.03,"motor anchor drift: "+anchorError);
                relativeAngle=Math.max(relativeAngle,2*Math.acos(Math.min(1,Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(root.rotation(),head.rotation())))));
                relativeSpeed=Math.abs(head.physics().angularVelocity().minus(root.physics().angularVelocity()).dot(root.rotation().rotate(new Vec3(0,1,0))));if(stage==1)samples++;else brakeSamples++;
            }else if(stage==4){freeSamples++;require(!module.connected(root)&&BodyJointRegistry.get(w).find(module.jointId()).isEmpty(),"cut joint stays absent");require(lasers+rockets==beforeFree,"detached head cannot receive phantom parent fire");freeTravel=Math.max(freeTravel,root.worldToLocal(head.physicsCentre()).subtract(new Vec3d(cutOffset.x(),cutOffset.y(),cutOffset.z())).length());}
        });
    }
    public static void aim(ServerPlayerEntity p,UfoControlSystem.TurretInput in){if(ENABLED&&p==pilot&&stage==1&&Math.abs(in.yaw())>.5)aimPackets++;}
    public static void trigger(ServerPlayerEntity p,UfoControlSystem.Fire in){if(ENABLED&&p==pilot&&stage>=1&&stage<=4&&(in.mask()&4)!=0)firePackets++;}
    public static void begin(SkyUfoTurret t,SkyUfoEntity b){if(!ENABLED||b!=root||head==null)return;preP=momentum(root).plus(momentum(head));preL=angular(root).plus(angular(head));expectedP=expectedL=new Vec3(0,0,0);}
    public static void fired(WeaponCore.FireConfig cfg,ProjectileEntity shot){
        if(!ENABLED||cfg.owner!=pilot||shot==null||stage<1||head==null)return;
        require(stage!=4,"no shots after physical detachment");var slot=cfg.meshKind==1?SkyUfoTurret.ROCKET:SkyUfoTurret.LASER;
        var at=SkyUfoWeapons.muzzle(head,slot);var dir=head.rotation().rotate(new Vec3(0,0,1));
        muzzleError=Math.max(muzzleError,at.distanceTo(cfg.pos));velocityError=Math.max(velocityError,entity(shot).getVelocity().distanceTo(new Vec3d(dir.x(),dir.y(),dir.z()).multiply(cfg.speed).add(head.surfaceVelocityAt(at).multiply(.05))));
        require(muzzleError<1e-8&&velocityError<1e-8,"projectile must follow actual child geometry and angular point velocity");
        var impulse=dir.scaled(cfg.meshKind==1?-160:-24);expectedP=expectedP.plus(impulse);expectedL=expectedL.plus(pure(at).cross(impulse));if(cfg.meshKind==1)rockets++;else lasers++;
    }
    public static void end(SkyUfoTurret t,SkyUfoEntity b){if(!ENABLED||b!=root||preP==null)return;
        momentumError=Math.max(momentumError,momentum(root).plus(momentum(head)).minus(preP).minus(expectedP).length());angularError=Math.max(angularError,angular(root).plus(angular(head)).minus(preL).minus(expectedL).length());
        require(momentumError<1e-7&&angularError<1e-6,"late recoil must conserve island momentum without pose integration: "+momentumError+" / "+angularError);preP=null;
    }
    public static void drew(BlockBodyEntity b){if(ENABLED&&head!=null&&entity(b).getId()==entity(head).getId())draws++;}
    public static boolean complete(){return stage==5;}
    public static String tick(MinecraftClient c){
        if(stage==0){require(installed&&module!=null&&head!=null,"real input installed the physical module");DescentKeybinds.turretRight.setPressed(true);DescentKeybinds.auxiliary.setPressed(true);stage=1;}
        else if(stage==1&&samples>=24&&relativeAngle>.25&&lasers>=3&&rockets>=1&&aimPackets>=8&&firePackets>=8){DescentKeybinds.turretRight.setPressed(false);DescentKeybinds.auxiliary.setPressed(false);stage=2;return "turret-yaw-and-fire";}
        else if(stage==2&&brakeSamples>=20&&relativeSpeed<.08){stage=3;return "turret-motor-braked";}
        else if(stage==3){stage=6;c.getServer().execute(()->{
            var pose=head.massPosition();var v=head.physics().linearVelocity();var l=head.physics().angularMomentum();
            require(root.setLocalBlock(SkyUfoTurret.ROOF,net.minecraft.block.Blocks.AIR.getDefaultState()),"actual roof material is removed");module.tick(root);
            separationError=Math.max(head.massPosition().minus(pose).length(),Math.max(head.physics().linearVelocity().minus(v).length(),head.physics().angularMomentum().minus(l).length()));require(separationError<1e-8&&head.physicsGravity(),"separation keeps exact physical state before free simulation");
            cutOffset=pure(root.worldToLocal(head.physicsCentre()));beforeFree=lasers+rockets;stage=4;
        });DescentKeybinds.auxiliary.setPressed(true);}
        else if(stage==4&&freeSamples>=10&&freeTravel>.15){DescentKeybinds.auxiliary.setPressed(false);stage=5;return "turret-anchor-destroyed";}
        return null;
    }
    public static void prepareExterior(MinecraftClient c){
        c.options.hudHidden=false;c.options.setPerspective(Perspective.FIRST_PERSON);c.getServer().execute(()->{
            require(!UfoControlSystem.controls(pilot),"external observation follows H release");pilot.changeGameMode(net.minecraft.world.GameMode.CREATIVE);pilot.getAbilities().flying=true;pilot.sendAbilitiesUpdate();
            pilot.getInventory().setStack(0,new ItemStack(ModWorldBlocks.SHIP_TURRET_KIT));pilot.getInventory().selectedSlot=0;pilot.getInventory().markDirty();pilot.currentScreenHandler.sendContentUpdates();
            var at=head.localToWorld(new Vec3d(4,3,8));pilot.teleport(pilot.getServerWorld(),at.x,at.y,at.z,Set.of(),180,0);pilot.setVelocity(Vec3d.ZERO);camera=at;exterior=true;
        });
    }
    public static boolean exteriorReady(MinecraftClient c){
        if(!exterior||camera==null||c.player.squaredDistanceTo(camera)>.5||!c.player.getMainHandStack().isOf(ModWorldBlocks.SHIP_TURRET_KIT))return false;
        if(!c.player.getAbilities().flying){c.player.setOnGround(false);c.player.getAbilities().flying=true;c.player.sendAbilitiesUpdate();}
        var e=c.world.getEntityById(entity(head).getId());if(!(e instanceof BlockBodyEntity b)||!entity(b).getUuid().equals(entity(head).getUuid()))return false;
        var d=b.localToWorld(new Vec3d(.5,1.5,.5)).subtract(c.player.getEyePos());var a=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(pure(d));c.player.setYaw((float)a.yawDegrees());c.player.setPitch((float)a.pitchDegrees());return draws>=3;
    }
    public static void append(Path p)throws java.io.IOException {
        if(!ENABLED)return;require(complete()&&exterior&&installed&&draws>=3,"all actual turret observations completed");var j=com.google.gson.JsonParser.parseString(Files.readString(p)).getAsJsonObject();
        j.addProperty("turretKitConsumed",true);j.addProperty("turretBody",entity(head).getUuid().toString());j.addProperty("turretJoint",module.jointId().toString());j.addProperty("turretAimPackets",aimPackets);j.addProperty("turretFirePackets",firePackets);j.addProperty("turretLaserShots",lasers);j.addProperty("turretRocketShots",rockets);j.addProperty("turretSamples",samples);j.addProperty("brakeSamples",brakeSamples);j.addProperty("freeSamples",freeSamples);j.addProperty("maxTurretAnchorError",anchorError);j.addProperty("relativeTurretAngle",relativeAngle);j.addProperty("brakedRelativeSpeed",relativeSpeed);j.addProperty("turretMuzzleError",muzzleError);j.addProperty("turretVelocityError",velocityError);j.addProperty("maxRecoilMomentumError",momentumError);j.addProperty("maxRecoilAngularError",angularError);j.addProperty("separationError",separationError);j.addProperty("freeModuleTravel",freeTravel);j.addProperty("turretRenderedFrames",draws);j.addProperty("brokenJointAbsent",true);j.addProperty("turretKitItemObserved",true);Files.writeString(p,new com.google.gson.Gson().toJson(j)+"\n");
    }
}
