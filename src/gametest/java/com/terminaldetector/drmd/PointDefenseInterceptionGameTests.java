package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.weapon.projectile.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.trap.DefenseTurrets;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Natural scheduled-block/ordinary-missile baseline. No defensive-fire call,
 * fake owner, entity tick, follow teleport, hit injection or in-flight rewrite. */
public final class PointDefenseInterceptionGameTests implements FabricGameTest {
    private static final class Rig implements AutoCloseable {
        final TestContext c;
        final ServerWorld world;
        final BlockPos base;
        final Vec3d muzzle,launch;
        final CowEntity owner;
        final AiFixtureChunks chunks;
        final boolean defended;
        final Map<BlockPos,BlockState> original=new LinkedHashMap<>();
        final Map<UUID,Vec3d> observedKinetic=new LinkedHashMap<>();
        final Set<UUID> emitted=new HashSet<>();
        ProjectileEntity rocket;
        WeaponCore.HitContext impact;
        Vec3d impactOtherPosition,impactOtherVelocity,impactIncomingPosition,impactIncomingVelocity;
        int impactCount,admission,ticks,launchTick=-1,launchAge=-1;
        long blockPlacedAt,launchedAt,impactAt;
        float originalSplashRadius;
        boolean admitted,closed;

        Rig(TestContext c,boolean defended,int offset){
            this.c=c;world=c.getWorld();this.defended=defended;
            base=c.getAbsolutePos(new BlockPos(88000+offset,200,6000));
            muzzle=Vec3d.ofCenter(base).add(0,.6,0);
            launch=Vec3d.ofCenter(base).add(11,0,0);
            chunks=new AiFixtureChunks(world,base,2);
            owner=EntityType.COW.create(world);
            try{
                c.assertTrue(owner!=null,"actual registered living missile owner is available");
                owner.setPosition(Vec3d.ofBottomCenter(base.add(24,0,0)));
                owner.setAiDisabled(true);owner.setNoGravity(true);owner.setPersistent();owner.setInvulnerable(true);
                // Test only reads the exact existing station predicate. It must not invent
                // a player/faction ownership policy for this ownerless placed block.
                var predicate=DefenseTurrets.BaseTurretBlock.class.getDeclaredMethod("isValidTarget",net.minecraft.entity.LivingEntity.class);
                predicate.setAccessible(true);
                c.assertTrue((boolean)predicate.invoke(ModWorldBlocks.POINT_DEFENSE_TURRET,owner),"owner satisfies current station threat policy");
                c.assertTrue(owner.squaredDistanceTo(Vec3d.ofCenter(base))>12*12,"shooter cannot substitute for a missile target in turret range");
                // Clear only the measured, already admitted narrow arena; retain every cell.
                for(var p:BlockPos.iterate(base.add(-1,0,-1),base.add(13,2,1))){
                    var key=p.toImmutable();original.put(key,world.getBlockState(key));world.setBlockState(key,Blocks.AIR.getDefaultState(),2);
                }
            }catch(Throwable failure){close();throw new RuntimeException(failure);}
        }

        boolean ready(){
            if(admitted)return true;
            if(!chunks.entityTicking(base,BlockPos.ofFloored(launch),owner.getBlockPos())){
                if(++admission>=80)c.assertTrue(false,"point-defense fixture obtains actual entity-ticking columns: "+chunks.readiness(base,owner.getBlockPos()));
                return false;
            }
            c.assertTrue(WorldEditSafety.loadedSegment(world,muzzle,launch),"full collision-shape halo is loaded before the transaction");
            c.assertTrue(world.spawnEntity(owner),"actual living missile owner spawns");
            world.setBlockState(base,(defended?ModWorldBlocks.POINT_DEFENSE_TURRET:Blocks.DISPENSER).getDefaultState(),3);
            blockPlacedAt=world.getTime();admitted=true;
            return false;
        }

        void startMissile(){
            var cfg=ProjectileFramework.config(ProjectileKind.ROCKET,owner,launch,new Vec3d(-1,0,0));
            // Keep every actual stock ROCKET flight/fuse/damage/blast parameter.
            originalSplashRadius=cfg.splashRadius>20?(float)DescentMod.su(cfg.splashRadius):cfg.splashRadius;
            var originalHit=cfg.onHit;
            cfg.onHit=ctx->{
                impactCount++;impact=ctx;impactAt=world.getTime();
                impactIncomingPosition=ctx.projectile().getPos();impactIncomingVelocity=ctx.projectile().getVelocity();
                if(ctx.hitEntity()!=null){impactOtherPosition=ctx.hitEntity().getPos();impactOtherVelocity=ctx.hitEntity().getVelocity();}
                if(originalHit!=null)originalHit.accept(ctx);
            };
            rocket=WeaponCore.fireProjectile(cfg);
            c.assertTrue(rocket!=null&&rocket.getOwnerLiving()==owner,"normal weapon factory emits the actual owned rocket");
            launchTick=ticks;launchedAt=world.getTime();launchAge=rocket.age;
            c.assertTrue(rocket.getDamageClass()==DamageClass.EXPLOSIVE&&rocket.getMeshKind()==ProjectileEntity.MESH_ROCKET,
                "actual stock missile identity is retained");
            c.assertTrue(rocket.getVelocity().x<0&&Double.isFinite(rocket.getVelocity().lengthSquared()),"actual finite launch velocity approaches the protected cube");
            System.out.println("POINT_DEFENSE_LAUNCH "+state()+" velocity="+rocket.getVelocity()+" splashRadius="+originalSplashRadius);
        }

