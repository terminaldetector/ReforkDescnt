package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.D6PortalTransform;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.inventory.*;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.bombardment.*;
import com.terminaldetector.drmd.world.portal.PortalTravel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.*;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Registered server item/control routes and ordinary entity clocks. Native keys/images/cold process are separate. */
public final class ProgrammedClusterGameTests implements FabricGameTest {
    private static final class Rig implements AutoCloseable {
        final TestContext c;final net.minecraft.server.world.ServerWorld w;final BlockPos at;final ServerPlayerEntity p;final AiFixtureChunks tickets;final io.netty.channel.embedded.EmbeddedChannel channel;final net.minecraft.network.ClientConnection connection;
        final List<Entity> extra=new ArrayList<>();final List<Runnable> cleanup=new ArrayList<>();
        boolean closed,ready;int ticks=-1,total;
        Rig(TestContext c){this.c=c;w=c.getWorld();at=c.getAbsolutePos(new BlockPos(8,140,8));tickets=new AiFixtureChunks(w,at,2);
            for(var pos:BlockPos.iterate(at.add(-8,-8,-8),at.add(8,8,32)))w.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
            var data=ConnectedClientData.createDefault(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"cluster-order"),false);p=new ServerPlayerEntity(w.getServer(),w,data.gameProfile(),data.syncedOptions());connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);channel=new io.netty.channel.embedded.EmbeddedChannel(connection);w.getServer().getPlayerManager().onPlayerConnect(connection,p,data);
            try{
                channel.runPendingTasks();channel.flushOutbound();int confirmations=0;
                for(var packet:List.copyOf(channel.outboundMessages()))if(packet instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket teleport){channel.writeInbound(new net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket(teleport.getTeleportId()));confirmations++;}
                c.assertTrue(confirmations>0,"actual initial native teleport packet observed and acknowledged through ordinary inbound connection");
                var pending=ServerPlayNetworkHandler.class.getDeclaredField("requestedTeleportPos");pending.setAccessible(true);c.assertTrue(pending.get(p.networkHandler)==null,"real TeleportConfirm handling clears pending position before fixture setup; no20tick spawn retry");
                p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.setNoGravity(true);p.setYaw(0);p.setPitch(0);p.setPosition(Vec3d.ofCenter(at));p.setVelocity(Vec3d.ZERO);DescentPlayerData.get(p).setEnabled(false);
                c.assertTrue(!w.getServer().getNetworkIo().getConnections().contains(connection),"fixture connection is not double-scheduled");w.getServer().getNetworkIo().getConnections().add(connection);
            }catch(ReflectiveOperationException failure){close();throw new RuntimeException(failure);}catch(RuntimeException|Error failure){close();throw failure;}
        }
        List<AerialBombEntity> bombs(){return w.getEntitiesByClass(AerialBombEntity.class,new Box(at).expand(192),e->{var n=e.writeNbt(new NbtCompound());return n.containsUuid("owner")&&n.getUuid("owner").equals(p.getUuid());});}
        List<AerialBombEntity> carriers(){return bombs().stream().filter(e->!e.writeNbt(new NbtCompound()).getBoolean("sub")).toList();}
        void stack(Item item,ClusterProgram.Pattern pattern){var s=new ItemStack(item,2);var n=new NbtCompound();n.put(ClusterFlight.ITEM_KEY,ClusterFlight.writeProgram(ClusterProgram.preset(pattern)));s.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));p.setStackInHand(Hand.MAIN_HAND,s);}
        void fire(Item item,ClusterProgram.Pattern pattern){stack(item,pattern);var result=((BombardmentItems.BombBayItem)item).useBank(w,p,Hand.MAIN_HAND,0);c.assertTrue(result.getResult().isAccepted(),"registered existing cluster item emits actual carrier");}
        void drive(IntConsumer action){c.runAtEveryTick(()->{if(closed)return;try{w.resetIdleTimeout();c.assertTrue(++total<220,"bounded ordinary cluster fixture");if(!ready){if(w.getEntity(p.getUuid())!=p||!tickets.entityTicking(at)||p.age<1){c.assertTrue(total<80,"real owner registration and entity-ticking admission");return;}ready=true;c.assertTrue(!p.isCreative()&&!p.isSpectator()&&p.getAbilities().allowModifyWorld,"actual noncreative Survival principal and modify permission");}action.accept(++ticks);}catch(RuntimeException|Error error){close();throw error;}});}
        void done(){close();c.complete();}
        public void close(){if(closed)return;closed=true;try{w.getServer().getNetworkIo().getConnections().remove(connection);extra.forEach(Entity::discard);cleanup.forEach(Runnable::run);bombs().forEach(Entity::discard);DroneArsenal.clear(p.getUuid());DescentPlayerData.get(p).setEnabled(false);w.getServer().getPlayerManager().remove(p);p.discard();}finally{channel.finishAndReleaseAll();tickets.close();PortalTravel.clear();}}
    }
    private static Vec3 vec(NbtCompound n,String field){var v=n.getCompound(field);return new Vec3(v.getDouble("x"),v.getDouble("y"),v.getDouble("z"));}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="cluster-program-real-input",tickLimit=140)
    public void realPrimaryAndSecondaryBanksFireForwardAndRolledDownwardWithoutFreeAmmoOrHeldSpam(TestContext c){
        var r=new Rig(c);UUID[] first={null};
        r.drive(t->{if(t==0){r.p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.BOMB_CLUSTER,3));r.p.setSneaking(true);ModItems.BOMB_CLUSTER.use(r.w,r.p,Hand.MAIN_HAND);c.assertTrue(r.p.getMainHandStack().getCount()==3&&r.carriers().isEmpty(),"program selection does not consume ammo or create a round");c.assertTrue(r.p.getMainHandStack().get(DataComponentTypes.CUSTOM_DATA).copyNbt().getCompound(ClusterFlight.ITEM_KEY).getString("pattern").equals("RING"),"real sneak-use saves selected ring program");r.p.setSneaking(false);}
            if(t==5){DroneArsenal.receive(r.p,1);DroneArsenal.receive(r.p,0);DroneArsenal.tick(r.p);var rounds=r.carriers();c.assertTrue(rounds.size()==1&&r.p.getMainHandStack().getCount()==2,"one actual receiver→primary→registered item transaction debits one Survival round");var b=rounds.getFirst();first[0]=b.getUuid();c.assertTrue(b.getVelocity().squaredDistanceTo(new Vec3d(0,0,.9))<1e-12&&b.writeNbt(new NbtCompound()).getCompound(ClusterFlight.KEY).getCompound("program").getString("pattern").equals("RING"),"forward carrier retains real selected program and muzzle direction");for(int i=0;i<12;i++){DroneArsenal.receive(r.p,1);DroneArsenal.tick(r.p);}c.assertTrue(r.carriers().size()==1&&r.p.getMainHandStack().getCount()==2,"same-tick held/repeated controls cannot bypass actual item cooldown");DroneArsenal.receive(r.p,0);}
            if(t==17){var data=DescentPlayerData.get(r.p);data.setEnabled(true);data.setShipAttitude(0,1,0,1,0,0);data.setFlightVelocity(new Vec3d(20,0,0));r.p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);var ammo=new ItemStack(ModItems.BOMB_HEAVY_CLUSTER);DroneLoadout.of(r.p).setStack(1,ammo);DroneArsenal.receive(r.p,2);DroneArsenal.receive(r.p,0);DroneArsenal.tick(r.p);var born=r.carriers().stream().filter(b->!b.getUuid().equals(first[0])).toList();c.assertTrue(born.size()==1&&ammo.isEmpty()&&r.p.getMainHandStack().isEmpty(),"actual secondary socket works with empty main hand and consumes its own item");c.assertTrue(born.getFirst().getVelocity().squaredDistanceTo(new Vec3d(.55,.65,0))<1e-12,"rolled downward ejection inherits20m/s as1block/tick exactly once");var saved=born.getFirst().writeNbt(new NbtCompound());c.assertTrue(saved.getDouble("DownX")==-1&&vec(saved.getCompound(ClusterFlight.KEY),"forward").minus(new Vec3(-1,0,0)).lengthSquared()<1e-12,"program captures real full6DoF down and fan frame, not worldY");r.done();}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="cluster-program-natural-stages",tickLimit=180)
    public void realSevenAndFourteenTimedStagesSurviveActualMidflightSaveRestoreWithoutRepeatingChildren(TestContext c){
        var r=new Rig(c);var seen=new HashSet<UUID>();var births=new EnumMap<OrdnanceType,Map<Integer,Integer>>(OrdnanceType.class);var fuseIds=new EnumMap<OrdnanceType,Set<Integer>>(OrdnanceType.class);NbtCompound[] saved={null};UUID[] identity={null};boolean[] restored={false};
        r.drive(t->{if(t==0){r.p.setPosition(Vec3d.ofCenter(r.at).add(-4,0,0));r.fire(ModItems.BOMB_CLUSTER,ClusterProgram.Pattern.STAGGER);r.p.setPosition(Vec3d.ofCenter(r.at).add(4,0,0));r.fire(ModItems.BOMB_HEAVY_CLUSTER,ClusterProgram.Pattern.STAGGER);}
            for(var b:r.bombs()){var n=b.writeNbt(new NbtCompound());if(!n.getBoolean("sub")||!seen.add(b.getUuid()))continue;c.assertTrue(!n.contains(ClusterFlight.KEY)&&n.getInt("ClusterChildFuse")>=60&&n.getInt("ClusterChildFuse")<=86&&n.getUuid("owner").equals(r.p.getUuid()),"real child inherits original owner/type/fuse without another carrier program");births.computeIfAbsent(b.getOrdnance(),k->new LinkedHashMap<>()).merge(t,1,Integer::sum);c.assertTrue(fuseIds.computeIfAbsent(b.getOrdnance(),k->new HashSet<>()).add(n.getInt("ClusterChildFuse")),"saved stage never repeats an already born ordinal");}
            if(saved[0]==null&&fuseIds.getOrDefault(OrdnanceType.CLUSTER,Set.of()).size()==2){var parent=r.carriers().stream().filter(b->b.getOrdnance()==OrdnanceType.CLUSTER).findFirst().orElseThrow();saved[0]=parent.writeNbt(new NbtCompound());identity[0]=parent.getUuid();c.assertTrue(saved[0].getCompound(ClusterFlight.KEY).getInt("stage")==1&&saved[0].getInt("FlightAge")>=16,"actual first wave advances saved carrier stage and flight clock");parent.discard();return;}
            if(saved[0]!=null&&!restored[0]&&r.w.getEntity(identity[0])==null){var parent=ModEntities.AERIAL_BOMB.create(r.w);parent.readNbt(saved[0]);var before=saved[0].getCompound(ClusterFlight.KEY);var after=parent.writeNbt(new NbtCompound()).getCompound(ClusterFlight.KEY);System.out.println("CLUSTER_RESTORE uuid="+parent.getUuid()+" expected="+identity[0]+" before="+before+" after="+after);c.assertTrue(parent.getUuid().equals(identity[0]),"restore retains real original UUID");c.assertTrue(after.equals(before),"strict entire original stage/frame/travel/arm/program NBT retained without normalization drift");c.assertTrue(r.w.spawnEntity(parent),"sameUUID restored carrier admitted only after prior live identity leaves actual world");restored[0]=true;}
            if(t==42){c.assertTrue(restored[0]&&fuseIds.getOrDefault(OrdnanceType.CLUSTER,Set.of()).size()==7&&fuseIds.getOrDefault(OrdnanceType.HEAVY_CLUSTER,Set.of()).size()==14&&seen.size()==21&&r.carriers().isEmpty(),"exact7/14once-only real children after normal stagger clocks and same-process save/restore: "+births);for(var type:List.of(OrdnanceType.CLUSTER,OrdnanceType.HEAVY_CLUSTER)){var waves=births.get(type);c.assertTrue(waves.size()==3,"three spatially separate actual birth times "+waves);var times=new ArrayList<>(waves.keySet());c.assertTrue(times.get(1)-times.get(0)>=4&&times.get(2)-times.get(1)>=4,"normal stage cadence retained across restored pause "+waves);}c.assertTrue(r.bombs().stream().filter(b->b.writeNbt(new NbtCompound()).getBoolean("sub")).anyMatch(b->b.age>2&&b.getVelocity().lengthSquared()>1e-4),"real children actually move under ordinary entity integration");r.done();}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="cluster-program-refusal",tickLimit=140)
    public void permissionsFutureProgramsAndOwnerDefuseRefuseAuthorityWithoutAmmoOrRecursiveFallback(TestContext c){
        var r=new Rig(c);AerialBombEntity[] future={null};NbtElement[] raw={null};
        r.drive(t->{if(t==0){r.stack(ModItems.BOMB_CLUSTER,ClusterProgram.Pattern.FAN);r.p.getAbilities().allowModifyWorld=false;var failed=ModItems.BOMB_CLUSTER.use(r.w,r.p,Hand.MAIN_HAND);c.assertTrue(!failed.getResult().isAccepted()&&r.p.getMainHandStack().getCount()==2&&r.bombs().isEmpty(),"real permission refusal creates no round and spends no ammo");r.p.getAbilities().allowModifyWorld=true;
                var data=new NbtCompound();raw[0]=NbtString.of("future37-not-ours");data.put(ClusterFlight.ITEM_KEY,raw[0].copy());r.p.getMainHandStack().set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));failed=ModItems.BOMB_CLUSTER.use(r.w,r.p,Hand.MAIN_HAND);c.assertTrue(!failed.getResult().isAccepted()&&r.p.getMainHandStack().get(DataComponentTypes.CUSTOM_DATA).copyNbt().get(ClusterFlight.ITEM_KEY).equals(raw[0])&&r.bombs().isEmpty(),"opaque item program stays exact and is never converted to a default armed carrier");
                r.fire(ModItems.BOMB_CLUSTER,ClusterProgram.Pattern.FAN);var b=r.carriers().getFirst();r.p.setSneaking(true);c.assertTrue(b.interact(r.p,Hand.MAIN_HAND).isAccepted()&&b.isRemoved(),"actual owning player can disarm the real unarmed carrier through normal entity interaction");r.p.setSneaking(false);
                future[0]=ModEntities.AERIAL_BOMB.create(r.w);future[0].configure(OrdnanceType.CLUSTER,r.p,null);future[0].setPosition(Vec3d.ofCenter(r.at).add(4,2,0));future[0].setVelocity(.1,0,.3);var n=future[0].writeNbt(new NbtCompound());n.put(ClusterFlight.KEY,raw[0].copy());future[0].readNbt(n);c.assertTrue(r.w.spawnEntity(future[0]),"actual unsupported saved carrier admitted for physical lifetime coverage");}
            if(t==36){c.assertTrue(r.bombs().stream().noneMatch(b->b.writeNbt(new NbtCompound()).getBoolean("sub")),"unknown saved state cannot fall back to the historical random7-child burst");c.assertTrue(future[0].isRemoved()||raw[0].equals(future[0].writeNbt(new NbtCompound()).get(ClusterFlight.KEY)),"unsupported raw program retained through normal movement without new authority");r.done();}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="cluster-program-physical-hit",tickLimit=140)
    public void actualVanillaArrowCanDestroyThePhysicalCarrierBeforeItArms(TestContext c){
        var r=new Rig(c);AerialBombEntity[] carrier={null};net.minecraft.entity.projectile.ArrowEntity[] arrow={null};
        r.drive(t->{if(t==0){r.fire(ModItems.BOMB_CLUSTER,ClusterProgram.Pattern.FAN);carrier[0]=r.carriers().getFirst();var at=carrier[0].getBoundingBox().getCenter();arrow[0]=EntityType.ARROW.create(r.w);arrow[0].setOwner(r.p);arrow[0].setNoGravity(true);arrow[0].setPosition(at.add(-1,0,0));arrow[0].setVelocity(2,0,carrier[0].getVelocity().z);c.assertTrue(r.w.spawnEntity(arrow[0]),"ordinary visible moving arrow, no direct damage callback");r.extra.add(arrow[0]);}
            if(t==4){c.assertTrue(arrow[0].age>0&&carrier[0].isRemoved()&&r.bombs().stream().noneMatch(b->b.writeNbt(new NbtCompound()).getBoolean("sub")),"real physical projectile hit destroys carrier without premature burst or immunity");arrow[0].discard();r.done();}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="cluster-program-actual-portal",tickLimit=140)
    public void actualPortalTurnsSavedFanAndGravityAxesButDoesNotSpendSafetyDistanceOrDoubleTransformVelocity(TestContext c){
        var r=new Rig(c);AerialBombEntity[] carrier={null};int[] age={0};long[] tick={0};boolean[] crossed={false};
        r.drive(t->{if(t==0){r.fire(ModItems.BOMB_CLUSTER,ClusterProgram.Pattern.RING);carrier[0]=r.carriers().getFirst();return;}
            if(!crossed[0]){var b=carrier[0];if(r.w.getEntity(b.getUuid())!=b||!r.tickets.entityTicking(b.getBlockPos())||b.age<1){c.assertTrue(t<20,"real born carrier UUID/entity ticking/ordinary age before actual portal");return;}var source=BlockPos.ofFloored(b.getPos());var exit=r.at.east(24);var old=b.getPos();b.setPosition(old.x,old.y,source.getZ()-.3);b.prevX=b.getX();b.prevY=b.getY();b.prevZ=b.getZ();b.move(MovementType.SELF,new Vec3d(0,0,.8));b.setVelocity(.12,.2,.8);var before=b.writeNbt(new NbtCompound());var oldFlight=before.getCompound(ClusterFlight.KEY);var velocity=b.getVelocity();
                var normal=new Vec3(0,0,-1);var from=new Vec3(source.getX()+.5,source.getY()+.5,source.getZ());var to=new Vec3(exit.getX()+.5,exit.getY()+1,exit.getZ()+.5);var transform=D6PortalTransform.of(from,normal,to,new Vec3(0,1,0));
                PortalTravel.carry(r.w,source,Direction.NORTH,exit,Direction.UP,4);var after=b.writeNbt(new NbtCompound());var flight=after.getCompound(ClusterFlight.KEY);
                c.assertTrue(b.getPos().distanceTo(old)>16&&b.getVelocity().squaredDistanceTo(new Vec3d(transform.transformVelocity(new Vec3(velocity.x,velocity.y,velocity.z)).x(),transform.transformVelocity(new Vec3(velocity.x,velocity.y,velocity.z)).y(),transform.transformVelocity(new Vec3(velocity.x,velocity.y,velocity.z)).z()))<1e-12,"actual generic entity traversal transforms one position and one real velocity");
                for(var field:List.of("forward","up"))c.assertTrue(vec(flight,field).minus(transform.rotation().rotate(vec(oldFlight,field))).lengthSquared()<1e-12,"saved fan axis follows actual tilted portal "+field);
                var down=transform.rotation().rotate(new Vec3(before.getDouble("DownX"),before.getDouble("DownY"),before.getDouble("DownZ")));c.assertTrue(new Vec3(after.getDouble("DownX"),after.getDouble("DownY"),after.getDouble("DownZ")).minus(down).lengthSquared()<1e-12&&flight.getDouble("travelled")==oldFlight.getDouble("travelled")&&after.getInt("FlightAge")==before.getInt("FlightAge"),"cached force rotates but the portal gap cannot arm/age/spend the program");age[0]=b.age;tick[0]=r.w.getTime();crossed[0]=true;return;}
            if(r.w.getTime()-tick[0]>=8){c.assertTrue(carrier[0].age-age[0]>=8&&!carrier[0].isRemoved(),"same real carrier actually resumes eight ordinary destination ticks");r.done();}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="cluster-program-friendly-impact",tickLimit=140)
    public void realArmedMaterialImpactDamagesAnEnemyButKeepsItsActualOwnerAndTeamSafe(TestContext c){
        var r=new Rig(c);net.minecraft.entity.passive.CowEntity[] friend={null},enemy={null};float[] health={0,0,0};AerialBombEntity[] carrier={null};
        r.drive(t->{if(t==0){for(var at:BlockPos.iterate(r.at.add(-7,-5,6),r.at.add(7,5,6)))r.w.setBlockState(at,Blocks.BEDROCK.getDefaultState(),2);
                var scoreboard=r.w.getScoreboard();var team=scoreboard.addTeam("cl_"+UUID.randomUUID().toString().substring(0,8));r.cleanup.add(()->scoreboard.removeTeam(team));scoreboard.addScoreHolderToTeam(r.p.getNameForScoreboard(),team);
                friend[0]=EntityType.COW.create(r.w);enemy[0]=EntityType.COW.create(r.w);for(int i=0;i<2;i++){var cow=i==0?friend[0]:enemy[0];cow.setPosition(Vec3d.ofCenter(r.at).add(i==0?-1.5:1.5,-2,5));cow.setNoGravity(true);cow.setAiDisabled(true);c.assertTrue(r.w.spawnEntity(cow),"real ordinary physical blast target");r.extra.add(cow);}scoreboard.addScoreHolderToTeam(friend[0].getNameForScoreboard(),team);c.assertTrue(r.p.isTeammate(friend[0])&&!r.p.isTeammate(enemy[0]),"real current vanilla allegiance, no invented faction rule");health[0]=friend[0].getHealth();health[1]=r.p.getHealth();health[2]=enemy[0].getHealth();r.fire(ModItems.BOMB_CLUSTER,ClusterProgram.Pattern.FAN);carrier[0]=r.carriers().getFirst();}
            if(t==32){c.assertTrue(carrier[0].isRemoved()&&r.bombs().stream().noneMatch(b->b.writeNbt(new NbtCompound()).getBoolean("sub")),"real bedrock impact ends the armed carrier before scheduled split");c.assertTrue(enemy[0].getHealth()<health[2]&&friend[0].getHealth()==health[0]&&r.p.getHealth()==health[1]&&friend[0].getVelocity().lengthSquared()<1e-8,"actual vanilla explosion damages enemy relative to its real initial HP while preserving owner/team health and knockback");r.done();}
        });
    }
}
