package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.physics.ragdoll.RagdollDebrisGroups;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.fixture.city.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import java.util.*;

/** Separate opt-in two-process proof. Never part of the running elapsed-time soak. */
public final class PostColdSettlementGameTests implements FabricGameTest {
    static final int TICK_LIMIT=60_000;
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="post-cold-authored-settlement",tickLimit=TICK_LIMIT)
    public void aRealSavedAuthoredSettlementReceivesNativeExplosionAndPhysicalCleanupAfterAnotherColdRestart(TestContext c){
        String phase=System.getProperty("drmd.postcold.phase","");c.assertTrue(phase.equals("place")||phase.equals("blast"),"explicit place/blast phase requires separate real server process");
        var run=new Run(c,phase);c.createTimedTaskRunner().expectMinDurationOrRun(TICK_LIMIT,run::tick);
    }
    static final class Memory extends PersistentState {
        NbtCompound data=new NbtCompound();
        static Memory get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(new Type<>(Memory::new,Memory::read,null),"drmd_postcold_settlement_proof");}
        static Memory read(NbtCompound n,RegistryWrapper.WrapperLookup lookup){var m=new Memory();m.data=n.copy();return m;}
        public NbtCompound writeNbt(NbtCompound ignored,RegistryWrapper.WrapperLookup lookup){return data.copy();}
    }
    private static final class Run {
        final TestContext c;final ServerWorld w;final String phase,source;final Memory memory;final NbtCompound data;final long began;
        final List<Entity> residents=new ArrayList<>();final List<DroneEntity> guards=new ArrayList<>();final List<RagdollEntity> corpses=new ArrayList<>();
        final Map<UUID,AiFixtureChunks> actorLeases=new HashMap<>();final Map<UUID,ChunkPos> actorColumns=new HashMap<>();
        final List<BlockBodyEntity> detached=new ArrayList<>();
        CityBlueprint.Plan plan;CityRuntimeSession session;Operator operator;AiFixtureChunks pageLease;CityAuthoringJob.Page leasedPage;BlockPos anchor;
        final NbtList afterStates=new NbtList();int stage,cursor,changed,admission;long actorsAt,blastAt,lastMarker;boolean done;TntEntity tnt;Set<UUID> beforeDropIds;RagdollCohortDrops drops;
        Run(TestContext c,String phase){
            this.c=c;this.phase=phase;w=c.getWorld();
            RagdollSoakGameTests.write("actual-java.json",Map.of("pid",ProcessHandle.current().pid(),"mode","postcold-"+phase,"source",System.getProperty("drmd.postcold.source"),"executable",ProcessHandle.current().info().command().orElse(""),"classPath",System.getProperty("java.class.path")));memory=Memory.get(w);data=memory.data;began=w.getTime();source=System.getProperty("drmd.postcold.source","");c.assertTrue(source.matches("[0-9a-f]{40}"),"exact test source40");
            var prior=RagdollSoakState.get(w).data;c.assertTrue(prior.getInt("Schema")==1&&(prior.getString("Status").equals("SHORT_COLD_PROOF_PASS")||prior.getString("Status").equals("TWO_HOUR_SOAK_AND_COLD_PASS")),"requires the original actual saved-world cold proof, not a manufactured city-only world");
            if(phase.equals("place")){
                c.assertTrue(data.isEmpty(),"fresh post-cold authoring receipt; no automatic overwrite/retry");
                var previousRows=prior.getList("Rows",NbtElement.COMPOUND_TYPE);var previous=BlockPos.fromLong(previousRows.getCompound(previousRows.size()-1).getLong("Base"));
                anchor=new BlockPos(previous.getX()+8192,w.getTopY()-96,previous.getZ());operator=new Operator(c,UUID.randomUUID());operator.player.setPosition(anchor.getX()+160.5,anchor.getY()+5,anchor.getZ()+160.5);operator.player.setNoGravity(true);
                var context=CityWorldStorage.capture(operator.player);c.assertTrue(context.backend()==CityManifest.Backend.STOCK,"this explicit same-save fixture is ordinary STOCK; native cubic readback remains separate");plan=CityBlueprint.create(2,new CityBlueprint.Point(anchor.getX(),anchor.getY(),anchor.getZ()),context.bounds());
                data.putInt("Schema",1);data.putString("Backend",CityWorldStorage.capture(operator.player).backend().name());data.putString("Source",source);data.putString("PriorSource",prior.getString("Source"));data.putString("PriorStatus",prior.getString("Status"));data.putLong("Anchor",anchor.asLong());data.putString("PlanHash",plan.hash());data.putUuid("Operator",operator.player.getUuid());data.putLong("Seed",2);data.putString("Status","PLACING");memory.markDirty();
                c.assertTrue(plan.buildings().size()==16&&plan.buildings().stream().map(CityBlueprint.Building::archetype).distinct().count()==8,"genuine public fullCITY16lots/8archetypes, no truncated CITY/hash substitute");
                session=CityAuthoringSystem.plan(operator.player,2,plan.anchor(),CityManifest.Kind.CITY);
            }else{
                c.assertTrue(data.getInt("Schema")==1&&data.getString("Source").equals(source)&&data.getString("Status").equals("SAVED_SETTLEMENT"),"same completed source/settlement receipt restored by the second actual JVM");
                anchor=BlockPos.fromLong(data.getLong("Anchor"));operator=new Operator(c,data.getUuid("Operator"));operator.player.setPosition(anchor.getX()+160.5,anchor.getY()+5,anchor.getZ()+160.5);operator.player.setNoGravity(true);
                plan=CityBlueprint.create(data.getLong("Seed"),new CityBlueprint.Point(anchor.getX(),anchor.getY(),anchor.getZ()),CityWorldStorage.capture(operator.player).bounds());c.assertTrue(plan.hash().equals(data.getString("PlanHash")),"same authored plan/hash/bounds after cold; no re-placement or fallback");
            }
        }
        void tick(){if(done)return;try{
            w.resetIdleTimeout();operator.drain();for(var e:residents)if(!e.isRemoved())follow(e);for(var body:corpses)if(!body.isRemoved())follow(body);
            if(System.nanoTime()-lastMarker>=60_000_000_000L){lastMarker=System.nanoTime();System.out.println("POST_COLD_SETTLEMENT phase="+phase+" stage="+stage+" cursor="+cursor+" worldTicks="+(w.getTime()-began)+" actualCorpses="+corpses.stream().map(b->b.getUuid()+":"+b.age+":"+b.physicalLifeTicks()+":"+b.isRemoved()).toList());}
            if(phase.equals("place")){place();return;}blast();
        }catch(RuntimeException|Error failure){done=true;data.putString("Failure",failure.toString());data.putString("Status","FIRST_FAILURE");memory.markDirty();RagdollSoakGameTests.write("postcold-"+phase+"-first-failure.json",Map.of("failure",failure.toString(),"stage",stage,"cursor",cursor,"worldTicks",w.getTime()-began));closeLeases();operator.close();throw failure;}}
        void place(){
            var status=session.inspect();c.assertTrue(CityWorldStorage.eligible(operator.player),"native author remains actually connected/authorized");c.assertTrue(stage>=3||status.state()!=CityRuntimeSession.State.CLOSED&&status.state()!=CityRuntimeSession.State.CLOSING,"unrequested runtime closure is NONPASS");c.assertTrue(status.state()!=CityRuntimeSession.State.FAILED,"actual authoring/WAL failure: "+status.detail());
            if(stage==0){if(status.job().isEmpty())return;var job=status.job().orElseThrow();c.assertTrue(status.manifest().orElseThrow().kind()==CityManifest.Kind.CITY&&status.manifest().orElseThrow().identity().planHash().equals(plan.hash()),"normal runtime generated the same full CITY manifest");
                if(job.cursor()<plan.cells().size())lease(plan.cells().get(job.cursor()).position());
                if(status.state()==CityRuntimeSession.State.READY){c.assertTrue(session.apply(operator.player.getUuid()),"explicit owner applies through real CityAuthoringJob/64CAS/WAL");return;}
                if(job.state()==CityAuthoringJob.State.PAUSED){c.assertTrue(job.reason()==CityAuthoringJob.Reason.UNAVAILABLE||job.reason()==CityAuthoringJob.Reason.MANUAL,"unexpected authoring refusal remains NONPASS: "+status.detail());session.resume(operator.player.getUuid());return;}
                if(job.state()!=CityAuthoringJob.State.COMPLETED)return;c.assertTrue(job.cursor()==plan.cells().size(),"actual entire plan committed; no fake partial city");data.putLong("CommittedBatches",job.nextBatch());stage=1;cursor=0;return;
            }
            if(stage==1){if(!verifyOriginal())return;populate();actorsAt=w.getTime();stage=2;return;}
            if(stage==2){if(residents.stream().anyMatch(e->e.age<8)||w.getTime()-actorsAt<8)return;var roster=new NbtList();for(var e:residents){var n=new NbtCompound();n.putUuid("Id",e.getUuid());n.putLong("Position",e.getBlockPos().asLong());n.putBoolean("Guard",e instanceof DroneEntity);roster.add(n);}data.put("Roster",roster);data.putInt("Cells",plan.cells().size());data.putInt("Solids",plan.solidCount());data.putInt("Cuts",plan.cutCount());session.ownerDisconnected(operator.player.getUuid());stage=3;return;}
            if(!session.settled())return;data.putString("Status","SAVED_SETTLEMENT");memory.markDirty();finish("POST_COLD_SETTLEMENT_SAVE_PASS");
        }
        boolean verifyOriginal(){
            if(cursor==plan.cells().size())return true;lease(plan.cells().get(cursor).position());var page=CityAuthoringJob.Page.of(plan.cells().get(cursor).position());
            for(int n=0;n<64&&cursor<plan.cells().size();n++){
                var cell=plan.cells().get(cursor);if(!CityAuthoringJob.Page.of(cell.position()).equals(page))break;var p=pos(cell.position());var actual=WorldAccess.of(w).readIfLoaded(p);c.assertTrue(actual.isPresent()&&Registries.BLOCK.getId(actual.orElseThrow().getBlock()).toString().equals(cell.state()),"actual "+phase+" world matches every authored cell, including cuts: "+p);cursor++;
            }return cursor==plan.cells().size();
        }
        void populate(){
            var entry=plan.buildings().stream().filter(b->b.archetype()==CityBlueprint.Archetype.COURTYARD).findFirst().orElseThrow().entry();var p=pos(entry);lease(entry);
            for(int i=0;i<2;i++){var villager=EntityType.VILLAGER.create(w);c.assertTrue(villager!=null,"native resident factory");villager.setPosition(Vec3d.ofBottomCenter(p.add(i==0?-5:5,0,6)));villager.setCustomName(net.minecraft.text.Text.literal("Authored settlement resident "+i));c.assertTrue(w.spawnEntity(villager),"actual alive native resident registration");residents.add(villager);follow(villager);}
            for(int i=0;i<2;i++){DroneEntity guard=(i==0?ModEntities.DRONE:ModEntities.JET).create(w);c.assertTrue(guard!=null,"actual standard guard factory");guard.setPosition(Vec3d.ofBottomCenter(p.add(i,0,1)));guard.setAiDisabled(true);guard.setPersistent();var tag=new NbtCompound();guard.writeNbt(tag);tag.putFloat("Health",1);tag.putFloat("shield",0);tag.putFloat("armor",0);guard.readNbt(tag);c.assertTrue(w.spawnEntity(guard),"actual guarded inhabited settlement actor");residents.add(guard);guards.add(guard);follow(guard);}
            data.putLong("BlastPosition",p.add(-1,1,1).asLong());
        }
        void blast(){
            if(stage==0){
                var roster=data.getList("Roster",NbtElement.COMPOUND_TYPE);c.assertTrue(roster.size()==4,"two actual saved residents and two guards");
                if(cursor<roster.size()){var tag=roster.getCompound(cursor);var p=BlockPos.fromLong(tag.getLong("Position"));lease(new CityBlueprint.Point(p.getX(),p.getY(),p.getZ()));var e=w.getEntity(tag.getUuid("Id"));c.assertTrue(++admission<200,"saved actual population must load, never respawned");if(e==null||!w.shouldTickEntity(e.getBlockPos()))return;c.assertTrue(tag.getBoolean("Guard")?e instanceof DroneEntity:e instanceof net.minecraft.entity.passive.VillagerEntity,"same actual saved population type");residents.add(e);if(e instanceof DroneEntity g)guards.add(g);follow(e);cursor++;admission=0;return;}
                actorsAt=w.getTime();stage=1;cursor=0;return;
            }
            if(stage==1){if(!verifyOriginal())return;stage=2;return;}
            if(stage==2){if(w.getTime()-actorsAt<8)return;c.assertTrue(residents.stream().allMatch(Entity::isAlive),"saved settlement genuinely inhabited on normal ticks before native damage");
                beforeDropIds=RagdollCohortDrops.identities(w);var at=BlockPos.fromLong(data.getLong("BlastPosition"));tnt=new TntEntity(w,at.getX()+.5,at.getY()+.2,at.getZ()+.5,null);tnt.setFuse(1);c.assertTrue(w.spawnEntity(tnt),"fresh native TNT spawned ONLY AFTER actual settlement cold load");blastAt=w.getTime();stage=3;return;}
            var area=new Box(anchor).expand(192);
            if(stage==3){if(!tnt.isRemoved()||tnt.age<1)return;c.assertTrue(guards.stream().noneMatch(Entity::isAlive),"real post-cold explosion kills real saved guards");corpses.addAll(w.getEntitiesByClass(RagdollEntity.class,area,Entity::isAlive));c.assertTrue(corpses.size()==2&&corpses.stream().map(Entity::getUuid).distinct().count()==2,"two genuine physical death events, no factory substitute or duplicate");for(var b:corpses)follow(b);drops=RagdollCohortDrops.afterActualDeaths(w,beforeDropIds);detached.addAll(BodySpatialIndex.query(w,area));c.assertTrue(detached.size()<=4,"existing structural body allowance unchanged; permanent components retained");stage=4;cursor=0;return;}
            if(stage==4){
                if(cursor<plan.cells().size()){lease(plan.cells().get(cursor).position());var page=CityAuthoringJob.Page.of(plan.cells().get(cursor).position());for(int n=0;n<64&&cursor<plan.cells().size();n++){var cell=plan.cells().get(cursor);if(!CityAuthoringJob.Page.of(cell.position()).equals(page))break;var state=WorldAccess.of(w).readIfLoaded(pos(cell.position()));c.assertTrue(state.isPresent(),"post-blast material accounting needs real FULL data");var id=Registries.BLOCK.getId(state.orElseThrow().getBlock()).toString();afterStates.add(NbtString.of(id));if(!id.equals(cell.state()))changed++;cursor++;}return;}
                c.assertTrue(changed>0,"native TNT actually changes authored settlement material, no cosmetic explosion");stage=5;
            }
            if(corpses.stream().anyMatch(b->!b.isRemoved()||w.getEntity(b.getUuid())!=null))return;
            c.assertTrue(w.getTime()-blastAt>=1200,"actual remaining ordinary corpse lifetime, no accelerated/manual ticks");
            for(var b:corpses)c.assertTrue(b.physicalLifeTicks()==1201&&b.simulatedPhysicsTicks()+b.skippedPhysicsTicks()==1200&&RagdollDebrisGroups.get(w).snapshot(b.debrisGroupId()).isEmpty(),"real post-cold death/solver/sleep/TTL/group cleanup");
            drops.assertConserved(c,area);for(var b:detached)c.assertTrue(b.isAlive(),"permanent authored structural material cannot be timer-deleted as corpse cleanup");
            var birthIds=new NbtList();for(var e:drops.births){var id=new NbtCompound();id.putUuid("Id",e.getUuid());birthIds.add(id);}data.put("DropBirthIds",birthIds);var quantities=new NbtCompound();drops.original.forEach(quantities::putInt);data.put("DropCounts",quantities);data.put("AfterStates",afterStates);data.putLongArray("PlanPositions",plan.cells().stream().mapToLong(cell->pos(cell.position()).asLong()).toArray());var savedBodies=new NbtList();for(var b:detached){var n=new NbtCompound();b.writeNbt(n);savedBodies.add(n);}data.put("Detached",savedBodies);data.putInt("ChangedCells",changed);data.putInt("NewCorpses",2);data.putInt("RetainedStructuralBodies",detached.size());data.putString("Status","POST_COLD_SETTLEMENT_BLAST_CLEANUP_PASS");memory.markDirty();finish("POST_COLD_SETTLEMENT_BLAST_CLEANUP_PASS");
        }
        void lease(CityBlueprint.Point p){var page=CityAuthoringJob.Page.of(p);if(page.equals(leasedPage))return;if(pageLease!=null)pageLease.close();pageLease=new AiFixtureChunks(w,pos(p),0);leasedPage=page;}
        void follow(Entity e){var key=new ChunkPos(e.getBlockPos());if(key.equals(actorColumns.get(e.getUuid())))return;var old=actorLeases.remove(e.getUuid());if(old!=null)old.close();actorLeases.put(e.getUuid(),new AiFixtureChunks(w,e.getBlockPos(),0));actorColumns.put(e.getUuid(),key);}
        void closeLeases(){if(pageLease!=null)pageLease.close();actorLeases.values().forEach(AiFixtureChunks::close);}
        void finish(String status){done=true;closeLeases();operator.close();memory.markDirty();c.assertTrue(w.getServer().saveAll(false,true,true),"actual persistent settlement/actor/content save");RagdollSoakGameTests.write("postcold-"+phase+"-result.json",Map.of("status",status,"source",source,"priorSource",data.getString("PriorSource"),"priorStatus",data.getString("PriorStatus"),"planHash",plan.hash(),"cells",plan.cells().size(),"changedCells",changed,"worldTicks",w.getTime()-began,"population",residents.size(),"retainedStructuralBodies",detached.size()));c.complete();}
    }
    private static BlockPos pos(CityBlueprint.Point p){return new BlockPos(p.x(),p.y(),p.z());}
    /** Actual native connected identity and genuine keepalive replies, no permission-source spoof. */
    private static final class Operator implements AutoCloseable {
        final net.minecraft.server.network.ServerPlayerEntity player;final io.netty.channel.embedded.EmbeddedChannel channel;final net.minecraft.server.OperatorEntry previous;final ServerWorld world;
        Operator(TestContext c,UUID id){world=c.getWorld();var server=world.getServer();var profile=new com.mojang.authlib.GameProfile(id,"postcold-author");var options=net.minecraft.server.network.ConnectedClientData.createDefault(profile,false);player=new net.minecraft.server.network.ServerPlayerEntity(server,world,profile,options.syncedOptions());var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);channel=new io.netty.channel.embedded.EmbeddedChannel(connection);previous=server.getPlayerManager().getOpList().get(profile);server.getPlayerManager().onPlayerConnect(connection,player,options);server.getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(profile,2,false));player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);c.assertTrue(CityWorldStorage.eligible(player),"real connected Creative OP2 identity");}
        void drain(){Object p;while((p=channel.readOutbound())!=null){if(p instanceof net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket k)player.networkHandler.onKeepAlive(new net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket(k.getId()));io.netty.util.ReferenceCountUtil.release(p);}}
        public void close(){var m=world.getServer().getPlayerManager();if(previous!=null)m.getOpList().add(previous);else if(m.isOperator(player.getGameProfile()))m.removeFromOperators(player.getGameProfile());if(m.getPlayer(player.getUuid())==player)m.remove(player);player.discard();channel.finishAndReleaseAll();}
    }
}
