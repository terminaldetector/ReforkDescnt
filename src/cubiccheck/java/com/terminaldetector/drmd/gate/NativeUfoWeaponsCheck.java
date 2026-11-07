package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.mega.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Real mouse input and normal world ticks, with no assigned carrier pose or momentum. */
public final class NativeUfoWeaponsCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.ufoWeapons");
    private static SkyUfoEntity body;private static ServerPlayerEntity pilot;
    private static volatile int stage,lasers,rockets,offTicks,accepted,repairLasers,repairRockets;
    private static volatile long repairRevision;private static volatile Vec3d camera;
    private static volatile double muzzleError,launchError,pointVelocityError,materialError,rotationTravel;
    private static com.terminaldetector.drmd.client.portal.PortalTransform.Quat startRotation;
    private static final Map<UUID,Integer> SHOTS=new ConcurrentHashMap<>();
    private static final Set<UUID> SYNCHRONIZED=ConcurrentHashMap.newKeySet(),RENDERED=ConcurrentHashMap.newKeySet();
    private static Map<BlockPos,BlockState> original;
    private static List<BlockPos> slots(){var s=new ArrayList<>(SkyUfoWeapons.LASERS);s.addAll(SkyUfoWeapons.ROCKETS);return s;}
    private static Map<BlockPos,BlockState> grid(){var m=new HashMap<BlockPos,BlockState>();for(var c:body.cells())m.put(c.pos(),c.state());return m;}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException("UFO mounted weapons: "+why);}
    public static void watch(SkyUfoEntity b,ServerPlayerEntity p){
        if(!ENABLED)return;body=b;pilot=p;original=grid();
        ServerTickEvents.END_WORLD_TICK.register(w->{if(w!=b.getWorld()||stage<1||stage>=5)return;
            require(UfoControlSystem.controls(p,b),"normal pilot authority must survive rotating fire/damage/repair");
            rotationTravel=Math.max(rotationTravel,2*Math.acos(Math.min(1,Math.abs(startRotation.x()*b.rotation().x()+startRotation.y()*b.rotation().y()+startRotation.z()*b.rotation().z()+startRotation.w()*b.rotation().w()))));
            if(stage==2){require(slots().stream().allMatch(s->body.getLocalBlock(s).isAir()),"disabled bank remains real empty material");offTicks++;}
        });
    }
    public static void accepted(ServerPlayerEntity p,UfoControlSystem.Fire input){if(ENABLED&&p==pilot&&stage>=1&&stage<=3&&input.mask()==3)accepted++;}
    public static void fired(WeaponCore.FireConfig cfg,ProjectileEntity shot){
        if(!ENABLED||cfg.owner!=pilot||shot==null)return;
        SHOTS.put(shot.getUuid(),shot.getMeshKind());
        if(stage<1||stage>3)return;require(stage!=2,"a destroyed bank emitted a phantom shot");
        var bank=cfg.meshKind==1?SkyUfoWeapons.ROCKETS:SkyUfoWeapons.LASERS;
        var mount=bank.stream().min(Comparator.comparingDouble(s->SkyUfoWeapons.muzzle(body,s).squaredDistanceTo(cfg.pos))).orElseThrow();
        var block=cfg.meshKind==1?ModWorldBlocks.SHIP_ROCKET:ModWorldBlocks.SHIP_LASER;
        require(body.getLocalBlock(mount).isOf(block),"emission comes from a real weapon cell");
        var at=SkyUfoWeapons.muzzle(body,mount);var f=body.rotation().rotate(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(0,0,1));var dir=new Vec3d(f.x(),f.y(),f.z());
        muzzleError=Math.max(muzzleError,at.distanceTo(cfg.pos));launchError=Math.max(launchError,Math.max(dir.distanceTo(cfg.dir),shot.getVelocity().distanceTo(dir.multiply(cfg.speed).add(body.surfaceVelocityAt(at).multiply(.05)))));
        pointVelocityError=Math.max(pointVelocityError,cfg.inheritedVelocity.distanceTo(body.surfaceVelocityAt(at).multiply(.05)));
        require(muzzleError<1e-8&&launchError<1e-8&&pointVelocityError<1e-8,"actual muzzle/direction/linear+angular inheritance mismatch");
        if(stage==1){if(cfg.meshKind==1)rockets++;else lasers++;}else{if(cfg.meshKind==1)repairRockets++;else repairLasers++;}
    }
    public static void drew(ProjectileEntity p){if(ENABLED&&SHOTS.containsKey(p.getUuid()))RENDERED.add(p.getUuid());}
    private static void held(MinecraftClient c,boolean b){c.options.attackKey.setPressed(b);c.options.useKey.setPressed(b);}
    public static boolean complete(){return stage==5;}
    public static String tick(MinecraftClient c){
        for(var e:c.world.getEntities())if(e instanceof ProjectileEntity&&SHOTS.containsKey(e.getUuid()))SYNCHRONIZED.add(e.getUuid());
        if(stage==0){stage=1;startRotation=body.rotation();held(c,true);}
        else if(stage==1&&lasers>=6&&rockets>=2){
            stage=4;c.getServer().execute(()->{
                var p=new Vec3d(3.5,-.5,3.5);var at=body.localToWorld(p);var v=body.surfaceVelocityAt(at);var cuts=new HashMap<BlockPos,BlockState>();for(var s:slots())cuts.put(s,Blocks.AIR.getDefaultState());
                require(body.setLocalBlocks(cuts),"actual grid damage removes both banks");materialError=Math.max(materialError,Math.max(at.distanceTo(body.localToWorld(p)),v.distanceTo(body.surfaceVelocityAt(body.localToWorld(p)))));stage=2;
            });return "mounted-weapons-rotating-fire";
        }else if(stage==2&&offTicks>=28&&accepted>=24){stage=4;return "mounted-weapons-destroyed";}
        else if(stage==4&&offTicks>=28){
            stage=6;c.getServer().execute(()->{
                var point=new Vec3d(3.5,-.5,3.5);var at=body.localToWorld(point);var v=body.surfaceVelocityAt(at);var access=new BlockBodyWorldAccess(body);
                for(var s:slots())require(access.placeLocalBlock(s,original.get(s)),"real collision-checked repair of mounted part");
                materialError=Math.max(materialError,Math.max(at.distanceTo(body.localToWorld(point)),v.distanceTo(body.surfaceVelocityAt(body.localToWorld(point)))));
                require(materialError<1e-8&&grid().equals(original),"repair preserves material motion and exact original grid");repairRevision=body.shapeRevision();stage=3;
            });
        }else if(stage==3&&repairLasers>=4&&repairRockets>=2){
            require(rotationTravel>.3,"mounted fire must be observed during genuine hull rotation");
            require(SYNCHRONIZED.stream().anyMatch(id->SHOTS.get(id)==0)&&SYNCHRONIZED.stream().anyMatch(id->SHOTS.get(id)==1),"both real projectile meshes synchronize to the client");
            held(c,false);stage=5;return "mounted-weapons-repaired-fire";
        }
        return null;
    }
    public static void prepareExterior(MinecraftClient c){
        c.options.hudHidden=false;c.options.setPerspective(Perspective.FIRST_PERSON);
        c.getServer().execute(()->{
            require(!UfoControlSystem.controls(pilot),"external observer follows real H release");
            var at=body.localToWorld(new Vec3d(0,1,17));pilot.changeGameMode(net.minecraft.world.GameMode.CREATIVE);pilot.getAbilities().flying=true;pilot.sendAbilitiesUpdate();
            pilot.getInventory().selectedSlot=0;pilot.getInventory().setStack(0,new ItemStack(ModWorldBlocks.SHIP_LASER));pilot.getInventory().setStack(1,new ItemStack(ModWorldBlocks.SHIP_ROCKET));pilot.getInventory().markDirty();pilot.currentScreenHandler.sendContentUpdates();
            pilot.teleport(pilot.getServerWorld(),at.x,at.y,at.z,Set.of(),180,0);pilot.setVelocity(Vec3d.ZERO);camera=at;
        });
    }
    public static boolean exteriorReady(MinecraftClient c){
        if(camera==null||c.player.squaredDistanceTo(camera)>.5||!c.player.getMainHandStack().isOf(ModWorldBlocks.SHIP_LASER.asItem())||!c.player.getInventory().getStack(1).isOf(ModWorldBlocks.SHIP_ROCKET.asItem()))return false;
        if(!c.player.getAbilities().flying){c.player.setOnGround(false);c.player.getAbilities().flying=true;c.player.sendAbilitiesUpdate();}
        var e=c.world.getEntityById(body.getId());if(!(e instanceof SkyUfoEntity b)||b.shapeRevision()!=repairRevision||slots().stream().anyMatch(s->!b.getLocalBlock(s).equals(original.get(s))))return false;
        var at=b.localToWorld(new Vec3d(0,.5,9));var d=at.subtract(c.player.getEyePos());var a=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(d.x,d.y,d.z));c.player.setYaw((float)a.yawDegrees());c.player.setPitch((float)a.pitchDegrees());return true;
    }
    public static void append(Path p)throws java.io.IOException{
        if(!ENABLED)return;require(complete()&&camera!=null,"all native mounted weapon scenes completed");var j=com.google.gson.JsonParser.parseString(Files.readString(p)).getAsJsonObject();
        j.addProperty("mountedLaserShots",lasers);j.addProperty("mountedRocketShots",rockets);j.addProperty("repairedLaserShots",repairLasers);j.addProperty("repairedRocketShots",repairRockets);j.addProperty("destroyedBankTicks",offTicks);j.addProperty("acceptedFireSnapshots",accepted);
        j.addProperty("maxMuzzleError",muzzleError);j.addProperty("maxLaunchVelocityError",launchError);j.addProperty("maxPointVelocityError",pointVelocityError);j.addProperty("weaponMaterialContinuityError",materialError);j.addProperty("weaponRotationTravel",rotationTravel);j.addProperty("synchronizedMountedShots",SYNCHRONIZED.size());j.addProperty("renderedMountedShots",RENDERED.size());j.addProperty("actualWeaponItemsObserved",true);Files.writeString(p,new com.google.gson.Gson().toJson(j)+"\n");
    }
}
