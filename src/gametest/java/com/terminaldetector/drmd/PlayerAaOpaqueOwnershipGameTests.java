package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Physical automatic-fire policy for optional, unreadable AA ownership data.
 * No defense/emitter/weapon ticks or factories are called by this fixture. */
public final class PlayerAaOpaqueOwnershipGameTests implements FabricGameTest {
    private static final BlockPos BEARING=BlockPos.ORIGIN,DRIVE=new BlockPos(-1,0,0),GUN=new BlockPos(1,0,0),LEVER=GUN.up();
    private static final int OBSERVATION_TICKS=16,MAX_RIGS=4,MAX_END_ACTIONS=4;
    private static final Set<Rig> RIGS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean registered;
    private enum Input { FUTURE, CORRUPT, ORDINARY }

    /** One finite observer/action per fixture per actual END phase, after the
     * registered body/contact solve and mounted controller. Results are relayed
     * into the sole normal GameTest task; END never completes a GameTest. */
    private static synchronized void registerDispatcher(){
        if(registered)return;registered=true;
        ServerTickEvents.END_WORLD_TICK.register(world->{
            int actions=0;
            for(var rig:List.copyOf(RIGS)){
                if(rig.world!=world||rig.closed||!rig.pending&&rig.startedAt<0)continue;
                if(actions++>=MAX_END_ACTIONS)break;
                try{rig.endPhase();}catch(Throwable failure){rig.failure=failure;rig.record("END_FIRST_FAILURE "+failure);try{rig.close();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}}
            }
        });
        ServerWorldEvents.UNLOAD.register((server,world)->{
            for(var rig:List.copyOf(RIGS))if(rig.world==world){
                rig.pending=false;RIGS.remove(rig);
                if(!rig.closed)rig.failure=new IllegalStateException("Fixture world unloaded before opaque ownership observation completed");
            }
        });
    }
    private static RuntimeException propagate(Throwable failure){
        if(failure instanceof RuntimeException runtime)return runtime;
        if(failure instanceof Error error)throw error;
        return new RuntimeException(failure);
    }
    private static final class Flight {
        final long born;final int initialAge;final Vec3d initialPosition,initialVelocity;
        Vec3d lastPosition;int lastAge,observations;
        Flight(long now,ProjectileEntity shot){born=now;initialAge=shot.age;initialPosition=shot.getPos();initialVelocity=shot.getVelocity();lastPosition=initialPosition;lastAge=initialAge;}
        boolean moved(){return observations>=2&&lastAge>initialAge&&lastPosition.squaredDistanceTo(initialPosition)>1;}
    }
    private static final class Rig implements AutoCloseable {
        final TestContext c;final ServerWorld world;final Input input;final BlockPos base;final AiFixtureChunks chunks;
        final List<String> trace=new ArrayList<>();final Map<UUID,Flight> flights=new LinkedHashMap<>();
        BlockBodyEntity root,tip;ServerPlayerEntity principal;EmbeddedChannel channel;UUID joint;
        NbtCompound loadedMounted,expectedOpaque;Vec3d operatorAtStart;
        long due,requestedAt,startedAt=-1,lastObserved=-1;int phase,admission,endFrames,scheduledChecks;
        boolean initialized,pending,closed,success;Throwable failure;
        Rig(TestContext c,Input input){
            this.c=c;world=c.getWorld();this.input=input;
            base=c.getAbsolutePos(new BlockPos(103424+input.ordinal()*512,200,22016));
            // Forty blocks of actual outgoing flight remain in entity-ticking
            // interest. These are test-owned tickets, not a gameplay load API.
            chunks=new AiFixtureChunks(world,base,3);
        }
        void require(boolean condition,String message){c.assertTrue(condition,"PRECONDITION_UNOBSERVED: "+message+" input="+input+" phase="+phase);}
        void record(String text){var row="testTick="+c.getTick()+" worldTime="+world.getTime()+" "+text;trace.add(row);System.out.println("PLAYER_AA_OPAQUE "+input+" "+row);}
        BlockState bearing(Direction face){return ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(MechanicalBearingBlock.FACING,face);}
        void setup(){
            var data=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),"aa-opaque-"+input.ordinal()),false);
            principal=new ServerPlayerEntity(world.getServer(),world,data.gameProfile(),data.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);
            world.getServer().getPlayerManager().onPlayerConnect(connection,principal,data);
            principal.changeGameMode(GameMode.SURVIVAL);principal.setNoGravity(true);principal.getAbilities().flying=false;
            DescentPlayerData.get(principal).setEnabled(false);principal.setPosition(Vec3d.ofCenter(base.up(5)));
            require(world.getEntity(principal.getUuid())==principal&&!principal.isCreative()&&!principal.isSpectator(),"actual loaded ordinary Survival operator");
            var a=new ArrayList<BlockBodyEntity.Cell>();var b=new ArrayList<BlockBodyEntity.Cell>();
            a.add(new BlockBodyEntity.Cell(BEARING,bearing(Direction.EAST)));
            a.add(new BlockBodyEntity.Cell(DRIVE,ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(MechanicalBearingBlock.FACING,Direction.EAST).with(Properties.POWER,0)));
            b.add(new BlockBodyEntity.Cell(BEARING,bearing(Direction.WEST)));
            b.add(new BlockBodyEntity.Cell(GUN,ModWorldBlocks.SHIP_LASER.getDefaultState().with(Properties.FACING,Direction.SOUTH)));
            b.add(new BlockBodyEntity.Cell(LEVER,Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE,BlockFace.FLOOR).with(Properties.POWERED,false)));
            for(int x=-6;x<=-1;x++)for(int y=-2;y<=-1;y++)for(int z=-3;z<=3;z++)a.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
            for(int x=1;x<=6;x++)for(int y=-2;y<=-1;y++)for(int z=-3;z<=3;z++)b.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
            root=BlockBodyEntity.createLocal(world,a,Vec3d.of(base),Quat.IDENTITY,Vec3d.ZERO,Vec3d.ZERO);root.setPhysicsGravity(false);
            tip=BlockBodyEntity.createLocal(world,b,Vec3d.of(base.east()),Quat.IDENTITY,Vec3d.ZERO,Vec3d.ZERO);tip.setPhysicsGravity(false);
            initialized=true;use(root,BEARING,true);phase=1;due=c.getTick()+5;
            record("SETUP root="+root.getUuid()+" tip="+tip.getUuid()+" principal="+principal.getUuid());
        }
        void use(BlockBodyEntity body,BlockPos cell,boolean wrench){
            require(startedAt<0,"all aim/player preparation precedes the observation window");
            principal.setStackInHand(Hand.MAIN_HAND,wrench?new ItemStack(ModWorldBlocks.MECHANISM_WRENCH):ItemStack.EMPTY);
            var bounds=body.localBlockBounds().get(cell);require(bounds!=null,"actual requested material exists");
            var point=body.localToWorld(new Vec3d(bounds.centre().x(),bounds.centre().y(),bounds.centre().z()));
            var eye=point.add(0,2.5,cell.equals(GUN)?-3:0);double eyeHeight=principal.getEyeY()-principal.getY();
            principal.setPosition(eye.subtract(0,eyeHeight,0));principal.setVelocity(Vec3d.ZERO);
            var delta=point.subtract(principal.getEyePos()).normalize();var angle=PortalTransform.vectorToYawPitch(new Vec3(delta.x,delta.y,delta.z));
            principal.setYaw((float)angle.yawDegrees());principal.setPitch((float)angle.pitchDegrees());principal.setHeadYaw(principal.getYaw());
            var selected=BodyBlockInteraction.target(principal,principal.getEyePos(),BodyBlockInteraction.viewDirection(principal),principal.getBlockInteractionRange());
            require(selected.isPresent()&&selected.get().body()==body&&selected.get().hit().localBlock().equals(cell),"actual nearest material ray selects USE: "+selected);
            var edit=new BodyBlockInteraction.Edit(body.getUuid(),body.shapeRevision(),cell,BodyBlockInteraction.USE);
            boolean accepted=BodyBlockInteraction.receive(principal,edit);record("USE body="+body.getUuid()+" cell="+cell+" revision="+edit.revision()+" wrench="+wrench+" accepted="+accepted);
            require(accepted,"actual revisioned material USE accepted");
        }
        void step(){
            world.resetIdleTimeout();if(closed)return;
            if(!initialized){
                if(!chunks.entityTicking(base.add(-8,0,-8),base.add(12,0,48))){require(++admission<80,"entity-ticking interest admitted within80 normal ticks");return;}
                setup();return;
            }
            if(pending||startedAt>=0||c.getTick()<due)return;
            switch(phase){
                case 1->{use(tip,BEARING,true);var links=BodyJointRegistry.get(world).forBody(tip.getUuid());require(links.size()==1&&links.getFirst().blockDriven(),"real separate bearing/drive joint");joint=links.getFirst().id();phase=2;due=c.getTick()+5;}
                case 2->{use(tip,GUN,true);var claim=tip.mountedWeapons().defenseClaims().get(GUN);require(claim.isPresent()&&claim.get().principal().equals(principal.getUuid()),"actual owner claim before optional save mutation");phase=3;due=c.getTick()+5;}
                case 3->{if(input==Input.ORDINARY){use(tip,GUN,true);require(!tip.mountedWeapons().defenseClaims().readOnly()&&tip.mountedWeapons().defenseClaims().size()==0,"actual owner explicitly releases AA mode");}
                    phase=4;due=c.getTick()+5;}
                case 4->{require(RIGS.size()<MAX_RIGS,"bounded test-only END registry");registerDispatcher();RIGS.add(this);requestedAt=world.getTime();pending=true;record("REQUEST_FRESH_END actual pending action; no direct solve/tick");}
                default->require(false,"finite setup state machine");
            }
        }
        boolean fresh(BlockBodyEntity body){return body!=null&&world.getEntity(body.getUuid())==body&&BodySpatialIndex.find(world,body.getId()).orElse(null)==body
            &&!body.isRemoved()&&body.hasSimulatedPhysics()&&!body.boardingMotionPaused();}
        void endPhase(){
            require(world.getServer().isOnThread()&&world==c.getWorld(),"actual fixture/world server thread");
            if(pending){
                require(world.getTime()-requestedAt<=32,"fresh END admission bounded to32 ticks");
                if(!fresh(root)||!fresh(tip))return;
                pending=false;begin();return;
            }
            require(fresh(root)&&fresh(tip),"normal real material/contact frame remains fresh during observation");
            observe();
        }
        void begin(){
            require(!tip.getLocalBlock(LEVER).get(Properties.POWERED),"actual lever remains OFF until optional load and observation");
            var controller=tip.mountedWeapons();var saved=controller.write(tip);require(saved.getList("cooldowns",NbtElement.COMPOUND_TYPE).isEmpty(),"no prior automatic shots or cooldown resets");
            if(input!=Input.ORDINARY){
                var aa=saved.getCompound("aaDefense");var claims=aa.getList("claims",NbtElement.COMPOUND_TYPE);
                require(aa.getInt("schema")==1&&claims.size()==1&&claims.getCompound(0).containsUuid("principal"),"optional data derives from the actual normal owner USE");
                var originalWithoutAa=saved.copy();originalWithoutAa.remove("aaDefense");
                loadedMounted=saved.copy();expectedOpaque=aa.copy();
                if(input==Input.FUTURE)expectedOpaque.putInt("schema",37);
                else expectedOpaque.getList("claims",NbtElement.COMPOUND_TYPE).getCompound(0).remove("principal");
                loadedMounted.put("aaDefense",expectedOpaque.copy());
                var id=tip.getUuid();var cells=List.copyOf(tip.cells());long revision=tip.shapeRevision();var pos=tip.getPos();var rotation=tip.rotation();
                var velocity=tip.physicsVelocity();var angular=tip.physicsAngularVelocity();double mass=tip.physicsMass();
                // Explicit save-load fixture: only the optional ownership bytes
                // differ; the ordinary real body/controller is not replaced.
                controller.read(tip,loadedMounted);
                var roundTrip=controller.write(tip);var withoutAa=roundTrip.copy();withoutAa.remove("aaDefense");
                require(controller.defenseClaims().readOnly()&&controller.defenseClaims().size()==0&&expectedOpaque.equals(roundTrip.get("aaDefense")),"opaque optional record is preserved without decoded authority");
                require(withoutAa.equals(originalWithoutAa)&&id.equals(tip.getUuid())&&cells.equals(tip.cells())&&revision==tip.shapeRevision()&&pos.equals(tip.getPos())&&rotation.equals(tip.rotation())
                    &&velocity.equals(tip.physicsVelocity())&&angular.equals(tip.physicsAngularVelocity())&&mass==tip.physicsMass(),"optional codec load changes no legacy timers/material/body/motion state");
            }else {loadedMounted=saved.copy();require(!saved.contains("aaDefense")&&!controller.defenseClaims().readOnly(),"explicitly released ordinary mode has valid absent extension");}
            var cfg=MountedWeaponEmitter.config(tip,tip,GUN);
            require(cfg!=null&&cfg.speed==2.5f&&!cfg.speedIsSourceUnits&&cfg.life==4&&cfg.directDamage==8&&cfg.dmgClass==DamageClass.ENERGY&&cfg.inherit==1,
                "same original mounted laser config:2.5/tick plus real material inheritance,8damage,80life");
            var muzzle=MountedWeaponEmitter.muzzle(tip,GUN);var end=muzzle.add(MountedWeaponEmitter.direction(tip,GUN).multiply(44));
            require(WorldEditSafety.loadedSegment(world,muzzle,end)&&chunks.entityTicking(BlockPos.ofFloored(muzzle),BlockPos.ofFloored(end)),"full outgoing observation corridor is actually available and entity-ticking");
            require(world.raycast(new RaycastContext(muzzle,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,tip)).getType()==HitResult.Type.MISS,"real ordinary terrain corridor is clear");
            for(var body:BodySpatialIndex.query(world,new Box(muzzle,end).expand(.1)))require(body.physicsRaycast(muzzle,end).isEmpty(),"no real hull occludes the outgoing material corridor");
            use(tip,LEVER,false);require(tip.getLocalBlock(LEVER).get(Properties.POWERED),"normal real lever USE powers the existing controller");
            principal.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);principal.setPosition(tip.localToWorld(Vec3d.ofCenter(GUN)).add(0,-principal.getHeight()*.5,-6));principal.setVelocity(Vec3d.ZERO);
            operatorAtStart=principal.getPos();startedAt=lastObserved=world.getTime();
            record("BEGIN freshRoot="+fresh(root)+" freshTip="+fresh(tip)+" body="+tip.getUuid()+" raw="+loadedMounted+" operator="+operatorAtStart+" configuredSpeed="+cfg.speed+" inherited="+cfg.inheritedVelocity);
        }
        void observe(){
            long now=world.getTime();require(now==lastObserved+1,"observer samples each of16 consecutive actual world ticks");lastObserved=now;endFrames++;
            require(principal.isAlive()&&world.getEntity(principal.getUuid())==principal&&!principal.isCreative()&&!principal.isSpectator()
                &&tip.getLocalBlock(GUN).isOf(ModWorldBlocks.SHIP_LASER)&&tip.getLocalBlock(LEVER).get(Properties.POWERED)
                &&BodyJointRegistry.get(world).find(joint).isPresent(),"same loaded operator/material/powered mechanism survives the observation");
            var stats=BodyMountedWeapons.stats(world);scheduledChecks+=stats.checks();
            for(var shot:world.getEntitiesByClass(ProjectileEntity.class,new Box(base).expand(80),entity->entity.isAlive()&&entity.getOwnerEntity()==tip)){
                var n=shot.writeNbt(new NbtCompound());
                require(shot.getOwnerUuid().equals(tip.getUuid())&&shot.pointDefenseSource()==null&&shot.getDamageClass()==DamageClass.ENERGY&&shot.getMeshKind()==ProjectileEntity.MESH_BOLT
                    &&n.getFloat("dmg")==8&&n.getInt("life")==80-shot.age&&n.getFloat("splash")==0&&shot.getVisualScale()==.75f&&shot.getColorRgb()==0x46ebff,"observed actual original body-owned ordinary ENERGY bolt, not synthetic count/defense factory");
                var flight=flights.get(shot.getUuid());
                if(flight==null){require(flights.size()<4,"bounded original-cadence observation history");flight=new Flight(now,shot);flights.put(shot.getUuid(),flight);
                    record("ACTUAL_ORDINARY_SHOT uuid="+shot.getUuid()+" bodyOwner="+shot.getOwnerUuid()+" age="+shot.age+" pos="+shot.getPos()+" velocity="+shot.getVelocity()+" nbt="+n);}
                flight.lastAge=shot.age;flight.lastPosition=shot.getPos();flight.observations++;
            }
            record("FRAME elapsed="+(now-startedAt)+" fresh=true scheduler="+stats+" bolts="+flights.size()+" operator="+principal.getPos());
            if(now-startedAt<OBSERVATION_TICKS)return;
            c.assertTrue(endFrames==OBSERVATION_TICKS&&scheduledChecks>=2,"actual16 world/contact/controller frames include two normal laser opportunities");
            var after=tip.mountedWeapons().write(tip);writeProof(after);
            if(input==Input.ORDINARY){
                c.assertTrue(!tip.mountedWeapons().defenseClaims().readOnly()&&!after.contains("aaDefense"),"valid unclaimed ordinary controller remains explicitly ordinary");
                require(!flights.isEmpty(),"healthy ordinary control receives an actual automatic-fire opportunity");
                c.assertTrue(flights.size()>=2&&flights.values().stream().allMatch(Flight::moved),"healthy ordinary mechanism emits at least two real flying original body-owned ENERGY bolts in16 normal ticks");
                var births=flights.values().stream().map(flight->flight.born).sorted().toList();
                for(int i=1;i<births.size();i++)c.assertTrue(births.get(i)-births.get(i-1)==8,"actual unchanged eight-tick ordinary laser cadence");
            }else{
                c.assertTrue(expectedOpaque.equals(after.get("aaDefense"))&&tip.mountedWeapons().defenseClaims().readOnly(),"exact opaque AA record survives real powered observation");
                if(!flights.isEmpty())require(flights.values().stream().anyMatch(Flight::moved),"a claimed physical-emission negative observes real flight, not only birth or a synthetic counter");
                c.assertTrue(flights.isEmpty(),"unreadable "+input+" AA ownership must refuse automatic ordinary firing; actual body-owned ordinary bolts="+flights.keySet());
            }
            record("PASS complete16-frame material policy observation");success=true;close();
        }
        Path folder(){return Path.of(System.getProperty("drmd.test.playerAaOpaqueProofDir","reports/player-aa-opaque-proof"));}
        void writeProof(NbtCompound after){
            try{Files.createDirectories(folder());var proof=new NbtCompound();proof.putString("input",input.name());proof.put("loadedMounted",loadedMounted.copy());proof.put("afterMounted",after.copy());
                proof.putLong("startedAt",startedAt);proof.putLong("observedAt",world.getTime());proof.putInt("observedFrames",endFrames);proof.putInt("actualOrdinaryBolts",flights.size());
                NbtIo.writeCompressed(proof,folder().resolve(input.name().toLowerCase(Locale.ROOT)+"-actual-mounted.dat"));
            }catch(IOException failure){throw new UncheckedIOException(failure);}
        }
        @Override public void close(){
            if(closed)return;closed=true;pending=false;RIGS.remove(this);
            try{
                try{Files.createDirectories(folder());Files.writeString(folder().resolve(input.name().toLowerCase(Locale.ROOT)+"-trace.txt"),String.join("\n",trace)+"\n",StandardOpenOption.CREATE_NEW);}
                finally{
                    for(var shot:world.getEntitiesByClass(ProjectileEntity.class,new Box(base).expand(160),entity->root!=null&&entity.getOwnerEntity()==root||tip!=null&&entity.getOwnerEntity()==tip))shot.discard();
                    if(tip!=null){BodyJointRegistry.get(world).removeBody(tip.getUuid());tip.discard();}
                    if(root!=null){BodyJointRegistry.get(world).removeBody(root.getUuid());root.discard();}
                }
            }catch(IOException failure){throw new UncheckedIOException(failure);}
            finally{try{if(principal!=null){world.getServer().getPlayerManager().remove(principal);principal.discard();}}
                finally{if(channel!=null)channel.finishAndReleaseAll();chunks.close();}}
        }
    }
    private static void run(TestContext c,Input input){
        var rig=new Rig(c,input);
        c.runAtEveryTick(()->{
            try{
                if(rig.failure!=null)throw propagate(rig.failure);
                if(rig.success){rig.success=false;c.complete();return;}
                if(!rig.closed)rig.step();
            }catch(Throwable failure){rig.record("FIRST_FAILURE "+failure);try{rig.close();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}throw failure;}
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-opaque-future",tickLimit=240)
    public void futureOwnershipSchemaPreservesOpaqueBytesWithoutOrdinaryAutomaticFire(TestContext c){run(c,Input.FUTURE);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-opaque-corrupt",tickLimit=240)
    public void missingPrincipalPreservesCorruptClaimWithoutOrdinaryAutomaticFire(TestContext c){run(c,Input.CORRUPT);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-opaque-ordinary",tickLimit=240)
    public void explicitlyReleasedOrdinaryMechanismKeepsItsRealTwoBoltCadence(TestContext c){run(c,Input.ORDINARY);}
}