        void sample(){
            ticks++;
            // Place->four normal world ticks->launch; the real first six-tick block
            // opportunity then occurs while the stock missile is still approaching.
            if(ticks==4)startMissile();
            if(rocket==null)return;
            var area=new Box(base).expand(16);
            for(var shot:world.getEntitiesByClass(ProjectileEntity.class,area,e->e!=rocket&&e.isAlive()&&e.getDamageClass()==DamageClass.KINETIC)){
                emitted.add(shot.getUuid());observedKinetic.put(shot.getUuid(),shot.getPos());
            }
            if(impact!=null){
                String detail=state();
                c.assertTrue(impactCount==1,"incoming terminal payload runs exactly once: "+detail);
                c.assertTrue(rocket.age-launchAge>=3&&launch.squaredDistanceTo(impact.hitPos())>9,"actual missile age/travel reaches a material outcome before expiry: "+detail);
                if(defended){
                    c.assertTrue(impact.hitEntity() instanceof ProjectileEntity,"point defense must physically intercept the real missile before an ordinary base hit: "+detail);
                    var interceptor=(ProjectileEntity)impact.hitEntity();
                    c.assertTrue(interceptor.getDamageClass()==DamageClass.KINETIC&&emitted.contains(interceptor.getUuid()),"actual emitted kinetic was observed in normal flight before contact: "+detail);
                    var observed=observedKinetic.get(interceptor.getUuid());
                    c.assertTrue(observed!=null&&observed.squaredDistanceTo(muzzle)>1,"emitted interceptor travels rather than substituting immediate manager removal: "+detail);
                    c.assertTrue(impactOtherPosition!=null&&impactOtherVelocity!=null
                        &&segmentDistanceSquared(impact.hitPos(),impactOtherPosition.subtract(impactOtherVelocity),impactOtherPosition.add(impactOtherVelocity))<.7*.7,
                        "terminal callback point lies on the actual interceptor's relative swept contact neighbourhood: "+detail);
                    c.assertTrue(segmentDistanceSquared(impact.hitPos(),impactIncomingPosition.subtract(impactIncomingVelocity),impactIncomingPosition.add(impactIncomingVelocity))<.7*.7,
                        "the same terminal callback point lies on the actual incoming missile's swept contact neighbourhood: "+detail);
                    c.assertTrue(impact.hitPos().distanceTo(Vec3d.ofCenter(base))>originalSplashRadius+.5,
                        "interception occurs beyond the original blast radius rather than free warhead disarming: "+detail);
                    c.assertTrue(world.getBlockState(base).isOf(ModWorldBlocks.POINT_DEFENSE_TURRET),"real protected point-defense block remains intact: "+detail);
                    c.assertTrue(!rocket.isAlive(),"real incoming rocket terminates on physical contact: "+detail);
                    System.out.println("POINT_DEFENSE_INTERCEPTION_PASS "+detail);
                }else{
                    c.assertTrue(impact.isBlock()&&BlockPos.ofFloored(impact.hitPos().add(-.001,0,0)).equals(base),"unprotected control has real material impact on the base cube: "+detail);
                    c.assertTrue(emitted.isEmpty(),"inert control does not invent an interceptor: "+detail);
                    System.out.println("POINT_DEFENSE_CONTROL_PASS "+detail);
                }
                close();c.complete();
            }else if(ticks>=40){
                String detail=state();close();c.assertTrue(false,"ordinary missile/interception must produce a real terminal contact before expiry, not timeout disappearance: "+detail);
            }
        }

        String state(){return "defended="+defended+" ticks="+ticks+" worldTime="+world.getTime()+" placedAt="+blockPlacedAt
            +" launchAt="+launchedAt+" launchTick="+launchTick+" impactAt="+impactAt+" missileAge="+(rocket==null?-1:rocket.age)
            +" missileAlive="+(rocket!=null&&rocket.isAlive())+" missilePos="+(rocket==null?"none":rocket.getPos())
            +" emitted="+emitted.size()+" hitCount="+impactCount+" hitBlock="+(impact!=null&&impact.isBlock())
            +" hitEntity="+(impact==null||impact.hitEntity()==null?"none":impact.hitEntity().getUuid())+" impact="+(impact==null?"none":impact.hitPos());}

        @Override public void close(){
            if(closed)return;closed=true;
            for(var shot:world.getEntitiesByClass(ProjectileEntity.class,new Box(base).expand(24),e->e==rocket||e.getOwnerLiving()==owner||emitted.contains(e.getUuid())))shot.discard();
            if(owner!=null)owner.discard();
            for(var entry:original.entrySet())world.setBlockState(entry.getKey(),entry.getValue(),2);
            chunks.close();
        }
    }

    private static double segmentDistanceSquared(Vec3d p,Vec3d a,Vec3d b){
        var delta=b.subtract(a);double length=delta.lengthSquared();
        double fraction=length<1e-12?0:MathHelper.clamp(p.subtract(a).dotProduct(delta)/length,0,1);
        return p.squaredDistanceTo(a.add(delta.multiply(fraction)));
    }

    private void run(TestContext c,boolean defended,int offset){
        var rig=new Rig(c,defended,offset);
        c.runAtEveryTick(()->{
            if(rig.closed)return;
            try{rig.world.resetIdleTimeout();if(rig.ready())rig.sample();}
            catch(Throwable failure){System.out.println("POINT_DEFENSE_FIRST_FAILURE "+rig.state());rig.close();throw failure;}
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="point-defense-real-interception",tickLimit=160)
    public void scheduledPointDefensePhysicallyInterceptsAnIncomingStockRocket(TestContext c){run(c,true,0);}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="point-defense-unprotected-control",tickLimit=160)
    public void unprotectedCubeActuallyReceivesTheSameStockRocket(TestContext c){run(c,false,512);}
}
