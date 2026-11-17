package com.terminaldetector.drmd.world.threat;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.RocketDroneEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.datafixer.Schemas;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.nio.file.*;
import java.util.*;

/** Saved protocol/intent isolation. Supplied DTO observations and ACK IDs are not
 * live sight/emission proof; no runtime siege/counterplay acceptance is claimed. */
public final class SiegePlanStateGameTests implements FabricGameTest {
    private static final UUID SITE=UUID.fromString("cfb945d7-0899-47df-90ca-7c11aab44d92"),
        OBSERVER=UUID.fromString("679ec242-3781-4a49-b0ed-c64be4c91dd4"),
        FIRST=UUID.fromString("8c793274-df2f-4df9-befd-e3021e33740f"),SECOND=UUID.fromString("b1b6b682-b288-46ab-b1c8-a9c1ef7b935d");
    private static final String WORLD="minecraft:overworld";
    private static final ThreatProgress.Point POINT=new ThreatProgress.Point(18,140,-7);
    private static final SiegePlan.Readiness READY=new SiegePlan.Readiness(true,true,true,true,true);
    private static SiegePlan.Observation sight(UUID participant,UUID observer,long at){return new SiegePlan.Observation(SITE,observer,participant,WORLD,at,POINT,null,null);}
    private static SiegePlan proposed(UUID id,UUID participant,long now){return new SiegePlan(id,participant,WORLD,now,sight(participant,OBSERVER,now));}
    private static SiegePlan ready(UUID id,UUID participant,long now){
        var s=proposed(id,participant,now).snapshot();var plan=new SiegePlan(new SiegePlan.Snapshot(s.id(),s.participant(),s.world(),s.phase(),s.proposedAt(),s.clock(),ThreatProgress.GRACE_TICKS,s.since(),s.until(),s.warningEnds(),s.encounterEnds(),s.cooldownEnds(),s.origin(),s.known(),s.actors(),s.acks()));
        plan.bindFutureIdentity(0,FIRST);plan.bindFutureIdentity(1,SECOND);return plan;
    }
    private static SiegePlan firing(UUID id,UUID participant){var p=ready(id,participant,100);p.advance(100,0,true,true,null,false);p.advance(500,0,true,true,sight(participant,FIRST,500),false);return p;}
    private static void cleanupDirectory(Path dir)throws java.io.IOException{try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    private static Path proofDirectory(TestContext c,String label)throws java.io.IOException{
        String configured=System.getProperty("drmd.test.siegeProofDir");
        Path base=configured==null?c.getWorld().getServer().getRunDirectory().resolve("siege-plan-proof"):Path.of(configured);
        Files.createDirectories(base);return Files.createTempDirectory(base,label+"-");
    }
    private static void preserveFile(TestContext c,Path file,String label,NbtCompound expected)throws Exception{
        var report=proofDirectory(c,label);var bytes=Files.readAllBytes(file);Files.write(report.resolve("ledger.dat"),bytes,StandardOpenOption.CREATE_NEW);
        String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        Files.writeString(report.resolve("SHA256SUMS"),sha+"  ledger.dat\n",StandardOpenOption.CREATE_NEW);
        Files.writeString(report.resolve("expected-managed-state.nbt.txt"),expected.asString(),StandardOpenOption.CREATE_NEW);
        System.out.println("SIEGE_PLAN_PERSISTENCE_FILE label="+label+" bytes="+bytes.length+" sha256="+sha+" report="+report);
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="siege-plan-real-save")
    public void actualPersistentStateReopenKeepsFiniteAcksWithoutReconstructingPermits(TestContext c)throws Exception{
        var lookup=c.getWorld().getRegistryManager();var old=SiegePlanCodecBaselineGameTests.legacy();var state=SurvivalThreatState.fromNbt(old.copy(),lookup);
        var plan=firing(UUID.randomUUID(),SiegePlanCodecBaselineGameTests.PARTICIPANT);
        var permit=plan.requestFire(0,FIRST,500,sight(plan.snapshot().participant(),FIRST,500),READY).orElseThrow();
        c.assertTrue(plan.acknowledge(permit,UUID.randomUUID(),500),"protocol fixture accepts one declared actual-emission ID");
        var pending=plan.requestFire(1,SECOND,500,sight(plan.snapshot().participant(),SECOND,500),READY).orElseThrow();
        c.assertTrue(state.putSiegePlan(plan.snapshot()),"explicit non-executable snapshot is stored");
        var directory=Files.createTempDirectory("drmd-siege-plan-save-");
        try{
            var manager=new PersistentStateManager(directory.toFile(),Schemas.getFixer(),lookup);manager.set(SurvivalThreatState.ID,state);manager.save();
            c.assertTrue(Files.isRegularFile(directory.resolve(SurvivalThreatState.ID+".dat")),"Minecraft wrote an actual compressed PersistentState file");
            preserveFile(c,directory.resolve(SurvivalThreatState.ID+".dat"),"history",state.writeNbt(new NbtCompound(),lookup));
            var reopened=new PersistentStateManager(directory.toFile(),Schemas.getFixer(),lookup);
            var restored=reopened.getOrCreate(new PersistentState.Type<>(SurvivalThreatState::new,SurvivalThreatState::fromNbt,null),SurvivalThreatState.ID);
            var snapshot=restored.siegePlan(plan.snapshot().id()).orElseThrow();
            c.assertTrue(snapshot.equals(plan.snapshot()),"actual file reopen keeps exact immutable intent/known point/future bindings/finite ACK history");
            var instance=new SiegePlan(snapshot);
            c.assertTrue(!instance.acknowledge(pending,UUID.randomUUID(),500)&&instance.remaining(0)==4&&instance.remaining(1)==5,"load cannot recreate an unacknowledged transient permit or refill spent history");
            SiegePlanCodecBaselineGameTests.assertLegacy(c,old,restored.writeNbt(new NbtCompound(),lookup));
        }finally{cleanupDirectory(directory);}
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="siege-plan-bounded-state")
    public void boundedIntentUpdatesNeverReserveReconAndCannotRollbackFiniteHistory(TestContext c){
        var lookup=c.getWorld().getRegistryManager();var old=SiegePlanCodecBaselineGameTests.legacy();var state=SurvivalThreatState.fromNbt(old.copy(),lookup);
        UUID liveActor=UUID.fromString(old.getCompound("actors").getKeys().iterator().next());
        var collision=proposed(UUID.randomUUID(),UUID.randomUUID(),100);collision.bindFutureIdentity(0,liveActor);
        c.assertTrue(!state.putSiegePlan(collision.snapshot())&&state.actorCount()==1&&state.canDeploy(SiegePlanCodecBaselineGameTests.PARTICIPANT),"future identity cannot claim a live recon identity or reserve its remaining slot");
        var first=firing(UUID.randomUUID(),SiegePlanCodecBaselineGameTests.PARTICIPANT);var before=first.snapshot();c.assertTrue(state.putSiegePlan(before),"first intent admitted");
        var competing=proposed(UUID.randomUUID(),UUID.randomUUID(),100);competing.bindFutureIdentity(0,FIRST);
        c.assertTrue(!state.putSiegePlan(competing.snapshot()),"two decoded plans cannot claim one future identity");
        c.assertTrue(!state.putSiegePlan(proposed(UUID.randomUUID(),first.snapshot().participant(),100).snapshot()),"one participant cannot stack multiple intent records");
        var token=first.requestFire(0,FIRST,500,sight(first.snapshot().participant(),FIRST,500),READY).orElseThrow();first.acknowledge(token,UUID.randomUUID(),500);
        c.assertTrue(state.putSiegePlan(first.snapshot())&&!state.putSiegePlan(before),"append-only update succeeds and stale snapshot cannot refill finite ACK history");
        for(int i=1;i<8;i++)c.assertTrue(state.putSiegePlan(proposed(UUID.randomUUID(),UUID.randomUUID(),100).snapshot()),"bounded intent admission up to8");
        c.assertTrue(state.siegePlanCount()==8&&!state.putSiegePlan(proposed(UUID.randomUUID(),UUID.randomUUID(),100).snapshot()),"ninth intent is refused without eviction or an unbounded queue");
        c.assertTrue(!state.removeSiegePlan(first.snapshot().id()),"active data/cooldown cannot be removed to bypass the protocol");
        first.advance(first.snapshot().encounterEnds(),0,true,true,null,false);first.advance(first.snapshot().cooldownEnds(),0,true,true,null,false);
        c.assertTrue(state.putSiegePlan(first.snapshot())&&state.removeSiegePlan(first.snapshot().id()),"only a closed non-executable record is explicitly removable");
        SiegePlanCodecBaselineGameTests.assertLegacy(c,old,state.writeNbt(new NbtCompound(),lookup));
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="siege-plan-opaque")
    public void unsupportedAndOversizedSubtreesAreReadOnlyDefensiveCopies(TestContext c)throws Exception{
        var lookup=c.getWorld().getRegistryManager();var old=SiegePlanCodecBaselineGameTests.legacy();
        for(boolean oversized:new boolean[]{false,true}){
            var ext=SiegePlanCodecBaselineGameTests.extension();
            if(oversized)for(int i=0;i<8;i++)ext.getCompound("plans").put(UUID.randomUUID().toString(),SiegePlanCodecBaselineGameTests.record());else ext.putInt("schema",37);
            var original=ext.copy();var input=old.copy();input.put("siegePlans",ext);
            var state=SurvivalThreatState.fromNbt(input,lookup);ext.putInt("schema",1);ext.putString("mutatedAfterDecode","must-not-change-owned-state");
            c.assertTrue(state.siegePlansReadOnly()&&state.siegePlanCount()==0&&!state.putSiegePlan(proposed(UUID.randomUUID(),UUID.randomUUID(),100).snapshot()),"unknown/oversized data remains opaque and grants no mutation or plan authority");
            var out=state.writeNbt(new NbtCompound(),lookup);c.assertTrue(out.getCompound("siegePlans").equals(original),"opaque save data is a stable owned copy and roundtrips without clobbering");
            SiegePlanCodecBaselineGameTests.assertLegacy(c,old,out);
            var directory=Files.createTempDirectory("drmd-siege-opaque-");
            try{
                var manager=new PersistentStateManager(directory.toFile(),Schemas.getFixer(),lookup);manager.set(SurvivalThreatState.ID,state);manager.save();
                preserveFile(c,directory.resolve(SurvivalThreatState.ID+".dat"),oversized?"opaque-oversized":"opaque-future",out);
                var reopened=new PersistentStateManager(directory.toFile(),Schemas.getFixer(),lookup);
                var saved=reopened.getOrCreate(new PersistentState.Type<>(SurvivalThreatState::new,SurvivalThreatState::fromNbt,null),SurvivalThreatState.ID);
                c.assertTrue(saved.siegePlansReadOnly()&&saved.writeNbt(new NbtCompound(),lookup).getCompound("siegePlans").equals(original),"actual compressed save/reopen preserves opaque payload without granting decoded authority");
                SiegePlanCodecBaselineGameTests.assertLegacy(c,old,saved.writeNbt(new NbtCompound(),lookup));
            }finally{cleanupDirectory(directory);}
        }c.complete();
    }

    private static final class InactiveFixture implements AutoCloseable {
        final ServerWorld world;final ServerPlayerEntity player;final EmbeddedChannel channel;final boolean mobRule;
        SurvivalThreatState state;SiegePlan plan;SiegePlan.Snapshot expected;
        final List<String> observations=new ArrayList<>();
        long started;int stage;boolean closed;
        InactiveFixture(TestContext c){
            world=c.getWorld();mobRule=world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true,world.getServer());
            var client=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),"siege-intent"),false);
            player=new ServerPlayerEntity(world.getServer(),world,client.gameProfile(),client.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);
            world.getServer().getPlayerManager().onPlayerConnect(connection,player,client);
            try{
            player.changeGameMode(GameMode.SURVIVAL);player.getAbilities().flying=false;player.setNoGravity(true);
            DescentPlayerData.get(player).setEnabled(false);player.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(8,180,8))));player.setVelocity(Vec3d.ZERO);
            c.assertTrue(!player.isCreative()&&!player.isSpectator()&&world.getDifficulty()!=Difficulty.PEACEFUL,"actual ordinary Survival participant and enabled encounters, not mode-disabled apparent inactivity");
            state=SurvivalThreatState.get(world);started=world.getTime();plan=ready(UUID.randomUUID(),player.getUuid(),started);expected=plan.snapshot();
            c.assertTrue(state.putSiegePlan(expected),"non-executable high-readiness future intent stored without a production trigger");
            observations.add("phase=PLANNED actualWorldTime="+started+" playerMode="+player.interactionManager.getGameMode()+" mobSpawning="+world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING));
            }catch(Throwable failure){close();throw failure;}
        }
        void check(TestContext c){
            long now=world.getTime();world.resetIdleTimeout();
            c.assertTrue(state.siegePlan(plan.snapshot().id()).orElseThrow().equals(expected),"ordinary live director must not advance a persisted plan or consume its pure orders");
            c.assertTrue(state.rosterSize(player.getUuid())==0&&world.getEntitiesByClass(RocketDroneEntity.class,player.getBoundingBox().expand(96),e->e.isAlive()).isEmpty(),"inactive intent creates no rocket pair or live roster reservation");
            var recon=state.progress(player.getUuid());c.assertTrue(recon.isEmpty()||recon.get().phase()==ThreatProgress.Phase.QUIET,"ordinary recon remains QUIET without an actual eligible alarm");
            if(stage==0&&now-started>=20){
                plan.advance(now,0,true,true,null,false);expected=plan.snapshot();c.assertTrue(state.putSiegePlan(expected),"test explicitly writes a pure WARNING intent; production never advanced it");stage=1;
                observations.add("phase=WARNING actualWorldTime="+now+" fullWarningEnds="+expected.warningEnds());
            }else if(stage==1&&now>=expected.warningEnds()){
                // This DTO, not a live observer, is deliberately insufficient for runtime
                // authority. Waiting uses real world ticks; no server clock is changed.
                plan.advance(now,0,true,true,sight(player.getUuid(),FIRST,now),false);expected=plan.snapshot();
                c.assertTrue(expected.phase()==SiegePlan.Phase.SIEGE&&state.putSiegePlan(expected),"explicit pure SIEGE data remains non-executable");started=now;stage=2;
                observations.add("phase=SIEGE_DTO actualWorldTime="+now+" liveRoster="+state.rosterSize(player.getUuid()));
            }else if(stage==2&&now-started>=20){
                observations.add("completed actualWorldTime="+now+" playerHp="+player.getHealth()+" liveRoster="+state.rosterSize(player.getUuid())+" planUnchanged=true");
                try{Files.writeString(proofDirectory(c,"non-autostart").resolve("actual-survival-ticks.txt"),String.join("\n",observations)+"\n",StandardOpenOption.CREATE_NEW);}
                catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                System.out.println("SIEGE_INTENT_NON_AUTOSTART_PASS actualWorldTime="+now+" planPhase="+expected.phase()+" liveRoster="+state.rosterSize(player.getUuid()));close();c.complete();
            }
        }
        @Override public void close(){
            if(closed)return;closed=true;
            var stored=state==null||plan==null?Optional.<SiegePlan.Snapshot>empty():state.siegePlan(plan.snapshot().id());if(stored.isPresent()){
                var retiring=new SiegePlan(stored.get());retiring.advance(retiring.snapshot().clock(),0,false,false,null,false);
                if(retiring.snapshot().phase()!=SiegePlan.Phase.CLOSED)retiring.advance(retiring.snapshot().cooldownEnds(),0,true,true,null,false);
                state.putSiegePlan(retiring.snapshot());state.removeSiegePlan(retiring.snapshot().id());
            }
            world.getServer().getPlayerManager().remove(player);player.discard();channel.finishAndReleaseAll();world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(mobRule,world.getServer());
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="siege-plan-no-runtime",tickLimit=560)
    public void actualEnabledSurvivalTicksDoNotExecutePlannedWarningOrSiegeData(TestContext c){
        var f=new InactiveFixture(c);c.runAtEveryTick(()->{if(f.closed)return;try{f.check(c);}catch(Throwable failure){f.close();throw failure;}});
    }
}
