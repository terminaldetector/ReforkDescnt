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
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Separate future controls. All emission belongs to the real registered
 * automatic controller; no emitter/factory, solver or production tick is called. */
final class PlayerAaMaterialFixture implements AutoCloseable {
    enum Kind { FUTURE_MIXED, CORRUPT_MIXED, EMPTY_MIXED, FUTURE_FRAGMENT, ORDINARY_FRAGMENT, FUTURE_DESCENDANT, FUTURE_RELOAD_GUARD;
        boolean fracture(){return this==FUTURE_FRAGMENT||this==ORDINARY_FRAGMENT||this==FUTURE_DESCENDANT||this==FUTURE_RELOAD_GUARD;}
        boolean opaque(){return this==FUTURE_MIXED||this==CORRUPT_MIXED||this==FUTURE_FRAGMENT||this==FUTURE_DESCENDANT||this==FUTURE_RELOAD_GUARD;}
    }
    private static final BlockPos BEARING=BlockPos.ORIGIN,DRIVE=new BlockPos(-1,0,0),GUN=new BlockPos(2,0,0),BRIDGE=new BlockPos(1,0,0);
    private static final int MAX_RIGS=4,MAX_END_ACTIONS=4,FLIGHT_SETTLEMENT_TICKS=2;
    private static final Set<PlayerAaMaterialFixture> RIGS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean registered;
    private enum Phase { PREPARE, LOAD_PENDING, POWER, WINDOW_PENDING, QUIET, DESCENDANT_WAIT, RELOAD_WAIT, RELOAD_FRESH, ORIGIN_GONE_WAIT, MEASURE }
    private record Use(BlockBodyEntity body,BlockPos cell,boolean wrench){}
    private record Bank(String name,BlockBodyEntity body,BlockPos cell,boolean rocket,boolean refused){}
    private static final class Flight {
        final UUID id;final Bank bank;final long born;final boolean inWindow;final String bornPhase;final Vec3d initialPosition,initialVelocity;
        Vec3d lastPosition;int firstAge,lastAge,observations;
        Flight(ProjectileEntity shot,Bank bank,long now,boolean inWindow,String bornPhase){
            id=shot.getUuid();this.bank=bank;born=now;this.inWindow=inWindow;this.bornPhase=bornPhase;initialPosition=lastPosition=shot.getPos();initialVelocity=shot.getVelocity();firstAge=lastAge=shot.age;
        }
        boolean moved(){return observations>=2&&lastAge>firstAge&&lastPosition.squaredDistanceTo(initialPosition)>1;}
    }
    private static RuntimeException propagate(Throwable failure){
        if(failure instanceof RuntimeException runtime)return runtime;if(failure instanceof Error error)throw error;return new RuntimeException(failure);
    }
    private static synchronized void registerDispatcher(){
        if(registered)return;registered=true;
        ServerTickEvents.END_WORLD_TICK.register(world->{
            int work=0;
            for(var f:List.copyOf(RIGS)){
                if(f.world!=world||f.closed||f.phase==Phase.PREPARE)continue;
                if(work++>=MAX_END_ACTIONS)break;
                try{f.end();}catch(Throwable failure){f.failure=failure;f.record("END_FIRST_FAILURE "+failure);try{f.close();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}}
            }
        });
        ServerWorldEvents.UNLOAD.register((server,world)->{
            for(var f:List.copyOf(RIGS))if(f.world==world){RIGS.remove(f);f.failure=new IllegalStateException("Fixture world unloaded before material AA controls completed");}
        });
    }
    final TestContext c;final ServerWorld world;final Kind kind;final BlockPos base;final AiFixtureChunks chunks;
    final List<String> trace=new ArrayList<>();final List<BlockBodyEntity> bodies=new ArrayList<>();final List<Bank> banks=new ArrayList<>();
    final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();final List<Use> preparation=new ArrayList<>(),power=new ArrayList<>();
    final Map<UUID,Flight> flights=new LinkedHashMap<>();final Set<UUID> baseline=new HashSet<>();
    BlockBodyEntity root,tip,neighbor,fragment,descendantCarrier;ServerPlayerEntity principal;EmbeddedChannel channel;UUID joint,reloadedId;
    Path savedFragmentFile;long lifecycleAt;boolean lifecycleReady;
    NbtCompound loadedMounted,expectedOpaque,preCutMounted;Phase phase=Phase.PREPARE;
    int admission,nextPreparation,nextPower,frames,checks;long due,requestedAt,quietAt,startedAt=-1,lastObserved;
    boolean initialized,ownershipLoaded,closed,success;Throwable failure;

