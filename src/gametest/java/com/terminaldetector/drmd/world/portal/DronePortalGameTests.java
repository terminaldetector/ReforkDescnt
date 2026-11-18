package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.D6PortalTransform;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.entity.model.FlightHullPose;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.lang.reflect.*;
import java.util.*;

/** Real registered actors and production portal transactions. No entity/physics ticks or motor-state proxies. */
public final class DronePortalGameTests implements FabricGameTest {
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private static final ChunkTicketType<UUID> TICKET=ChunkTicketType.create("drmd_drone_portal_fixture",Comparator.<UUID>naturalOrder());
    private static Vec3 p(Vec3d p){return new Vec3(p.x,p.y,p.z);}
    private static D6PortalTransform map(Vec3 a,Vec3 b){return D6PortalTransform.of(a,new Vec3(0,0,-1),b,new Vec3(0,1,0));}
    private static Quat pose(DroneEntity d){return FlightHullPose.physicalRotation(d.getYaw(),d.getPitch(),d.getFlightRoll());}
    private static void basis(TestContext c,Quat actual,Quat expected,String why){
        for(var axis:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)))
            c.assertTrue(actual.rotate(axis).minus(expected.rotate(axis)).lengthSquared()<1e-10,why+" axis="+axis+" actual="+actual+" expected="+expected);
    }
    private static NbtCompound combat(DroneEntity d){
        var all=d.writeNbt(new NbtCompound());var result=new NbtCompound();
        for(var key:List.of("role","Health","shield","armor","modEnergy","attackCooldown","aiPhase","aiPhaseTime"))if(all.contains(key))result.put(key,all.get(key).copy());
        return result;
    }
    private static void authoredState(DroneEntity d){
        var n=d.writeNbt(new NbtCompound());n.putFloat("modEnergy",67);n.putFloat("attackCooldown",.2f);n.putFloat("shield",31);n.putFloat("armor",7);n.putString("aiPhase","BREAK");n.putFloat("aiPhaseTime",.75f);d.readCustomDataFromNbt(n);
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Object invoke(Object object,String name,Class<?>[] types,Object...args)throws ReflectiveOperationException{var m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}
    @FunctionalInterface private interface Step{boolean tick(Rig r)throws Exception;}
    private static final class Rig implements AutoCloseable{
        final TestContext c;final ServerWorld w;final BlockPos at,exit;final UUID ticket=UUID.randomUUID();final Set<ChunkPos> columns=new LinkedHashSet<>();final List<Entity> entities=new ArrayList<>();int ticks;boolean closed;
        Rig(TestContext c){this.c=c;w=c.getWorld();at=c.getAbsolutePos(new BlockPos(8,130,8));exit=at.east(32);w.resetIdleTimeout();
            for(var centre:List.of(at,exit)){
                for(int x=(centre.getX()>>4)-1;x<=(centre.getX()>>4)+1;x++)for(int z=(centre.getZ()>>4)-1;z<=(centre.getZ()>>4)+1;z++){var q=new ChunkPos(x,z);if(columns.add(q))w.getChunkManager().addTicket(TICKET,q,2,ticket);}
                for(int x=(centre.getX()>>4)-3;x<=(centre.getX()>>4)+3;x++)for(int z=(centre.getZ()>>4)-3;z<=(centre.getZ()>>4)+3;z++)w.getChunk(x,z);
                air(centre,7,20,18);
            }
        }
        void air(BlockPos centre,int x,int y,int z){for(var q:BlockPos.iterate(centre.add(-x,-4,-z),centre.add(x,y,z)))w.setBlockState(q,Blocks.AIR.getDefaultState(),2);}
        <T extends Entity>T add(T e,Vec3d pos){c.assertTrue(e!=null,"registered fixture type exists");e.setPosition(pos);e.setNoGravity(true);if(e instanceof MobEntity mob)mob.setAiDisabled(true);c.assertTrue(w.spawnEntity(e),"actual registered entity spawn");entities.add(e);return e;}
        boolean admitted(){for(var e:entities)if(w.getEntity(e.getUuid())!=e||!w.shouldTickEntity(e.getBlockPos())||e.age<1)return false;return true;}
        void drive(Step step){c.runAtEveryTick(()->{if(closed)return;try{w.resetIdleTimeout();ticks++;if(!admitted()){c.assertTrue(ticks<80,"real UUID lookup/entity ticking and one ordinary actor tick before portal: "+ticks);return;}if(step.tick(this)){close();c.complete();}}catch(Exception|AssertionError failure){close();if(failure instanceof RuntimeException runtime)throw runtime;throw new RuntimeException(failure);}});}
        void crossing(DroneEntity d,int level){var a=Vec3d.ofCenter(at).add(0,level,-.5);d.setPosition(a.add(0,0,.2));d.prevX=d.getX();d.prevY=d.getY();d.prevZ=a.z-.2;}
        PortalTransfer.Plan plan(DroneEntity d){var t=map(p(Vec3d.ofCenter(at)),p(Vec3d.ofCenter(exit)));return PortalTransfer.prepare(w,d,t,ZERO,t.sourcePoint(),16,false,w.getServer().getTicks(),u->false,false).accepted();}
        @Override public void close(){if(closed)return;closed=true;for(var e:entities)if(!e.isRemoved())e.discard();for(var q:columns)w.getChunkManager().removeTicket(TICKET,q,2,ticket);PortalTravel.clear();}
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-portal-four-hulls",tickLimit=120)
    public void allFourLiveKindsCarryFullBankedAndVerticalBasisVelocityTargetAndCombatState(TestContext c){
        var r=new Rig(c);var types=List.of(ModEntities.ORB,ModEntities.ROCKET,ModEntities.JET,ModEntities.TELEPORT_FLYER);var drones=new ArrayList<DroneEntity>();
        for(int i=0;i<types.size();i++)drones.add(r.add((DroneEntity)types.get(i).create(r.w),Vec3d.ofCenter(r.at).add(-4+i*3,4,0)));
        var cow=r.add(EntityType.COW.create(r.w),Vec3d.ofCenter(r.at).add(-6,0,4));
        r.drive(x->{var expected=new HashMap<UUID,Quat>();var velocity=new HashMap<UUID,Vec3>();var state=new HashMap<UUID,NbtCompound>();
            for(int i=0;i<drones.size();i++){var d=drones.get(i);authoredState(d);d.setTarget(cow);r.crossing(d,i*4);d.setYaw(28+i*13);d.setPitch(i==2?-90:17-i*11);d.setFlightRoll(37-i*9);d.setHeadYaw(d.getYaw()+23);d.setVelocity(.12,-.08,.2);
                var t=map(p(Vec3d.ofCenter(r.at).add(0,0,-.5)),p(Vec3d.ofCenter(r.exit).add(0,.5,0)));expected.put(d.getUuid(),t.transformOrientation(pose(d)));velocity.put(d.getUuid(),t.transformVelocity(p(d.getVelocity())));state.put(d.getUuid(),combat(d));}
            PortalTravel.carry(r.w,r.at,Direction.NORTH,r.exit,Direction.UP,16);
            for(var d:drones){c.assertTrue(d.portalFrameRevision()==1&&r.w.getEntity(d.getUuid())==d&&d.getX()>r.exit.getX()-8,"actual admitted same living hull crosses once: "+d.getType());basis(c,pose(d),expected.get(d.getUuid()),"all three transported physical axes "+d.getType());
                c.assertTrue(p(d.getVelocity()).minus(velocity.get(d.getUuid())).lengthSquared()<1e-12&&d.getTarget()==cow&&combat(d).equals(state.get(d.getUuid())),"one velocity transform retains actual target/role/health/energy/cooldown/phase "+d.getType());
                c.assertTrue(d.prevX==d.getX()&&d.prevY==d.getY()&&d.prevZ==d.getZ()&&d.prevYaw==d.getYaw()&&d.prevPitch==d.getPitch()&&d.prevHeadYaw==d.getHeadYaw(),"only display history snaps at discontinuity");}
            return true;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-portal-refusal",tickLimit=120)
    public void changedBankAndLateRealExitStoneRefuseWithoutConsumingAnyActorState(TestContext c){
        var r=new Rig(c);var d=r.add(ModEntities.JET.create(r.w),Vec3d.ofCenter(r.at));
        r.drive(x->{authoredState(d);d.setYaw(33);d.setPitch(-21);d.setFlightRoll(37);d.setHeadYaw(79);d.setVelocity(.12,.06,.2);var first=r.plan(d);c.assertTrue(first!=null,"actual clear prepared exit");d.setFlightRoll(38);var before=d.getPos();var v=d.getVelocity();var saved=combat(d);
            c.assertTrue(!first.apply()&&d.portalFrameRevision()==0&&d.getPos().equals(before)&&d.getVelocity().equals(v),"late auxiliary bank change rejects before generic mutation");
            var second=r.plan(d);c.assertTrue(second!=null,"bank change can be prepared normally");var destination=second.transform.transformPoint(p(d.getPos()));var stone=BlockPos.ofFloored(destination.x(),destination.y()+d.getHeight()*.5,destination.z());r.w.setBlockState(stone,Blocks.STONE.getDefaultState(),2);
            c.assertTrue(!second.apply()&&r.plan(d)==null&&d.portalFrameRevision()==0&&d.getPos().equals(before)&&d.getVelocity().equals(v)&&d.getFlightRoll()==38&&combat(d).equals(saved),"late real material refuses whole drone transaction without resetting combat/motor state");r.w.setBlockState(stone,Blocks.AIR.getDefaultState(),2);
            var accepted=r.plan(d);c.assertTrue(accepted!=null&&accepted.apply()&&!accepted.apply()&&d.portalFrameRevision()==1,"refusal consumes no future transfer and accepted plan is single-use");
            var head=accepted.transform.rotation().rotate(p(Vec3d.fromPolar(-21,79)));float expected=(float)Math.toDegrees(Math.atan2(-head.x(),head.z()));c.assertTrue(Math.abs(MathHelper.wrapDegrees(d.getHeadYaw()-expected))<1e-4,"server retains independently transformed head direction rather than replacing it with hull yaw");return true;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-portal-source-orders",tickLimit=160)
    public void actualQueuedChargeAndWarningAndExistingRouteRetireWithoutOldEndpointSnapOrResourceReset(TestContext c){
        var r=new Rig(c);var queued=r.add(ModEntities.TELEPORT_FLYER.create(r.w),Vec3d.ofCenter(r.at).add(-3,3,0));var warned=r.add(ModEntities.TELEPORT_FLYER.create(r.w),Vec3d.ofCenter(r.at).add(3,3,0));var cow=r.add(EntityType.COW.create(r.w),Vec3d.ofCenter(r.at).add(0,3,6));int[] stage={0};long[] crossed={0};Object[] nav={null};Vec3d[] oldMarker={null};
        r.drive(x->{if(stage[0]==0){warned.setAiDisabled(false);warned.setTarget(cow);c.assertTrue(warned.requestCharge()&&warned.ability().phase()==TeleportAbilityState.Phase.START_PENDING,"real eligible target queues production START without manual phase tick");stage[0]=1;return false;}
            if(stage[0]==1){if(warned.ability().phase()!=TeleportAbilityState.Phase.WARN){c.assertTrue(r.ticks<100,"normal END relay admits actual WARN: "+warned.ability().phase());return false;}oldMarker[0]=warned.lockedDestination();c.assertTrue(oldMarker[0]!=null&&warned.teleportCount()==0,"real old-pose admitted endpoint exists");
                queued.setAiDisabled(false);queued.setTarget(cow);c.assertTrue(queued.requestCharge()&&queued.ability().phase()==TeleportAbilityState.Phase.START_PENDING,"second real START queued in current world tick");var submitted=TeleportAbilityRelay.stats(r.w);c.assertTrue(submitted.pending()>0,"actual pending relay request precedes crossing");
                var type=Class.forName("com.terminaldetector.drmd.ai.DroneNavigation");var ctor=type.getDeclaredConstructor(DroneEntity.class);ctor.setAccessible(true);nav[0]=ctor.newInstance(warned);var oldOrigin=warned.getPos();invoke(nav[0],"occluded",new Class[]{Vec3d.class},cow.getEyePos());invoke(nav[0],"steer",new Class[]{Vec3d.class,Vec3d.class,boolean.class},new Vec3d(.1,0,0),oldOrigin.add(0,0,20),true);
                c.assertTrue(field(nav[0],"search")!=null&&field(nav[0],"space")!=null,"actual existing bounded route search and origin cache admitted before transfer");
                for(var d:List.of(queued,warned)){var resource=combat(d);var plan=r.plan(d);c.assertTrue(plan!=null&&plan.apply(),"live teleporter normal portal transaction");c.assertTrue(d.ability().phase()==TeleportAbilityState.Phase.RECOVERY&&d.lockedDestination()==null&&d.teleportCount()==0&&d.getTarget()==cow&&combat(d).equals(resource),"portal retires only old-pose pending ability, not target/weapon/resources");}
                c.assertTrue(TeleportAbilityRelay.stats(r.w).pending()<submitted.pending()&&TeleportAbilityRelay.stats(r.w).stale()>submitted.stale(),"same actor pending UUID request actually removed rather than replayed at END");
                invoke(nav[0],"occluded",new Class[]{Vec3d.class},cow.getEyePos());c.assertTrue(field(nav[0],"search")==null&&field(nav[0],"space")==null&&((List<?>)field(nav[0],"route")).isEmpty()&&field(nav[0],"visibilityOrigin").equals(warned.getPos())&&!warned.getPos().equals(oldOrigin),"existing navigation retires old search/route/space and samples sight from actual new position");crossed[0]=r.w.getTime();stage[0]=2;return false;
            }
            c.assertTrue(queued.portalFrameRevision()==1&&warned.portalFrameRevision()==1&&queued.teleportCount()==0&&warned.teleportCount()==0&&queued.lockedDestination()==null&&warned.lockedDestination()==null,"old START/WARN cannot later snap back to source endpoint");return r.w.getTime()-crossed[0]>=8;
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-portal-normal-wire",tickLimit=120)
    public void normalDronePositionPoseVelocityAndStatusPrecedeItsActualPassengersNativeTeleport(TestContext c){
        var r=new Rig(c);var d=r.add(ModEntities.ORB.create(r.w),Vec3d.ofCenter(r.at));
        var client=net.minecraft.server.network.ConnectedClientData.createDefault(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"drone-portal-wire"),false);var player=new net.minecraft.server.network.ServerPlayerEntity(r.w.getServer(),r.w,client.gameProfile(),client.syncedOptions());var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);r.w.getServer().getPlayerManager().onPlayerConnect(connection,player,client);r.entities.add(player);player.setNoGravity(true);DescentPlayerData.get(player).setEnabled(false);player.setPosition(d.getPos());
        r.drive(x->{try{d.setFlightRoll(37);d.setVelocity(.12,.08,.2);c.assertTrue(player.startRiding(d,true),"actual online native passenger rides actual drone");player.setPosition(d.getPos().add(0,d.getHeight(),0));
                for(int i=0;i<4096&&!channel.outboundMessages().isEmpty();i++)io.netty.util.ReferenceCountUtil.release(channel.readOutbound());c.assertTrue(channel.outboundMessages().isEmpty(),"actual setup packet backlog bounded");
                var plan=r.plan(d);c.assertTrue(plan!=null&&plan.members.contains(player)&&plan.apply(),"whole live drone and passenger transaction");channel.runPendingTasks();int index=0,position=-1,pose=-1,velocity=-1,status=-1,teleport=-1;
                for(Object raw:channel.outboundMessages()){
                    if(raw instanceof net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket packet&&packet.getEntityId()==d.getId())position=index;
                    if(raw instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket packet&&packet.id()==d.getId())for(var value:packet.trackedValues())if(value.value() instanceof Float bank&&bank.equals(d.getFlightRoll()))pose=index;
                    if(raw instanceof net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket packet&&packet.getEntityId()==d.getId())velocity=index;
                    if(raw instanceof net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket packet&&packet.getEntity(r.w)==d&&packet.getStatus()==DroneEntity.PORTAL_DISCONTINUITY_STATUS)status=index;
                    if(raw instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket)teleport=index;index++;
                }
                c.assertTrue(position>=0&&pose>position&&velocity>pose&&status>velocity&&teleport>status,"normal ordered pose/velocity/status124 precede carried-player absolute teleport: "+List.of(position,pose,velocity,status,teleport));c.assertTrue(d.getDataTracker().isDirty(),"explicit normal snapshot does not consume ordinary tracker dirtiness");return true;
            }finally{player.stopRiding();r.w.getServer().getPlayerManager().remove(player);player.discard();channel.finishAndReleaseAll();}
        });
    }
}
