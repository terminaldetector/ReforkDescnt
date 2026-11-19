package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** Real registered bodies/rounds and ordinary engine travel; explicit weapon API, not a native-key claim. */
public final class DroneFireAdmissionGameTests implements FabricGameTest {
    private static final class Rig implements AutoCloseable {
        final TestContext c;final net.minecraft.server.world.ServerWorld w;final BlockPos base;final AiFixtureChunks lease;
        final List<FlightEnemyEntity> shooters=new ArrayList<>();final List<CowEntity> targets=new ArrayList<>();final List<Entity> extra=new ArrayList<>();
        final Map<BlockPos,net.minecraft.block.BlockState> blocks=new LinkedHashMap<>();final List<ProjectileEntity> rounds=new ArrayList<>();
        boolean spawned,closed;int admission,ticks;
        Rig(TestContext c){
            this.c=c;w=c.getWorld();base=c.getAbsolutePos(new BlockPos(144000,140,20000));lease=new AiFixtureChunks(w,base.add(12,0,4),2);
            for(var kind:List.of(FlightEnemyKind.ORB,FlightEnemyKind.ROCKET,FlightEnemyKind.JET)){
                var d=kind.create(w);var t=EntityType.COW.create(w);int i=shooters.size();
                d.setAiDisabled(true);d.setPosition(Vec3d.ofBottomCenter(base.add(0,0,i*5)));d.setPersistent();
                t.setAiDisabled(true);t.setNoGravity(true);t.setPosition(Vec3d.ofBottomCenter(base.add(24,0,i*5)));t.setPersistent();
                shooters.add(d);targets.add(t);
            }
        }
        boolean admit(){
            if(!spawned){
                for(int i=0;i<shooters.size();i++)if(!lease.entityTicking(shooters.get(i).getBlockPos(),targets.get(i).getBlockPos())){
                    c.assertTrue(++admission<80,"real source and target columns become entity-ticking");return false;
                }
                for(int i=0;i<shooters.size();i++)c.assertTrue(w.spawnEntity(shooters.get(i))&&w.spawnEntity(targets.get(i)),"registered normal role bodies");
                spawned=true;return false;
            }
            for(int i=0;i<shooters.size();i++)if(shooters.get(i).age<1||targets.get(i).age<1||w.getEntity(shooters.get(i).getUuid())!=shooters.get(i))return false;
            return true;
        }
        void block(BlockPos p){blocks.put(p,w.getBlockState(p));w.setBlockState(p,Blocks.BEDROCK.getDefaultState(),2);}
        void done(){close();c.complete();}
        public void close(){
            if(closed)return;closed=true;var ids=new HashSet<UUID>();shooters.forEach(d->ids.add(d.getUuid()));
            for(var e:java.util.stream.StreamSupport.stream(w.iterateEntities().spliterator(),false).toList())if(e instanceof ProjectileEntity shot&&ids.contains(shot.getOwnerUuid()))shot.discard();
            shooters.forEach(Entity::discard);targets.forEach(Entity::discard);for(var e:extra){if(e instanceof net.minecraft.server.network.ServerPlayerEntity p)w.getServer().getPlayerManager().remove(p);e.discard();}
            blocks.forEach((p,s)->w.setBlockState(p,s,2));lease.close();
        }
    }
    private static float cooldown(DroneEntity d){return d.writeNbt(new NbtCompound()).getFloat("attackCooldown");}
    private static void refused(TestContext c,DroneEntity d,LivingEntity target,String reason){
        float e=d.getModEnergy(),cd=cooldown(d);c.assertTrue(!d.tryFireAt(target)&&d.getModEnergy()==e&&cooldown(d)==cd,reason+" refuses before energy/cooldown");
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-fire-invalid",tickLimit=180)
    public void realRoleWeaponsRejectAlliesDeadTargetsForeignWorldsAndIneligiblePlayers(TestContext c){
        var r=new Rig(c);c.runAtEveryTick(()->{if(r.closed)return;try{
            r.w.resetIdleTimeout();if(!r.admit())return;
            var ally=ModEntities.DRONE.create(r.w);ally.setPosition(r.targets.getFirst().getPos());r.extra.add(ally);
            var dead=EntityType.COW.create(r.w);dead.setPosition(ally.getPos());dead.setHealth(0);r.extra.add(dead);
            var foreignWorld=r.w.getServer().getWorld(World.NETHER);c.assertTrue(foreignWorld!=null&&foreignWorld!=r.w,"distinct actual dimension for foreign target guard");
            var foreign=EntityType.COW.create(foreignWorld);foreign.setPosition(ally.getPos());r.extra.add(foreign);
            var player=c.createMockCreativeServerPlayerInWorld();player.setPosition(ally.getPos());r.extra.add(player);
            for(var d:r.shooters){refused(c,d,ally,"existing ally");refused(c,d,dead,"dead target");refused(c,d,foreign,"foreign world");refused(c,d,player,"Creative target");}
            player.changeGameMode(GameMode.SPECTATOR);for(var d:r.shooters)refused(c,d,player,"Spectator target");
            var deadOwner=ModEntities.DRONE.create(r.w);deadOwner.setPosition(r.shooters.getFirst().getPos());deadOwner.setHealth(0);r.extra.add(deadOwner);
            refused(c,deadOwner,r.targets.getFirst(),"dead shooter");
            c.assertTrue(r.w.getEntitiesByClass(ProjectileEntity.class,new Box(Vec3d.of(r.base),Vec3d.of(r.base.add(30,6,16))).expand(4),q->true).isEmpty(),"refused targets never emitted a hidden physical round");
            r.done();
        }catch(Throwable t){r.close();throw t;}});
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-fire-unloaded")
    public void anUnavailableMuzzleCannotSpendResourcesOrMaterializeRemoteColumns(TestContext c){
        var w=c.getWorld();var at=new Vec3d(8_730_000.5,Math.max(w.getBottomY()+16,Math.min(w.getTopY()-16,80)),-6_510_000.5);
        var p=BlockPos.ofFloored(at);c.assertTrue(w.getChunkManager().getWorldChunk(p.getX()>>4,p.getZ()>>4)==null,"genuine remote unknown muzzle");
        int loaded=w.getChunkManager().getLoadedChunkCount();var target=EntityType.COW.create(w);target.setPosition(at.add(8,0,0));
        try{for(var kind:List.of(FlightEnemyKind.ORB,FlightEnemyKind.ROCKET,FlightEnemyKind.JET)){
            var d=kind.create(w);d.setPosition(at);try{refused(c,d,target,"unknown muzzle "+kind);}finally{d.discard();}
        }}finally{target.discard();}
        c.assertTrue(w.getChunkManager().getLoadedChunkCount()==loaded&&w.getChunkManager().getWorldChunk(p.getX()>>4,p.getZ()>>4)==null,"loaded-only muzzle probe never pulls remote terrain into memory");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="drone-fire-real-cover",tickLimit=180)
    public void allThreeAuthoredWeaponsStillEmitTravelAndHitRealSolidCoverWithTheirActualCosts(TestContext c){
        var r=new Rig(c);boolean[] fired={false};var moving=new HashSet<UUID>();var impacts=new HashSet<UUID>();var births=new HashMap<UUID,Vec3d>();
        c.runAtEveryTick(()->{if(r.closed)return;try{
            r.w.resetIdleTimeout();if(!r.admit())return;
            if(!fired[0]){
                for(int i=0;i<r.shooters.size();i++){
                    var d=r.shooters.get(i);var target=r.targets.get(i);var source=d.getBoundingBox().getCenter();var barrier=BlockPos.ofFloored(source.lerp(target.getBoundingBox().getCenter(),1.0/3));r.block(barrier);
                    c.assertTrue(r.w.raycast(new RaycastContext(source,target.getBoundingBox().getCenter(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,d)).getType()==net.minecraft.util.hit.HitResult.Type.BLOCK,"real solid cover is deliberately in this weapon lane");
                    float energy=d.getModEnergy();c.assertTrue(d.tryFireAt(target),"actual authored weapon may physically strike cover, not an artificial LOS nerf: "+d.kind());
                    var found=r.w.getEntitiesByClass(ProjectileEntity.class,d.getBoundingBox().expand(1),q->d.getUuid().equals(q.getOwnerUuid()));c.assertTrue(found.size()==1,"one actual emitted round per accepted action");
                    var shot=found.getFirst();r.rounds.add(shot);births.put(shot.getUuid(),shot.getPos());var n=shot.writeNbt(new NbtCompound());
                    c.assertTrue(d.getModEnergy()==energy-d.getRole().weaponCost&&cooldown(d)==d.getRole().weaponCooldown&&shot.remainingLifeTicks()==80
                        &&n.getFloat("dmg")==d.getRole().weaponDamage&&n.getString("class").equals(d.getRole().weaponClass.id),"unchanged authored energy/cooldown/damage/class/lifetime "+d.kind());
                    float after=d.getModEnergy();c.assertTrue(!d.tryFireAt(target)&&d.getModEnergy()==after,"same-tick retry cannot bypass actual cooldown");
                    // Default drone rounds have no gameplay onHit callback: this passive receipt only observes the real engine terminal trace.
                    shot.setOnHit(hit->{c.assertTrue(hit.projectile()==shot&&shot.getOwnerEntity()==d&&hit.isBlock()&&hit.hitEntity()==null
                        &&Math.abs(hit.hitPos().x-barrier.getX())<1e-5,"genuine owner/round/material terminal receipt");impacts.add(shot.getUuid());});
                }
                fired[0]=true;return;
            }
            r.ticks++;for(var shot:r.rounds)if(!shot.isRemoved()&&shot.getPos().squaredDistanceTo(births.get(shot.getUuid()))>.01&&shot.getVelocity().lengthSquared()>0)moving.add(shot.getUuid());
            if(r.ticks<16&&(moving.size()<3||impacts.size()<3))return;
            c.assertTrue(moving.size()==3&&impacts.size()==3&&r.rounds.stream().allMatch(Entity::isRemoved),"three actual normal-tick projectiles traveled and terminated on cover: moving="+moving.size()+" impact="+impacts.size());
            c.assertTrue(r.blocks.keySet().stream().allMatch(p->r.w.getBlockState(p).isOf(Blocks.BEDROCK)),"protected real material was not bypassed or erased");r.done();
        }catch(Throwable t){r.close();throw t;}});
    }
}
