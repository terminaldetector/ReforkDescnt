package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.world.contraption.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual weapon entry points, not a second damage implementation in the tests. */
public final class BodyCombatGameTests implements FabricGameTest {
    private BlockBodyEntity line(TestContext c,int count) {
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int x=0;x<count;x++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState()));
        for(int x=1249;x<=1258;x++)for(int z=1249;z<=1251;z++)c.getWorld().getChunk(x,z);
        return BlockBodyEntity.createLocal(c.getWorld(),cells,new Vec3d(20000,180,20000));
    }
    private Set<Integer> ids(TestContext c){var set=new HashSet<Integer>();BodySpatialIndex.loaded(c.getWorld()).forEach(b->set.add(b.getId()));return set;}
    private List<BlockBodyEntity> added(TestContext c,Set<Integer> before){return BodySpatialIndex.loaded(c.getWorld()).stream().filter(b->!before.contains(b.getId())).toList();}
    private void clean(TestContext c,BlockBodyEntity root,Set<Integer> before){added(c,before).forEach(BlockBodyEntity::discard);root.discard();}
    private Vec3d front(BlockBodyEntity body,int x){return body.localToWorld(new Vec3d(x+.5,.5,0));}
    private Vec3d aim(BlockBodyEntity body){var d=body.rotation().rotate(new Vec3(0,0,1));return new Vec3d(d.x(),d.y(),d.z());}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-combat-hitscan")
    public void actualHitscanAccumulatesLocalMaterialDamageAndSeversRotatedBridge(TestContext c) {
        var body=line(c,5);var before=ids(c);var owner=EntityType.ZOMBIE.create(c.getWorld());
        body.physics().withRotation(new Quat(.2,.3,.4,.7).normalized());Vec3d direction=aim(body),impact=front(body,2),start=impact.subtract(direction.multiply(2));owner.setPosition(start);
        try {
            WeaponCore.hitscan(owner,start,direction,1000,30,DamageClass.KINETIC,null,false,false);
            c.assertTrue(body.cells().size()==5&&Math.abs(body.materialDamage(new BlockPos(2,0,0))-30)<1e-8,"first real ray hit damages only the struck local iron block");
            c.assertTrue(body.materialDamage(BlockPos.ORIGIN)==0,"world coordinates cannot select a different local cell");
            WeaponCore.hitscan(owner,start,direction,1000,30,DamageClass.KINETIC,null,false,false);
            c.assertTrue(body.getLocalBlock(new BlockPos(2,0,0)).isAir()&&added(c,before).size()==1,"second hit cuts the bridge into two real physical bodies");
            c.assertTrue(body.cells().size()==2&&added(c,before).getFirst().cells().size()==2,"only the hit block is destroyed");
        }finally{clean(c,body,before);owner.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-combat-projectile")
    public void actualProjectileCollisionDestroysLocalMaterialAndKeepsRemainingVelocity(TestContext c) {
        var body=line(c,5);var before=ids(c);var shot=ModEntities.PROJECTILE.create(c.getWorld());
        body.physics().withRotation(new Quat(.3,-.2,.1,.8).normalized()).withLinearVelocity(new Vec3(2,1,3));
        var impact=front(body,2);var direction=aim(body);
        shot.setPosition(impact.subtract(direction.multiply(2)));shot.setVelocity(direction.multiply(4));shot.setDirectDamage(60);
        try {
            c.assertTrue(c.getWorld().spawnEntity(shot),"real projectile enters server world");shot.tick();
            c.assertTrue(shot.isRemoved(),"projectile reaches the actual rotated collider in one tick");
            var child=added(c,before);c.assertTrue(child.size()==1&&body.getLocalBlock(new BlockPos(2,0,0)).isAir(),"projectile's contact handler severs the local bridge");
            c.assertTrue(body.physics().linearVelocity().length()>2&&child.getFirst().physics().linearVelocity().length()>2,"damage cannot replace the moving hull with stationary fragments");
        }finally{shot.discard();clean(c,body,before);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-combat-large-splash")
    public void weaponSplashHitsNearbyMaterialEvenWhenHullComIsOutsideRadius(TestContext c) {
        var body=line(c,128);var before=ids(c);var owner=EntityType.ZOMBIE.create(c.getWorld());var origin=body.localToWorld(new Vec3d(0,.5,.5));
        try {
            c.assertTrue(body.physicsCentre().distanceTo(origin)>60,"fixture really places the hull COM outside this tiny blast");
            WeaponCore.splashDamage(owner,c.getWorld(),origin,120,.75f,DamageClass.EXPLOSIVE);
            c.assertTrue(body.getLocalBlock(BlockPos.ORIGIN).isAir()&&body.cells().size()==127,"sphere/local-box geometry includes the near end of a large hull");
            c.assertTrue(!body.getLocalBlock(new BlockPos(1,0,0)).isAir(),"out-of-radius material is preserved");
            c.assertTrue(body.physics().linearVelocity().length()>0,"near-surface splash applies a physical impulse too");
        }finally{clean(c,body,before);owner.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-combat-partial-persistence")
    public void partialMaterialDamageFollowsItsFragmentAndSurvivesNbtWithoutHealing(TestContext c) {
        var body=line(c,5);var before=ids(c);var pos=new BlockPos(4,0,0);var clone=ModEntities.BLOCK_BODY.create(c.getWorld());
        try {
            body.weaponHit(front(body,4),aim(body),25);body.weaponHit(front(body,2),aim(body),60);
            var parts=added(c,before);c.assertTrue(parts.size()==1,"bridge splits while another block has partial damage");
            var part=body.getLocalBlock(pos).isAir()?parts.getFirst():body;
            c.assertTrue(part.materialDamage(pos)==25,"partial damage belongs to the detached material, not just its old entity");
            clone.readNbt(part.writeNbt(new NbtCompound()));c.assertTrue(clone.materialDamage(pos)==25,"NBT preserves partial block damage");
            clone.weaponHit(front(clone,4),aim(clone),35);
            c.assertTrue(clone.getLocalBlock(pos).isAir(),"reloaded material needs only its remaining strength to break");
            c.assertTrue(added(c,before).size()==1,"unspawned NBT inspection cannot add a duplicate to the live index");
        }finally{clone.discard();clean(c,body,before);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-combat-partial-blast")
    public void oneBlastTransfersPartialDamageOnTheSameBatchThatSeparatesAFragment(TestContext c) {
        var body=line(c,5);var before=ids(c);
        try {
            // Centre block receives90 and breaks; adjacent iron receives45 and remains damaged.
            body.weaponBlast(body.localToWorld(new Vec3d(2.5,.5,.5)),90,1);
            var parts=added(c,before);c.assertTrue(parts.size()==1,"one atomic radial edit breaks the middle bridge");
            var part=body.getLocalBlock(new BlockPos(3,0,0)).isAir()?parts.getFirst():body;
            c.assertTrue(Math.abs(part.materialDamage(new BlockPos(3,0,0))-45)<1e-8,"new partial damage travels with material detached by that same blast");
            c.assertTrue(body.shapeRevision()==3,"parent records one combined middle-block and detached-cell edit, not repeated rebuilds per blast cell");
        }finally{clean(c,body,before);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-combat-invalid")
    public void invalidDamageDoesNotPoisonPhysicsOrGridAndReplacementGetsNewStrength(TestContext c) {
        var body=line(c,2);var before=ids(c);var impact=front(body,0);var direction=aim(body);
        try {
            body.weaponHit(impact,direction,Float.NaN);body.weaponBlast(impact,100,Double.NaN);body.weaponBlast(new Vec3d(Double.NaN,0,0),100,1);
            c.assertTrue(body.cells().size()==2&&body.materialDamage(BlockPos.ORIGIN)==0&&body.physics().linearVelocity().length()==0,"non-finite damage never reaches material or momentum");
            body.weaponHit(impact,direction,30);c.assertTrue(body.materialDamage(BlockPos.ORIGIN)==30,"valid partial damage exists");
            body.setLocalBlock(BlockPos.ORIGIN,Blocks.GOLD_BLOCK.getDefaultState());
            c.assertTrue(body.materialDamage(BlockPos.ORIGIN)==0,"construction replacement cannot inherit damage of removed material");
        }finally{clean(c,body,before);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-combat-vanilla-blast")
    public void vanillaExplosionDamagesLocalHullOnceAndRespectsItsDestructionMode(TestContext c) {
        var body=line(c,5);var before=ids(c);
        var profile=com.terminaldetector.drmd.physics.PhysicsProfileState.get(c.getWorld());var old=profile.profile();
        profile.set(com.terminaldetector.drmd.physics.PhysicsProfile.EXPANDED);
        try {
            var centre=body.localToWorld(new Vec3d(2.5,.5,.5));
            var denied=new net.minecraft.world.explosion.Explosion(c.getWorld(),null,centre.x,centre.y,centre.z,3,false,net.minecraft.world.explosion.Explosion.DestructionType.KEEP);
            denied.affectWorld(false);
            c.assertTrue(body.cells().size()==5&&body.materialDamage(new BlockPos(2,0,0))==0,"KEEP explosion mode does not destroy local material");
            var blast=new net.minecraft.world.explosion.Explosion(c.getWorld(),null,centre.x,centre.y,centre.z,3,false,net.minecraft.world.explosion.Explosion.DestructionType.DESTROY);
            blast.affectWorld(false);
            c.assertTrue(body.getLocalBlock(new BlockPos(2,0,0)).isAir(),"vanilla explosion callback reaches moving hull material");
            var parts=added(c,before);c.assertTrue(parts.size()==1,"single local-grid blast can detach two end cells");
            c.assertTrue(parts.getFirst().materialDamage(new BlockPos(4,0,0))==54||body.materialDamage(BlockPos.ORIGIN)==54,"new fragments receive this blast's partial load exactly once");
        }finally{profile.set(old);clean(c,body,before);}c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="hull-combat-blast-admission")
    public void refusedMassDestructionCannotPartiallyConsumeOrDamageHundredsOfComponents(TestContext c) {
        var cells=new ArrayList<BlockBodyEntity.Cell>();
        for(int x=0;x<40;x++)for(int z=0;z<40;z++) {
            cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,z),Blocks.GLASS.getDefaultState()));
            if(x%2==0&&z%2==0)for(int y=1;y<=2;y++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
        }
        var body=BlockBodyEntity.createLocal(c.getWorld(),cells,new Vec3d(20000,180,20000));var before=ids(c);
        try {
            body.weaponBlast(body.physicsCentre(),50,1000);
            c.assertTrue(body.cells().size()==2400&&body.shapeRevision()==0&&added(c,before).isEmpty(),"excess-fragment admission refuses the entire radial edit");
            c.assertTrue(body.materialDamage(new BlockPos(0,1,0))==0,"refused destruction cannot silently injure surviving components");
        }finally{clean(c,body,before);}c.complete();
    }

}
