package com.terminaldetector.drmd;

import com.terminaldetector.drmd.physics.GravyPhysics;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import com.terminaldetector.drmd.world.gravity.TransientGravityFields;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Actual registered gravity-item paths over real BlockBody targets; no direct tryGrab/fling calls. */
public final class GravityGunBehaviorGameTests implements FabricGameTest {
    private static final class Rig implements AutoCloseable {
        final TestContext c;
        final ServerPlayerEntity player;
        final AiFixtureChunks chunks;
        final List<BlockBodyEntity> bodies=new ArrayList<>();
        final Set<UUID> fields=new HashSet<>();
        private boolean closed;
        Rig(TestContext c,AiFixtureChunks chunks,BlockPos at,Vec3d forward){
            this.c=c;this.chunks=chunks;player=c.createMockCreativeServerPlayerInWorld();
            try{
                player.setPosition(Vec3d.ofCenter(at));player.setNoGravity(true);player.getAbilities().flying=true;
                player.setYaw(180);player.setPitch(0);
                var data=DescentPlayerData.get(player);data.setEnabled(false);data.setGravyEnergy(100);
                var f=forward.normalize();var side=f.crossProduct(Math.abs(f.y)<.9?new Vec3d(0,1,0):new Vec3d(1,0,0)).normalize();
                data.setShipAttitude((float)f.x,(float)f.y,(float)f.z,(float)side.x,(float)side.y,(float)side.z);
                c.assertTrue(WeaponCore.aimDir(player).dotProduct(player.getRotationVector())<.9,"fixture uses a banked ship frame distinct from vanilla yaw/pitch");
            }catch(Throwable failure){close();throw failure;}
        }
        Vec3d aim(){return WeaponCore.aimDir(player).normalize();}
        BlockBodyEntity body(Vec3d centre,int sx,int sy,int sz){
            var cells=new ArrayList<BlockBodyEntity.Cell>();
            for(int x=0;x<sx;x++)for(int y=0;y<sy;y++)for(int z=0;z<sz;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
            var body=BlockBodyEntity.createLocal(c.getWorld(),cells,centre.subtract(sx*.5,sy*.5,sz*.5));bodies.add(body);return body;
        }
        boolean use(Item item,boolean equip){
            if(equip)player.setStackInHand(Hand.MAIN_HAND,new ItemStack(item));
            return item.use(c.getWorld(),player,Hand.MAIN_HAND).getResult().isAccepted();
        }
        String state(BlockBodyEntity body){
            var stack=player.getMainHandStack();var custom=stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
            long now=c.getWorld().getTime(),last=custom==null?0:custom.copyNbt().getLong("lastFire");
            int cooldown=stack.getItem() instanceof DescentWeaponItem weapon?Math.max(1,(int)(weapon.getDef().fireRate*20)):-1;
            var data=DescentPlayerData.get(player);
            return "time="+now+" stack="+net.minecraft.registry.Registries.ITEM.getId(stack.getItem())+" lastFire="+last+" elapsed="+(now-last)+" cooldown="+cooldown
                +" managerCooling="+player.getItemCooldownManager().isCoolingDown(ModItems.GRAVY_RAILGUN)
                +" mode="+GravyPhysics.activeMode(player)+" held="+GravyPhysics.heldCount(player)+" mass="+GravyPhysics.heldMass(player)+" energy="+data.getGravyEnergy()
                +" playerAlive="+player.isAlive()+" playerPos="+player.getPos()+" enabled="+data.isEnabled()+" savedMode="+data.hasSavedFlightMode()
                +" attitude="+data.hasShipAttitude()+" aim="+aim()+" up="+data.shipUp(player)
                +" bodyAlive="+body.isAlive()+" removed="+body.getRemovalReason()+" lookup="+(c.getWorld().getEntity(body.getUuid())==body)+" age="+body.age
                +" bodyPos="+body.physicsCentre()+" velocity="+body.physicsVelocity()+" gravity="+body.physicsGravity()+" tether="+body.distanceTo(player);
        }
        UUID field(BlockBodyEntity body){var id=UUID.nameUUIDFromBytes(("drmd:gravity-projector:"+body.getUuid()).getBytes(StandardCharsets.UTF_8));fields.add(id);return id;}
        void later(long ticks,Runnable action){c.waitAndRun(ticks,()->{try{action.run();}catch(Throwable failure){close();throw failure;}});}
        void complete(){close();c.complete();}
        public void close(){
            if(closed)return;closed=true;
            try{GravyPhysics.release(player);fields.forEach(TransientGravityFields::remove);bodies.forEach(BlockBodyEntity::discard);}
            finally{
                try{
                    if(c.getWorld().getServer().getPlayerManager().getPlayerList().contains(player))
                        player.networkHandler.onDisconnected(new net.minecraft.network.DisconnectionInfo(net.minecraft.text.Text.literal("gravity-item fixture closed")));
                }finally{player.discard();chunks.close();}
            }
        }
    }
    private void admitted(TestContext c,int offset,Vec3d aim,Consumer<Rig> action){
        var at=c.getAbsolutePos(new BlockPos(78000+offset,160,2000));var lease=new AiFixtureChunks(c.getWorld(),at,2);
        boolean[] started={false};int[] admission={0};
        c.runAtEveryTick(()->{
            if(started[0])return;c.getWorld().resetIdleTimeout();
            if(!lease.entityTicking(at,at.add(24,24,24),at.add(-24,-24,-24))){if(++admission[0]<80)return;started[0]=true;lease.close();c.assertTrue(false,"gravity item fixture obtains real entity-ticking columns");return;}
            started[0]=true;Rig rig=null;
            try{rig=new Rig(c,lease,at,aim);action.accept(rig);}catch(Throwable failure){if(rig==null)lease.close();else rig.close();throw failure;}
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="gravity-item-swarm",tickLimit=150)
    public void actualSwarmItemCapturesSeveralBodiesAndLaunchesADistinctVolley(TestContext c){
        admitted(c,0,new Vec3d(0,0,1),r->{
            var centre=r.player.getEyePos().add(r.aim().multiply(8));
            for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})r.body(centre.add(x*1.2,y*1.2,0),1,1,1).setPhysicsGravity(false);
            c.assertTrue(r.use(ModItems.GRAVITY_SWARM,true),"registered swarm item accepts its real cone targets");
            c.assertTrue(GravyPhysics.activeMode(r.player).orElse(null)==GravyPhysics.Mode.SWARM&&GravyPhysics.heldCount(r.player)==4,"swarm holds four real body identities rather than a single ray target");
            c.assertTrue(Math.abs(DescentPlayerData.get(r.player).getGravyEnergy()-82)<1e-5,"capture debits the swarm capacitor once");
            var initial=r.bodies.stream().map(BlockBodyEntity::physicsCentre).toList();
            r.later(8,()->{
                c.assertTrue(r.bodies.stream().allMatch(b->b.age>=5),"held bodies advance under ordinary entity ticks");
                c.assertTrue(java.util.stream.IntStream.range(0,4).anyMatch(i->r.bodies.get(i).physicsCentre().distanceTo(initial.get(i))>.15),"ordinary server hold springs move actual material");
                float before=DescentPlayerData.get(r.player).getGravyEnergy();
                c.assertTrue(r.use(ModItems.GRAVITY_SWARM,false)&&!GravyPhysics.isHolding(r.player),"second actual use releases the complete captured volley");
                c.assertTrue(Math.abs(before-DescentPlayerData.get(r.player).getGravyEnergy()-10)<1e-5,"four-body volley debits one mode-specific launch cost");
                var velocities=r.bodies.stream().map(BlockBodyEntity::physicsVelocity).toList();
                c.assertTrue(velocities.stream().allMatch(v->Double.isFinite(v.lengthSquared())&&v.dotProduct(r.aim())>24),"each real body receives a forward physical launch");
                c.assertTrue(velocities.get(0).distanceTo(velocities.get(1))>.1,"swarm launches preserve distinct aim-plane spread");r.complete();
            });
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="gravity-item-rail",tickLimit=150)
    public void actualRailItemCapturesHeavyMassAndCrossesItsBallisticAimPlane(TestContext c){
        admitted(c,300,new Vec3d(1,2,3),r->{
            var body=r.body(r.player.getEyePos().add(r.aim().multiply(12)),5,5,4);
            c.assertTrue(body.physicsMass()>96&&body.physicsMass()<384,"real hundred-cell mass exceeds field-thrower capacity but fits rail");
            c.assertTrue(r.use(ModItems.GRAVY_RAILGUN,true),"registered heavy rail captures real material with exact ship aim");
            c.assertTrue(GravyPhysics.activeMode(r.player).orElse(null)==GravyPhysics.Mode.RAIL&&GravyPhysics.heldCount(r.player)==1&&Math.abs(GravyPhysics.heldMass(r.player)-100)<1e-5,"rail keeps exactly one aggregate heavy body");
            var holdTrace=new ArrayList<String>();holdTrace.add(r.state(body));
            for(int tick=1;tick<8;tick++)r.later(tick,()->holdTrace.add(r.state(body)));
            r.later(8,()->{
                var start=body.physicsCentre();var aim=r.aim();
                var before=r.state(body);boolean launched=r.use(ModItems.GRAVY_RAILGUN,false);var after=r.state(body);
                DescentMod.LOGGER.info("[gravity-item rail] launched={} holdTrace={} before={} after={}",launched,holdTrace,before,after);
                c.assertTrue(launched,"normal second rail use launches the held mass: before="+before+" after="+after+" holdTrace="+holdTrace);
                c.assertTrue(body.physicsVelocity().distanceTo(aim.multiply(42))<1e-5,"actual rail impulse produces its precise 6DoF launch velocity");
                Vec3d[] previous={start};boolean[] crossed={false};double[] miss={Double.POSITIVE_INFINITY};
                c.runAtEveryTick(()->{
                    if(crossed[0]||body.isRemoved())return;
                    var now=body.physicsCentre();double a=previous[0].subtract(start).dotProduct(aim),b=now.subtract(start).dotProduct(aim);
                    if(a<12&&b>=12){var at=previous[0].lerp(now,(12-a)/(b-a));miss[0]=at.subtract(start.add(aim.multiply(12))).length();crossed[0]=true;}
                    previous[0]=now;
                });
                r.later(12,()->{c.assertTrue(crossed[0]&&miss[0]<1.2,"ordinary gravity/body updates carry heavy rail material within 1.2 blocks of the aim centre at twelve blocks: crossed="+crossed[0]+" miss="+miss[0]);r.complete();});
            });
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="gravity-item-shift",tickLimit=150)
    public void actualShifterLaunchAttachesADirectionalFieldToTheThrownBody(TestContext c){
        admitted(c,600,new Vec3d(0,1,0),r->{
            var body=r.body(r.player.getEyePos().add(r.aim().multiply(8)),1,1,1);body.setPhysicsGravity(false);UUID id=r.field(body);
            c.assertTrue(r.use(ModItems.GRAVITY_SHIFTER,true)&&GravyPhysics.activeMode(r.player).orElse(null)==GravyPhysics.Mode.SHIFT,"registered shifter captures its own distinct physical mode");
            r.later(8,()->{
                c.assertTrue(r.use(ModItems.GRAVITY_SHIFTER,false),"second shifter use throws the captured real body");
                var field=GravityFields.all().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow();var first=field.origin();
                c.assertTrue(field.downDir().dotProduct(r.aim())>.9999&&TransientGravityFields.remainingTicks(id)==160,"actual gun launch publishes the bounded field along 6DoF ship aim");
                c.assertTrue(body.physicsVelocity().dotProduct(r.aim())>24&&body.physicsVelocity().dotProduct(r.aim())<26,"shifter launch remains mechanically distinct from the heavy rail");
                r.later(4,()->{
                    var moved=GravityFields.all().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow();
                    c.assertTrue(!moved.origin().equals(first)&&Vec3d.ofCenter(moved.origin()).distanceTo(body.getPos())<3,"normal server updates move the field with its actual anchor");
                    body.discard();r.later(2,()->{c.assertTrue(TransientGravityFields.remainingTicks(id)==0&&GravityFields.all().stream().noneMatch(f->f.id().equals(id)),"anchor removal retires the gun-created field without a manual service tick");r.complete();});
                });
            });
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="gravity-item-mode-handoff",tickLimit=150)
    public void switchingFromSwarmToRailCannotInheritTheMultiBodyGrab(TestContext c){
        admitted(c,900,new Vec3d(0,0,1),r->{
            var centre=r.player.getEyePos().add(r.aim().multiply(8));
            r.body(centre.add(-1,0,0),1,1,1);r.body(centre.add(1,0,0),1,1,1);var central=r.body(centre,1,1,1);
            c.assertTrue(r.use(ModItems.GRAVITY_SWARM,true)&&GravyPhysics.heldCount(r.player)==3,"real swarm capture precedes tool change");
            c.assertTrue(r.use(ModItems.GRAVY_RAILGUN,true)&&GravyPhysics.activeMode(r.player).orElse(null)==GravyPhysics.Mode.RAIL&&GravyPhysics.heldCount(r.player)==1,"actual tool change releases the old list and recaptures one rail target");
            c.assertTrue(r.bodies.stream().allMatch(b->b.physicsVelocity().lengthSquared()<1e-9),"switching tools cannot accidentally launch the old swarm");
            c.assertTrue(central.physicsRaycast(r.player.getEyePos(),r.player.getEyePos().add(r.aim().multiply(24))).isPresent(),"one actually intersected central material target remains available");r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="gravity-item-empty-capacitor",tickLimit=150)
    public void allThreeRealItemsReleaseTentativeCapturesWhenEnergyIsInsufficient(TestContext c){
        admitted(c,1200,new Vec3d(0,0,1),r->{
            var body=r.body(r.player.getEyePos().add(r.aim().multiply(8)),1,1,1);
            for(Item item:List.of(ModItems.GRAVITY_SWARM,ModItems.GRAVY_RAILGUN,ModItems.GRAVITY_SHIFTER)){
                DescentPlayerData.get(r.player).setGravyEnergy(0);
                c.assertTrue(!r.use(item,true)&&!GravyPhysics.isHolding(r.player)&&DescentPlayerData.get(r.player).getGravyEnergy()==0,"insufficient capacitor cannot retain a tentative capture or debit energy: "+item);
                c.assertTrue(body.physicsVelocity().lengthSquared()<1e-9,"failed actual use cannot push its physical target");
            }r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="gravity-item-unknown-ray",tickLimit=150)
    public void actualRailUseCannotGenerateAnUnknownRemoteColumn(TestContext c){
        admitted(c,1500,new Vec3d(1,0,0),r->{
            var at=new Vec3d(1340000.5,180.5,2450000.5);r.player.setPosition(at);var p=BlockPos.ofFloored(at);
            c.assertTrue(c.getWorld().getChunkManager().getWorldChunk(p.getX()>>4,p.getZ()>>4)==null,"actual gravity-item query begins in unavailable terrain");
            boolean accepted=r.use(ModItems.GRAVY_RAILGUN,true);
            c.assertTrue(c.getWorld().getChunkManager().getWorldChunk(p.getX()>>4,p.getZ()>>4)==null&&!accepted&&!GravyPhysics.isHolding(r.player),"failed real rail use cannot synchronously generate unknown terrain while looking for a target");r.complete();
        });
    }
}