    private PlayerAaMaterialFixture(TestContext c,Kind kind){
        this.c=c;world=c.getWorld();this.kind=kind;base=c.getAbsolutePos(new BlockPos(108032+kind.ordinal()*512,200,25088));
        chunks=new AiFixtureChunks(world,base,3);
    }
    static void run(TestContext c,Kind kind){
        var f=new PlayerAaMaterialFixture(c,kind);
        c.runAtEveryTick(()->{
            try{if(f.failure!=null)throw propagate(f.failure);if(f.success){f.success=false;c.complete();return;}if(!f.closed)f.step();}
            catch(Throwable failure){f.record("FIRST_FAILURE "+failure);try{f.close();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}throw failure;}
        });
    }
    private void require(boolean condition,String message){c.assertTrue(condition,"PRECONDITION_UNOBSERVED: "+message+" kind="+kind+" phase="+phase);}
    private void record(String text){var row="testTick="+c.getTick()+" worldTime="+world.getTime()+" "+text;trace.add(row);System.out.println("PLAYER_AA_MATERIAL "+kind+" "+row);}
    private BlockState bearing(Direction face){return ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(MechanicalBearingBlock.FACING,face);}
    private BlockPos gunCell(){return kind==Kind.FUTURE_DESCENDANT?new BlockPos(6,0,0):GUN;}
    private void cell(List<BlockBodyEntity.Cell> cells,BlockPos at,BlockState state){cells.add(new BlockBodyEntity.Cell(at,state));}
    private void gun(List<BlockBodyEntity.Cell> cells,BlockPos pos,boolean rocket){
        cell(cells,pos,(rocket?ModWorldBlocks.SHIP_ROCKET:ModWorldBlocks.SHIP_LASER).getDefaultState().with(Properties.FACING,Direction.SOUTH));
        cell(cells,pos.up(),Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE,BlockFace.FLOOR).with(Properties.POWERED,false));
    }
    private BlockBodyEntity create(List<BlockBodyEntity.Cell> cells,BlockPos at){
        var body=BlockBodyEntity.createLocal(world,cells,Vec3d.of(at),Quat.IDENTITY,Vec3d.ZERO,Vec3d.ZERO);body.setPhysicsGravity(false);bodies.add(body);return body;
    }
    private void setup(){
        var data=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),"aa-material-"+kind.ordinal()),false);
        principal=new ServerPlayerEntity(world.getServer(),world,data.gameProfile(),data.syncedOptions());
        var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);world.getServer().getPlayerManager().onPlayerConnect(connection,principal,data);
        principal.changeGameMode(GameMode.SURVIVAL);principal.setNoGravity(true);principal.getAbilities().flying=false;DescentPlayerData.get(principal).setEnabled(false);
        principal.setPosition(Vec3d.ofCenter(base.up(5)));require(world.getEntity(principal.getUuid())==principal&&!principal.isCreative(),"actual loaded ordinary Survival operator");
        var a=new ArrayList<BlockBodyEntity.Cell>();var b=new ArrayList<BlockBodyEntity.Cell>();
        cell(a,BEARING,bearing(Direction.EAST));cell(a,DRIVE,ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(MechanicalBearingBlock.FACING,Direction.EAST).with(Properties.POWER,0));
        for(int x=-8;x<=-1;x++)for(int y=-2;y<=-1;y++)for(int z=-3;z<=3;z++)cell(a,new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState());
        cell(b,BEARING,bearing(Direction.WEST));gun(b,gunCell(),false);
        if(kind==Kind.FUTURE_DESCENDANT){
            // Two real bridges, with successively smaller detached components.
            // Both cuts retain their source body's identity on the larger side.
            for(int y=-3;y<=-1;y++)for(int z=-20;z<=20;z++)cell(b,new BlockPos(0,y,z),Blocks.IRON_BLOCK.getDefaultState());
            cell(b,BRIDGE,Blocks.IRON_BLOCK.getDefaultState());
            for(int x=2;x<=5;x++)cell(b,new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState());
            for(int y=-2;y<=-1;y++)for(int z=-12;z<=12;z++)cell(b,new BlockPos(2,y,z),Blocks.IRON_BLOCK.getDefaultState());
            for(int x=6;x<=9;x++)for(int z=-2;z<=2;z++)cell(b,new BlockPos(x,-1,z),Blocks.IRON_BLOCK.getDefaultState());
            for(int x=7;x<=9;x++)cell(b,new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState());
        }else if(kind.fracture()){
            // Exactly one removable bridge. Parent ballast stays larger; no
            // floor cell bypasses the bridge into the detachable material.
            cell(b,BRIDGE,Blocks.IRON_BLOCK.getDefaultState());
            for(int y=-2;y<=-1;y++)for(int z=-12;z<=12;z++)cell(b,new BlockPos(0,y,z),Blocks.IRON_BLOCK.getDefaultState());
            for(int x=2;x<=5;x++)for(int z=-2;z<=2;z++)cell(b,new BlockPos(x,-1,z),Blocks.IRON_BLOCK.getDefaultState());
            for(int x=3;x<=5;x++)cell(b,new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState());
        }else{
            gun(b,new BlockPos(6,0,0),false);gun(b,new BlockPos(10,0,0),true);
            for(int x=1;x<=12;x++)for(int y=-2;y<=-1;y++)for(int z=-3;z<=3;z++)cell(b,new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState());
            cell(b,new BlockPos(0,-1,0),Blocks.IRON_BLOCK.getDefaultState());
        }
        root=create(a,base);tip=create(b,base.east());banks.add(new Bank("laser-a",tip,gunCell(),false,kind.opaque()));
        if(!kind.fracture()){
            banks.add(new Bank("laser-b",tip,new BlockPos(6,0,0),false,kind.opaque()));banks.add(new Bank("rocket",tip,new BlockPos(10,0,0),true,kind.opaque()));
            var n=new ArrayList<BlockBodyEntity.Cell>();gun(n,GUN,false);
            for(int x=-2;x<=6;x++)for(int y=-2;y<=-1;y++)for(int z=-3;z<=3;z++)cell(n,new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState());
            neighbor=create(n,base.east(24));banks.add(new Bank("ordinary-neighbor",neighbor,GUN,false,false));
        }
        // A real terminal wall bounds flight and loaded-volume work. The first
        // twelve+ blocks remain clear; bolts actually move before normal hits.
        for(int x=-8;x<=36;x++)for(int y=-12;y<=12;y++){
            var pos=base.add(x,y,28);previous.put(pos,world.getBlockState(pos));world.setBlockState(pos,Blocks.OBSIDIAN.getDefaultState(),2);
        }
        preparation.add(new Use(root,BEARING,true));preparation.add(new Use(tip,BEARING,true));preparation.add(new Use(tip,gunCell(),true));
        if(kind==Kind.EMPTY_MIXED||kind==Kind.ORDINARY_FRAGMENT)preparation.add(new Use(tip,gunCell(),true));
        if(neighbor!=null){preparation.add(new Use(neighbor,GUN,true));preparation.add(new Use(neighbor,GUN,true));}
        for(var bank:banks)power.add(new Use(bank.body(),bank.cell().up(),false));
        initialized=true;due=c.getTick();record("SETUP bodies="+bodies.stream().map(BlockBodyEntity::getUuid).toList()+" banks="+banks+" principal="+principal.getUuid());
    }
    private void use(Use action){
        require(startedAt<0&&phase!=Phase.QUIET,"all player aiming/preparation ends before measured material transaction");
        var body=action.body();var pos=action.cell();principal.setStackInHand(Hand.MAIN_HAND,action.wrench()?new ItemStack(ModWorldBlocks.MECHANISM_WRENCH):ItemStack.EMPTY);
        var bounds=body.localBlockBounds().get(pos);require(bounds!=null,"actual USE material exists");
        var p=body.localToWorld(new Vec3d(bounds.centre().x(),bounds.centre().y(),bounds.centre().z()));
        var eye=p.add(0,2.5,body.getLocalBlock(pos).isOf(ModWorldBlocks.SHIP_LASER)?-3:0);double h=principal.getEyeY()-principal.getY();
        principal.setPosition(eye.subtract(0,h,0));principal.setVelocity(Vec3d.ZERO);var d=p.subtract(principal.getEyePos()).normalize();var q=PortalTransform.vectorToYawPitch(new Vec3(d.x,d.y,d.z));
        principal.setYaw((float)q.yawDegrees());principal.setPitch((float)q.pitchDegrees());principal.setHeadYaw(principal.getYaw());
        var target=BodyBlockInteraction.target(principal,principal.getEyePos(),BodyBlockInteraction.viewDirection(principal),principal.getBlockInteractionRange());
        require(target.isPresent()&&target.get().body()==body&&target.get().hit().localBlock().equals(pos),"actual nearest-material USE ray: "+target);
        var edit=new BodyBlockInteraction.Edit(body.getUuid(),body.shapeRevision(),pos,BodyBlockInteraction.USE);boolean accepted=BodyBlockInteraction.receive(principal,edit);
        record("USE body="+body.getUuid()+" cell="+pos+" wrench="+action.wrench()+" revision="+edit.revision()+" accepted="+accepted);require(accepted,"normal revisioned USE accepted with original cooldown");
    }
    private void request(Phase requested){
        registerDispatcher();require(RIGS.contains(this)||RIGS.size()<MAX_RIGS,"bounded test-only END admission");RIGS.add(this);phase=requested;requestedAt=world.getTime();
    }
    private void step(){
        world.resetIdleTimeout();
        if(!initialized){if(!chunks.entityTicking(base.add(-10,0,-14),base.add(38,0,32))){require(++admission<80,"entity-ticking interest obtained within80ticks");return;}setup();}
        if(c.getTick()<due)return;
        if(phase==Phase.PREPARE){
            if(nextPreparation<preparation.size()){
                use(preparation.get(nextPreparation++));due=c.getTick()+5;
                if(nextPreparation==2){var links=BodyJointRegistry.get(world).forBody(tip.getUuid());require(links.size()==1&&links.getFirst().blockDriven(),"real player-built bearing/drive link");joint=links.getFirst().id();}
            }else request(Phase.LOAD_PENDING);
        }else if(phase==Phase.POWER){
            if(nextPower<power.size()){use(power.get(nextPower++));due=c.getTick()+5;}
            else{
                var subject=lifecycleReady?fragment:tip;principal.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);principal.setPosition(subject.localToWorld(Vec3d.ofCenter(gunCell())).add(0,-principal.getHeight()*.5,-6));principal.setVelocity(Vec3d.ZERO);
                request(Phase.WINDOW_PENDING);
            }
        }
    }
    private boolean fresh(BlockBodyEntity body){return body!=null&&body.isAlive()&&world.getEntity(body.getUuid())==body
        &&BodySpatialIndex.find(world,body.getId()).orElse(null)==body&&body.hasSimulatedPhysics()&&!body.boardingMotionPaused();}
    private void end(){
        require(world.getServer().isOnThread()&&world==c.getWorld(),"actual world server thread");
        // Only ordinary warm-up may be excluded from the later birth window.
        // An opaque controller is never entitled to fire during preparation.
        if(ownershipLoaded&&kind.opaque()&&phase!=Phase.MEASURE)for(var shot:rounds())if(forbiddenOwner(shot))observeShot(shot);
        if(phase==Phase.RELOAD_WAIT){restoreFragment();return;}
        if(phase==Phase.RELOAD_FRESH){
            require(world.getTime()-lifecycleAt<=32,"restored real body obtains normal fresh contact phase");if(!fresh(fragment))return;
            removeOriginAndRejectEdit();return;
        }
        if(phase==Phase.ORIGIN_GONE_WAIT){
            require(world.getTime()-lifecycleAt<=32,"actual source removal is observed within bounded normal ticks");
            if(world.getTime()-lifecycleAt<2||world.getEntity(tip.getUuid())!=null)return;
            prepareIndependentBody();return;
        }
        if(phase==Phase.LOAD_PENDING||phase==Phase.WINDOW_PENDING){
            require(world.getTime()-requestedAt<=32,"bounded32tick fresh-END/timer admission");
            if(bodies.stream().anyMatch(body->!fresh(body)))return;
            if(phase==Phase.LOAD_PENDING){loadOwnership();phase=Phase.POWER;due=c.getTick()+5;return;}
            if(kind==Kind.EMPTY_MIXED&&remaining(tip,new BlockPos(10,0,0))>8)return;
            validatePoweredCorridors();
            if(kind.fracture()&&!lifecycleReady){quietAt=world.getTime();phase=Phase.QUIET;record("BEGIN_PRECUT quiet8frames body="+tip.getUuid());}
            else beginWindow();return;
        }
        require(bodies.stream().allMatch(this::fresh),"real tracked bodies retain normal contact freshness");
        if(phase==Phase.QUIET){
            if(world.getTime()-quietAt<8)return;cut();
            if(kind==Kind.FUTURE_DESCENDANT){descendantCarrier=fragment;lifecycleAt=world.getTime();phase=Phase.DESCENDANT_WAIT;return;}
            if(kind==Kind.FUTURE_RELOAD_GUARD){saveFragment();return;}
            beginWindow();return;
        }
        if(phase==Phase.DESCENDANT_WAIT){if(world.getTime()-lifecycleAt<8)return;cutDescendant();beginWindow();return;}
        if(phase==Phase.MEASURE)observe();
    }
    private void loadOwnership(){
        var state=tip.mountedWeapons();var saved=state.write(tip);require(saved.getList("cooldowns",NbtElement.COMPOUND_TYPE).isEmpty(),"optional load does not reset prior shooting timers");
        loadedMounted=saved.copy();
        if(kind.opaque()){
            require(state.defenseClaims().get(gunCell()).isPresent(),"actual owner USE supplies known source provenance");expectedOpaque=saved.getCompound("aaDefense").copy();
            if(kind==Kind.CORRUPT_MIXED)expectedOpaque.getList("claims",NbtElement.COMPOUND_TYPE).getCompound(0).remove("principal");else expectedOpaque.putInt("schema",37);
            loadedMounted.put("aaDefense",expectedOpaque.copy());
        }else{
            require(!state.defenseClaims().readOnly()&&state.defenseClaims().size()==0,"normal owner release retains valid ordinary mode");
            if(kind==Kind.EMPTY_MIXED){var empty=new NbtCompound();empty.putInt("schema",1);empty.put("claims",new NbtList());loadedMounted.put("aaDefense",empty);}
        }
        var cells=List.copyOf(tip.cells());var id=tip.getUuid();var pos=tip.getPos();var q=tip.rotation();var v=tip.physicsVelocity();var spin=tip.physicsAngularVelocity();long revision=tip.shapeRevision();double mass=tip.physicsMass();
        state.read(tip,loadedMounted);var after=state.write(tip);var beforeTimers=saved.copy();beforeTimers.remove("aaDefense");var afterTimers=after.copy();afterTimers.remove("aaDefense");
        require(cells.equals(tip.cells())&&id.equals(tip.getUuid())&&pos.equals(tip.getPos())&&q.equals(tip.rotation())&&v.equals(tip.physicsVelocity())&&spin.equals(tip.physicsAngularVelocity())
            &&revision==tip.shapeRevision()&&mass==tip.physicsMass()&&beforeTimers.equals(afterTimers),"only optional ownership bytes change; physical material/motion/timers are untouched");
        require(state.defenseClaims().readOnly()==kind.opaque()&&state.defenseClaims().size()==0,"input takes intended opaque or valid empty/absent decoder path");
        if(kind.opaque())require(expectedOpaque.equals(after.get("aaDefense")),"exact raw future/corrupt blob survives initial load");
        else require(!after.contains("aaDefense"),"valid empty/absent ownership canonicalizes to ordinary without opaque data");
        ownershipLoaded=true;record("LOADED actualMounted="+loadedMounted+" after="+after);
    }
    private int remaining(BlockBodyEntity body,BlockPos cell){
        var list=body.mountedWeapons().write(body).getList("cooldowns",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++)if(list.getCompound(i).getLong("cell")==cell.asLong())return list.getCompound(i).getInt("remaining");return 0;
    }
    private void validatePoweredCorridors(){
        for(var bank:banks){
            require(bank.body().getLocalBlock(bank.cell().up()).get(Properties.POWERED),"actual attached lever stays ON: "+bank.name());
            var cfg=MountedWeaponEmitter.config(bank.body(),bank.body(),bank.cell());require(cfg!=null&&cfg.speed==(bank.rocket()?1.4f:2.5f)&&cfg.directDamage==(bank.rocket()?16:8),"original authored material weapon config");
            var from=cfg.pos;var to=from.add(cfg.dir.multiply(35));require(WorldEditSafety.loadedSegment(world,from,to),"real firing corridor is loaded");
            var hit=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,bank.body()));
            require(hit.getType()==HitResult.Type.BLOCK&&hit.getPos().distanceTo(from)>12,"real terminal wall leaves a clear ordinary flight interval");
            var clearEnd=from.add(cfg.dir.multiply(12));
            for(var body:BodySpatialIndex.query(world,new Box(from,clearEnd).expand(.1)))require(body.physicsRaycast(from,clearEnd).isEmpty(),"no hull masks physical emission opportunity");
        }
    }
    private List<ProjectileEntity> rounds(){return world.getEntitiesByClass(ProjectileEntity.class,new Box(base).expand(96),s->s.isAlive()&&bodies.stream().anyMatch(b->b.getUuid().equals(s.getOwnerUuid())));}
    private boolean forbiddenOwner(ProjectileEntity shot){
        if(!kind.opaque())return false;
        return shot.getOwnerEntity()==tip||shot.getOwnerEntity()==fragment||shot.getOwnerEntity()==descendantCarrier
            ||banks.stream().anyMatch(bank->bank.refused()&&bank.body()==shot.getOwnerEntity());
    }
    private void cut(){
        preCutMounted=tip.mountedWeapons().write(tip);fragment=cutMaterial(tip,BRIDGE,"REAL_CUT");
    }
    private BlockBodyEntity cutMaterial(BlockBodyEntity source,BlockPos bridge,String receipt){
        var priorTimers=source.mountedWeapons().write(source).getList("cooldowns",NbtElement.COMPOUND_TYPE).copy();
        var point=source.localToWorld(Vec3d.ofCenter(gunCell()));var velocity=source.surfaceVelocityAt(point);var before=new HashSet<>(BodySpatialIndex.loaded(world));
        require(source.setLocalBlock(bridge,Blocks.AIR.getDefaultState()),"actual bridge edit commits material fracture");
        var actualChildren=BodySpatialIndex.loaded(world).stream().filter(b->!before.contains(b)&&b.getPos().squaredDistanceTo(Vec3d.of(base))<64*64).toList();bodies.addAll(actualChildren);
        var fragments=actualChildren.stream().filter(b->b.getLocalBlock(gunCell()).isOf(ModWorldBlocks.SHIP_LASER)).toList();
        require(fragments.size()==1,"one actual tracked continuous gun fragment, not a static proxy");var child=fragments.getFirst();
        require(child!=source&&!child.getUuid().equals(source.getUuid())&&source.getLocalBlock(gunCell()).isAir()&&child.getLocalBlock(gunCell().up()).isOf(Blocks.LEVER)
            &&child.localToWorld(Vec3d.ofCenter(gunCell())).squaredDistanceTo(point)<1e-10&&child.surfaceVelocityAt(point).squaredDistanceTo(velocity)<1e-10,
            "cut preserves real continuous material point and world contact velocity without body recreation/follow teleport");
        require(child.mountedWeapons().write(child).getList("cooldowns",NbtElement.COMPOUND_TYPE).equals(priorTimers),"successful cut retains the actual material cooldown, not a reset");
        banks.clear();banks.add(new Bank("detached-laser",child,gunCell(),false,kind.opaque()));
        record(receipt+" parent="+source.getUuid()+" child="+child.getUuid()+" point="+point+" velocity="+velocity+" parentMounted="+source.mountedWeapons().write(source)+" childMounted="+child.mountedWeapons().write(child));return child;
    }
    private void cutDescendant(){
        fragment=cutMaterial(descendantCarrier,new BlockPos(5,0,0),"REAL_DESCENDANT_CUT");
        record("DESCENDANT_ORIGINS original="+tip.getUuid()+" carrier="+descendantCarrier.getUuid()+" finalChild="+fragment.getUuid());
    }
    private void saveFragment(){
        try{
            Files.createDirectories(folder());savedFragmentFile=folder().resolve("actual-refusal-fragment.dat");
            var held=fragment.mountedWeapons().write(fragment);var callerCopy=held.getCompound("aaDefense");callerCopy.remove("origin");
            require(fragment.mountedWeapons().write(fragment).getCompound("aaDefense").containsUuid("origin"),"mutating returned snapshot cannot alter stored refusal");
            NbtIo.writeCompressed(fragment.writeNbt(new NbtCompound()),savedFragmentFile);reloadedId=fragment.getUuid();
            record("REAL_FILE_SAVED body="+reloadedId+" path="+savedFragmentFile+" marker="+fragment.mountedWeapons().write(fragment));
            fragment.discard();bodies.remove(fragment);fragment=null;lifecycleAt=world.getTime();phase=Phase.RELOAD_WAIT;
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private void restoreFragment(){
        require(world.getTime()-lifecycleAt<=32,"saved old entity retires before bounded normal reload");
        if(world.getTime()-lifecycleAt<2||world.getEntity(reloadedId)!=null)return;
        try{
            var input=NbtIo.readCompressed(savedFragmentFile,NbtSizeTracker.ofUnlimitedBytes());var restored=ModEntities.BLOCK_BODY.create(world);require(restored!=null,"actual registered body decoder");
            restored.readNbt(input);require(world.spawnEntity(restored)&&restored.getUuid().equals(reloadedId),"one actual saved material body returns with same UUID");
            fragment=restored;bodies.add(restored);banks.clear();banks.add(new Bank("restored-laser",restored,gunCell(),false,true));
            input.getCompound("mountedWeapons").getCompound("aaDefense").putInt("schema",1);
            require(restored.mountedWeapons().write(restored).getCompound("aaDefense").getInt("schema")==-1&&restored.mountedWeapons().defenseClaims().readOnly(),"saved-input mutation cannot alias the restored stored denial");
            require(BodySpatialIndex.loaded(world).stream().filter(b->b.getUuid().equals(reloadedId)).count()==1,"no duplicate saved material entity");
            lifecycleAt=world.getTime();phase=Phase.RELOAD_FRESH;record("REAL_FILE_RELOADED body="+restored.getUuid()+" marker="+restored.mountedWeapons().write(restored));
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private void removeOriginAndRejectEdit(){
        var before=fragment.mountedWeapons().write(fragment);var cells=List.copyOf(fragment.cells());long revision=fragment.shapeRevision();
        var position=fragment.getPos();var velocity=fragment.physicsVelocity();var rotation=fragment.rotation();var spin=fragment.physicsAngularVelocity();double mass=fragment.physicsMass();
        var known=new HashSet<>(BodySpatialIndex.loaded(world));
        require(!BlockBodyEntity.supports(Blocks.CHEST.getDefaultState())&&!fragment.setLocalBlock(new BlockPos(0,1,0),Blocks.CHEST.getDefaultState()),"actual unsupported functional-block edit is rejected before commit");
        require(known.equals(new HashSet<>(BodySpatialIndex.loaded(world)))&&before.equals(fragment.mountedWeapons().write(fragment))&&cells.equals(fragment.cells())&&revision==fragment.shapeRevision()
            &&position.equals(fragment.getPos())&&velocity.equals(fragment.physicsVelocity())&&rotation.equals(fragment.rotation())&&spin.equals(fragment.physicsAngularVelocity())&&mass==fragment.physicsMass(),
            "rejected real edit leaves no fragment, metadata, timer, geometry or motion residue");
        record("REAL_EDIT_REJECTED body="+fragment.getUuid()+" marker="+before+" revision="+revision);
        BodyJointRegistry.get(world).removeBody(tip.getUuid());tip.discard();bodies.remove(tip);lifecycleAt=world.getTime();phase=Phase.ORIGIN_GONE_WAIT;
        record("ORIGINAL_ENTITY_REMOVED actualOrigin="+tip.getUuid()+" restored="+fragment.getUuid());
    }
    private void prepareIndependentBody(){
        var cells=new ArrayList<BlockBodyEntity.Cell>();gun(cells,GUN,false);
        for(int x=-2;x<=6;x++)for(int y=-2;y<=-1;y++)for(int z=-3;z<=3;z++)cell(cells,new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState());
        neighbor=create(cells,base.east(24));require(!neighbor.mountedWeapons().defenseClaims().readOnly()&&neighbor.mountedWeapons().defenseClaims().size()==0,"independent new material receives no inherited refusal");
        banks.add(new Bank("ordinary-independent",neighbor,GUN,false,false));power.clear();power.add(new Use(neighbor,GUN,true));power.add(new Use(neighbor,GUN,true));power.add(new Use(neighbor,GUN.up(),false));
        nextPower=0;lifecycleReady=true;phase=Phase.POWER;due=c.getTick()+5;
        record("INDEPENDENT_BODY body="+neighbor.getUuid()+" actualOriginStillRemoved="+(world.getEntity(tip.getUuid())==null));
    }
    private void beginWindow(){
        baseline.clear();for(var shot:rounds())if(!forbiddenOwner(shot))baseline.add(shot.getUuid());
        startedAt=lastObserved=world.getTime();frames=checks=0;phase=Phase.MEASURE;
        record("BEGIN_WINDOW ticks="+window()+" settlement="+FLIGHT_SETTLEMENT_TICKS+" existingWarmupIds="+baseline+" operator="+principal.getPos());
    }
    private int window(){return kind.fracture()?16:32;}
    private void observe(){
        long now=world.getTime();require(now==lastObserved+1,"consecutive real END observation frames");lastObserved=now;frames++;
        require(principal.isAlive()&&principal.interactionManager.getGameMode()==GameMode.SURVIVAL&&world.getEntity(principal.getUuid())==principal
            &&(kind==Kind.FUTURE_RELOAD_GUARD&&lifecycleReady?tip.isRemoved()&&world.getEntity(tip.getUuid())==null:BodyJointRegistry.get(world).find(joint).isPresent()),"same real operator and expected material/source lifecycle survive without input rewrites");
        var stats=BodyMountedWeapons.stats(world);require(stats.checks()<=BodyMountedWeapons.WORLD_CHECKS&&stats.shots()<=BodyMountedWeapons.WORLD_SHOTS&&stats.bodies()<=BodyMountedWeapons.WORLD_BODIES,"original world work/shot/body budgets remain bounded");checks+=stats.checks();
        for(var shot:rounds())if(!baseline.contains(shot.getUuid()))observeShot(shot);
        record("FRAME elapsed="+(now-startedAt)+" scheduler="+stats+" observedActualShots="+flights.size());
        if(now-startedAt<window()+FLIGHT_SETTLEMENT_TICKS)return;
        finish();
    }
    private void observeShot(ProjectileEntity shot){
        long now=world.getTime();var flight=flights.get(shot.getUuid());
        if(flight==null){
            require(flights.size()<64&&shot.age==0,"bounded history observes actual new birth at the normal post-controller END phase");
            var matching=banks.stream().filter(b->b.body().getUuid().equals(shot.getOwnerUuid())&&b.rocket()==(shot.getMeshKind()==ProjectileEntity.MESH_ROCKET))
                .min(Comparator.comparingDouble(b->MountedWeaponEmitter.muzzle(b.body(),b.cell()).squaredDistanceTo(shot.getPos()))).orElseThrow();
            require(MountedWeaponEmitter.muzzle(matching.body(),matching.cell()).squaredDistanceTo(shot.getPos())<.0001,"actual new projectile belongs to the real material muzzle");
            flight=new Flight(shot,matching,now,phase==Phase.MEASURE&&now-startedAt<=window(),phase.name());flights.put(shot.getUuid(),flight);
            record("ACTUAL_SHOT uuid="+shot.getUuid()+" bank="+matching.name()+" phase="+phase+" window="+flight.inWindow+" pos="+shot.getPos()+" velocity="+shot.getVelocity()+" nbt="+shot.writeNbt(new NbtCompound()));
        }
        var n=shot.writeNbt(new NbtCompound());boolean rocket=flight.bank.rocket();
        require(shot.getOwnerEntity()==flight.bank.body()&&shot.pointDefenseSource()==null&&shot.getMeshKind()==(rocket?ProjectileEntity.MESH_ROCKET:ProjectileEntity.MESH_BOLT)
            &&shot.getDamageClass()==(rocket?DamageClass.EXPLOSIVE:DamageClass.ENERGY)&&n.getFloat("dmg")== (rocket?16:8)
            &&n.getInt("life")== (rocket?100:80)-shot.age&&n.getFloat("splash")== (rocket?20:0)&&n.getFloat("radius")== (rocket?2:0)&&!n.getBoolean("blast"),"original body-owned ordinary projectile and naturally decreasing lifetime");
        flight.lastAge=shot.age;flight.lastPosition=shot.getPos();flight.observations++;
    }
    private void finish(){
        require(frames==window()+FLIGHT_SETTLEMENT_TICKS&&checks>=window(),"complete fixed emission window and bounded real-flight settlement");writeProof();
        // Establish independent ordinary opportunity before a refusal assertion.
        for(var bank:banks)if(!bank.refused()){
            var seen=flights.values().stream().filter(f->f.bank.equals(bank)&&f.inWindow).sorted(Comparator.comparingLong(f->f.born)).toList();
            int expected=bank.rocket()?2:window()/8;require(!seen.isEmpty(),"healthy bank has actual ordinary emission opportunity: "+bank.name());
            c.assertTrue(seen.size()==expected&&seen.stream().allMatch(Flight::moved),"healthy "+bank.name()+" emits exact authored count of moving original projectiles: expected="+expected+" actual="+seen.size());
            for(int i=1;i<seen.size();i++)c.assertTrue(seen.get(i).born-seen.get(i-1).born==(bank.rocket()?24:8),"unchanged real bank cadence");
        }
        var refused=flights.values().stream().filter(f->f.bank.refused()).toList();
        if(!refused.isEmpty())require(refused.stream().anyMatch(Flight::moved),"decisive refusal negative contains an actual moving material-owned projectile");
        c.assertTrue(refused.isEmpty(),"opaque continuous ownership must refuse actual automatic fire, including detached/mixed banks; real fired ids="+refused.stream().map(f->f.id).toList());
        if(kind.opaque()){
            var owner=kind.fracture()?fragment:tip;
            c.assertTrue(expectedOpaque.equals(tip.mountedWeapons().write(tip).get("aaDefense")),"original controller retains its exact raw unknown data once");
            var child=owner.mountedWeapons().write(owner).getCompound("aaDefense");
            c.assertTrue((!kind.fracture()||child.getKeys().size()==2&&child.contains("schema",NbtElement.INT_TYPE)&&child.getInt("schema")==-1&&child.containsUuid("origin")&&child.getUuid("origin").equals(tip.getUuid()))
                &&owner.mountedWeapons().defenseClaims().readOnly()&&owner.mountedWeapons().defenseClaims().size()==0,
                "continuous child has compact persisted refusal provenance without unknown-blob replication or decoded live authority");
            if(descendantCarrier!=null){
                var carrier=descendantCarrier.mountedWeapons().write(descendantCarrier).getCompound("aaDefense");
                c.assertTrue(carrier.getKeys().size()==2&&carrier.contains("schema",NbtElement.INT_TYPE)&&carrier.getInt("schema")==-1&&carrier.containsUuid("origin")&&carrier.getUuid("origin").equals(tip.getUuid())
                    &&descendantCarrier.mountedWeapons().defenseClaims().readOnly()&&descendantCarrier.mountedWeapons().defenseClaims().size()==0,
                    "intermediate actual carrier keeps the same fixed two-field refusal, not another full unknown blob or ancestry chain");
            }
        }else c.assertTrue(!tip.mountedWeapons().defenseClaims().readOnly()&&(fragment==null||!fragment.mountedWeapons().defenseClaims().readOnly()),"valid empty/released ordinary material remains ordinary");
        record("PASS complete physical material bank/fragment control");success=true;close();
    }
    private Path folder(){return Path.of(System.getProperty("drmd.test.playerAaMaterialProofDir","reports/player-aa-material-proof"));}
    private void writeProof(){
        try{
            Files.createDirectories(folder());var n=new NbtCompound();n.putString("kind",kind.name());n.put("loadedMounted",loadedMounted.copy());n.put("parentMounted",tip.mountedWeapons().write(tip));
            if(fragment!=null){n.putUuid("actualFragment",fragment.getUuid());n.put("fragmentMounted",fragment.mountedWeapons().write(fragment));n.put("preCutMounted",preCutMounted.copy());}
            if(descendantCarrier!=null){n.putUuid("actualDescendantCarrier",descendantCarrier.getUuid());n.put("carrierMounted",descendantCarrier.mountedWeapons().write(descendantCarrier));}
            if(reloadedId!=null){n.putUuid("actualSavedEntity",reloadedId);n.putBoolean("originalEntityRemoved",tip.isRemoved()&&world.getEntity(tip.getUuid())==null);}
            if(neighbor!=null)n.put("neighborMounted",neighbor.mountedWeapons().write(neighbor));n.putInt("emissionWindow",window());n.putInt("observedFrames",frames);n.putLong("startedAt",startedAt);n.putLong("observedAt",world.getTime());
            var list=new NbtList();for(var f:flights.values()){
                var entry=new NbtCompound();entry.putUuid("projectile",f.id);entry.putString("bank",f.bank.name());entry.putUuid("actualOwner",f.bank.body().getUuid());entry.putBoolean("inWindow",f.inWindow);entry.putBoolean("expectedRefusal",f.bank.refused());
                entry.putLong("born",f.born);entry.putString("bornPhase",f.bornPhase);entry.putInt("firstAge",f.firstAge);entry.putInt("lastAge",f.lastAge);entry.putInt("observations",f.observations);entry.putString("initialPosition",f.initialPosition.toString());entry.putString("lastPosition",f.lastPosition.toString());entry.putString("initialVelocity",f.initialVelocity.toString());list.add(entry);
                record("FLIGHT uuid="+f.id+" bank="+f.bank.name()+" born="+f.born+" first="+f.initialPosition+" last="+f.lastPosition+" age="+f.firstAge+".."+f.lastAge+" observed="+f.observations+" window="+f.inWindow);
            }n.put("actualFlights",list);NbtIo.writeCompressed(n,folder().resolve(kind.name().toLowerCase(Locale.ROOT)+"-actual-material.dat"));
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    @Override public void close(){
        if(closed)return;closed=true;RIGS.remove(this);
        try{
            try{Files.createDirectories(folder());Files.writeString(folder().resolve(kind.name().toLowerCase(Locale.ROOT)+"-trace.txt"),String.join("\n",trace)+"\n",StandardOpenOption.CREATE_NEW);}
            finally{for(var shot:rounds())shot.discard();for(var body:bodies){BodyJointRegistry.get(world).removeBody(body.getUuid());body.discard();}for(var entry:previous.entrySet())world.setBlockState(entry.getKey(),entry.getValue(),2);}
        }catch(IOException failure){throw new UncheckedIOException(failure);}
        finally{try{if(principal!=null){world.getServer().getPlayerManager().remove(principal);principal.discard();}}
            finally{if(channel!=null)channel.finishAndReleaseAll();chunks.close();}}
    }
}
