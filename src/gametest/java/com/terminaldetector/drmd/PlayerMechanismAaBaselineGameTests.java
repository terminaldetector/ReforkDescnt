package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.ai.HostileEnvironment;
import com.terminaldetector.drmd.client.portal.PortalTransform;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** First actual player-mechanism AA transaction. Existing public USE packets, an
 * authored drone weapon action and normal entity/contact phases own all effects.
 * Missing gun mode acceptance is recorded; only a real terminal outcome decides AA. */
public final class PlayerMechanismAaBaselineGameTests implements FabricGameTest {
    private static final BlockPos BEARING=BlockPos.ORIGIN,DRIVE=new BlockPos(-1,0,0),GUN=new BlockPos(1,0,0),LEVER=GUN.up();
    private static final int ADMISSION_TICKS=80,TRANSACTION_TICKS=40;

    private static final class Rig implements AutoCloseable {
        final TestContext c;final ServerWorld world;final boolean defended;final BlockPos base;
        final AiFixtureChunks chunks;final List<String> trace=new ArrayList<>();
        final Set<UUID> emitted=new LinkedHashSet<>();final Map<UUID,Vec3d> observed=new LinkedHashMap<>();
        final Map<UUID,Integer> observedAge=new LinkedHashMap<>();
        BlockBodyEntity root,tip;ServerPlayerEntity principal;EmbeddedChannel channel;RocketDroneEntity shooter;
        ProjectileEntity missile;WeaponCore.HitContext impact;
        Vec3d impactRoundPosition,impactRoundVelocity,impactMissilePosition,impactMissileVelocity;
        Vec3d launchedAt,principalAtLaunch;UUID linkId;long due,transactionAt,impactAt;
        int admission,phase,impactCount;boolean initialized,closed,claimRequestAccepted;

