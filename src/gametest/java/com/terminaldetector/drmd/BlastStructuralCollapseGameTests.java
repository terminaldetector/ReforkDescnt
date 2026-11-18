package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.physics.PhysicsProfileState;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual native TNT/projectile damage and normal body ticks. Never invokes the collapse service. */
public final class BlastStructuralCollapseGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="building-real-tnt",tickLimit=240)
    public void actualTntRemovesSupportAndTheSurvivingObsidianRoofFallsOnOrdinaryTicks(TestContext c) {
        drive(c,new Fixture(c,0,Mode.TNT));
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="building-real-rockets",tickLimit=240)
    public void anActualTwoRocketSalvoDetachesTheRoofThatTheLegacyColumnCutMustRefuse(TestContext c) {
        drive(c,new Fixture(c,256,Mode.ROCKETS));
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="building-anchors-and-data",tickLimit=240)
    public void remainingGroundSupportAndASeparateChestComponentRefuseDetachmentWithoutDataLoss(TestContext c) {
        drive(c,new Fixture(c,512,Mode.ANCHORED),new Fixture(c,768,Mode.CHEST));
    }
    private enum Mode {TNT,ROCKETS,ANCHORED,CHEST}
    private static void drive(TestContext c,Fixture... fixtures) {
        c.runAtEveryTick(()->{
            try {
                c.getWorld().resetIdleTimeout();
                for(var f:fixtures)if(!f.started&&!f.ready())return;
                for(var f:fixtures)if(!f.started)f.start();
                boolean done=true;for(var f:fixtures)done&=f.sample();
                if(done){for(var f:fixtures)f.close();c.complete();}
            }catch(Throwable failure){
                for(var f:fixtures){System.out.println("BUILDING_BLAST_FIRST_FAILURE "+f.state());f.close();}
                throw failure;
            }
        });
    }
    private static final class Fixture implements AutoCloseable {
        private static final int SUPPORT_HEIGHT=3,ROOF_HEIGHT=7;
        final TestContext c;
        final ServerWorld world;
        final BlockPos base;
        final Mode mode;
        final AiFixtureChunks chunks;
        final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
        final List<BlockPos> supports=new ArrayList<>(),roof=new ArrayList<>(),obsidian=new ArrayList<>(),secondPillar=new ArrayList<>();
        final List<Entity> ordnance=new ArrayList<>();
        final List<Vec3d> actualHits=new ArrayList<>();
        BlockBodyEntity body;
        TntEntity tnt;
        NbtCompound chestBefore;
        BlockPos chestPos;
        Vec3d bodyCapturePosition;
        int admission,bodyCaptureAge,actualCuts,peakBodies;
        long launchedAt,capturedAt,impactAt=Long.MIN_VALUE;
        boolean started,done,closed;

        Fixture(TestContext c,int offset,Mode mode) {
            this.c=c;world=c.getWorld();this.mode=mode;
            base=c.getAbsolutePos(new BlockPos(600000+offset,100,64000));
            chunks=new AiFixtureChunks(world,base,1);
        }
        boolean ready() {
            c.assertTrue(PhysicsProfileState.expanded(world),"ADMISSION: existing expanded profile owns structural physics");
            var launch=base.add(0,2,-8);var far=base.add(9,8,0);var corner=base.add(-10,0,-10);
            if(chunks.entityTicking(base,launch,far,corner))return true;
            c.assertTrue(++admission<=80,"ADMISSION: real actor, roof and fixture columns must be entity-ticking: "+chunks.readiness(base,launch,far,corner));
            return false;
        }
        void put(BlockPos p,BlockState state) {
            var before=WorldAccess.of(world).readIfLoaded(p);
            c.assertTrue(before.isPresent(),"ADMISSION: fixture never writes unavailable XYZ material: "+p);
            originals.putIfAbsent(p.toImmutable(),before.orElseThrow());
            world.setBlockState(p,state,2);
        }
        void start() {
            // This is a finite test-owned setup, not the production component/read budget.
            for(var p:BlockPos.iterate(base.add(-12,-1,-12),base.add(12,12,12))) {
                var state=WorldAccess.of(world).readIfLoaded(p);
                c.assertTrue(state.isPresent(),"ADMISSION: entire scene is FULL before setup");
                if(!state.orElseThrow().isAir())put(p,Blocks.AIR.getDefaultState());
            }
            for(var p:BlockPos.iterate(base.add(-10,0,-10),base.add(10,0,10)))put(p,Blocks.BEDROCK.getDefaultState());
            for(int y=1;y<=SUPPORT_HEIGHT;y++){var p=base.up(y);supports.add(p);put(p,Blocks.BRICKS.getDefaultState());}
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0) {
                var p=base.add(x,ROOF_HEIGHT,z);roof.add(p);obsidian.add(p);put(p,Blocks.OBSIDIAN.getDefaultState());
            }
            // Keep all breakable support in TNT's core. A resistant side brace joins the roof,
            // and also makes the old one-wide-column rocketCut reject before ownership changes.
            for(int y=SUPPORT_HEIGHT;y<ROOF_HEIGHT;y++) {
                var p=base.add(1,y,0);obsidian.add(p);put(p,Blocks.OBSIDIAN.getDefaultState());
            }
            if(mode==Mode.ANCHORED||mode==Mode.CHEST) {
                for(int x=2;x<=9;x++){var p=base.add(x,ROOF_HEIGHT,0);obsidian.add(p);put(p,Blocks.OBSIDIAN.getDefaultState());}
                if(mode==Mode.ANCHORED)for(int y=1;y<ROOF_HEIGHT;y++) {
                    var p=base.add(9,y,0);secondPillar.add(p);put(p,Blocks.BRICKS.getDefaultState());
                }
                else {
                    chestPos=base.add(9,ROOF_HEIGHT+1,0);put(chestPos,Blocks.CHEST.getDefaultState());
                    c.assertTrue(world.getBlockEntity(chestPos) instanceof ChestBlockEntity,"ADMISSION: real native chest exists");
                    var chest=(ChestBlockEntity)world.getBlockEntity(chestPos);
                    chest.setStack(0,new ItemStack(Items.DIAMOND,3));chest.setCustomName(Text.literal("structural-data-control"));chest.markDirty();
                    chestBefore=chest.createNbt(world.getRegistryManager());
                }
            }
            c.assertTrue(world.getBlockState(base.add(1,3,0)).isOf(Blocks.OBSIDIAN)
                &&world.getBlockState(base.up(3)).isOf(Blocks.BRICKS),"ADMISSION: attached resistant roof brace makes legacy rocketCut refuse the upper column");
            c.assertTrue(candidates().isEmpty(),"ADMISSION: no pre-existing physical roof in this private scene");
            if(mode==Mode.ROCKETS) {
                // A fixed disclosed two-round salvo accommodates the real crater's partial RNG.
                // Both launches are setup-only; no observation-driven firing/reseeding or movement.
                for(int shot=0;shot<2;shot++) {
                    var rocket=ModEntities.PROJECTILE.create(world);c.assertTrue(rocket!=null,"real registered projectile factory");
                    rocket.setDamageClass(DamageClass.EXPLOSIVE);rocket.setDirectDamage(100);
                    rocket.setSplashDamage(0);rocket.setSplashRadius(8);rocket.setWorldBlast(true);rocket.setLifeTicks(100);
                    rocket.setMeshKind(ProjectileEntity.MESH_ROCKET);rocket.setGravityStrength(0);rocket.setDrag(0);
                    rocket.setPosition(base.getX()+.5,base.getY()+2.5,base.getZ()-6-shot*2);
                    rocket.setVelocity(0,0,1);
                    rocket.setOnHit(hit->{if(hit.isBlock())actualHits.add(hit.hitPos());});
                    c.assertTrue(chunks.entityTicking(rocket.getBlockPos())&&world.spawnEntity(rocket),"ADMISSION: actual rocket spawn ticks");ordnance.add(rocket);
                }
            } else {
                tnt=new TntEntity(world,base.getX()+.5,base.getY()+2.5,base.getZ()-1.25,null);
                tnt.setVelocity(Vec3d.ZERO);tnt.setFuse(1);
                c.assertTrue(chunks.entityTicking(tnt.getBlockPos())&&world.spawnEntity(tnt),"ADMISSION: actual fuse1 TNT spawn ticks");ordnance.add(tnt);
            }
            launchedAt=world.getTime();started=true;
            System.out.println("BUILDING_BLAST_STARTED "+state());
        }
        List<BlockBodyEntity> candidates() {
            return BodySpatialIndex.query(world,new Box(base).expand(24)).stream().filter(BlockBodyEntity::physicsGravity)
                .filter(b->b.cells().stream().filter(cell->cell.state().isOf(Blocks.OBSIDIAN)).count()>=roof.size()).toList();
        }
        boolean sample() {
            if(done)return true;
            long elapsed=world.getTime()-launchedAt;
            c.assertTrue(elapsed<=140,"ADMISSION: finite real world-tick budget, no silent unticked actor: "+state());
            boolean impact=mode==Mode.ROCKETS?!actualHits.isEmpty():tnt.isRemoved()&&tnt.age>0;
            if(!impact){c.assertTrue(elapsed<=24,"ADMISSION: native ordnance must reach its actual impact: "+state());return false;}
            if(impactAt==Long.MIN_VALUE) {
                impactAt=world.getTime();actualCuts=(int)supports.stream().filter(p->world.getBlockState(p).isAir()||world.getBlockState(p).isReplaceable()).count();
                c.assertTrue(actualCuts>0,"ADMISSION: actual destructive blast must remove support; no guessed cuts: "+state());
                System.out.println("BUILDING_BLAST_ACTUAL_CUT "+state());
            }
            var found=candidates();peakBodies=Math.max(peakBodies,found.size());
            if(mode==Mode.ANCHORED||mode==Mode.CHEST) {
                c.assertTrue(found.isEmpty()&&obsidian.stream().allMatch(p->world.getBlockState(p).isOf(Blocks.OBSIDIAN)),"anchored/data component remains static rather than partially moving: "+state());
                if(mode==Mode.ANCHORED)c.assertTrue(secondPillar.stream().allMatch(p->world.getBlockState(p).isOf(Blocks.BRICKS)),"distant second pillar remains genuinely grounded");
                else c.assertTrue(world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest&&chestBefore.equals(chest.createNbt(world.getRegistryManager())),"native chest inventory/name/position data unchanged");
                if(world.getTime()-impactAt<8)return false;
            } else {
                c.assertTrue(found.size()<=1,"one connected roof cannot duplicate physical ownership: "+state());
                if(body==null) {
                    if(found.isEmpty()){c.assertTrue(world.getTime()-impactAt<=8,"GAMEPLAY: actual removed support must produce a physical roof: "+state());return false;}
                    body=found.getFirst();bodyCaptureAge=body.age;bodyCapturePosition=body.physicsCentre();capturedAt=world.getTime();
                    c.assertTrue(body.cells().stream().filter(cell->cell.state().isOf(Blocks.OBSIDIAN)).count()==obsidian.size()
                        &&obsidian.stream().allMatch(p->world.getBlockState(p).isAir()),"all surviving resistant roof cells transfer once with no static duplicates: "+state());
                    System.out.println("BUILDING_BLAST_ROOF_CAPTURED "+state());return false;
                }
                c.assertTrue(body.isAlive()&&found.size()==1&&found.getFirst()==body,"same live physical roof identity remains owned");
                int actualBodyTicks=body.age-bodyCaptureAge;
                c.assertTrue(chunks.entityTicking(body.getBlockPos()),"ADMISSION: actual moving body pivot remains entity-ticking");
                if(actualBodyTicks<8||body.physicsCentre().y>=bodyCapturePosition.y-.25) {
                    c.assertTrue(world.getTime()-capturedAt<=64&&actualBodyTicks<=48,"GAMEPLAY: gravity must lower the actual ticked roof, no manual body.step/tick: "+state());return false;
                }
                c.assertTrue(world.getTime()-capturedAt>=8&&body.physicsGravity()&&peakBodies==1,"normal world and entity ages prove actual gravitational movement");
            }
            done=true;System.out.println("BUILDING_BLAST_PASS "+state());return true;
        }
        String state() {
            return "mode="+mode+" base="+base+" launched="+launchedAt+" now="+world.getTime()+" hits="+actualHits
                +" supportCuts="+actualCuts+" roofWorld="+roof.stream().filter(p->world.getBlockState(p).isOf(Blocks.OBSIDIAN)).count()
                +" ordnanceAges="+ordnance.stream().map(e->e.age+":"+e.isRemoved()).toList()+" peakBodies="+peakBodies
                +" body="+(body==null?null:body.getUuid())+" bodyAge="+(body==null?-1:body.age)+" captureAge="+bodyCaptureAge
                +" bodyCentre="+(body==null?null:body.physicsCentre())+" captured="+bodyCapturePosition
                +" bodyVelocity="+(body==null?null:body.physics().linearVelocity());
        }
        @Override public void close() {
            if(closed)return;closed=true;
            for(var entity:ordnance)entity.discard();
            for(var candidate:candidates())candidate.discard();
            for(var entity:world.getEntitiesByClass(ItemEntity.class,new Box(base).expand(24),e->true))entity.discard();
            for(var e:originals.entrySet())world.setBlockState(e.getKey(),e.getValue(),2);
            chunks.close();
        }
    }
}
