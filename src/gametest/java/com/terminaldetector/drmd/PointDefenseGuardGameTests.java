package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.weapon.projectile.*;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.trap.PointDefenseInterception;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Component API guards complement the unchanged natural scheduled-block baseline.
 * Normal entity ticking always supplies flight/contact; no manual tick/list/ID/pose manipulation. */
public final class PointDefenseGuardGameTests implements FabricGameTest {
    enum Mode {MISSILE_FIRST,ROUND_FIRST,BLOCK,HULL,FRIENDLY,UNKNOWN_OWNER,UNKNOWN_ANCHOR,LEGACY,ROUND_CAP,CANDIDATES}
    private static final class Rig implements AutoCloseable {
        final TestContext c;final ServerWorld world;final Mode mode;final BlockPos base;
        final AiFixtureChunks sourceLease;AiFixtureChunks missileLease;
        final LivingEntity owner;final Map<BlockPos,BlockState> old=new LinkedHashMap<>();
        final List<ProjectileEntity> extra=new ArrayList<>();
        ProjectileEntity missile,round;BlockBodyEntity hull;
        WeaponCore.HitContext missileHit,roundHit;int payloads,roundPayloads,admission,ticks;boolean started,closed;
        Rig(TestContext c,Mode mode,int offset){
            this.c=c;this.mode=mode;world=c.getWorld();
            var absolute=c.getAbsolutePos(new BlockPos(91000+offset,200,8000));
            base=new BlockPos((absolute.getX()&~15)+15,absolute.getY(),(absolute.getZ()&~15)+8);
            sourceLease=new AiFixtureChunks(world,base,mode==Mode.ROUND_FIRST?0:1);
            owner=mode==Mode.FRIENDLY?ModEntities.REACTOR_KEEPER.create(world):EntityType.COW.create(world);
            c.assertTrue(owner!=null,"actual loaded living shooter class exists");
            owner.setPosition(Vec3d.ofBottomCenter(base.add(-24,0,0)));owner.setNoGravity(true);owner.setInvulnerable(true);
            if(owner instanceof net.minecraft.entity.mob.MobEntity mob){mob.setAiDisabled(true);mob.setPersistent();}
            for(var p:BlockPos.iterate(base.add(-1,0,-1),base.add(13,3,1)))set(p,Blocks.AIR.getDefaultState());
        }
        void set(BlockPos p,BlockState state){var key=p.toImmutable();old.putIfAbsent(key,world.getBlockState(key));world.setBlockState(key,state,2);}
        boolean ready(){
            if(started)return true;
            if(!sourceLease.entityTicking(base)){
                if(++admission>=80)c.assertTrue(false,"source obtains real entity-ticking status: "+sourceLease.readiness(base));return false;
            }
            set(base,ModWorldBlocks.POINT_DEFENSE_TURRET.getDefaultState());
            if(mode!=Mode.UNKNOWN_OWNER)c.assertTrue(world.spawnEntity(owner)&&world.getEntity(owner.getUuid())==owner,"real shooter has loaded tracked identity");
            if(mode==Mode.UNKNOWN_ANCHOR||mode==Mode.LEGACY){synchronous();return false;}
            if(mode==Mode.CANDIDATES){
                for(int i=0;i<120;i++){
                    var candidate=ModEntities.PROJECTILE.create(world);candidate.setOwner(owner);candidate.setPosition(Vec3d.ofCenter(base).add(5,1,0));
                    candidate.setVelocity(Vec3d.ZERO);candidate.setLifeTicks(100);candidate.setMeshKind(ProjectileEntity.MESH_BOLT);
                    c.assertTrue(world.spawnEntity(candidate),"real bounded-query candidate spawns");extra.add(candidate);
                }
                started=true;return false;
            }
            var launch=Vec3d.ofCenter(base).add(11,0,0);
            if(mode==Mode.ROUND_FIRST)c.assertTrue(!world.shouldTickEntity(BlockPos.ofFloored(launch)),"adjacent column is genuinely loaded/tracked before ENTITY_TICKING promotion");
            if(mode==Mode.BLOCK)for(int y=0;y<=3;y++)for(int z=-1;z<=1;z++)set(base.add(3,y,z),Blocks.BEDROCK.getDefaultState());
            if(mode==Mode.HULL){
                var q=ImmPtlQuaternions.rotationByRadians(new Vec3(0,1,1).normalized(),.6);var shift=q.rotate(new Vec3(.5,.5,.5));
                var centre=Vec3d.ofCenter(base).add(3,.3,0);
                hull=BlockBodyEntity.createLocal(world,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),centre.add(-shift.x(),-shift.y(),-shift.z()),q,Vec3d.ZERO,Vec3d.ZERO);
                hull.setPhysicsGravity(false);
                c.assertTrue(hull.raycast(PointDefenseInterception.muzzle(base),launch).isPresent(),"actual rotated material obstructs the emitted shot, rather than only its broadphase box");
            }
            var incoming=ProjectileFramework.config(ProjectileKind.ROCKET,owner,launch,new Vec3d(-1,0,0));
            // This component's dumbfire variant uses the real existing rocket flight/fuse.
            // The original two natural baseline cases retain the complete stock config.
            incoming.homing=false;var callback=incoming.onHit;
            incoming.onHit=ctx->{payloads++;missileHit=ctx;if(callback!=null)callback.accept(ctx);};
            missile=WeaponCore.fireProjectile(incoming);
            c.assertTrue(missile!=null&&world.getEntity(missile.getUuid())==missile,"actual factory missile has a loaded tracked identity before anchored admission");
            var offset=missile.getBoundingBox().getCenter().subtract(PointDefenseInterception.muzzle(base));
            var solution=InterceptAim.solve(v(offset),v(missile.getVelocity()),DescentMod.su(5000)/20,9).orElseThrow();
            var outgoing=roundConfig(mc(solution.direction()));
            round=WeaponCore.fireAnchoredProjectile(world,base,outgoing,missile.getUuid());
            if(mode==Mode.FRIENDLY||mode==Mode.UNKNOWN_OWNER)c.assertTrue(round==null,"low-level anchored primitive itself refuses exempt/unresolved actual owner, before emission");
            else c.assertTrue(round!=null,"validated actual anchor emits one finite physical kinetic round");
            if(mode==Mode.ROUND_CAP){
                for(int i=1;i<256;i++){
                    var candidate=WeaponCore.fireAnchoredProjectile(world,base,roundConfig(mc(solution.direction())),missile.getUuid());
                    c.assertTrue(candidate!=null,"legitimate component round admission remains available below its finite world cap: "+i);extra.add(candidate);
                }
                c.assertTrue(WeaponCore.fireAnchoredProjectile(world,base,roundConfig(mc(solution.direction())),missile.getUuid())==null,
                    "257th round is refused without an unbounded registry/backlog");
                c.assertTrue(PointDefenseInterception.stats(world).activeRounds()==256,"exact real active-round registry capacity is observed");
                System.out.println("POINT_DEFENSE_ROUND_CAP_PASS "+PointDefenseInterception.stats(world));close();c.complete();return false;
            }
            if(mode==Mode.ROUND_FIRST){
                c.assertTrue(missile.age==0&&round.age==0,"both real identities remain unadvanced before the actual interest transition");
                // Existing EntityList is insertion ordered. This genuine engine ticket
                // promotes the already tracked missile after the round entered ticking.
                missileLease=new AiFixtureChunks(world,BlockPos.ofFloored(launch),0);
            }
            started=true;return false;
        }
        WeaponCore.FireConfig roundConfig(Vec3d direction){
            var cfg=ProjectileFramework.config(ProjectileKind.KINETIC,null,PointDefenseInterception.muzzle(base),direction);
            cfg.directDamage=4;cfg.life=.5f;cfg.inherit=0;
            cfg.onHit=ctx->{roundPayloads++;roundHit=ctx;};return cfg;
        }
        void synchronous(){
            if(mode==Mode.UNKNOWN_ANCHOR){
                var remote=new BlockPos(1480000,120,2380000);var cm=world.getChunkManager();
                c.assertTrue(cm.getWorldChunk(remote.getX()>>4,remote.getZ()>>4)==null,"remote source column starts unavailable");
                var cfg=roundConfig(new Vec3d(1,0,0));cfg.pos=PointDefenseInterception.muzzle(remote);
                c.assertTrue(WeaponCore.fireAnchoredProjectile(world,remote,cfg,UUID.randomUUID())==null,"unavailable actual source/target refuses low-level admission");
                c.assertTrue(cm.getWorldChunk(remote.getX()>>4,remote.getZ()>>4)==null,"anchor validation never synchronously materializes a remote column");
            }else{
                var cfg=roundConfig(new Vec3d(1,0,0));
                c.assertTrue(WeaponCore.fireProjectile(cfg)==null,"unchanged owner-based weapon API still refuses null owners");
                var shot=ModEntities.PROJECTILE.create(world);shot.setOwner(owner);shot.setLifeTicks(80);shot.setDamageClass(DamageClass.KINETIC);
                var saved=new NbtCompound();shot.writeNbt(saved);
                c.assertTrue(!saved.contains("pdAnchor")&&!saved.containsUuid("pdTarget"),"ordinary legacy shot gains no defensive NBT/provenance");
                var restored=ModEntities.PROJECTILE.create(world);restored.readNbt(saved);
                c.assertTrue(restored.pointDefenseSource()==null&&restored.getOwnerLiving()==owner&&restored.remainingLifeTicks()==80,"old owner/lifetime/NBT remain ordinary, with no interception association");
                shot.discard();restored.discard();
            }
            System.out.println("POINT_DEFENSE_API_GUARD_PASS mode="+mode);close();c.complete();
        }
        void sample(){
            ticks++;
            if(mode==Mode.CANDIDATES){
                if(ticks>=8){
                    var stats=PointDefenseInterception.stats(world);
                    c.assertTrue(stats.candidates()==96&&stats.detailed()==32,"actual scheduled query admits96 and examines32 despite120 loaded ordinary candidates: "+stats);
                    c.assertTrue(stats.blockRays()<=32&&stats.hullTests()<=32&&stats.actorTests()<=32&&stats.cleanupExamined()<=32,
                        "shared actual opportunity geometry/cleanup metrics remain bounded: "+stats);
                    System.out.println("POINT_DEFENSE_CANDIDATE_CAP_PASS "+stats);close();c.complete();
                }
                return;
            }
            var proof=PointDefenseInterception.receipts(world).stream().filter(r->r.missile().equals(missile.getUuid())).findFirst();
            if(mode==Mode.MISSILE_FIRST||mode==Mode.ROUND_FIRST){
                if(proof.isPresent()){
                    var p=proof.get();
                    c.assertTrue(p.round().equals(round.getUuid()),"real controlled round, not an unrelated later automatic shot, supplies the contact");
                    c.assertTrue(p.roundMovedFirst()==(mode==Mode.ROUND_FIRST),"actual normal ticker order matches the component fixture: "+p+" "+state());
                    c.assertTrue(p.worldTime()>p.bornAt(),"round cannot retrospectively hit a segment from or before its actual birth tick");
                    c.assertTrue(payloads==1&&missileHit!=null&&missileHit.hitEntity()==round,"one real incoming terminal callback identifies the actual emitted entity");
                    c.assertTrue(roundPayloads==1&&roundHit!=null&&roundHit.hitEntity()==missile,"interceptor also follows its ordinary hit callback once");
                    c.assertTrue(!missile.isAlive()&&!round.isAlive(),"actual physical pair terminates both round identities");
                    c.assertTrue(p.point().distanceTo(Vec3d.ofCenter(base))>3.75+.5,"actual original rocket explodes clear of its protected source, without a blast nerf");
                    System.out.println("POINT_DEFENSE_REAL_ORDER_PASS mode="+mode+" receipt="+p+" "+state());close();c.complete();return;
                }
                if(missileHit!=null)c.assertTrue(false,"real component missile reaches another impact before the expected physical interception: "+state());
            }else if(missileHit!=null){
                c.assertTrue(payloads==1&&proof.isEmpty(),"no interception is invented through cover or exempt ownership: "+state());
                if(mode==Mode.BLOCK)c.assertTrue(missileHit.isBlock()&&roundHit!=null&&roundHit.isBlock(),"both actual trajectories terminate against nearer solid material");
                if(mode==Mode.HULL)c.assertTrue(missileHit.hitEntity()==hull&&roundHit!=null&&roundHit.hitEntity()==hull,"both trajectories hit real rotated material before the alleged pair contact");
                if(mode==Mode.FRIENDLY||mode==Mode.UNKNOWN_OWNER)c.assertTrue(round==null&&missileHit.isBlock(),"exempt or unresolved owner retains ordinary missile flight, not free disarming");
                System.out.println("POINT_DEFENSE_PHYSICAL_GUARD_PASS "+state());close();c.complete();return;
            }
            if(ticks>=40)c.assertTrue(false,"bounded normal component transaction must close without expiry/interest shortcuts: "+state());
        }
        String state(){return "mode="+mode+" ticks="+ticks+" missileAge="+(missile==null?-1:missile.age)+" roundAge="+(round==null?-1:round.age)
            +" targetTicking="+(missile!=null&&world.shouldTickEntity(missile.getBlockPos()))+" payloads="+payloads+" roundPayloads="+roundPayloads
            +" missileHit="+missileHit+" roundHit="+roundHit+" stats="+PointDefenseInterception.stats(world);}
        @Override public void close(){
            if(closed)return;closed=true;
            if(missile!=null)missile.discard();if(round!=null)round.discard();if(hull!=null)hull.discard();owner.discard();
            for(var entity:extra)entity.discard();
            for(var entry:old.entrySet())world.setBlockState(entry.getKey(),entry.getValue(),2);
            if(missileLease!=null)missileLease.close();sourceLease.close();
        }
    }
    private void run(TestContext c,Mode mode,int offset){
        PointDefenseInterception.register();var r=new Rig(c,mode,offset);
        c.runAtEveryTick(()->{if(r.closed)return;try{r.world.resetIdleTimeout();if(r.ready())r.sample();}catch(Throwable failure){System.out.println("POINT_DEFENSE_COMPONENT_FIRST_FAILURE "+r.state());r.close();throw failure;}});
    }
    private static Vec3 v(Vec3d p){return new Vec3(p.x,p.y,p.z);}
    private static Vec3d mc(Vec3 p){return new Vec3d(p.x(),p.y(),p.z());}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-missile-first",tickLimit=160)
    public void existingLoadedMissileTicksBeforeItsActualDefensiveRound(TestContext c){run(c,Mode.MISSILE_FIRST,0);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-round-first",tickLimit=160)
    public void trackedMissileEntersActualTickingAfterItsDefensiveRound(TestContext c){run(c,Mode.ROUND_FIRST,512);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-nearer-solid",tickLimit=160)
    public void nearerSolidMaterialPreventsAFalsePairContact(TestContext c){run(c,Mode.BLOCK,1024);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-nearer-hull",tickLimit=160)
    public void actualRotatedHullPreventsAFalsePairContact(TestContext c){run(c,Mode.HULL,1536);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-friendly-owner",tickLimit=160)
    public void actualStationFriendlyOwnerCannotBeIntercepted(TestContext c){run(c,Mode.FRIENDLY,2048);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-unknown-owner",tickLimit=160)
    public void unresolvableLivingOwnerCannotBeIntercepted(TestContext c){run(c,Mode.UNKNOWN_OWNER,2560);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-unknown-anchor",tickLimit=160)
    public void unknownAnchorCannotLoadColumnsOrEmit(TestContext c){run(c,Mode.UNKNOWN_ANCHOR,3072);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-legacy-api",tickLimit=160)
    public void ordinaryNullOwnerAndLegacyNbtKeepTheirOriginalSemantics(TestContext c){run(c,Mode.LEGACY,3584);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-round-admission-cap",tickLimit=160)
    public void activeRoundAdmissionCannotExceedItsFiniteWorldCap(TestContext c){run(c,Mode.ROUND_CAP,4096);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="pd-candidate-cap",tickLimit=160)
    public void actualScheduledQueriesBoundLoadedCandidateAndDetailedWork(TestContext c){run(c,Mode.CANDIDATES,4608);}
}
