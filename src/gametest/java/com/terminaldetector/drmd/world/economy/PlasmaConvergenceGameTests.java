package com.terminaldetector.drmd.world.economy;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.weapon.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.util.*;

/** Granted test gun, ordinary Survival item input and real twin physical rounds.
 * Crafting is accepted separately; this fixture grants no energy or target locks. */
public final class PlasmaConvergenceGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="plasma-reticle-victim",tickLimit=450)
    public void realTwinMuzzlesHitANarrowReticleVictimForTheOriginalEightEnergy(TestContext c){run(c,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="plasma-reticle-cover",tickLimit=450)
    public void realOpaqueCoverTerminatesBothBoltsWithoutHiddenDamageOrFreeEnergy(TestContext c){run(c,true);}
    private static void run(TestContext c,boolean covered){EconomyFixture.admitted(c,f->{
        var target=EntityType.COW.create(f.w);c.assertTrue(target!=null,"registered ordinary damageable victim");
        target.setPosition(Vec3d.ofCenter(f.origin).add(0,0,24));target.setNoGravity(true);target.setAiDisabled(true);
        c.assertTrue(f.w.spawnEntity(target),"actual narrow victim admitted");f.extras.add(target.getUuid());
        f.aim(Vec3d.ofCenter(f.origin).add(0,2,0),target.getBoundingBox().getCenter());
        var wall=new ArrayList<BlockPos>();if(covered)for(int x=-2;x<=2;x++)for(int y=0;y<=4;y++){
            var p=f.origin.add(x,y,12);f.setWorld(p,Blocks.BEDROCK.getDefaultState());wall.add(p);
        }
        f.stock(ModItems.PLASMA,1);f.hold(ModItems.PLASMA);
        float energy=DescentPlayerData.get(f.p).getEnergy(),health=target.getHealth();
        var muzzles=WeaponCore.allMuzzles(f.p,"plasma");c.assertTrue(muzzles.size()==2&&muzzles.get(0).distanceTo(muzzles.get(1))>1,"real separated twin anchors, not a centre-origin proxy");
        var result=f.p.interactionManager.interactItem(f.p,f.w,f.p.getMainHandStack(),Hand.MAIN_HAND);
        c.assertTrue(result.isAccepted()&&DescentPlayerData.get(f.p).getEnergy()==energy-8,"one actual Survival use pays its normal eight energy");
        var shots=f.w.getEntitiesByClass(ProjectileEntity.class,new Box(f.p.getPos(),target.getPos()).expand(5),s->f.p.getUuid().equals(s.getOwnerUuid()));
        c.assertTrue(shots.size()==2,"exactly two real registered Plasma rounds");
        var initial=new HashMap<UUID,Vec3d>();for(var shot:shots){f.extras.add(shot.getUuid());initial.put(shot.getUuid(),shot.getPos());
            c.assertTrue(shot.getMeshKind()==ProjectileEntity.MESH_ORB&&shot.getDamageClass()==DamageClass.EXOTIC&&muzzles.stream().anyMatch(p->p.distanceTo(shot.getPos())<1e-7),"actual typed Plasma starts at its retained physical barrel");
            if(!covered)c.assertTrue(target.getBoundingBox().raycast(shot.getPos(),shot.getPos().add(shot.getVelocity().normalize().multiply(40))).isPresent(),"both genuine emitted directions intersect the real reticle silhouette");
        }
        long began=f.w.getTime();boolean[] moved={false};f.observe=()->{
            for(var shot:shots)if(!shot.isRemoved()&&shot.getPos().distanceTo(initial.get(shot.getUuid()))>.1)moved[0]=true;
            if(!covered&&target.getHealth()<health){c.assertTrue(moved[0],"real ordinary projectile travel precedes actual hull damage");f.done();return;}
            if(f.w.getTime()-began<30)return;
            c.assertTrue(covered&&moved[0]&&target.isAlive()&&target.getHealth()==health&&shots.stream().allMatch(ProjectileEntity::isRemoved)
                &&wall.stream().allMatch(p->f.w.getBlockState(p).isOf(Blocks.BEDROCK))&&DescentPlayerData.get(f.p).getEnergy()==energy-8,
                "real nearer material terminates both ordinary shots; no penetration, hidden damage or energy rewrite");f.done();
        };
    });}
}
