package com.terminaldetector.drmd;

import com.terminaldetector.drmd.GenericControlCoreGameTests.Fixture;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import com.terminaldetector.drmd.world.mega.UfoControlSystem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.Predicate;

/** Unregistered first scheduler contracts. Actual world callbacks only: no direct
 * mounted tick/emitter/drive/body tick, no clock advance or authority injection. */
public final class GenericControlFireGameTests implements FabricGameTest {
    private static final BlockPos LASER=new BlockPos(1,1,4),ROCKET=new BlockPos(3,1,4);
    private static final BlockPos ORIGIN=new BlockPos(8,190,8),SECOND=new BlockPos(24,190,8);
    private static Map<BlockPos,BlockState> parts(boolean lever) {
        var cells=new LinkedHashMap<BlockPos,BlockState>();
        cells.put(LASER,ModWorldBlocks.SHIP_LASER.getDefaultState().with(Properties.FACING,Direction.SOUTH));
        cells.put(ROCKET,ModWorldBlocks.SHIP_ROCKET.getDefaultState().with(Properties.FACING,Direction.SOUTH));
        if(lever)cells.put(LASER.north(),Blocks.LEVER.getDefaultState().with(Properties.POWERED,false)
            .with(Properties.BLOCK_FACE,BlockFace.WALL).with(Properties.HORIZONTAL_FACING,Direction.NORTH));
        return cells;
    }
    private static List<ProjectileEntity> actual(Fixture f) {
        return f.c.getWorld().getEntitiesByClass(ProjectileEntity.class,f.body.getBoundingBox().expand(256),p->p.getOwnerEntity()==f.body);
    }
    private static boolean fire(Fixture f,int sequence,int mask) {
        return UfoControlSystem.receiveFire(f.pilot,new UfoControlSystem.Fire(UfoControlSystem.epoch(f.pilot),sequence,mask));
    }
    private static void claim(Fixture f) {
        f.c.assertTrue(GenericControlCoreGameTests.use(f)
            &&UfoControlSystem.receive(f.pilot,GenericControlCoreGameTests.input(f,0,0)),"real core USE and zero drive snapshot admitted");
    }
    private static void arm(Fixture f) {
        var lever=f.body.getLocalBlock(LASER.north());
        f.c.assertTrue(lever.isOf(Blocks.LEVER)&&!lever.get(Properties.POWERED)
            &&f.body.setLocalBlock(LASER.north(),lever.with(Properties.POWERED,true)),"actual supporting lever armed only after the required fixture authority is established");
    }
    private static void clean(Fixture f) {
        for(var shot:actual(f))shot.discard();f.close();
    }
    private static void aimAt(Fixture f,BlockPos cell) {
        var d=f.body.localToWorld(Vec3d.ofCenter(cell)).subtract(f.pilot.getEyePos());
        var a=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(new Vec3(d.x,d.y,d.z));
        f.pilot.setYaw((float)a.yawDegrees());f.pilot.setPitch((float)a.pitchDegrees());
    }
    private static final class Observation {
        final Fixture f;final Set<UUID> lasers=new HashSet<>(),rockets=new HashSet<>();
        final Map<UUID,Long> firstSeen=new LinkedHashMap<>();
        long began;boolean first;
        Observation(Fixture f){this.f=f;}
        long elapsed(){return f.c.getWorld().getTime()-began;}
        void collect(){for(var shot:actual(f)){
            (shot.getMeshKind()==1?rockets:lasers).add(shot.getUuid());
            firstSeen.putIfAbsent(shot.getUuid(),f.c.getWorld().getTime());
        }}
    }
    private static void observe(TestContext c,List<Fixture> owned,Predicate<Observation> scene) {
        var o=new Observation(owned.getFirst());int[] waits={0};boolean[] admitted={false};
        c.runAtEveryTick(()->{
            if(o.f.closed)return;
            try {
                c.getWorld().resetIdleTimeout();
                if(!admitted[0]){
                    c.assertTrue(++waits[0]<80,"bounded actual entity-ticking admission");
                    for(var f:owned)if(!f.chunks.entityTicking(f.body.getBlockPos()))return;
                    admitted[0]=true;o.began=c.getWorld().getTime();o.first=true;
                }
                c.assertTrue(o.elapsed()<90,"bounded first scheduler observation");o.collect();
                var stats=BodyMountedWeapons.stats(c.getWorld());
                c.assertTrue(stats.checks()<=BodyMountedWeapons.WORLD_CHECKS&&stats.shots()<=BodyMountedWeapons.WORLD_SHOTS,"shared real scheduler work/shot budgets");
                boolean complete=scene.test(o);o.first=false;
                if(complete){owned.forEach(GenericControlFireGameTests::clean);c.complete();}
            }catch(RuntimeException|Error failure){owned.forEach(GenericControlFireGameTests::clean);throw failure;}
        });
    }
    private static Fixture fixture(TestContext c,boolean lever){return new Fixture(c,parts(lever),ORIGIN);}
    private static Fixture second(TestContext c,Fixture first,boolean lever){
        try{return new Fixture(c,parts(lever),SECOND);}catch(RuntimeException|Error failure){first.close();throw failure;}
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-fire-select",tickLimit=180)
    public void realFireSnapshotsSelectLaserRocketAndZeroWithoutImmediateEmission(TestContext c) {
        var f=fixture(c,false);int[] phase={0};long[] changed={0};
        observe(c,List.of(f),o->{
            if(o.first){claim(f);c.assertTrue(fire(f,0,1)&&actual(f).isEmpty(),"receiver stores laser input without immediate emission");}
            if(phase[0]==0&&o.lasers.size()==1){
                c.assertTrue(o.rockets.isEmpty()&&fire(f,1,0),"actual laser only, then real zero snapshot");changed[0]=o.elapsed();phase[0]=1;
            }else if(phase[0]==1&&o.elapsed()-changed[0]>=6){
                c.assertTrue(o.lasers.size()==1&&o.rockets.isEmpty(),"zero does not repeat/queue either bank");
                c.assertTrue(!fire(f,2,4)&&fire(f,2,2),"unsupported auxiliary bit is refused without consuming the valid rocket sequence");phase[0]=2;
            }else if(phase[0]==2&&o.rockets.size()==1){
                c.assertTrue(o.lasers.size()==1&&UfoControlSystem.controlsBody(f.pilot,f.body),"same material scheduler selects a real rocket without another laser");return true;
            }
            return false;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-fire-independent",tickLimit=180)
    public void anActiveZeroCoreBlocksItsLeverFallbackButNotAnIndependentOrdinaryGun(TestContext c) {
        var f=fixture(c,true);var ordinary=second(c,f,true);var independent=new HashSet<UUID>();
        observe(c,List.of(f,ordinary),o->{
            if(o.first){claim(f);arm(f);arm(ordinary);c.assertTrue(fire(f,0,0),"explicit active zero fire snapshot");}
            for(var shot:actual(ordinary))independent.add(shot.getUuid());
            if(o.elapsed()<12)return false;
            c.assertTrue(o.lasers.isEmpty()&&o.rockets.isEmpty(),"active zero cannot fall through to the same body's powered lever");
            c.assertTrue(!independent.isEmpty()&&!ordinary.body.controlCore().bound(),"independent unpiloted ordinary lever still uses its existing schedule");return true;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-fire-refusal",tickLimit=180)
    public void compactAndOpaqueOwnershipRefusalsCannotBeBypassedByAValidCore(TestContext c) {
        var f=fixture(c,true);var opaque=second(c,f,true);var denied=new HashSet<UUID>();var marker=new NbtCompound();marker.putInt("schema",-1);marker.putUuid("origin",UUID.fromString("babe0000-0000-4000-8000-000000000001"));
        var unknown=new NbtCompound();unknown.putInt("schema",200);unknown.putString("unrecognized","preserve exact owned metadata");
        for(var entry:List.of(Map.entry(f,marker),Map.entry(opaque,unknown))){
            var tag=entry.getKey().body.mountedWeapons().write(entry.getKey().body);tag.put("aaDefense",entry.getValue().copy());
            entry.getKey().body.mountedWeapons().read(entry.getKey().body,tag);
        }
        observe(c,List.of(f,opaque),o->{
            if(o.first){for(var body:List.of(f,opaque)){claim(body);arm(body);c.assertTrue(fire(body,0,3)&&body.body.mountedWeapons().defenseClaims().readOnly(),"real valid manual input meets an actual refusal decoder state");}}
            for(var shot:actual(opaque))denied.add(shot.getUuid());
            if(o.elapsed()<12)return false;
            c.assertTrue(o.lasers.isEmpty()&&o.rockets.isEmpty()&&denied.isEmpty(),"no ordinary/manual fallback under compact or opaque refusal");
            c.assertTrue(f.body.mountedWeapons().write(f.body).get("aaDefense").equals(marker)
                &&opaque.body.mountedWeapons().write(opaque.body).get("aaDefense").equals(unknown),"both exact original refusal payloads remain owned and unchanged");
            c.assertTrue(f.body.mountedWeapons().write(f.body).getList("cooldowns",NbtElement.COMPOUND_TYPE).isEmpty()
                &&opaque.body.mountedWeapons().write(opaque.body).getList("cooldowns",NbtElement.COMPOUND_TYPE).isEmpty(),"a refusal does not create cooldown or a hidden shot");return true;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-fire-aa",tickLimit=180)
    public void aClaimedAaCellNeverFallsThroughWhileTheUnclaimedRocketCanFire(TestContext c) {
        var f=fixture(c,true);int[] phase={0};UUID[] token={null};
        observe(c,List.of(f),o->{
            if(o.first){
                f.pilot.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH));aimAt(f,LASER);
                c.assertTrue(BodyBlockInteraction.receive(f.pilot,new BodyBlockInteraction.Edit(f.body.getUuid(),f.body.shapeRevision(),LASER,BodyBlockInteraction.USE)),"normal real wrench USE creates the actual AA claim");
                token[0]=f.body.mountedWeapons().defenseClaims().get(LASER).orElseThrow().token();
                arm(f);
                c.assertTrue(BodyMountedWeapons.locallyControlled(f.body,LASER),"the claimed gun retains its real powered supporting lever");
            }
            if(phase[0]==0&&o.elapsed()>=4){claim(f);c.assertTrue(fire(f,0,3),"both manual banks requested after normal USE debounce");phase[0]=1;}
            if(phase[0]==1&&o.elapsed()>=12){
                c.assertTrue(o.lasers.isEmpty()&&o.rockets.size()==1,"invalid Creative AA principal cannot become ordinary manual laser; independent unclaimed rocket is the positive control");
                c.assertTrue(token[0].equals(f.body.mountedWeapons().defenseClaims().get(LASER).orElseThrow().token()),"manual control neither steals nor clears the actual AA token");return true;
            }
            return false;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-fire-expiry",tickLimit=180)
    public void staleFireAndExpiredOwnershipCannotQueueCatchupShots(TestContext c) {
        var f=fixture(c,false);int[] epoch={-1};
        observe(c,List.of(f),o->{
            if(o.first){claim(f);epoch[0]=UfoControlSystem.epoch(f.pilot);c.assertTrue(fire(f,0,1),"one fresh laser snapshot");}
            if(o.elapsed()<65)return false;
            c.assertTrue(o.lasers.size()==1&&o.rockets.isEmpty(),"four-tick fire freshness does not replay at later eight-tick opportunities");
            c.assertTrue(!UfoControlSystem.controls(f.pilot)&&!f.body.controlCore().bound()
                &&!UfoControlSystem.receiveFire(f.pilot,new UfoControlSystem.Fire(epoch[0],10000,3)),"ordinary existing lease expiry releases the core and rejects previous authority");return true;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-fire-cooldown",tickLimit=180)
    public void releaseToAnOrdinaryLeverRetainsTheSameMaterialCooldown(TestContext c) {
        var f=fixture(c,true);long[] first={Long.MIN_VALUE};
        observe(c,List.of(f),o->{
            if(o.first){claim(f);arm(f);c.assertTrue(fire(f,0,1),"manual laser request");}
            if(first[0]==Long.MIN_VALUE&&o.lasers.size()==1){
                first[0]=o.firstSeen.get(o.lasers.iterator().next());
                c.assertTrue(UfoControlSystem.release(f.pilot),"release keeps the same physical gun and powered lever");
                c.assertTrue(f.body.mountedWeapons().write(f.body).getList("cooldowns",NbtElement.COMPOUND_TYPE).stream()
                    .map(NbtCompound.class::cast).anyMatch(n->n.getLong("cell")==LASER.asLong()&&n.getInt("remaining")>0),"actual manual shot populated the existing material timer");
            }else if(first[0]!=Long.MIN_VALUE&&o.lasers.size()>=2){
                long next=o.lasers.stream().mapToLong(id->o.firstSeen.get(id)).filter(t->t>first[0]).min().orElseThrow();
                c.assertTrue(next-first[0]>=8,"ordinary lever cannot reset or bypass the manual shot's eight-tick cooldown");return true;
            }
            return false;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="generic-core-fire-core-loss",tickLimit=180)
    public void removingTheRealCoreBeforeTheWorldPhaseCancelsItsPendingShot(TestContext c) {
        var f=fixture(c,false);
        observe(c,List.of(f),o->{
            if(o.first){claim(f);c.assertTrue(fire(f,0,3),"valid pending manual snapshot");
                c.assertTrue(f.body.setLocalBlock(GenericControlCoreGameTests.CORE,Blocks.AIR.getDefaultState())&&!UfoControlSystem.controls(f.pilot),"real committed core loss revokes before ordinary emission");}
            if(o.elapsed()<12)return false;
            c.assertTrue(o.lasers.isEmpty()&&o.rockets.isEmpty(),"lost core cannot emit its pending mask without a powered lever fallback");return true;
        });
    }
}
