package com.terminaldetector.drmd.world.threat;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.AirMineEntity;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.OrbEntity;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.world.bombardment.AerialBombEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static com.terminaldetector.drmd.world.threat.ThreatProgress.*;

/** Actual default-online-grace fixture. No production clock/state/target injection. */
final class ReconAdmissionFixture implements AutoCloseable {
    enum Scenario { OBSERVE, HIDDEN_INTERCEPT, OBSERVED_INTERCEPT }
    private static final Gson JSON=new Gson();
    private static final Map<ServerWorld,Map<UUID,ReconAdmissionFixture>> ACTIVE=new IdentityHashMap<>();
    private static boolean observerInstalled;
    private static final class Rules implements AutoCloseable {
        private static final Map<ServerWorld,Rules> ACTIVE_RULES=new IdentityHashMap<>();
        final ServerWorld w;final boolean before;int owners;
        Rules(ServerWorld w){this.w=w;before=w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);}
        static Rules acquire(ServerWorld w){var r=ACTIVE_RULES.computeIfAbsent(w,Rules::new);r.owners++;w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true,w.getServer());return r;}
        public void close(){if(--owners==0){ACTIVE_RULES.remove(w);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(before,w.getServer());}}
    }
    private static final class Lease implements AutoCloseable {
        private static final ChunkTicketType<UUID> TYPE=ChunkTicketType.create("drmd_recon_fixture",Comparator.<UUID>naturalOrder());
        final ServerWorld w;final UUID owner=UUID.randomUUID();final Set<ChunkPos> columns=new LinkedHashSet<>();
        Lease(ServerWorld w,BlockPos at){
            this.w=w;w.resetIdleTimeout();
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){var p=new ChunkPos((at.getX()>>4)+x,(at.getZ()>>4)+z);columns.add(p);w.getChunkManager().addTicket(TYPE,p,2,owner);}
            for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        }
        boolean ready(){for(var p:columns)if(!w.shouldTickEntity(p.getStartPos()))return false;return true;}
        public void close(){for(var p:columns)w.getChunkManager().removeTicket(TYPE,p,2,owner);}
    }
    final TestContext c;final ServerWorld w;final Scenario scenario;final BlockPos centre;final Vec3d at;
    final List<Map<String,Object>> trace=new ArrayList<>();
    final Map<BlockPos,BlockState> previous=new LinkedHashMap<>(),witness=new LinkedHashMap<>();
    final Set<UUID> sources=new HashSet<>(),artifacts=new HashSet<>();
    final Map<String,Integer> notices=new HashMap<>();
    final List<Vec3d> deathPlaces=new ArrayList<>();
    Lease lease;Rules rules;ServerPlayerEntity player;EmbeddedChannel channel;
    int admission,phase,traceBytes,lastThousand=-1;long joinedWorld,joinedNano,lastTick=-1,lastServer=-1,observedAt=-1,cooldownAt=-1;
    long packets,projectiles,bombs,mines;float initialHealth,initialAbsorption;UUID scout,assault;
    boolean closed,complete;String observerFailure;

    ReconAdmissionFixture(TestContext c,Scenario scenario){
        this.c=c;w=c.getWorld();this.scenario=scenario;centre=c.getAbsolutePos(new BlockPos(8,220,8));at=Vec3d.ofCenter(centre);installObserver();
    }
    private void connect(){
        rules=Rules.acquire(w);var profile=new GameProfile(UUID.randomUUID(),"recon-"+UUID.randomUUID().toString().substring(0,7));
        var data=ConnectedClientData.createDefault(profile,false);player=new ServerPlayerEntity(w.getServer(),w,profile,data.syncedOptions());
        var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);
        w.getServer().getPlayerManager().onPlayerConnect(connection,player,data);
        player.changeGameMode(GameMode.SURVIVAL);player.getAbilities().flying=false;player.setNoGravity(true);player.setVelocity(Vec3d.ZERO);player.setPosition(at);
        player.getInventory().clear();DescentPlayerData.get(player).setEnabled(false);
        initialHealth=player.getHealth();initialAbsorption=player.getAbsorptionAmount();
        c.assertTrue(player.interactionManager.getGameMode()==GameMode.SURVIVAL&&!player.isCreative()&&!player.isSpectator()
            &&w.getServer().getPlayerManager().getPlayer(player.getUuid())==player&&w.getEntity(player.getUuid())==player,"ordinary connected Survival identity, never creative TestContext mock");
        c.assertTrue(w.getDifficulty()!=Difficulty.PEACEFUL&&w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING),"actual enabled Survival rules");
        c.assertTrue(player.getStatusEffects().isEmpty()&&!player.isInvulnerable(),"no damage resistance, absorption effect or invulnerability masks Support behavior");
        c.assertTrue(state().progress(player.getUuid()).isEmpty()&&state().reconProgress(player.getUuid()).isEmpty(),"fresh owner has no restored maturity, alarm or attempt");
        var owners=ACTIVE.computeIfAbsent(w,ignored->new HashMap<>());c.assertTrue(owners.size()<8,"bounded passive observer owners");owners.put(player.getUuid(),this);
        joinedWorld=w.getTime();joinedNano=System.nanoTime();record("connected-default-grace");
    }
    private SurvivalThreatState state(){return SurvivalThreatState.get(w);}
    private void put(BlockPos pos,BlockState material){
        var old=w.getBlockState(pos);c.assertTrue(!old.hasBlockEntity(),"fixture never replaces a block entity");previous.putIfAbsent(pos.toImmutable(),old);
        c.assertTrue(previous.size()<=256,"bounded256-cell owned terrain witness");w.setBlockState(pos,material,Block.NOTIFY_ALL);witness.put(pos.toImmutable(),material);
    }
    private void terrain(){
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)put(centre.add(x,-1,z),Blocks.STONE.getDefaultState());
        for(int i=0;i<16;i++)for(int radius:new int[]{40,48,56}){
            double angle=Math.PI*2*i/16;put(centre.add((int)Math.round(Math.cos(angle)*radius),-1,(int)Math.round(Math.sin(angle)*radius)),Blocks.STONE.getDefaultState());
        }
        if(scenario==Scenario.HIDDEN_INTERCEPT)for(int x=-2;x<=2;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)
            if(Math.abs(x)==2||Math.abs(z)==2||y==-1||y==3)put(centre.add(x,y,z),Blocks.BEDROCK.getDefaultState());
    }
    private static void installObserver(){
        if(observerInstalled)return;observerInstalled=true;
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            var owners=ACTIVE.get(world);if(owners==null)return;
            for(var f:owners.values())try{f.loaded(entity);}catch(RuntimeException error){f.observerFailure=error.getClass().getSimpleName()+":"+error.getMessage();}
        });
    }
    /** Passive observation only: callback never supplies targets, actors or event results. */
    private void loaded(Entity e){
        if(e instanceof DroneEntity&&e.getCommandTags().contains("drmd.threat.owner="+player.getUuid())){
            var r=state().reconProgress(player.getUuid()).orElse(null);
            if(r!=null&&(r.scout().equals(e.getUuid())||e.getUuid().equals(r.assault()))){
                if(sources.size()==2&&!sources.contains(e.getUuid()))observerFailure="third lifetime source identity";
                else sources.add(e.getUuid());
            }
        }
        if(e instanceof ProjectileEntity shot&&sources.contains(shot.getOwnerUuid()))projectiles++;
        if(e.getPos().squaredDistanceTo(at)<=192*192){
            if(e instanceof AerialBombEntity)bombs++;
            if(e instanceof AirMineEntity)mines++;
        }
        if(e instanceof RagdollEntity||e instanceof ItemEntity||e instanceof ExperienceOrbEntity){
            for(var death:deathPlaces)if(e.getPos().squaredDistanceTo(death)<100){
                if(artifacts.size()>=64&&!artifacts.contains(e.getUuid()))observerFailure="owned death artifacts exceed64";
                else artifacts.add(e.getUuid());break;
            }
        }
    }
    /** Drain only normally delivered outbound messages; no flush/pump/sleep. */
    private void packets(){
        for(int i=0;i<4096;i++){
            Object raw=channel.readOutbound();if(raw==null)return;packets++;
            try{if(raw instanceof GameMessageS2CPacket packet&&packet.content().getContent() instanceof TranslatableTextContent text){
                var key=text.getKey();if(key.startsWith("message.drmd.threat.")){notices.merge(key,1,Integer::sum);record("normal-packet:"+key);}
            }}finally{ReferenceCountUtil.release(raw);}
        }
        c.assertTrue(channel.outboundMessages().isEmpty(),"bounded4096-packet drain never leaves a growing transport backlog");
    }
    private void record(String event){
        if(trace.size()>=64){observerFailure="diagnostic event cap64";return;}
        var row=new LinkedHashMap<String,Object>();row.put("event",event);row.put("scenario",scenario.toString());row.put("gameTick",c.getTick());
        row.put("serverTick",w.getServer().getTicks());row.put("worldTime",w.getTime());row.put("monotonicNano",System.nanoTime());row.put("phase",phase);
        if(player!=null){
            row.put("owner",player.getUuid().toString());row.put("age",player.age);row.put("pose",player.getPos().toString());row.put("alive",player.isAlive());
            row.put("actualIdentity",w.getEntity(player.getUuid())==player&&w.getServer().getPlayerManager().getPlayer(player.getUuid())==player);
            row.put("health",player.getHealth());row.put("absorption",player.getAbsorptionAmount());row.put("progress",state().progress(player.getUuid()).orElse(null));
            row.put("recon",state().reconProgress(player.getUuid()).orElse(null));row.put("roster",state().rosterSize(player.getUuid()));
        }
        row.put("projectiles",projectiles);row.put("bombs",bombs);row.put("mines",mines);row.put("packets",packets);row.put("work",SurvivalThreatDirector.stats(w));
        String json=JSON.toJson(row);int bytes=json.getBytes(StandardCharsets.UTF_8).length;
        if(traceBytes+bytes>1_048_576){observerFailure="diagnostic byte cap1MiB";return;}
        traceBytes+=bytes;trace.add(row);System.out.println("U03_RECON_TRACE="+json);
    }
    private void safeEffects(){
        c.assertTrue(projectiles==0&&bombs==0&&mines==0,"actual entity-load observer sees zero owned projectiles and zero nearby bombs/mines before escalation");
        c.assertTrue(player.getHealth()==initialHealth&&player.getAbsorptionAmount()==initialAbsorption,"Support caused no unmasked owner health/absorption loss");
        for(var e:witness.entrySet())c.assertTrue(w.getBlockState(e.getKey()).equals(e.getValue()),"actual bounded stable terrain witness unchanged: "+e.getKey());
    }
    private void budgets(){
        var s=SurvivalThreatDirector.stats(w);
        c.assertTrue(s.players()<=4&&s.roster()<=16&&s.candidates()<=8&&s.sight()<=8&&s.spawned()<=1,"actual world pass keeps existing bounded work: "+s);
        c.assertTrue(state().actorCount()<=8&&state().rosterSize(player.getUuid())<=2,"shared global8/perowner2 roster cap");
    }
    private DroneEntity actual(UUID id){
        var e=w.getEntity(id);c.assertTrue(e instanceof OrbEntity drone&&drone.isAlive()&&!drone.isAiDisabled()&&e.getWorld()==w,"canonical live AI-enabled ORB is actually loaded: "+id);
        return (DroneEntity)e;
    }
    private void kill(UUID id){
        var e=actual(id);c.assertTrue(deathPlaces.size()<2,"two real lethal stimuli maximum");deathPlaces.add(e.getPos());
        c.assertTrue(e.damage(player.getDamageSources().playerAttack(player),1_000_000)&&!e.isAlive(),"actual lethal player-attributed damage reaches ordinary AFTER_DEATH");record("actual-death:"+id);
    }
    void tick(){
        if(closed||complete)return;w.resetIdleTimeout();
        c.assertTrue(c.getTick()<13_900,"14000 framework keeps100 cleanup ticks: scenario="+scenario+" phase="+phase);
        if(lastTick>=0)c.assertTrue(c.getTick()==lastTick+1&&w.getServer().getTicks()==lastServer+1,"single preregistered driver observes consecutive actual game/server ticks");
        lastTick=c.getTick();lastServer=w.getServer().getTicks();
        if(lease==null){lease=new Lease(w,centre);return;}
        if(!lease.ready()){
            c.assertTrue(player==null,"admitted fixture never silently pauses observations while actual owner keeps aging");
            c.assertTrue(++admission<80,"bounded80 actual entity-ticking arena admission");return;
        }
        if(player==null){terrain();connect();return;}
        packets();budgets();c.assertTrue(observerFailure==null,"passive observer capacity/diagnostics: "+observerFailure);
        c.assertTrue(player.isAlive()&&!player.isCreative()&&!player.isSpectator()&&w.getEntity(player.getUuid())==player,"ordinary Survival owner remains actual/alive throughout scenario");
        var p=state().progress(player.getUuid()).orElse(null);
        if(p==null){c.assertTrue(player.age<20,"ordinary registered director creates new owner progress within20ticks");safeEffects();return;}
        if(p.played()/1000>lastThousand){lastThousand=(int)(p.played()/1000);record("actual-online:"+p.played());}
        var r=state().reconProgress(player.getUuid()).orElse(null);long now=w.getTime();
        if(phase==0){
            safeEffects();
            if(r==null){
                c.assertTrue(p.phase()==Phase.QUIET&&!p.pending()&&state().rosterSize(player.getUuid())==0,"no kill/alarm/actors fake unprovoked default novice grace");
                c.assertTrue(player.age<GRACE_TICKS+200,"natural safe scout admission remains bounded after original12000online ticks");return;
            }
            c.assertTrue(p.played()==GRACE_TICKS&&player.age>=GRACE_TICKS&&now-joinedWorld>=GRACE_TICKS,"actual original12000 online/world ticks elapsed before automatic admission; no DTO seed");
            scout=r.scout();var drone=actual(scout);
            c.assertTrue(drone.getRole()==AiRole.SUPPORT&&sources.contains(scout)&&sources.size()==1&&!r.assaultIssued(),"actual unprovoked factory scout is sole ORB/SUPPORT and observed by passive load callback");
            phase=1;record("actual-default-scout");
        }
        if(phase==1){
            safeEffects();c.assertTrue(r.scout().equals(scout)&&!r.assaultIssued()&&!r.closed(),"same finite scout and no early combat slot");
            if(scenario==Scenario.HIDDEN_INTERCEPT){
                c.assertTrue(!r.observed()&&p.phase()==Phase.QUIET&&!p.pending(),"solid pre-existing test enclosure prevents actual own LOS/warning, never invents detection");
                if(now-r.started()<30)return;
                kill(scout);cooldownAt=now;phase=3;record("hidden-intercept-cooldown");return;
            }
            if(!r.observed()){c.assertTrue(now-r.started()<200,"existing ORB selectors naturally acquire actual Survival owner within200ticks");return;}
            var observer=actual(scout);
            c.assertTrue(observer.getTarget()==player&&observer.canSee(player),"own natural target and current real LOS, never setTarget or observation injection");
            observedAt=r.observedAt();
            c.assertTrue(p.phase()==Phase.WARNING&&p.since()==observedAt&&p.until()==observedAt+WARNING_TICKS,"first actual sight begins full original400tick warning");
            phase=2;record("actual-own-los-warning");
            if(scenario==Scenario.OBSERVED_INTERCEPT)kill(scout);
            return;
        }
        if(phase==2){
            c.assertTrue(r.scout().equals(scout)&&r.observed()&&r.observedAt()==observedAt,"first observed identity/time stays fixed through scout loss");
            if(now<observedAt+WARNING_TICKS){
                safeEffects();c.assertTrue(p.phase()==Phase.WARNING&&p.until()==observedAt+WARNING_TICKS&&!r.assaultIssued(),"no assault or damaging effects before complete400tick warning");return;
            }
            if(!r.assaultIssued()){c.assertTrue(now<observedAt+WARNING_TICKS+200,"safe actual assault admission bounded after original warning");return;}
            assault=r.assault();var combat=actual(assault);
            c.assertTrue(combat.getRole()==AiRole.ASSAULT&&sources.size()==2&&sources.contains(assault)&&!assault.equals(scout),"one distinct real ORB/ASSAULT shares the same lifetime attempt");
            c.assertTrue(p.spawned()==1&&(p.phase()==Phase.SEARCHING||p.phase()==Phase.HUNTING),"exactly one combat allowance issued through real existing search/hunt");
            c.assertTrue(notices.getOrDefault("message.drmd.threat.recon",0)==1&&notices.getOrDefault("message.drmd.threat.warning",0)==1,"actual normally delivered recon and warning packets precede combat admission");
            record("actual-assault-after-full-warning");
            if(scenario==Scenario.OBSERVE){finish();return;}
            kill(assault);cooldownAt=now;phase=3;return;
        }
        if(phase==3){
            var end=state().progress(player.getUuid()).orElseThrow();var attempt=state().reconProgress(player.getUuid()).orElseThrow();
            c.assertTrue(!end.pending()&&state().rosterSize(player.getUuid())==0&&sources.size()==(scenario==Scenario.HIDDEN_INTERCEPT?1:2),"real owned deaths never recurse into new alarm/actors");
            if(end.phase()!=Phase.COOLDOWN){c.assertTrue(now-cooldownAt<=SurvivalThreatDirector.INTERVAL,"real director enters existing cooldown on next scheduled pass");return;}
            c.assertTrue(end.since()>=cooldownAt&&end.since()<=cooldownAt+SurvivalThreatDirector.INTERVAL&&end.until()==end.since()+COOLDOWN_TICKS&&attempt.closed(),"actual same12000 cooldown is fixed after real interception");
            c.assertTrue(attempt.scout().equals(scout)&&attempt.assaultIssued()==(scenario==Scenario.OBSERVED_INTERCEPT)
                &&(assault==null||assault.equals(attempt.assault())),"scout death/base roster absence cannot unspend saved lifetime assault UUID");
            if(scenario==Scenario.HIDDEN_INTERCEPT)safeEffects();
            if(now-cooldownAt<100)return;
            c.assertTrue(notices.getOrDefault("message.drmd.threat.recon",0)==1&&notices.getOrDefault("message.drmd.threat.cooldown",0)==1,"actual normal packet transport delivers recon/cooldown once");
            finish();
        }
    }
    private void finish(){
        var saved=state().writeNbt(new NbtCompound(),w.getRegistryManager());var own=saved.getCompound("recon").getCompound("attempts").getCompound(player.getUuid().toString());
        c.assertTrue(!own.isEmpty()&&own.getUuid("scout").equals(scout)&&own.getBoolean("assaultIssued")== (assault!=null),"actual saved state preserves observed identity and permanent issued flag");
        if(assault!=null)c.assertTrue(own.getUuid("assault").equals(assault),"actual written extension preserves exact issued assault UUID");
        c.assertTrue(!saved.getCompound("victims").contains(scout.toString())&&(assault==null||!saved.getCompound("victims").contains(assault.toString())),"owned patrol deaths are never misattributed as new confirmed-kill alarms");
        record("success-before-test-owned-cleanup");
        System.out.println("U03_RECON_SAVED_OWNER="+JSON.toJson(Map.of("scenario",scenario.toString(),"owner",player.getUuid().toString(),"savedRecon",own.toString(),
            "worldTicks",w.getTime()-joinedWorld,"playerAge",player.age,"wallNanos",System.nanoTime()-joinedNano,"scope","actual accelerated TestServer ticks; not600-wall-second/native/cold reader proof")));
        complete=true;close();c.complete();
    }
    void failure(Throwable error){record("failure:"+error.getClass().getSimpleName()+":"+error.getMessage());}
    @Override public void close(){
        if(closed)return;closed=true;
        if(player!=null){
            var owners=ACTIVE.get(w);if(owners!=null){owners.remove(player.getUuid());if(owners.isEmpty())ACTIVE.remove(w);}
            // Explicit test-owned cleanup only AFTER success/failure receipts. Never
            // performed by the scenario to free a gameplay slot or fake an actor death.
            for(var e:state().actors())if(e.getValue().owner().equals(player.getUuid())){
                var live=w.getEntity(e.getKey());if(live!=null)live.discard();state().removeActor(e.getKey());
            }
            for(var id:artifacts){var live=w.getEntity(id);if(live!=null)live.discard();}
            w.getServer().getPlayerManager().remove(player);player.discard();
        }
        if(channel!=null)channel.finishAndReleaseAll();
        for(var e:previous.entrySet())w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_ALL);
        if(rules!=null)rules.close();if(lease!=null)lease.close();
    }
}