        Rig(TestContext c,boolean defended){
            this.c=c;world=c.getWorld();this.defended=defended;
            base=c.getAbsolutePos(new BlockPos(91520+(defended?0:512),200,12000));
            chunks=new AiFixtureChunks(world,base,2);record("CREATED production unchanged; runtime result not yet observed");
        }
        void prerequisite(boolean condition,String message){c.assertTrue(condition,"PRECONDITION_UNOBSERVED: "+message+"; "+state());}
        void record(String message){var line="testTick="+c.getTick()+" worldTime="+world.getTime()+" "+message;trace.add(line);System.out.println("PLAYER_MECHANISM_AA "+(defended?"DEFENDED ":"INERT ")+line);}
        BlockState bearing(Direction face){return ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(MechanicalBearingBlock.FACING,face);}
        void setup(){
            prerequisite(world.getDifficulty()!=Difficulty.PEACEFUL,"natural hostile actor needs non-Peaceful world");
            var data=ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(),defended?"aa-defended":"aa-inert"),false);
            principal=new ServerPlayerEntity(world.getServer(),world,data.gameProfile(),data.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);
            world.getServer().getPlayerManager().onPlayerConnect(connection,principal,data);
            principal.changeGameMode(GameMode.SURVIVAL);principal.setNoGravity(true);principal.getAbilities().flying=false;
            DescentPlayerData.get(principal).setEnabled(false);principal.setPosition(Vec3d.ofCenter(base.up(5)));
            principal.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.MECHANISM_WRENCH));
            prerequisite(!principal.isCreative()&&!principal.isSpectator()&&world.getEntity(principal.getUuid())==principal,
                "actual loaded ordinary Survival principal, not a permanently Creative mock");
            var a=new ArrayList<BlockBodyEntity.Cell>();var b=new ArrayList<BlockBodyEntity.Cell>();
            a.add(new BlockBodyEntity.Cell(BEARING,bearing(Direction.EAST)));
            a.add(new BlockBodyEntity.Cell(DRIVE,ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(MechanicalBearingBlock.FACING,Direction.EAST).with(Properties.POWER,0)));
            b.add(new BlockBodyEntity.Cell(BEARING,bearing(Direction.WEST)));
            b.add(new BlockBodyEntity.Cell(GUN,ModWorldBlocks.SHIP_LASER.getDefaultState().with(Properties.FACING,Direction.SOUTH)));
            b.add(new BlockBodyEntity.Cell(LEVER,Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE,BlockFace.FLOOR).with(Properties.POWERED,false)));
            // Real material ballast limits recoil without a hidden fixed carrier or pose reset.
            for(int x=-6;x<=-1;x++)for(int y=-2;y<=-1;y++)for(int z=-3;z<=3;z++)a.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
            for(int x=1;x<=6;x++)for(int y=-2;y<=-1;y++)for(int z=-3;z<=3;z++)b.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
            root=BlockBodyEntity.createLocal(world,a,Vec3d.of(base),Quat.IDENTITY,Vec3d.ZERO,Vec3d.ZERO);root.setPhysicsGravity(false);
            tip=BlockBodyEntity.createLocal(world,b,Vec3d.of(base.east()),Quat.IDENTITY,Vec3d.ZERO,Vec3d.ZERO);tip.setPhysicsGravity(false);
            shooter=ModEntities.ROCKET.create(world);prerequisite(shooter!=null,"registered ROCKET creates its actual authored actor");
            shooter.setAiDisabled(true);shooter.setPersistent();
            var launch=MountedWeaponEmitter.muzzle(tip,GUN).add(0,0,11);
            shooter.setPosition(launch.subtract(0,shooter.getHeight()*.5,0));
            prerequisite(shooter.getRole()==AiRole.ARTILLERY&&HostileEnvironment.isAlly(shooter)
                &&HostileEnvironment.isHostileTarget(shooter,principal)&&!shooter.isTeammate(principal)&&shooter.canTarget(principal),
                "existing real machine allegiance accepts this Survival enemy without a new faction rule");
            prerequisite(WorldEditSafety.loadedSegment(world,MountedWeaponEmitter.muzzle(tip,GUN),launch),"complete muzzle-to-launch collision halo is loaded");
            initialized=true;record("SETUP root="+root.getUuid()+" tip="+tip.getUuid()+" principal="+principal.getUuid()+" shooter="+shooter.getUuid()+" role="+shooter.getRole()+" energy="+shooter.getModEnergy());
            use(root,BEARING,true,true);phase=1;due=c.getTick()+5;
        }
        Vec3d centre(BlockBodyEntity body,BlockPos cell){
            var bounds=body.localBlockBounds().get(cell);prerequisite(bounds!=null,"real selectable material cell exists: "+cell);
            return body.localToWorld(new Vec3d(bounds.centre().x(),bounds.centre().y(),bounds.centre().z()));
        }
        boolean use(BlockBodyEntity body,BlockPos cell,boolean wrench,boolean required){
            prerequisite(missile==null,"all fixture pose/aim preparation completes before the combat transaction");
            principal.setStackInHand(Hand.MAIN_HAND,wrench?new ItemStack(ModWorldBlocks.MECHANISM_WRENCH):ItemStack.EMPTY);
            var point=centre(body,cell);
            // The gun's actual top lever occludes a vertical ray. Approach its
            // north face from above, keeping the player clear of real ballast.
            var eye=point.add(0,2.5,cell.equals(GUN)?-3:0);double eyeHeight=principal.getEyeY()-principal.getY();
            principal.setPosition(eye.subtract(0,eyeHeight,0));principal.setVelocity(Vec3d.ZERO);
            var d=point.subtract(principal.getEyePos()).normalize();var angle=PortalTransform.vectorToYawPitch(new Vec3(d.x,d.y,d.z));
            principal.setYaw((float)angle.yawDegrees());principal.setPitch((float)angle.pitchDegrees());principal.setHeadYaw(principal.getYaw());
            var selected=BodyBlockInteraction.target(principal,principal.getEyePos(),BodyBlockInteraction.viewDirection(principal),principal.getBlockInteractionRange());
            prerequisite(selected.isPresent()&&selected.get().body()==body&&selected.get().hit().localBlock().equals(cell),"normal nearest-material view ray selects requested USE cell "+cell+" actual="+selected);
            long revision=body.shapeRevision();boolean accepted=BodyBlockInteraction.receive(principal,new BodyBlockInteraction.Edit(body.getUuid(),revision,cell,BodyBlockInteraction.USE));
            record("USE body="+body.getUuid()+" cell="+cell+" revision="+revision+" wrench="+wrench+" accepted="+accepted+" ray="+selected.get().hit());
            if(required)prerequisite(accepted,"existing bearing/lever normal USE transaction accepted: "+cell);
            return accepted;
        }
        void launch(){
            var gun=centre(tip,GUN);var launch=shooter.getPos().add(0,shooter.getHeight()*.5,0);
            principal.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);principal.setPosition(gun.add(0,-principal.getHeight()*.5,-6));principal.setVelocity(Vec3d.ZERO);
            // Admit the real shooter before its actual weapon action, after
            // preparation. Ordinary unclaimed warm-up bolts must not alter the
            // launcher's velocity/energy before the measured transaction.
            prerequisite(world.spawnEntity(shooter)&&world.getEntity(shooter.getUuid())==shooter,"actual tracked rocket shooter is loaded before its weapon action");
            var joint=BodyJointRegistry.get(world).find(linkId);
            prerequisite(joint.isPresent()&&joint.get().blockDriven()&&root.isAlive()&&tip.isAlive(),"real separate material bearing/drive link remains live before launch");
            prerequisite(chunks.entityTicking(principal.getBlockPos(),shooter.getBlockPos(),root.getBlockPos(),tip.getBlockPos()),"all actual participants are entity-ticking");
            prerequisite(WorldEditSafety.loadedSegment(world,launch,principal.getBoundingBox().getCenter()),"real authored missile path has a loaded collision halo");
            if(!defended)prerequisite(!tip.getLocalBlock(LEVER).get(Properties.POWERED),"inert comparison has its actual lever switched OFF through USE");
            var before=new HashSet<UUID>();for(var p:world.getEntitiesByClass(ProjectileEntity.class,new Box(base).expand(24),p->p.isAlive()))before.add(p.getUuid());
            float energy=shooter.getModEnergy();boolean fired=shooter.tryFireAt(principal);
            var actual=world.getEntitiesByClass(ProjectileEntity.class,new Box(base).expand(24),p->p.isAlive()&&!before.contains(p.getUuid())&&p.getOwnerUuid()!=null&&p.getOwnerUuid().equals(shooter.getUuid()));
            prerequisite(fired&&actual.size()==1,"real ROCKET.tryFireAt emits exactly one actual owned missile: "+actual.size());
            missile=actual.getFirst();launchedAt=missile.getPos();transactionAt=world.getTime();principalAtLaunch=principal.getPos();
            var n=missile.writeNbt(new NbtCompound());var actor=shooter.writeNbt(new NbtCompound());
            prerequisite(missile.isPointDefenseMissile()&&missile.getOwnerEntity()==shooter&&missile.getDamageClass()==DamageClass.EXPLOSIVE
                &&missile.getMeshKind()==ProjectileEntity.MESH_ROCKET&&Math.abs(missile.getVelocity().length()-1.375)<1e-6
                &&Math.abs(n.getFloat("dmg")-40)<1e-6&&Math.abs(n.getFloat("splash")-30)<1e-6&&Math.abs(missile.splashRadius()-1.5)<1e-6
                &&n.getBoolean("blast")&&n.getInt("life")==80&&Math.abs(energy-shooter.getModEnergy()-20)<1e-6&&Math.abs(actor.getFloat("attackCooldown")-2.5)<1e-6,
                "authored ARTILLERY actual launch/damage/blast/life/energy/cooldown unchanged: missile="+n+" energyBefore="+energy+" after="+shooter.getModEnergy()+" actor="+actor);
            // DroneEntity.tryFireAt has no onHit callback. This observer records the
            // existing terminal path; it does not replace a payload or change state.
            missile.setOnHit(hit->{impactCount++;impact=hit;impactAt=world.getTime();impactMissilePosition=hit.projectile().getPos();impactMissileVelocity=hit.projectile().getVelocity();
                if(hit.hitEntity()!=null){impactRoundPosition=hit.hitEntity().getPos();impactRoundVelocity=hit.hitEntity().getVelocity();}
                record("TERMINAL "+state());});
            record("LAUNCH missile="+missile.getUuid()+" pos="+launchedAt+" velocity="+missile.getVelocity()+" stockNbt="+n+" claimUseAccepted="+claimRequestAccepted);
        }
        void observe(){
            for(var shot:world.getEntitiesByClass(ProjectileEntity.class,new Box(base).expand(48),p->p.isAlive()&&p.getOwnerEntity()==tip)){
                emitted.add(shot.getUuid());observed.put(shot.getUuid(),shot.getPos());observedAge.put(shot.getUuid(),shot.age);
            }
            record("FLIGHT "+state());
            if(impact!=null){
                c.assertTrue(impactCount==1,"actual incoming terminal callback occurs once: "+state());
                c.assertTrue(missile.age>=3&&launchedAt.squaredDistanceTo(impact.hitPos())>9,"actual missile ages and travels to material outcome, not fixture expiry: "+state());
                if(defended){
                    c.assertTrue(impact.hitEntity() instanceof ProjectileEntity,"player-built armed mechanism must physically intercept the authored rocket before ordinary material impact: "+state());
                    var round=(ProjectileEntity)impact.hitEntity();
                    c.assertTrue(claimRequestAccepted&&round.getOwnerEntity()==tip&&round.getDamageClass()==DamageClass.ENERGY
                        &&round.getMeshKind()==ProjectileEntity.MESH_BOLT&&emitted.contains(round.getUuid()),"explicit normal gun mode USE and actual observed body-owned original ENERGY bolt, not station/prefab/direct API: "+state());
                    c.assertTrue(observedAge.getOrDefault(round.getUuid(),0)>=1&&observed.get(round.getUuid()).squaredDistanceTo(MountedWeaponEmitter.muzzle(tip,GUN))>1,
                        "real outgoing bolt flies before the pair hit rather than manager-removing a missile");
                    c.assertTrue(impactRoundPosition!=null&&impactRoundVelocity!=null&&segmentDistanceSquared(impact.hitPos(),impactRoundPosition.subtract(impactRoundVelocity),impactRoundPosition.add(impactRoundVelocity))<.7*.7
                        &&segmentDistanceSquared(impact.hitPos(),impactMissilePosition.subtract(impactMissileVelocity),impactMissilePosition.add(impactMissileVelocity))<.7*.7,
                        "same actual callback point belongs to both real swept contact neighborhoods");
                    c.assertTrue(impact.hitPos().distanceTo(MountedWeaponEmitter.muzzle(tip,GUN))>missile.splashRadius()+.5,
                        "original live rocket payload detonates beyond its authored splash reach of protected gun");
                    c.assertTrue(principal.isAlive()&&tip.isAlive()&&tip.getLocalBlock(GUN).isOf(ModWorldBlocks.SHIP_LASER)&&!missile.isAlive(),
                        "real principal and protected gun survive physical interception without safe disarming");
                }else{
                    c.assertTrue(impact.hitEntity()==tip&&!(impact.hitEntity() instanceof ProjectileEntity),"inert control has actual physical hull impact, not timeout disappearance or invented pair contact: "+state());
                    c.assertTrue(!missile.isAlive(),"inert incoming rocket terminates on actual material contact");
                }
                record("PASS "+state());
                close();
                c.complete();return;
            }
            if(world.getTime()-transactionAt>=TRANSACTION_TICKS)c.assertTrue(false,"PRECONDITION_UNOBSERVED: actual missile must reach an observed terminal contact within 40 normal world ticks; "+state());
        }
        void step(){
            if(closed)return;world.resetIdleTimeout();
            if(!initialized){
                if(!chunks.entityTicking(base.add(-8,0,-8),base.add(10,0,16))){if(++admission<ADMISSION_TICKS)return;prerequisite(false,"entity-ticking fixture admission within80 ticks: "+chunks.readiness(base));}
                setup();return;
            }
            if(missile!=null){observe();return;}
            if(c.getTick()<due)return;
            switch(phase){
                case 1->{use(tip,BEARING,true,true);var links=BodyJointRegistry.get(world).forBody(tip.getUuid());prerequisite(links.size()==1&&links.getFirst().blockDriven(),"two public material wrench USEs create one real block-driven bearing");linkId=links.getFirst().id();phase=2;due=c.getTick()+5;}
                case 2->{if(defended)claimRequestAccepted=use(tip,GUN,true,false);record("CLAIM_REQUEST_RESULT accepted="+claimRequestAccepted+" absence alone is not decisive failure");phase=3;due=c.getTick()+5;}
                case 3->{use(tip,LEVER,false,true);prerequisite(tip.getLocalBlock(LEVER).get(Properties.POWERED),"real attached lever is ON after normal USE");phase=4;due=c.getTick()+5;}
                case 4->{if(!defended){use(tip,LEVER,false,true);prerequisite(!tip.getLocalBlock(LEVER).get(Properties.POWERED),"real comparison lever turns OFF");phase=5;due=c.getTick()+8;}else launch();}
                case 5->launch();
                default->prerequisite(false,"finite setup state machine");
            }
        }
        String state(){return "defended="+defended+" phase="+phase+" initialized="+initialized+" transactionAt="+transactionAt+" impactAt="+impactAt
            +" claimUse="+claimRequestAccepted+" playerAlive="+(principal!=null&&principal.isAlive())+" playerPosition="+(principal==null?"none":principal.getPos())+" launchPlayerPosition="+principalAtLaunch
            +" missile="+(missile==null?"none":missile.getUuid())+" missileAge="+(missile==null?-1:missile.age)+" missileAlive="+(missile!=null&&missile.isAlive())+" missilePos="+(missile==null?"none":missile.getPos())
            +" hitCount="+impactCount+" hitEntity="+(impact==null||impact.hitEntity()==null?"none":impact.hitEntity().getType()+"/"+impact.hitEntity().getUuid())+" point="+(impact==null?"none":impact.hitPos())
            +" emitted="+emitted+" tip="+(tip==null?"none":tip.getUuid())+" gun="+(tip==null?"none":tip.getLocalBlock(GUN))+" schedule="+BodyMountedWeapons.stats(world);}
        @Override public void close(){
            if(closed)return;closed=true;
            try{
                var folder=System.getProperty("drmd.test.playerAaProofDir");if(folder!=null)try{var destination=Path.of(folder);Files.createDirectories(destination);Files.writeString(destination.resolve(defended?"defended-trace.txt":"inert-trace.txt"),String.join("\n",trace)+"\n",StandardOpenOption.CREATE_NEW);}catch(IOException e){throw new UncheckedIOException(e);}
            }finally{
                try{
                    for(var shot:world.getEntitiesByClass(ProjectileEntity.class,new Box(base).expand(128),p->p==missile||tip!=null&&p.getOwnerEntity()==tip||root!=null&&p.getOwnerEntity()==root||shooter!=null&&p.getOwnerEntity()==shooter))shot.discard();
                    // This admitted remote volume belongs only to this fixture,
                    // including actual explosion-created fragments.
                    for(var body:BodySpatialIndex.query(world,new Box(base).expand(64))){BodyJointRegistry.get(world).removeBody(body.getUuid());body.discard();}
                    if(shooter!=null)shooter.discard();
                }finally{
                    try{if(principal!=null){world.getServer().getPlayerManager().remove(principal);principal.discard();}}
                    finally{if(channel!=null)channel.finishAndReleaseAll();chunks.close();}
                }
            }
        }
    }
    private static double segmentDistanceSquared(Vec3d point,Vec3d a,Vec3d b){var d=b.subtract(a);double t=d.lengthSquared()<1e-12?0:MathHelper.clamp(point.subtract(a).dotProduct(d)/d.lengthSquared(),0,1);return point.squaredDistanceTo(a.add(d.multiply(t)));}
    private static void run(TestContext c,boolean defended){var rig=new Rig(c,defended);c.runAtEveryTick(()->{if(rig.closed)return;try{rig.step();}catch(Throwable failure){rig.record("FIRST_FAILURE "+failure);rig.close();throw failure;}});}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-natural-defended",tickLimit=240)
    public void armedPlayerBuiltMechanismInterceptsTheRealRocketBeforeProtectedMaterial(TestContext c){run(c,true);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="player-aa-natural-inert",tickLimit=240)
    public void offLeverControlStillReceivesTheRealRocketOnItsPhysicalHull(TestContext c){run(c,false);}
}
