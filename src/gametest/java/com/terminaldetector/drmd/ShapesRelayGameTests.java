package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.test.*;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import java.util.*;

/** NEW real-cover relay: original flat same-victim contention remains a retained known negative. */
public final class ShapesRelayGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shapes-physical-observer-cover",tickLimit=340)
    public void liveArmedScoutLosesRealLosAndWingmanFinishesFromAnExpiringObservation(TestContext c){
        var rig=new ShapesFixture(c,2048,true,true);DroneSquadMailbox.Message[] original={null};
        boolean[] scoutSaw={false},expired={false},actualHealthDamage={false};float[] closureHealth={Float.NaN};
        rig.drive(r->{
            var stats=DroneSquadCoordinator.stats(r.world);
            c.assertTrue(stats.mailboxes()<=256&&stats.maxAllies()<=32&&stats.maxCandidates()<=96&&stats.cleanupExamined()<=32,"same bounded shared coordinator: "+stats);
            scoutSaw[0]|=r.scout.getTarget()==r.victim&&r.scout.canSee(r.victim);
            if(original[0]==null){
                var observation=DroneSquadCoordinator.message(r.infantry);
                if(observation.isEmpty()){c.assertTrue(r.ticks<60,"natural scout obtains actual own target/LOS and publishes before physical-cover admission: "+r.state());return;}
                var message=observation.get();c.assertTrue(scoutSaw[0]&&r.infantry.getTarget()==null&&!r.infantry.canSee(r.victim)
                    &&message.scout().equals(r.scout.getUuid())&&message.target().equals(r.victim.getUuid())&&message.expiresAt()==message.observedAt()+60,"first true observation reaches an actually occluded ground receiver");
                encloseRealScout(c,r);original[0]=message;closureHealth[0]=r.victim.getHealth();
                System.out.println("SHAPES_PHYSICAL_RELAY_COVER tick="+r.ticks+" worldTick="+r.world.getTime()+" message="+message+" actualVictimHealth="+closureHealth[0]);
            }
            c.assertTrue(r.scout.isAlive()&&!r.scout.isAiDisabled()&&r.scout.hasInfantryGun()&&!r.scout.canSee(r.victim),"scout stays alive, armed and normal AI, with actual material blocking LOS");
            var current=DroneSquadCoordinator.message(r.infantry);long now=r.world.getTime();
            if(now<original[0].expiresAt())c.assertTrue(current.isPresent()&&current.get().equals(original[0]),"opaque original observer cannot publish hidden coordinates or renew the copied observation");
            else{expired[0]=true;c.assertTrue(current.isEmpty()&&!DroneSquadCoordinator.valid(r.infantry,original[0]),"real original60tick TTL expires despite receiver own combat");}
            actualHealthDamage[0]|=r.victim.getHealth()<closureHealth[0]&&r.victim.getAttacker()==r.infantry;
            if(r.ticks<240&&!(r.preOwnInvestigation&&r.ownTarget&&r.movingShot&&r.attributedDamage&&actualHealthDamage[0]&&expired[0]))return;
            c.assertTrue(scoutSaw[0]&&r.firstMailPos!=null&&r.preOwnInvestigation&&r.ownTarget&&r.movingShot&&r.attributedDamage&&actualHealthDamage[0]&&expired[0],
                "new physical-cover relay retains actual wingman movement/own target/moving shots/attributed HEALTH damage and TTL, inside original240ticks: "+r.state());
            c.assertTrue(Math.abs(r.infantry.getY()-r.start.y)<.1,"real ground route, no flight or teleport fallback");
            System.out.println("SHAPES_PHYSICAL_RELAY_PASS "+r.state()+" healthAtCover="+closureHealth[0]+" actualHealthNow="+r.victim.getHealth()+" originalExpired="+expired[0]);r.complete();
        });
    }
    private static void encloseRealScout(TestContext c,ShapesFixture r){
        var centre=r.scout.getBlockPos();var cells=new ArrayList<BlockPos>();
        for(int x=-1;x<=1;x++)for(int y=0;y<=4;y++)for(int z=-1;z<=1;z++)
            if(Math.abs(x)==1||Math.abs(z)==1||y==4)cells.add(centre.add(x,y,z));
        c.assertTrue(cells.size()==41&&cells.size()<=48,"fixed41material edits, bounded48cell admission");
        for(var at:cells){
            c.assertTrue(WorldEditSafety.mayChange(r.world,at,r.scout),"fixture system edit must have known loaded halo and world bounds before state reads: "+at);
            var state=r.world.getBlockState(at);c.assertTrue(state.isAir()&&state.getFluidState().isEmpty()&&r.world.getBlockEntity(at)==null,"only empty dry fixture air may be replaced, no protected material/block entity: "+at);
            c.assertTrue(!new Box(at).intersects(r.scout.getBoundingBox().contract(1e-6)),"new material must not clip or push the actual scout body");
        }
        var position=r.scout.getPos();var velocity=r.scout.getVelocity();var held=r.scout.getMainHandStack().copy();float health=r.scout.getHealth(),energy=r.scout.getWeaponEnergy();int cooldown=r.scout.getAttackCooldownTicks();
        var candidates=new ArrayList<ProjectileEntity>();r.world.collectEntitiesByType(TypeFilter.instanceOf(ProjectileEntity.class),r.scout.getBoundingBox().expand(32),Entity::isAlive,candidates,96);
        var existing=candidates.stream().filter(p->p.getOwnerLiving()==r.scout).toList();
        for(var at:cells)r.block(at,Blocks.BEDROCK);
        c.assertTrue(r.world.isSpaceEmpty(r.scout,r.scout.getBoundingBox())&&!r.scout.canSee(r.victim),"actual opaque geometry closes LOS without body penetration");
        c.assertTrue(r.scout.getPos().equals(position)&&r.scout.getVelocity().equals(velocity)&&r.scout.getHealth()==health&&r.scout.getWeaponEnergy()==energy
            &&r.scout.getAttackCooldownTicks()==cooldown&&net.minecraft.item.ItemStack.areEqual(held,r.scout.getMainHandStack())&&r.scout.hasInfantryGun()&&!r.scout.isAiDisabled(),"physical closure cannot alter actor/weapon/cooldown state");
        for(var shot:existing)c.assertTrue(!shot.isRemoved()&&r.world.getEntity(shot.getUuid())==shot,"already emitted Scout rounds are retained for normal engine flight/collision");
        System.out.println("SHAPES_SCOUT_OPAQUE_EDITS count="+cells.size()+" scout="+r.scout.getUuid()+" retainedLiveRoundIds="+existing.stream().map(Entity::getUuid).toList()+" cooldown="+cooldown+" health="+health+" energy="+energy);
    }
}
