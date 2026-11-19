package com.terminaldetector.drmd.world.threat;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.datafixer.Schemas;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static com.terminaldetector.drmd.world.threat.ThreatProgress.*;

/** Real server events, real Survival connection, natural target acquisition and disk persistence.
 * No setTarget, manual entity ticks, production clock jumps or global forced-chunk flags. */
public final class SurvivalThreatGameTests implements FabricGameTest {
    private static Point point(Vec3d p){return new Point(p.x,p.y,p.z);}
    private static ThreatProgress ready(Vec3d at){return new ThreatProgress(new Snapshot(Phase.QUIET,GRACE_TICKS,0,0,0,-1,-1,false,0,point(at),point(at)));}
    private static ThreatProgress warning(Vec3d at,long now,long until){return new ThreatProgress(new Snapshot(Phase.WARNING,GRACE_TICKS,now,until,0,now,-1,false,0,point(at),point(at)));}
    private static ThreatProgress searching(Vec3d at,long now){return new ThreatProgress(new Snapshot(Phase.SEARCHING,GRACE_TICKS,now,now+SEARCH_TICKS,now+SEARCH_TICKS+HUNT_TICKS,now,-1,false,0,point(at),point(at)));}

    /** Vanilla TestServer disables mob spawning by default. Temporarily exercise the
     * real Survival rule and restore it after the last test-owned fixture leaves. */
    private static final class Rules implements AutoCloseable {
        private static final Map<ServerWorld,Rules> ACTIVE=new IdentityHashMap<>();
        final ServerWorld world;final boolean original;int users;
        private Rules(ServerWorld w){world=w;original=w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);}
        static Rules acquire(ServerWorld w){var r=ACTIVE.computeIfAbsent(w,Rules::new);r.users++;w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true,w.getServer());return r;}
        public void close(){if(--users==0){ACTIVE.remove(world);world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(original,world.getServer());}}
    }

    /** Exactly Vanilla's TestContext connection recipe, using a normal player instead of
     * its permanently-creative anonymous subclass. Existing enemy predicates remain unmodified. */
    private static final class Player implements AutoCloseable {
        final ServerPlayerEntity entity;final EmbeddedChannel channel;boolean closed;
        Player(TestContext c,Vec3d at){
            var w=c.getWorld();var client=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),"threat-fixture"),false);
            entity=new ServerPlayerEntity(w.getServer(),w,client.gameProfile(),client.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);
            w.getServer().getPlayerManager().onPlayerConnect(connection,entity,client);
            try{
                entity.changeGameMode(GameMode.SURVIVAL);entity.getAbilities().flying=false;entity.setNoGravity(true);
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,2_000,4,false,false));
                DescentPlayerData.get(entity).setEnabled(false);entity.setPosition(at);entity.setVelocity(Vec3d.ZERO);
                c.assertTrue(!entity.isCreative()&&!entity.isSpectator()&&entity.interactionManager.getGameMode()==GameMode.SURVIVAL,"ordinary server player really is Survival for the existing natural drone AI");
                c.assertTrue(w.getDifficulty()!=Difficulty.PEACEFUL&&w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING),"fixture requires normal enabled Survival encounters");
            }catch(Throwable failure){close();throw failure;}
        }
        public void close(){if(closed)return;closed=true;entity.getServer().getPlayerManager().remove(entity);entity.discard();channel.finishAndReleaseAll();}
    }
    private static final class Lease implements AutoCloseable {
        private static final ChunkTicketType<UUID> TYPE=ChunkTicketType.create("drmd_threat_fixture",Comparator.<UUID>naturalOrder());
        final ServerWorld world;final UUID id=UUID.randomUUID();final Set<ChunkPos> columns=new LinkedHashSet<>();
        Lease(ServerWorld world,BlockPos centre){
            this.world=world;world.resetIdleTimeout();
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){var p=new ChunkPos((centre.getX()>>4)+x,(centre.getZ()>>4)+z);columns.add(p);world.getChunkManager().addTicket(TYPE,p,2,id);}
            // Entity-ticking tickets need a FULL neighbor region. This is test setup only.
            for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)world.getChunk((centre.getX()>>4)+x,(centre.getZ()>>4)+z);
        }
        boolean ready(){for(var p:columns)if(!world.shouldTickEntity(p.getStartPos()))return false;return true;}
        public void close(){for(var p:columns)world.getChunkManager().removeTicket(TYPE,p,2,id);}
    }
    private static final class Fixture implements AutoCloseable {
        final TestContext c;final ServerWorld w;final Vec3d at;final Player player;final Lease lease;final Rules rules;
        final List<Entity> owned=new ArrayList<>();final Map<BlockPos,BlockState> blocks=new HashMap<>();boolean closed;
        Fixture(TestContext c,Vec3d at,Lease lease){this.c=c;w=c.getWorld();this.at=at;this.lease=lease;rules=Rules.acquire(w);try{player=new Player(c,at);}catch(Throwable failure){rules.close();if(lease!=null)lease.close();throw failure;}}
        SurvivalThreatState state(){return SurvivalThreatState.get(w);}
        List<DroneEntity> patrol(){var out=new ArrayList<DroneEntity>();for(var actor:state().actors())if(actor.getValue().owner().equals(player.entity.getUuid())){var entity=w.getEntity(actor.getKey());if(entity instanceof DroneEntity drone)out.add(drone);}return out;}
        DroneEntity victim(){var d=ModEntities.ORB.create(w);d.setPosition(at.add(3,0,0));d.setAiDisabled(true);c.assertTrue(w.spawnEntity(d),"real canonical victim spawned");owned.add(d);return d;}
        void kill(DroneEntity d,net.minecraft.entity.damage.DamageSource source){
            var box=d.getBoundingBox().expand(5);var before=new HashSet<UUID>();for(var e:w.getOtherEntities(null,box))before.add(e.getUuid());
            c.assertTrue(d.damage(source,1_000_000)&&!d.isAlive(),"real lethal damage reaches the registered AFTER_DEATH callback");
            for(var e:w.getOtherEntities(null,box))if(!before.contains(e.getUuid()))owned.add(e);
        }
        void seal(Vec3d centre){var root=BlockPos.ofFloored(centre);for(int x=-2;x<=2;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)if(Math.abs(x)==2||Math.abs(z)==2||y==-1||y==3){var p=root.add(x,y,z);blocks.put(p,w.getBlockState(p));w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),3);}}
        public void close(){
            if(closed)return;closed=true;
            for(var actor:state().actors())if(actor.getValue().owner().equals(player.entity.getUuid())){var e=w.getEntity(actor.getKey());if(e!=null)e.discard();state().removeActor(actor.getKey());}
            for(var e:owned)e.discard();for(var e:blocks.entrySet())w.setBlockState(e.getKey(),e.getValue(),3);
            try{player.close();}finally{rules.close();if(lease!=null)lease.close();}
        }
    }
    private void admitted(TestContext c,Consumer<Fixture> start){
        var pos=c.getAbsolutePos(new BlockPos(8,220,8));var lease=new Lease(c.getWorld(),pos);int[] wait={0};boolean[] started={false};
        // One repeating task registered before iteration; never add a repeating task from it.
        c.runAtEveryTick(()->{if(started[0])return;if(!lease.ready()){if(++wait[0]<80)return;started[0]=true;lease.close();c.assertTrue(false,"threat fixture requires actual entity-ticking columns");return;}started[0]=true;var f=new Fixture(c,Vec3d.ofCenter(pos),lease);try{start.accept(f);}catch(Throwable failure){f.close();throw failure;}});
    }
    private static void budgets(TestContext c){var s=SurvivalThreatDirector.stats(c.getWorld());c.assertTrue(s.players()<=SurvivalThreatDirector.PLAYER_CHECKS&&s.candidates()<=SurvivalThreatDirector.CANDIDATE_CHECKS&&s.sight()<=SurvivalThreatDirector.SIGHT_CHECKS&&s.spawned()<=SurvivalThreatDirector.SPAWNS&&s.roster()<=2*SurvivalThreatState.MAX_ACTORS,"one world pass obeys bounded player/roster/candidate/sight/spawn work: "+s);}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="threat-confirmed-signal",tickLimit=100)
    public void onlyAnActualSurvivalMachineKillCreatesAnAttributedDeduplicatedAlarm(TestContext c){
        var f=new Fixture(c,Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(8,220,8))),null);
        try{
            var state=f.state();var p=f.player.entity;var live=f.victim();
            c.assertTrue(!SurvivalThreatDirector.confirmedKill(f.w,p,live)&&state.progress(p.getUuid()).isEmpty(),"live damage or an arbitrary nearest player cannot fabricate a kill");
            p.changeGameMode(GameMode.CREATIVE);f.kill(live,p.getDamageSources().playerAttack(p));c.assertTrue(state.progress(p.getUuid()).isEmpty(),"real creative kill is ignored");
            p.changeGameMode(GameMode.SURVIVAL);var environmental=f.victim();f.kill(environmental,f.w.getDamageSources().generic());c.assertTrue(state.progress(p.getUuid()).isEmpty(),"environmental death is not attributed to a nearby player");
            f.w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,f.w.getServer());var disabled=f.victim();f.kill(disabled,p.getDamageSources().playerAttack(p));c.assertTrue(state.progress(p.getUuid()).isEmpty(),"the actual disabled Survival spawn rule prevents threat creation");f.w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true,f.w.getServer());
            var actual=f.victim();var at=point(actual.getPos());f.kill(actual,p.getDamageSources().playerAttack(p));
            var alarm=state.progress(p.getUuid()).orElseThrow();c.assertTrue(alarm.pending()&&alarm.phase()==Phase.QUIET&&alarm.origin().equals(at),"registered real death records the actual alarm location while preserving novice grace");
            c.assertTrue(!SurvivalThreatDirector.confirmedKill(f.w,p,actual)&&state.progress(p.getUuid()).orElseThrow().equals(alarm),"repeated callback cannot reset the persisted alarm");
            var alias=ModEntities.ORB.create(f.w);alias.setUuid(actual.getUuid());alias.setHealth(0);c.assertTrue(!SurvivalThreatDirector.confirmedKill(f.w,p,alias),"a dead UUID alias is not the world's actual confirmed victim");budgets(c);
            var orphan=ModEntities.ORB.create(f.w);orphan.setPosition(f.at.add(7,0,0));orphan.addCommandTag("drmd.threat");orphan.addCommandTag("drmd.threat.faction="+SurvivalThreatState.FACTION);orphan.addCommandTag("drmd.threat.owner="+p.getUuid());orphan.addCommandTag("drmd.threat.until="+(f.w.getTime()+1_000));f.owned.add(orphan);f.w.spawnEntity(orphan);
            c.assertTrue(orphan.isRemoved()&&state.rosterSize(p.getUuid())==0,"unloaded actors absent from the persisted roster are retired on actual entity admission, never revived outside its cap");
        }finally{f.close();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="threat-persisted-ledger",tickLimit=100)
    public void actualPersistentStateFilesRestoreCooldownRosterAndVictimDeduplication(TestContext c) throws Exception {
        var state=new SurvivalThreatState();var owner=UUID.randomUUID();var second=UUID.randomUUID();var victim=UUID.randomUUID();var at=new Vec3d(20,220,20);long now=c.getWorld().getTime();
        state.restorePlayer(owner,searching(at,now));var a=UUID.randomUUID();var b=UUID.randomUUID();c.assertTrue(state.addActor(owner,a,now+4_800)&&state.addActor(owner,b,now+4_800),"two initial roster slots");state.cancel(owner,now);
        c.assertTrue(state.alarm(second,victim,point(at),now),"separate confirmed alarm before persistence");var expected=state.progress(owner).orElseThrow();
        var directory=Files.createTempDirectory("drmd-threat-save-");
        try{
            var lookup=c.getWorld().getRegistryManager();var manager=new PersistentStateManager(directory.toFile(),Schemas.getFixer(),lookup);manager.set(SurvivalThreatState.ID,state);manager.save();
            c.assertTrue(Files.size(directory.resolve(SurvivalThreatState.ID+".dat"))>0,"real Minecraft PersistentState writer produced a compressed save");
            var reopened=new PersistentStateManager(directory.toFile(),Schemas.getFixer(),lookup);var restored=reopened.getOrCreate(new PersistentState.Type<>(SurvivalThreatState::new,SurvivalThreatState::fromNbt,null),SurvivalThreatState.ID);
            c.assertTrue(restored!=state&&restored.progress(owner).orElseThrow().equals(expected),"new manager reads exactly the remaining cooldown and lifetime allowance from disk");
            c.assertTrue(restored.rosterSize(owner)==2&&restored.actor(a).expires()==now+200&&restored.actor(b).expires()==now+200,"unloaded roster identities and shortened retirement deadline survive");
            c.assertTrue(!restored.alarm(second,victim,point(at),now+1)&&!restored.canDeploy(owner),"reload cannot duplicate a confirmed signal or mint a new cooldown wave");
            var malformed=state.writeNbt(new NbtCompound(),lookup);malformed.getCompound("players").put("bad-uuid",new NbtCompound());
            c.assertTrue(SurvivalThreatState.fromNbt(malformed,lookup).progress(owner).orElseThrow().equals(expected),"one corrupt participant cannot erase a valid saved encounter");
        }finally{Files.deleteIfExists(directory.resolve(SurvivalThreatState.ID+".dat"));Files.deleteIfExists(directory);}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="threat-warning",tickLimit=160)
    public void noDroneAppearsBeforeThePersistedWarningDeadline(TestContext c){admitted(c,f->{
        long until=f.w.getTime()+30;f.state().restorePlayer(f.player.entity.getUuid(),warning(f.at,f.w.getTime(),until));
        c.waitAndRun(20,()->{try{c.assertTrue(f.state().rosterSize(f.player.entity.getUuid())==0&&f.state().progress(f.player.entity.getUuid()).orElseThrow().phase()==Phase.WARNING,"actual world ticks before warning deadline cannot create an invisible early wave");budgets(c);}finally{f.close();}c.complete();});
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="threat-natural-search",tickLimit=440)
    public void realReconAcquiresNaturallyLosesSightAndStopsAfterItsPairIsDefeated(TestContext c){
        Fixture[] live={null};boolean[] covered={false};long[] seenAt={0};Point[] known={null};
        c.runAtEveryTick(()->{var f=live[0];if(f==null||covered[0])return;budgets(c);var snapshot=f.state().progress(f.player.entity.getUuid()).orElseThrow();
            if(snapshot.phase()!=Phase.HUNTING||f.patrol().size()!=2)return;covered[0]=true;
            try{
                c.assertTrue(f.patrol().stream().anyMatch(d->d.getTarget()==f.player.entity),"existing selectors, not scripted target injection, acquired the ordinary Survival player");
                c.assertTrue(f.patrol().stream().allMatch(d->d instanceof FlightEnemyEntity typed&&typed.kind()==FlightEnemyKind.ORB),"recon uses the actual ORB identity rather than a role alias or village artillery");
                known[0]=snapshot.known();seenAt[0]=f.w.getTime();var hiding=f.player.entity.getPos().add(6,0,0);f.seal(hiding);f.player.entity.setPosition(hiding);f.player.entity.setVelocity(Vec3d.ZERO);
            }catch(Throwable failure){f.close();live[0]=null;throw failure;}
            c.waitAndRun(LOST_SIGHT_TICKS+15,()->{try{
                var hidden=f.state().progress(f.player.entity.getUuid()).orElseThrow();c.assertTrue(hidden.phase()==Phase.SEARCHING&&hidden.known().equals(known[0])&&hidden.lastSeen()<=seenAt[0],"solid world cover causes search at the last real sighting, never omniscient hidden tracking: "+hidden);
                c.assertTrue(hidden.spawned()==2&&f.state().rosterSize(f.player.entity.getUuid())==2,"no replacement swarm is silently added during the encounter");
                for(var d:f.patrol())f.kill(d,f.player.entity.getDamageSources().playerAttack(f.player.entity));
                c.waitAndRun(10,()->{try{var done=f.state().progress(f.player.entity.getUuid()).orElseThrow();c.assertTrue(done.phase()==Phase.COOLDOWN&&f.state().rosterSize(f.player.entity.getUuid())==0&&!done.pending(),"destroying the real recon pair starts cooldown without recursively provoking another pair");budgets(c);}finally{f.close();live[0]=null;}c.complete();});
            }catch(Throwable failure){f.close();live[0]=null;throw failure;}});
        });
        admitted(c,f->{live[0]=f;f.state().restorePlayer(f.player.entity.getUuid(),warning(f.at,f.w.getTime(),f.w.getTime()+5));
            c.waitAndRun(120,()->{if(!covered[0]){try{c.assertTrue(false,"bounded loaded recon must naturally find the real Survival target: "+f.state().progress(f.player.entity.getUuid())+" actors="+f.patrol().size()+" stats="+SurvivalThreatDirector.stats(f.w));}finally{f.close();live[0]=null;}}});
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="threat-unavailable",tickLimit=100)
    public void unavailableAlarmVolumesCannotCreateActorsOrGenerateChunks(TestContext c){
        var f=new Fixture(c,Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(8,220,8))),null);var p=f.player.entity;
        // The owner is actually nearby, but every prospective recon origin is beyond world height.
        var edge=new Vec3d(p.getX(),f.w.getTopY()+80,p.getZ());p.setPosition(edge);f.state().restorePlayer(p.getUuid(),searching(edge,f.w.getTime()));
        c.waitAndRun(20,()->{try{
            c.assertTrue(f.state().rosterSize(p.getUuid())==0,"unknown/out-of-height candidates fail closed");var stats=SurvivalThreatDirector.stats(f.w);c.assertTrue(stats.candidates()>0&&stats.candidates()<=8&&stats.spawned()==0,"bounded real director ran and rejected candidates: "+stats);
            // Compare one synchronous director call; normal player streaming may legitimately
            // load chunks between world ticks and must not be mistaken for director generation.
            int loadedBefore=f.w.getChunkManager().getLoadedChunkCount();SurvivalThreatDirector.tick(f.w);
            c.assertTrue(f.w.getChunkManager().getLoadedChunkCount()==loadedBefore,"rejected recon candidates cannot request extra chunks");budgets(c);
        }finally{f.close();}c.complete();});
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="threat-siege-real-cover",tickLimit=900)
    public void aNaturallyObservedExistingPairEntersFiniteSiegeAndRealCoverReturnsItToSearch(TestContext c){pressure(c,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="threat-siege-real-escape",tickLimit=1_000)
    public void escapingAnActualSiegeStopsNewFireAndTheSamePairPhysicallyRetreatsBeforeRetirement(TestContext c){pressure(c,true);}
    private void pressure(TestContext c,boolean escape){
        Fixture[] live={null};boolean[] done={false},pressing={false},retiring={false},movedAway={false};
        long[] huntAt={-1},decisionAt={-1},retreatAt={-1};Point[] known={null};int[] ticks={0};
        var actors=new LinkedHashSet<UUID>();var alreadyFlying=new HashSet<UUID>();var beforeRetreat=new HashMap<UUID,Vec3d>();
        // This sole assertion driver is registered before admission; never register work from its callbacks.
        c.runAtEveryTick(()->{
            var f=live[0];if(done[0]||f==null)return;
            try{
                f.w.resetIdleTimeout();ticks[0]++;budgets(c);long now=f.w.getTime();
                var progress=f.state().progress(f.player.entity.getUuid()).orElseThrow();var patrol=f.patrol();
                if(!pressing[0]){
                    c.assertTrue(ticks[0]<620,"normal own-LOS pair enters pressure after its full hunt dwell: "+progress);
                    if(progress.phase()==Phase.HUNTING&&huntAt[0]<0)huntAt[0]=progress.since();
                    if(progress.phase()!=Phase.SIEGE)return;
                    c.assertTrue(huntAt[0]>=0&&progress.since()-huntAt[0]>=WARNING_TICKS&&progress.lastSeen()>=now-SurvivalThreatDirector.INTERVAL,
                        "actual fresh own observation after400ordinary hunt ticks, no timer/target injection: "+progress);
                    c.assertTrue(patrol.size()==2&&progress.spawned()==2&&patrol.stream().allMatch(d->d instanceof OrbEntity&&d.getRole()==com.terminaldetector.drmd.ai.AiRole.ASSAULT
                        &&d.getCommandTags().contains("drmd.threat.faction="+SurvivalThreatState.FACTION)),"same two original attributed ORBs, no replacement rocket role or larger wave");
                    c.assertTrue(patrol.stream().anyMatch(d->d.getTarget()==f.player.entity&&d.canSee(f.player.entity)),"normal actor target selectors actually saw the eligible Survival player");
                    var copy=SurvivalThreatState.fromNbt(f.state().writeNbt(new NbtCompound(),f.w.getRegistryManager()),f.w.getRegistryManager());
                    c.assertTrue(copy.progress(f.player.entity.getUuid()).orElseThrow().equals(progress)&&copy.rosterSize(f.player.entity.getUuid())==2,
                        "actual runtime SIEGE and finite roster survive the normal saved-state codec");
                    c.assertTrue(patrol.stream().allMatch(d->!d.isPersistent()&&SurvivalThreatDirector.leasesDespawn(d)&&d.cannotDespawn()),"real dynamic finite lease must survive Vanilla128m distance despawn without a saved persistent bit");
                    for(var d:patrol)actors.add(d.getUuid());known[0]=progress.known();decisionAt[0]=now;pressing[0]=true;
                    System.out.println("THREAT_PRESSURE_DECISION escape="+escape+" player="+f.player.entity.getPos()+" world="+now+" actors="+pressureActors(f,patrol));
                    if(escape){
                        // One deliberate player escape, in the same ticketed columns and inside real world height.
                        var at=f.player.entity.getPos();double dy=at.y-f.w.getBottomY()>162?-161:161;
                        var destination=at.add(0,dy,0);
                        c.assertTrue(destination.y>=f.w.getBottomY()&&destination.y+f.player.entity.getHeight()<f.w.getTopY(),"real escape stays in world bounds");
                        f.player.entity.setPosition(destination);f.player.entity.setVelocity(Vec3d.ZERO);
                    }else{
                        // After400ticks of real combat an ORB may already be inside the old player
                        // room. Choose one bounded real shelter excluding the actual attacking hulls.
                        Vec3d hiding=null;
                        for(var shift:new Vec3d[]{new Vec3d(8,0,0),new Vec3d(-8,0,0),new Vec3d(0,0,8),new Vec3d(0,0,-8),new Vec3d(0,8,0),new Vec3d(0,-8,0)}){
                            var candidate=f.player.entity.getPos().add(shift);var centre=BlockPos.ofFloored(candidate);
                            var room=new Box(Vec3d.of(centre.add(-2,-1,-2)),Vec3d.of(centre.add(3,4,3))).expand(1);
                            if(room.minY<f.w.getBottomY()||room.maxY>f.w.getTopY()||patrol.stream().anyMatch(d->room.intersects(d.getBoundingBox())))continue;
                            if(!com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedSegment(f.w,f.player.entity.getEyePos(),candidate.add(0,f.player.entity.getStandingEyeHeight(),0)))continue;
                            hiding=candidate;break;
                        }
                        c.assertTrue(hiding!=null,"real cover admission excludes every current pressure ORB within six fixed loaded candidates");
                        f.seal(hiding);f.player.entity.setPosition(hiding);f.player.entity.setVelocity(Vec3d.ZERO);
                        c.assertTrue(patrol.stream().allMatch(d->f.w.raycast(new RaycastContext(d.getEyePos(),f.player.entity.getEyePos(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,d)).getType()==net.minecraft.util.hit.HitResult.Type.BLOCK),
                            "fresh real material blocks all outside attackers after the single legitimate shelter entry");
                        System.out.println("THREAT_PRESSURE_COVER player="+hiding+" world="+now+" actors="+pressureActors(f,patrol));
                    }
                    return;
                }
                c.assertTrue(progress.spawned()==2,"pressure cannot reset the lifetime pair allowance");
                if(!escape){
                    if(now-decisionAt[0]<LOST_SIGHT_TICKS+SurvivalThreatDirector.INTERVAL+5)return;
                    c.assertTrue(progress.phase()==Phase.SEARCHING&&progress.known().equals(known[0])&&progress.lastSeen()<=decisionAt[0],
                        "real opaque cover breaks siege and preserves only the last genuinely seen position: "+progress);
                    c.assertTrue(patrol.size()==2&&patrol.stream().allMatch(d->actors.contains(d.getUuid())),"same living pair, no refill during hidden search");
                    done[0]=true;f.close();c.complete();return;
                }
                if(!retiring[0]){
                    c.assertTrue(now-decisionAt[0]<=SurvivalThreatDirector.INTERVAL+5,"escaping the alarm area promptly ends pressure");
                    if(progress.phase()!=Phase.COOLDOWN)return;
                    retiring[0]=true;retreatAt[0]=now;
                    System.out.println("THREAT_PRESSURE_RETREAT player="+f.player.entity.getPos()+" world="+now+" actors="+pressureActors(f,patrol));
                    for(var d:patrol){
                        beforeRetreat.put(d.getUuid(),d.getPos());var actor=f.state().actor(d.getUuid());
                        c.assertTrue(actor!=null&&actor.expires()<=now+LOST_SIGHT_TICKS&&SurvivalThreatDirector.retreatFrom(d)!=null,
                            "saved finite retirement and real owned AI retreat authority");
                        c.assertTrue(!d.isPersistent()&&SurvivalThreatDirector.leasesDespawn(d)&&d.cannotDespawn(),"bounded cooldown retreat keeps its actual dynamic lease without mutating saved persistence");
                        float energy=d.getModEnergy();c.assertTrue(!d.tryFireAt(f.player.entity)&&d.getModEnergy()==energy,"retiring patrol refuses a fresh actual weapon action without energy debit");
                    }
                    for(var entity:f.w.iterateEntities())if(entity instanceof ProjectileEntity shot&&actors.contains(shot.getOwnerUuid()))alreadyFlying.add(shot.getUuid());
                }
                for(var entity:f.w.iterateEntities())if(entity instanceof ProjectileEntity shot&&actors.contains(shot.getOwnerUuid()))
                    c.assertTrue(alreadyFlying.contains(shot.getUuid()),"existing rounds keep normal flight; no new patrol projectile is emitted after cooldown");
                var remembered=new Vec3d(known[0].x(),known[0].y(),known[0].z());
                for(var d:patrol){var before=beforeRetreat.get(d.getUuid());
                    if(before!=null&&d.getPos().squaredDistanceTo(remembered)>before.squaredDistanceTo(remembered)+.05&&d.getVelocity().lengthSquared()>1e-8)movedAway[0]=true;
                }
                if(now-retreatAt[0]>=25){
                    if(!movedAway[0])System.out.println("THREAT_PRESSURE_NO_MOVEMENT before="+beforeRetreat+" known="+known[0]+" world="+now+" actors="+pressureActors(f,patrol));
                    c.assertTrue(movedAway[0],"normal flight AI physically retreats from last observation, no follow teleport or velocity injection");
                }
                if(now-retreatAt[0]<LOST_SIGHT_TICKS+SurvivalThreatDirector.INTERVAL+10)return;
                c.assertTrue(progress.phase()==Phase.COOLDOWN&&f.state().rosterSize(f.player.entity.getUuid())==0
                    &&actors.stream().allMatch(id->f.w.getEntity(id)==null||f.w.getEntity(id).isRemoved()),"the pair retires despite earlier visible hunting; no self-extending patrol or recursive wave");
                done[0]=true;f.close();c.complete();
            }catch(Throwable failure){done[0]=true;f.close();throw failure;}
        });
        admitted(c,f->{live[0]=f;f.state().restorePlayer(f.player.entity.getUuid(),searching(f.at,f.w.getTime()));});
    }
    private static Object pressureActors(Fixture f,List<DroneEntity> patrol){
        return patrol.stream().limit(2).map(d->d.getUuid()+" age="+d.age+" pos="+d.getPos()+" v="+d.getVelocity()+" persistent="+d.isPersistent()+" lease="+SurvivalThreatDirector.leasesDespawn(d)
            +" alive="+d.isAlive()+" removed="+d.isRemoved()+" aiOff="+d.isAiDisabled()+" ticking="+f.w.shouldTickEntity(d.getBlockPos())
            +" retreat="+SurvivalThreatDirector.retreatFrom(d)).toList();
    }
}
