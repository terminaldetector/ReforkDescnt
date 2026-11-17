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
        private record Due(long tick,long order,Runnable action){}
        private final PriorityQueue<Due> due=new PriorityQueue<>(Comparator.comparingLong(Due::tick).thenComparingLong(Due::order));
        private final List<Runnable> observers=new ArrayList<>();
        private long nextOrder;
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
        // The GameTest task map is being iterated when this driver runs. Keep delayed work
        // and observations here rather than adding GameTest callbacks from another callback.
        void later(long ticks,Runnable action){
            if(ticks<=0)throw new IllegalArgumentException("fixture work must wait for a normal world tick");
            due.add(new Due(c.getWorld().getTime()+ticks,nextOrder++,action));
        }
        void observe(Runnable action){observers.add(action);}
        void tick(){
            for(var observer:List.copyOf(observers)){if(closed)return;observer.run();}
            long now=c.getWorld().getTime();
            while(!closed&&!due.isEmpty()&&due.peek().tick()<=now)due.remove().action().run();
        }
        void complete(){close();c.complete();}
        public void close(){
            if(closed)return;closed=true;due.clear();observers.clear();
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
        boolean[] started={false};int[] admission={0};Rig[] live={null};
        // Register the sole driver at test entry, before GameTestState starts iterating tasks.
        c.runAtEveryTick(()->{
            if(started[0]){
                if(live[0]!=null&&!live[0].closed)try{live[0].tick();}catch(Throwable failure){live[0].close();throw failure;}
                return;
            }
            c.getWorld().resetIdleTimeout();
            if(!lease.entityTicking(at,at.add(24,24,24),at.add(-24,-24,-24))){if(++admission[0]<80)return;started[0]=true;lease.close();c.assertTrue(false,"gravity item fixture obtains real entity-ticking columns");return;}
            started[0]=true;Rig rig=null;
            try{rig=new Rig(c,lease,at,aim);live[0]=rig;action.accept(rig);}catch(Throwable failure){if(rig==null)lease.close();else rig.close();throw failure;}
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
                r.observe(()->{
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
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="gravity-item-empty-fling",tickLimit=150)
    public void allThreeHeldItemsRetainTheirTargetsOnEmptyFlingAndRetryAfterRefill(TestContext c){
        admitted(c,1800,new Vec3d(0,0,1),r->checkEmptyFling(r,0));
    }
    private void checkEmptyFling(Rig r,int index){
        if(index==3){r.complete();return;}
        var c=r.c;var mode=List.of(GravyPhysics.Mode.SWARM,GravyPhysics.Mode.RAIL,GravyPhysics.Mode.SHIFT).get(index);
        Item item=switch(mode){case SWARM->ModItems.GRAVITY_SWARM;case RAIL->ModItems.GRAVY_RAILGUN;case SHIFT->ModItems.GRAVITY_SHIFTER;};
        var owned=new ArrayList<BlockBodyEntity>();var centre=r.player.getEyePos().add(r.aim().multiply(mode==GravyPhysics.Mode.RAIL?12:8));
        if(mode==GravyPhysics.Mode.SWARM){
            for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})owned.add(r.body(centre.add(x*1.2,y*1.2,0),1,1,1));
        }else owned.add(mode==GravyPhysics.Mode.RAIL?r.body(centre,5,5,4):r.body(centre,1,1,1));
        var ids=owned.stream().map(r::field).toList();var data=DescentPlayerData.get(r.player);data.setGravyEnergy(100);
        c.assertTrue(r.use(item,true)&&GravyPhysics.activeMode(r.player).orElse(null)==mode&&GravyPhysics.heldCount(r.player)==owned.size(),"actual funded capture precedes empty-fling input for "+mode);
        double mass=GravyPhysics.heldMass(r.player);
        r.later(8,()->{
            c.assertTrue(GravyPhysics.activeMode(r.player).orElse(null)==mode&&GravyPhysics.heldCount(r.player)==owned.size()&&Math.abs(GravyPhysics.heldMass(r.player)-mass)<1e-5,"ordinary held state survives eight normal world ticks for "+mode);
            c.assertTrue(owned.stream().allMatch(b->b.age>=5&&c.getWorld().getEntity(b.getUuid())==b&&b.isAlive()),"same registered targets actually tick before the empty fling for "+mode);
            var stack=r.player.getMainHandStack();var custom=stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
            long last=custom==null?0:custom.copyNbt().getLong("lastFire");
            int cooldown=Math.max(1,(int)(((DescentWeaponItem)item).getDef().fireRate*20));
            c.assertTrue(custom!=null&&c.getWorld().getTime()-last>=cooldown,"persisted use cooldown has elapsed before testing capacitor rejection for "+mode);
            float managerCooldown=r.player.getItemCooldownManager().getCooldownProgress(item,0);
            var velocities=owned.stream().map(BlockBodyEntity::physicsVelocity).toList();
            data.setGravyEnergy(0); // Explicit negative input after a real, still-active capture.
            c.assertTrue(!r.use(item,false),"empty capacitor rejects the actual held-item fling for "+mode+": "+r.state(owned.getFirst()));
            c.assertTrue(GravyPhysics.activeMode(r.player).orElse(null)==mode&&GravyPhysics.heldCount(r.player)==owned.size()&&Math.abs(GravyPhysics.heldMass(r.player)-mass)<1e-5,"rejected fling retains its original mode/count/mass for "+mode);
            for(int i=0;i<owned.size();i++){
                var body=owned.get(i);
                c.assertTrue(c.getWorld().getEntity(body.getUuid())==body&&body.isAlive()&&body.physicsVelocity().squaredDistanceTo(velocities.get(i))<1e-12,"rejected fling preserves actual target identity and immediate physical velocity for "+mode);
            }
            var after=stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
            c.assertTrue(data.getGravyEnergy()==0&&r.player.getMainHandStack()==stack&&after!=null&&after.copyNbt().getLong("lastFire")==last&&r.player.getItemCooldownManager().getCooldownProgress(item,0)==managerCooldown,"rejection neither spends energy nor restamps either cooldown for "+mode);
            c.assertTrue(ids.stream().allMatch(id->TransientGravityFields.remainingTicks(id)==0)&&GravityFields.all().stream().noneMatch(f->ids.contains(f.id())),"rejected fling creates no field on any owned target for "+mode);
            data.setGravyEnergy(100);
            c.assertTrue(r.use(item,false)&&!GravyPhysics.isHolding(r.player),"refill permits an immediate actual retry, without another cooldown wait, for "+mode);
            float cost=switch(mode){case SWARM->10;case RAIL->12;case SHIFT->18;};
            c.assertTrue(Math.abs(data.getGravyEnergy()-(100-cost))<1e-5,"only the successful retry debits one launch cost for "+mode);
            c.assertTrue(owned.stream().allMatch(b->c.getWorld().getEntity(b.getUuid())==b&&Double.isFinite(b.physicsVelocity().lengthSquared())&&b.physicsVelocity().dotProduct(r.aim())>24),"successful retry launches every original target rather than recapturing substitutes for "+mode);
            if(mode==GravyPhysics.Mode.SHIFT)c.assertTrue(ids.stream().allMatch(id->TransientGravityFields.remainingTicks(id)==160),"only the successful shifter retry attaches its field");
            DescentMod.LOGGER.info("[gravity-item empty-fling] mode={} targets={} preservedMass={} retryEnergy={} state={}",mode,owned.size(),mass,data.getGravyEnergy(),r.state(owned.getFirst()));
            ids.forEach(TransientGravityFields::remove);owned.forEach(BlockBodyEntity::discard);
            r.later(1,()->checkEmptyFling(r,index+1));
        });
    }
}
