package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.entity.mob.ShapesInfantryEntity;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.weapon.core.*;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** New role identity, real physical shots and negative admission; no claim of native visual acceptance. */
public final class ShapesInfantryGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shapes-identity")
    public void tallArmedIdentityAndOrdinaryCombatStateSurviveActualNbtFactory(TestContext c){
        var shape=ModEntities.SHAPES_INFANTRY.create(c.getWorld());var vanilla=EntityType.ENDERMAN.create(c.getWorld());Entity copy=null;
        try{
            c.assertTrue(shape!=null&&vanilla!=null&&shape.getType()!=EntityType.ENDERMAN&&shape.getClass()==ShapesInfantryEntity.class,"separate role, no vanilla Enderman substitution");
            c.assertTrue(Math.abs(shape.getWidth()-.6)<1e-6&&Math.abs(shape.getHeight()-2.9)<1e-6&&Math.abs((shape.getEyePos().y-shape.getY())-2.55)<1e-6,"actual tall physical silhouette and eye position");
            c.assertTrue(shape.getMaxHealth()==vanilla.getMaxHealth()&&shape.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)==vanilla.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                &&!shape.hasNoGravity()&&!shape.isAiDisabled()&&shape.hasInfantryGun(),"vanilla Enderman baseline and actual armed active ground actor");
            c.assertTrue(Arrays.asList(ModItems.creativeSpawnEggs()).contains(ModItems.EGG_SHAPES_INFANTRY)&&Registries.ITEM.containsId(Identifier.of("drmd","spawn_egg_shapes_infantry")),"own discoverable spawn egg");
            shape.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(4,8,4))));shape.setVelocity(.1,0,-.2);shape.setHealth(13);
            var tag=new NbtCompound();c.assertTrue(shape.saveSelfNbt(tag),"actual entity save");tag.putFloat("drmdEnergy",37);tag.putInt("shapesAttackCooldown",1);
            copy=EntityType.getEntityFromNbt(tag,c.getWorld()).orElseThrow();var restored=(ShapesInfantryEntity)copy;
            c.assertTrue(copy.getType()==ModEntities.SHAPES_INFANTRY&&copy.getUuid().equals(shape.getUuid())&&restored.getHealth()==13&&restored.getVelocity().equals(shape.getVelocity())
                &&restored.getWeaponEnergy()==37&&restored.getAttackCooldownTicks()==1&&restored.hasInfantryGun(),"real NBT factory preserves identity, equipment, health, motion, energy and pending cooldown");
            c.assertTrue(vanilla.getClass()==net.minecraft.entity.mob.EndermanEntity.class,"ordinary Enderman implementation stays intact");
        }finally{if(shape!=null)shape.discard();if(vanilla!=null)vanilla.discard();if(copy!=null)copy.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shapes-unknown")
    public void unknownRemoteShotCannotMaterializeTerrainOrSpendEnergy(TestContext c){
        var w=c.getWorld();var shape=ModEntities.SHAPES_INFANTRY.create(w);var victim=EntityType.COW.create(w);var at=new Vec3d(1740000.5,130,2450000.5);
        shape.setPosition(at);victim.setPosition(at.add(8,0,0));var pos=BlockPos.ofFloored(at);
        try{c.assertTrue(w.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)==null,"remote premise is genuinely unknown");
            float energy=shape.getWeaponEnergy();c.assertTrue(!shape.tryFireAt(victim),"unknown geometry cannot authorize a shot");
            c.assertTrue(shape.getWeaponEnergy()==energy&&shape.getAttackCooldownTicks()==0&&w.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)==null,"no energy/cooldown spend or raw-ray column generation");
        }finally{shape.discard();victim.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shapes-natural-fire",tickLimit=260)
    public void ordinaryGroundGoalNaturallyAcquiresMovesFiresAndDamages(TestContext c){
        var rig=new ShapesFixture(c,0,false,false);rig.drive(r->{
            if(r.ticks<160&&!(r.ownTarget&&r.movingShot&&r.attributedDamage&&r.infantry.getPos().squaredDistanceTo(r.start)>1))return;
            c.assertTrue(r.ownTarget&&r.movingShot&&r.attributedDamage&&r.infantry.getPos().squaredDistanceTo(r.start)>1,"natural ground navigation, own target, actual moving owned laser and attributed damage: "+r.state());
            c.assertTrue(r.infantry.isOnGround()&&Math.abs(r.infantry.getY()-r.start.y)<.1,"ordinary floor contact rather than flying/teleport fallback");
            System.out.println("SHAPES_NATURAL_FIRE_PASS "+r.state());r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shapes-guards",tickLimit=160)
    public void materialEquipmentEnergyCooldownAndAllegianceGuardTheRealWeapon(TestContext c){
        var rig=new ShapesFixture(c,512,true,false);var player=c.createMockCreativeServerPlayerInWorld();rig.extra.add(player);player.setPosition(rig.start.add(2,0,0));
        rig.drive(r->{
            float before=r.infantry.getWeaponEnergy();c.assertTrue(!r.infantry.tryFireAt(r.victim)&&before==r.infantry.getWeaponEnergy()&&r.infantry.getAttackCooldownTicks()==0,"real bedrock cover refuses before spend");
            c.assertTrue(!r.infantry.tryFireAt(player),"Creative is not an enemy");player.changeGameMode(GameMode.SPECTATOR);c.assertTrue(!r.infantry.tryFireAt(player),"Spectator is not an enemy");
            var ally=EntityType.ENDERMAN.create(r.world);ally.setPosition(r.start.add(2,0,0));r.extra.add(ally);c.assertTrue(!r.infantry.tryFireAt(ally),"existing actual End-faction ally cannot be fired upon");
            for(int y=0;y<=4;y++)for(int z=-2;z<=2;z++)r.block(r.base.add(4,y,z),Blocks.AIR);
            r.infantry.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);c.assertTrue(!r.infantry.tryFireAt(r.victim),"empty equipment does not fire a hidden loadout");
            r.infantry.equipStack(EquipmentSlot.MAINHAND,new ItemStack(net.minecraft.item.Items.STICK));c.assertTrue(!r.infantry.tryFireAt(r.victim),"replaced equipment does not fire a hidden loadout");
            r.infantry.equipStack(EquipmentSlot.MAINHAND,new ItemStack(ModItems.LASER));var tag=new NbtCompound();r.infantry.writeCustomDataToNbt(tag);tag.putFloat("drmdEnergy",0);r.infantry.readCustomDataFromNbt(tag);
            c.assertTrue(!r.infantry.tryFireAt(r.victim)&&r.infantry.getWeaponEnergy()==0&&r.infantry.getAttackCooldownTicks()==0,"empty saved capacitor cannot create a shot or cooldown");
            tag.putFloat("drmdEnergy",100);r.infantry.readCustomDataFromNbt(tag);c.assertTrue(r.infantry.tryFireAt(r.victim),"same real weapon fires when cover/equipment/energy become admissible");
            float after=r.infantry.getWeaponEnergy();c.assertTrue(after==100-AiRole.LASER.weaponCost&&r.infantry.getAttackCooldownTicks()==MathHelper.ceil(AiRole.LASER.weaponCooldown*20),"exact existing LASER cost/cooldown");
            c.assertTrue(!r.infantry.tryFireAt(r.victim)&&r.infantry.getWeaponEnergy()==after,"same-tick repeated trigger cannot bypass cooldown");
            c.assertTrue(!r.world.getEntitiesByClass(ProjectileEntity.class,r.infantry.getBoundingBox().expand(4),p->p.getOwnerLiving()==r.infantry).isEmpty(),"positive action emitted an actual owned projectile");r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shapes-hull-cover",tickLimit=160)
    public void rotatedMaterialHullBlocksTheActualWeaponAdmission(TestContext c){
        var rig=new ShapesFixture(c,1024,false,false);rig.victim.setPosition(rig.start.add(8,0,0));rig.infantry.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        rig.drive(r->{
            var from=r.infantry.getPos().add(0,r.infantry.getHeight()*.72,.35);var to=r.victim.getBoundingBox().getCenter();var centre=from.lerp(to,.5);
            var q=ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.7);var shift=q.rotate(new Vec3(.5,.5,.5));
            var body=BlockBodyEntity.createLocal(r.world,List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState())),centre.add(-shift.x(),-shift.y(),-shift.z()),q,Vec3d.ZERO,Vec3d.ZERO);body.setPhysicsGravity(false);r.extra.add(body);
            c.assertTrue(body.physicsRaycast(from,to).isPresent(),"physical rotated material is actually on the gun ray");r.infantry.equipStack(EquipmentSlot.MAINHAND,new ItemStack(ModItems.LASER));
            float energy=r.infantry.getWeaponEnergy();c.assertTrue(!r.infantry.tryFireAt(r.victim)&&r.infantry.getWeaponEnergy()==energy&&r.infantry.getAttackCooldownTicks()==0,"real rotated hull cover, not AABB proxy, refuses before spend");r.complete();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="shapes-projectile-damage",tickLimit=180)
    public void aRealMovingProjectileHurtsShapesWithoutVanillaTeleportImmunity(TestContext c){
        var rig=new ShapesFixture(c,1536,false,false);rig.infantry.setAiDisabled(true);var owner=c.createMockCreativeServerPlayerInWorld();rig.extra.add(owner);owner.setPosition(rig.start.add(-8,0,0));
        ProjectileEntity[] fired={null};Vec3d[] old={rig.start};double[] maximumStep={0};boolean[] movement={false};
        rig.drive(r->{
            if(fired[0]==null){var cfg=new WeaponCore.FireConfig();cfg.owner=owner;cfg.pos=r.infantry.getPos().add(-4,1.5,0);cfg.dir=new Vec3d(1,0,0);cfg.speedIsSourceUnits=false;cfg.speed=1;cfg.directDamage=4;cfg.life=2;cfg.dmgClass=DamageClass.KINETIC;
                fired[0]=WeaponCore.fireProjectile(cfg);c.assertTrue(fired[0]!=null,"real ordinary projectile factory emitted a live round");r.extra.add(fired[0]);return;}
            movement[0]|=fired[0].getX()>r.start.x-3.5;maximumStep[0]=Math.max(maximumStep[0],r.infantry.getPos().distanceTo(old[0]));old[0]=r.infantry.getPos();
            if(r.infantry.getHealth()==r.infantry.getMaxHealth()&&r.ticks<24)return;
            c.assertTrue(movement[0]&&r.infantry.getHealth()<r.infantry.getMaxHealth()&&r.infantry.getAttacker()==owner&&maximumStep[0]<1,"actual moving shot causes attributed ordinary damage, no immune teleport: hp="+r.infantry.getHealth()+" maxStep="+maximumStep[0]);r.complete();
        });
    }
}
