package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.weapon.projectile.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import com.terminaldetector.drmd.world.trap.PointDefenseInterception;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Material/owner/ordinary-collision and shared-factory controls. These lower-layer
 * cases complement the separate unchanged natural packet-driven pair; they are
 * not native keys, two-process reload or a 256-missile combat benchmark. */
public final class PlayerMechanismAaGuardGameTests implements FabricGameTest {
    private static final BlockPos GUN=new BlockPos(2,0,0),LEVER=GUN.up();
    private static final int MAX_FACTORY_RIGS=16,MAX_PENDING=2,WORLD_ACTIONS=8;
    private static final Set<Rig> FACTORY_RIGS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean factoryDispatcherRegistered;
    private record FactoryAction(long identity,UUID body,long notBefore,long expires,Runnable action){}
    private static RuntimeException propagate(Throwable failure){
        if(failure instanceof RuntimeException runtime)return runtime;if(failure instanceof Error error)throw error;return new RuntimeException(failure);
    }
    /** Test actions run after the actual registered body/contact solve. They
     * never invoke that solve, advance a clock, write a pose or stamp freshness. */
    private static synchronized void registerFactoryDispatcher(){
        if(factoryDispatcherRegistered)return;factoryDispatcherRegistered=true;
        ServerTickEvents.END_WORLD_TICK.register(world->{
            int actions=0;
            for(var rig:List.copyOf(FACTORY_RIGS)){
                if(rig.w!=world||rig.closed||rig.factoryActions.isEmpty())continue;
                if(actions>=WORLD_ACTIONS)break;
                var request=rig.factoryActions.peekFirst();long now=world.getTime();
                if(now<request.notBefore())continue;
                try{
                    rig.c.assertTrue(now<=request.expires(),"PRECONDITION_UNOBSERVED: bounded factory action waits for real fresh-body END phase");
                    rig.c.assertTrue(world.getServer().isOnThread()&&request.body().equals(rig.body.getUuid())&&world.getEntity(request.body())==rig.body
                        &&BodySpatialIndex.find(world,rig.body.getId()).orElse(null)==rig.body&&!rig.body.isRemoved(),"factory request retains its actual Rig/world/body identity");
                    if(!rig.body.hasSimulatedPhysics()||rig.body.boardingMotionPaused())continue;
                    rig.factoryActions.removeFirst();
                    rig.c.assertTrue(request.identity()>rig.lastFactoryAction,"one-shot test action cannot replay a consumed request");rig.lastFactoryAction=request.identity();actions++;
                    var receipt="FACTORY_END_PHASE identity="+request.identity()+" worldTime="+now+" testTick="+rig.c.getTick()+" body="+rig.body.getUuid()
                        +" simulated="+rig.body.hasSimulatedPhysics()+" paused="+rig.body.boardingMotionPaused()+" remainingPending="+rig.factoryActions.size();
                    rig.factoryReceipts.add(receipt);System.out.println("PLAYER_AA_"+receipt);
                    rig.runningFactoryAction=true;
                    try{request.action().run();}finally{rig.runningFactoryAction=false;}
                }catch(Throwable failure){
                    // Event callbacks must not crash the whole server outside
                    // GameTest's task boundary. The existing sole test driver
                    // rethrows this exact error on its next normal observation.
                    rig.factoryReceipts.add("FACTORY_PHASE_FAILURE worldTime="+now+" request="+request.identity()+" "+failure);
                    rig.factoryFailure=failure;try{rig.close();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}
                }
            }
        });
        ServerWorldEvents.UNLOAD.register((server,world)->{
            for(var rig:List.copyOf(FACTORY_RIGS))if(rig.w==world){rig.factoryActions.clear();FACTORY_RIGS.remove(rig);rig.factoryFailure=new IllegalStateException("Fixture world unloaded before pending factory action completed");}
        });
    }
    private static final class Rig implements AutoCloseable {
        final TestContext c;final ServerWorld w;final BlockPos at;final AiFixtureChunks chunks;
        final List<ServerPlayerEntity> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();
        final List<Entity> extras=new ArrayList<>();final List<BlockBodyEntity> bodies=new ArrayList<>();final List<Runnable> cleanup=new ArrayList<>();
        final Map<BlockPos,BlockState> previous=new LinkedHashMap<>();final NavigableMap<Long,List<Runnable>> due=new TreeMap<>();
        final ArrayDeque<FactoryAction> factoryActions=new ArrayDeque<>();final List<String> factoryReceipts=new ArrayList<>();
        long nextFactoryAction,lastFactoryAction;Throwable factoryFailure;int factoryOffset;
        boolean runningFactoryAction,factorySuccessPending;
        final ServerPlayerEntity p;BlockBodyEntity body;RocketDroneEntity shooter;ProjectileEntity missile;
        MountedDefenseClaims.Claim claim;Runnable observer=()->{};boolean closed;
        Rig(TestContext c,BlockPos at,AiFixtureChunks chunks,boolean fragment){
            this.c=c;w=c.getWorld();this.at=at;this.chunks=chunks;p=player("aa-guard");
            try{
            var cells=new ArrayList<BlockBodyEntity.Cell>();
            cells.add(new BlockBodyEntity.Cell(GUN,ModWorldBlocks.SHIP_LASER.getDefaultState().with(Properties.FACING,Direction.SOUTH)));
            cells.add(new BlockBodyEntity.Cell(LEVER,Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE,BlockFace.FLOOR).with(Properties.POWERED,true)));
            if(fragment){for(int x=-2;x<=1;x++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState()));}
            else for(int x=-4;x<=5;x++)for(int z=-4;z<=5;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,-1,z),Blocks.IRON_BLOCK.getDefaultState()));
            body=BlockBodyEntity.createLocal(w,cells,Vec3d.of(at),Quat.IDENTITY,Vec3d.ZERO,Vec3d.ZERO);body.setPhysicsGravity(false);bodies.add(body);
            c.assertTrue(use(p,body,GUN,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH)),"actual initial revisioned wrench USE claims this continuous material gun");
            claim=body.mountedWeapons().defenseClaims().get(GUN).orElseThrow();
            // Keep a second player's later real interaction ray clear. This is
            // preparation only; combatPosition ends movement before any shot.
            p.setPosition(Vec3d.ofCenter(at.add(10,5,-10)));
            }catch(Throwable failure){close();throw failure;}
        }
        ServerPlayerEntity player(String name){
            var client=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),name),false);
            var player=new ServerPlayerEntity(w.getServer(),w,client.gameProfile(),client.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);players.add(player);
            w.getServer().getPlayerManager().onPlayerConnect(connection,player,client);
            player.changeGameMode(GameMode.SURVIVAL);player.setNoGravity(true);player.getAbilities().flying=false;DescentPlayerData.get(player).setEnabled(false);
            player.setPosition(Vec3d.ofCenter(at.up(5)));c.assertTrue(!player.isCreative()&&w.getEntity(player.getUuid())==player,"actual loaded ordinary Survival principal");return player;
        }
        void aim(ServerPlayerEntity player,BlockBodyEntity target,BlockPos cell,Direction face){
            var local=Vec3d.ofCenter(cell).add(Vec3d.of(face.getVector()).multiply(.5));var point=target.localToWorld(local);
            var normal=target.rotation().rotate(new Vec3(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ()));var eye=point.add(normal.x()*3,normal.y()*3,normal.z()*3).add(0,face.getAxis().isHorizontal()?2.5:0,0);
            double h=player.getEyeY()-player.getY();player.setPosition(eye.subtract(0,h,0));player.setVelocity(Vec3d.ZERO);
            var d=point.subtract(player.getEyePos()).normalize();var q=PortalTransform.vectorToYawPitch(new Vec3(d.x,d.y,d.z));player.setYaw((float)q.yawDegrees());player.setPitch((float)q.pitchDegrees());player.setHeadYaw(player.getYaw());
            var selection=BodyBlockInteraction.target(player,player.getEyePos(),BodyBlockInteraction.viewDirection(player),player.getBlockInteractionRange());
            c.assertTrue(selection.isPresent()&&selection.get().body()==target&&selection.get().hit().localBlock().equals(cell),"actual requested material ray; actual="+selection);
        }
        boolean use(ServerPlayerEntity player,BlockBodyEntity target,BlockPos cell,ItemStack held){
            player.setStackInHand(Hand.MAIN_HAND,held);aim(player,target,cell,Direction.NORTH);
            return BodyBlockInteraction.receive(player,new BodyBlockInteraction.Edit(target.getUuid(),target.shapeRevision(),cell,BodyBlockInteraction.USE));
        }
        void after(int ticks,Runnable task){due.computeIfAbsent(c.getTick()+ticks,key->new ArrayList<>()).add(task);}
        void afterRealContactPhase(int offset,Runnable action){
            c.assertTrue(!closed&&factoryActions.size()<MAX_PENDING&&nextFactoryAction<8,"bounded fixture-local factory action admission");
            registerFactoryDispatcher();c.assertTrue(FACTORY_RIGS.contains(this)||FACTORY_RIGS.size()<MAX_FACTORY_RIGS,"bounded total factory fixture admission");
            FACTORY_RIGS.add(this);factoryOffset=offset;long now=w.getTime();factoryActions.addLast(new FactoryAction(++nextFactoryAction,body.getUuid(),now+1,now+32,action));
        }
        void combatPosition(){var center=body.localToWorld(Vec3d.ofCenter(GUN));p.setPosition(center.add(0,-p.getHeight()*.5,-6));p.setVelocity(Vec3d.ZERO);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);}
        ProjectileEntity fire(){return fire(false);}
        ProjectileEntity fire(boolean away){
            combatPosition();if(away)p.setPosition(body.localToWorld(Vec3d.ofCenter(GUN)).add(0,-p.getHeight()*.5,200));
            shooter=ModEntities.ROCKET.create(w);c.assertTrue(shooter!=null,"registered authored ROCKET");shooter.setAiDisabled(true);shooter.setPersistent();
            var from=MountedWeaponEmitter.muzzle(body,GUN).add(0,0,11);shooter.setPosition(from.subtract(0,shooter.getHeight()*.5,0));c.assertTrue(w.spawnEntity(shooter),"actual shooter admitted before its real action");extras.add(shooter);
            var before=new HashSet<UUID>();for(var entity:w.getEntitiesByClass(ProjectileEntity.class,new Box(at).expand(24),e->e.isAlive()))before.add(entity.getUuid());
            c.assertTrue(shooter.tryFireAt(p),"authored actual rocket weapon action");
            var fired=w.getEntitiesByClass(ProjectileEntity.class,new Box(at).expand(24),e->e.isAlive()&&!before.contains(e.getUuid())&&shooter.getUuid().equals(e.getOwnerUuid()));
            c.assertTrue(fired.size()==1&&fired.getFirst().isPointDefenseMissile(),"one actual typed owned warhead");missile=fired.getFirst();extras.add(missile);return missile;
        }
        WeaponCore.FireConfig config(){return MountedWeaponEmitter.config(body,body,GUN);}
        ProjectileEntity round(WeaponCore.FireConfig cfg){return WeaponCore.fireMountedDefenseProjectile(body,GUN,claim.principal(),claim.token(),cfg,missile.getUuid());}
        long rounds(){return w.getEntitiesByClass(ProjectileEntity.class,new Box(at).expand(64),s->s.isAlive()&&s.getOwnerEntity()==body).size();}
        void done(){close();if(runningFactoryAction)factorySuccessPending=true;else c.complete();}
        @Override public void close(){
            if(closed)return;closed=true;due.clear();factoryActions.clear();FACTORY_RIGS.remove(this);
            try{
                try{
                    if(!factoryReceipts.isEmpty()){
                        var folder=Path.of(System.getProperty("drmd.test.playerAaProofDir","reports/player-aa-proof"));Files.createDirectories(folder);
                        Files.writeString(folder.resolve("factory-phase-"+factoryOffset+".txt"),String.join("\n",factoryReceipts)+"\n",StandardOpenOption.CREATE_NEW);
                    }
                }finally{
                    for(var e:extras)e.discard();
                    for(var shot:w.getEntitiesByClass(ProjectileEntity.class,new Box(at).expand(256),e->body!=null&&e.getOwnerEntity()==body||e.pointDefenseSource()!=null&&e.pointDefenseSource().mounted()&&e.pointDefenseSource().principal().equals(p.getUuid())))shot.discard();
                    for(var b:BodySpatialIndex.query(w,new Box(at).expand(64))){BodyJointRegistry.get(w).removeBody(b.getUuid());b.discard();}
                    for(var action:cleanup)action.run();for(var entry:previous.entrySet())w.setBlockState(entry.getKey(),entry.getValue(),2);
                }
            }catch(IOException failure){throw new UncheckedIOException(failure);}
            finally{for(var player:players){w.getServer().getPlayerManager().remove(player);player.discard();}for(var channel:channels)channel.finishAndReleaseAll();chunks.close();}
        }
    }
    private static void run(TestContext c,int offset,boolean fragment,Consumer<Rig> start){
        run(c,offset,fragment,false,start);
    }
    private static void runFactory(TestContext c,int offset,Consumer<Rig> start){run(c,offset,false,true,start);}
    private static void run(TestContext c,int offset,boolean fragment,boolean afterContact,Consumer<Rig> start){
        var at=c.getAbsolutePos(new BlockPos(95000+offset*512,200,16000));var chunks=new AiFixtureChunks(c.getWorld(),at,2);Rig[] scene={null};int[] waited={0};
        c.runAtEveryTick(()->{var f=scene[0];try{
            if(f!=null&&f.factoryFailure!=null)throw propagate(f.factoryFailure);
            // Vanilla emits terminal listeners only when completion occurs
            // inside its own task tick. Relay successful END callbacks too;
            // cleanup already occurred at the actual successful action.
            if(f!=null&&f.factorySuccessPending){f.factorySuccessPending=false;c.complete();return;}
            if(f!=null&&f.closed)return;
            c.getWorld().resetIdleTimeout();
            if(f==null){if(!chunks.entityTicking(at.add(-8,0,-8),at.add(24,0,24))){c.assertTrue(++waited[0]<80,"PRECONDITION_UNOBSERVED: guard fixture obtains actual entity-ticking interest");return;}
                f=new Rig(c,at,chunks,fragment);scene[0]=f;
                if(afterContact){var ready=f;f.afterRealContactPhase(offset,()->start.accept(ready));}else start.accept(f);}
            if(f.closed)return;while(!f.due.isEmpty()&&f.due.firstKey()<=c.getTick()){var tasks=f.due.pollFirstEntry().getValue();for(var task:tasks)if(!f.closed)task.run();}if(!f.closed)f.observer.run();
        }catch(Throwable failure){System.out.println("PLAYER_AA_GUARD_FIRST_FAILURE offset="+offset+" "+failure);if(f!=null)f.close();else chunks.close();throw failure;}});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-own-placement",tickLimit=180)
    public void ownershipDoesNotSeizeAnotherPlayersGunOrConsumeOrdinaryBlockPlacement(TestContext c){run(c,0,false,f->{
        var other=f.player("aa-other");
        c.assertTrue(!f.use(other,f.body,GUN,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH)),"another authorized nearby Survival player cannot seize or release this claim");
        other.setPosition(Vec3d.ofCenter(f.at.add(-12,5,-12)));
        c.assertTrue(!f.body.canUseLocalBlock(GUN)&&!f.body.canUseLocalBlock(GUN,ItemStack.EMPTY)&&!f.body.canUseLocalBlock(GUN,new ItemStack(Blocks.IRON_BLOCK))
            &&f.body.canUseLocalBlock(GUN,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH)),"old hint/empty-hand/BlockItem semantics retained; only actual wrench gains a new USE hint");
        f.after(5,()->{
            f.p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Blocks.IRON_BLOCK));f.aim(f.p,f.body,GUN,Direction.SOUTH);
            c.assertTrue(BodyBlockInteraction.receive(f.p,new BodyBlockInteraction.Edit(f.body.getUuid(),f.body.shapeRevision(),GUN,BodyBlockInteraction.PLACE)),"ordinary revisioned BlockItem placement on a claimed gun remains real placement");
            c.assertTrue(f.body.getLocalBlock(GUN.south()).isOf(Blocks.IRON_BLOCK)&&f.p.getMainHandStack().isEmpty()&&f.body.mountedWeapons().defenseClaims().get(GUN).orElseThrow().equals(f.claim),"placement consumes one actual item and unrelated material change preserves exact claim token");
            f.after(5,()->{c.assertTrue(f.use(f.p,f.body,GUN,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH))&&f.body.mountedWeapons().defenseClaims().size()==0,"owner explicitly releases mode without a body/pose reset");f.done();});
        });
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-owner-modes",tickLimit=180)
    public void invalidOwnerModePausesWithoutOrdinaryFireAndSurvivalCanResume(TestContext c){run(c,1,false,f->{
        f.p.changeGameMode(GameMode.CREATIVE);long start=f.w.getTime();
        f.after(12,()->{
            c.assertTrue(f.rounds()==0&&f.body.mountedWeapons().defenseClaims().get(GUN).orElseThrow().equals(f.claim),"powered claimed gun does not fall through into ordinary fire during twelve invalid-owner world ticks");
            c.assertTrue(f.w.getTime()-start>=12,"actual time elapses during pause");f.p.changeGameMode(GameMode.SURVIVAL);f.fire();long launched=f.w.getTime();
            f.observer=()->{if(f.rounds()==0){c.assertTrue(f.w.getTime()-launched<8,"resumed actual Survival principal permits a real scheduled ENERGY round without cooldown replay");return;}
                f.p.changeGameMode(GameMode.SPECTATOR);c.assertTrue(f.round(f.config())==null,"Spectator cannot admit another round through the low-level entry");
                f.p.changeGameMode(GameMode.ADVENTURE);c.assertTrue(f.round(f.config())==null,"unreviewed Adventure cannot acquire Survival AA");
                f.p.changeGameMode(GameMode.SURVIVAL);f.p.kill();c.assertTrue(f.round(f.config())==null&&f.body.mountedWeapons().defenseClaims().get(GUN).orElseThrow().equals(f.claim),"real principal death pauses admission and preserves metadata");f.done();};
        });
    });}
    private static void exemptWarhead(TestContext c,int offset,boolean unknown){run(c,offset,false,f->{
        f.fire();
        if(unknown)f.shooter.discard();
        else {var scoreboard=f.w.getScoreboard();var team=scoreboard.addTeam("aa_"+UUID.randomUUID().toString().substring(0,8));f.cleanup.add(()->scoreboard.removeTeam(team));
            scoreboard.addScoreHolderToTeam(f.p.getNameForScoreboard(),team);scoreboard.addScoreHolderToTeam(f.shooter.getNameForScoreboard(),team);}
        final WeaponCore.HitContext[] hit={null};int[] callbacks={0};long started=f.w.getTime();
        f.missile.setOnHit(value->{callbacks[0]++;hit[0]=value;});
        f.observer=()->{if(hit[0]==null){c.assertTrue(f.w.getTime()-started<40,"actual exempt warhead must reach a real terminal outcome");return;}
            c.assertTrue(callbacks[0]==1&&hit[0].hitEntity()==f.body&&!f.missile.isAlive()&&f.rounds()==0,
                "actual friendly/unresolved warhead is not acquired, manager-deleted or safely disarmed; real hull payload remains: unknown="+unknown+" hit="+hit[0]+" rounds="+f.rounds());f.done();};
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-friendly-machine",tickLimit=180)
    public void actualTeammateMachineWarheadRetainsItsRealHullImpact(TestContext c){exemptWarhead(c,2,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-unresolved-owner",tickLimit=180)
    public void actualShooterRemovalDoesNotAuthorizeInterceptionOrEraseItsWarhead(TestContext c){exemptWarhead(c,3,true);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-factory-codec",tickLimit=180)
    public void lowLevelFactoryRejectsAlteredWeaponUnknownTargetAndRealMuzzleCover(TestContext c){runFactory(c,4,f->{
        f.fire();var valid=f.config();c.assertTrue(PointDefenseInterception.mayLaunchMounted(f.w,f.body,GUN,f.claim.principal(),f.claim.token(),valid,f.missile.getUuid()),"positive loaded real source/hostile/config admitted before negative controls");
        var wrong=f.config();wrong.dir=new Vec3d(1,0,0);c.assertTrue(f.round(wrong)==null,"factory cannot bend a physical barrel toward a side target");
        wrong=f.config();wrong.directDamage=80;c.assertTrue(f.round(wrong)==null,"AA cannot smuggle a buffed weapon config");
        c.assertTrue(WeaponCore.fireMountedDefenseProjectile(f.body,GUN,f.claim.principal(),UUID.randomUUID(),f.config(),f.missile.getUuid())==null
            &&WeaponCore.fireMountedDefenseProjectile(f.body,GUN,f.claim.principal(),f.claim.token(),f.config(),UUID.randomUUID())==null,"fabricated claim and unborn target grant no firing authority");
        c.assertTrue(f.body.setLocalBlock(GUN.south(),Blocks.IRON_BLOCK.getDefaultState())&&f.round(f.config())==null,"real own material covers the muzzle; no source-hull immunity");
        c.assertTrue(f.body.setLocalBlock(GUN.south(),Blocks.AIR.getDefaultState()),"remove actual obstruction");
        var saved=f.body.mountedWeapons().write(f.body);var legacy=saved.copy();legacy.remove("aaDefense");var old=new BodyMountedWeapons();old.read(f.body,legacy);
        c.assertTrue(old.defenseClaims().size()==0&&old.write(f.body).equals(legacy),"actual old mounted timer codec stays ordinary and byte-semantic");
        var raw=saved.getCompound("aaDefense").copy();raw.putInt("schema",71);var future=saved.copy();future.put("aaDefense",raw);old.read(f.body,future);
        c.assertTrue(old.defenseClaims().readOnly()&&old.defenseClaims().size()==0&&old.write(f.body).get("aaDefense").equals(raw)
            &&old.write(f.body).get("cooldowns").equals(saved.get("cooldowns")),"unsupported optional owner data stays opaque/inactive without corrupting legacy timers");
        var corrupt=saved.getCompound("aaDefense").copy();corrupt.getList("claims",NbtElement.COMPOUND_TYPE).getCompound(0).remove("principal");future.put("aaDefense",corrupt);old.read(f.body,future);
        c.assertTrue(old.defenseClaims().readOnly()&&old.defenseClaims().size()==0&&old.write(f.body).get("aaDefense").equals(corrupt)
            &&old.write(f.body).get("cooldowns").equals(saved.get("cooldowns")),"corrupt known-schema missing-owner data stays opaque and cannot mint an active claim");
        c.assertTrue(f.round(f.config())!=null,"removing real cover restores actual original weapon emission");f.done();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-ordinary-living",tickLimit=180)
    public void emittedLaserStillHitsRealLivingCoverWhenPrincipalBecomesInvalid(TestContext c){runFactory(c,5,f->{
        f.fire();var shot=f.round(f.config());c.assertTrue(shot!=null,"actual validated body-owned ENERGY bolt emitted");f.extras.add(shot);
        c.assertTrue(f.body.setLocalBlock(LEVER,f.body.getLocalBlock(LEVER).with(Properties.POWERED,false)),"turn actual material OFF after launch to isolate the already emitted round");
        HuskEntity target=EntityType.HUSK.create(f.w);c.assertTrue(target!=null,"ordinary living collision target");target.setAiDisabled(true);target.setNoGravity(true);
        var center=MountedWeaponEmitter.muzzle(f.body,GUN).add(0,0,2);target.setPosition(center.subtract(0,target.getHeight()*.5,0));c.assertTrue(f.w.spawnEntity(target),"real living cover enters after legitimate launch");f.extras.add(target);
        f.p.changeGameMode(GameMode.CREATIVE);long started=f.w.getTime();int[] hit={0};shot.setOnHit(value->{if(value.hitEntity()==target)hit[0]++;});
        f.observer=()->{if(hit[0]==0){c.assertTrue(f.w.getTime()-started<8,"already emitted original laser reaches normal living collision instead of erased/frozen/pass-through");return;}
            c.assertTrue(hit[0]==1&&!shot.isAlive()&&target.getHealth()<20&&target.getRecentDamageSource()!=null&&target.getRecentDamageSource().getSource()==f.body,
                "normal actual ENERGY damage/terminal collision retains the physical body owner despite invalid AA principal");f.done();};
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-fragment-file",tickLimit=200)
    public void actualGunFragmentPreservesOneClaimAndCooldownThroughARealSavedFile(TestContext c){run(c,6,true,f->{
        f.fire();long started=f.w.getTime();
        f.observer=()->{
            var timers=f.body.mountedWeapons().write(f.body).getList("cooldowns",NbtElement.COMPOUND_TYPE);if(timers.isEmpty()){c.assertTrue(f.w.getTime()-started<8,"natural admitted AA shot creates the ordinary material cooldown before cut");return;}
            var known=new HashSet<>(BodySpatialIndex.loaded(f.w));c.assertTrue(f.body.setLocalBlock(new BlockPos(1,0,0),Blocks.AIR.getDefaultState()),"actual bridge material is cut at commit boundary");
            var fragment=BodySpatialIndex.loaded(f.w).stream().filter(b->!known.contains(b)&&b.getLocalBlock(GUN).isOf(ModWorldBlocks.SHIP_LASER)).findFirst().orElseThrow();f.bodies.add(fragment);
            c.assertTrue(f.body.mountedWeapons().defenseClaims().size()==0&&fragment.mountedWeapons().defenseClaims().get(GUN).orElseThrow().equals(f.claim)
                &&fragment.mountedWeapons().write(fragment).getList("cooldowns",NbtElement.COMPOUND_TYPE).equals(timers),"one actual intact material fragment inherits the exact token/timer, former parent has no duplicate");
            var saved=fragment.writeNbt(new NbtCompound());var id=fragment.getUuid();var principal=f.claim.principal();var token=f.claim.token();
            final Path file;
            try{var folder=Path.of(System.getProperty("drmd.test.playerAaProofDir","reports/player-aa-proof"));Files.createDirectories(folder);file=folder.resolve("actual-aa-fragment.dat");NbtIo.writeCompressed(saved,file);}
            catch(IOException failure){throw new UncheckedIOException(failure);}
            fragment.discard();f.observer=()->{};
            f.after(2,()->{
                try{
                    var read=NbtIo.readCompressed(file,NbtSizeTracker.ofUnlimitedBytes());var restored=ModEntities.BLOCK_BODY.create(f.w);c.assertTrue(restored!=null,"actual registered restored body");restored.readNbt(read);
                    c.assertTrue(f.w.spawnEntity(restored)&&restored.getUuid().equals(id),"actual saved body respawns once after old material entity is removed");f.bodies.add(restored);
                    c.assertTrue(restored.mountedWeapons().defenseClaims().matches(restored,GUN,principal,token),"real compressed-file read restores exact player/material provenance");
                    var remaining=restored.mountedWeapons().write(restored).getList("cooldowns",NbtElement.COMPOUND_TYPE);
                    c.assertTrue(remaining.size()==1&&remaining.getCompound(0).getInt("remaining")<=timers.getCompound(0).getInt("remaining")-2,"real elapsed world ticks reduce, never reset or duplicate the material timer");
                    c.assertTrue(restored.setLocalBlock(GUN,Blocks.IRON_BLOCK.getDefaultState())&&restored.mountedWeapons().defenseClaims().size()==0,"actual gun replacement revokes provenance");f.done();
                }catch(IOException failure){throw new UncheckedIOException(failure);}
            });
        };
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-shared-round-cap",tickLimit=180)
    public void stationAndMountedSourcesShareTheSameReal256RoundAdmissionPool(TestContext c){runFactory(c,7,f->{
        f.fire();var anchor=f.at.add(2,0,18);f.previous.put(anchor,f.w.getBlockState(anchor));f.w.setBlockState(anchor,ModWorldBlocks.POINT_DEFENSE_TURRET.getDefaultState(),2);
        var from=PointDefenseInterception.muzzle(anchor);var direction=f.missile.getPos().subtract(from).normalize();
        for(int i=0;i<255;i++){
            var cfg=ProjectileFramework.config(ProjectileKind.KINETIC,null,from,direction);cfg.life=.5f;cfg.directDamage=4;cfg.inherit=0;
            var round=WeaponCore.fireAnchoredProjectile(f.w,anchor,cfg,f.missile.getUuid());c.assertTrue(round!=null,"real original station source fills existing pool entry "+i);f.extras.add(round);
        }
        var last=f.round(f.config());c.assertTrue(last!=null,"mounted original ENERGY source admits the last shared real slot");f.extras.add(last);
        var cfg=ProjectileFramework.config(ProjectileKind.KINETIC,null,from,direction);cfg.life=.5f;cfg.directDamage=4;cfg.inherit=0;
        c.assertTrue(PointDefenseInterception.stats(f.w).activeRounds()==256&&f.round(f.config())==null&&WeaponCore.fireAnchoredProjectile(f.w,anchor,cfg,f.missile.getUuid())==null,
            "both actual source factories refuse slot257; no duplicate256 banks or missile removal");f.done();
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-bounded-hold-expiry",tickLimit=220)
    public void realPostLaunchCrowdBudgetRefusesMovementWhileNormalLifetimeStillExpires(TestContext c){runFactory(c,8,f->{
        // Direct-factory layer: an actual loaded authored warhead flies AWAY.
        // No incoming segment/pose/velocity/fuse is stopped or rewritten. It
        // cannot detonate the real cover crowd during this outgoing-bolt guard.
        f.fire(true);var shot=f.round(f.config());c.assertTrue(shot!=null,"real original body-owned bolt is admitted before crowd appears");f.extras.add(shot);
        c.assertTrue(f.body.setLocalBlock(LEVER,f.body.getLocalBlock(LEVER).with(Properties.POWERED,false)),"actual source material is OFF after launch, not a fabricated controller pause");
        // Stand feet touch the real ballast top instead of starting0.1m inside
        // it. No depenetration impulse is part of this query-cap fixture.
        var point=shot.getPos().add(0,-.5,1.5);
        for(int i=0;i<33;i++){
            var stand=EntityType.ARMOR_STAND.create(f.w);c.assertTrue(stand!=null,"actual ordinary non-pushing living collider");stand.setNoGravity(true);stand.setPosition(point);
            c.assertTrue(f.w.spawnEntity(stand),"real post-launch collider admitted");f.extras.add(stand);
        }
        var position=shot.getPos();var velocity=shot.getVelocity();var incomingStart=f.missile.getPos();long started=f.w.getTime();long before=PointDefenseInterception.stats(f.w).mountedGeometryHolds();int[] hits={0};shot.setOnHit(value->hits[0]++);
        f.observer=()->{
            long elapsed=f.w.getTime()-started;
            if(shot.isAlive()){
                c.assertTrue(shot.getPos().squaredDistanceTo(position)<1e-16&&shot.getVelocity().squaredDistanceTo(velocity)<1e-16,
                    "33 real actor queries refuse bounded geometry without pass-through, fake contact or ordinary unbounded fallback");
                c.assertTrue(shot.remainingLifeTicks()<=80-elapsed,"normal remaining life continues during technical geometry holds");
                if(elapsed==3)c.assertTrue(f.missile.getPos().squaredDistanceTo(incomingStart)>9,"normal actual incoming weapon movement continues independently of outgoing technical hold");
                c.assertTrue(elapsed<=80,"unknown/budget pause cannot extend the original projectile lifetime");return;
            }
            c.assertTrue(hits[0]==0&&elapsed>=79&&elapsed<=81&&PointDefenseInterception.stats(f.w).mountedGeometryHolds()-before>=78,
                "actual normal80-tick expiry ends held bolt; no manager hit/discard or extra lifetime. elapsed="+elapsed+" stats="+PointDefenseInterception.stats(f.w));
            System.out.println("PLAYER_AA_TECHNICAL_HOLD_PASS elapsed="+elapsed+" holds="+(PointDefenseInterception.stats(f.w).mountedGeometryHolds()-before)+" incoming="+f.missile.getPos());f.done();
        };
    });}
}
