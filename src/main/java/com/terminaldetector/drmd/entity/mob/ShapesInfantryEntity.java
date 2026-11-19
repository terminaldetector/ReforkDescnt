package com.terminaldetector.drmd.entity.mob;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.items.ModItems;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** Armed Enderman-derived infantry: physical ground movement and ordinary CyberMob damage, no teleport. */
public final class ShapesInfantryEntity extends CyberMobEntity {
    private int attackCooldown;
    public ShapesInfantryEntity(EntityType<? extends ShapesInfantryEntity> type,World world){
        super(type,world);
        equipStack(EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(ModItems.LASER));
    }
    /** Vanilla Enderman baseline, not the drone LASER hull/flight statistics. */
    public static DefaultAttributeContainer.Builder createShapesAttributes(){return EndermanEntity.createEndermanAttributes();}
    @Override protected void initGoals(){
        goalSelector.add(0,new SwimGoal(this));
        goalSelector.add(0,new com.terminaldetector.drmd.world.threat.RocketSiegeGoal(this));
        goalSelector.add(1,new ShapesCombatGoal(this));
        goalSelector.add(3,new ShapesInvestigateGoal(this));
        goalSelector.add(7,new WanderAroundFarGoal(this,1));
        goalSelector.add(8,new LookAroundGoal(this));
        HostileEnvironment.installTargets(this,targetSelector);
    }
    @Override public void tick(){
        super.tick();
        if(!getWorld().isClient){if(attackCooldown>0)attackCooldown--;DroneSquadCoordinator.tick(this);}
    }
    public float getWeaponEnergy(){return energy;}
    public int getAttackCooldownTicks(){return attackCooldown;}
    public AiRole infantryRole(){return getMainHandStack().isOf(ModItems.ROCKET_DUAL)?AiRole.RPG:AiRole.LASER;}
    public boolean hasInfantryGun(){return getMainHandStack().isOf(ModItems.LASER)||getMainHandStack().isOf(ModItems.ROCKET_DUAL);}
    @Override public boolean cannotDespawn(){return com.terminaldetector.drmd.world.threat.RocketSiegeRuntime.leasesDespawn(this)||super.cannotDespawn();}
    public boolean validEnemy(LivingEntity target){
        return target!=null&&target.isAlive()&&!target.isRemoved()&&target.getWorld()==getWorld()
            &&HostileEnvironment.isHostileTarget(this,target)&&!isTeammate(target)&&canTarget(target);
    }
    /** Admission stays in the actor; even a direct caller cannot fire through unknown geometry or at an ally. */
    public boolean tryFireAt(LivingEntity target){
        var role=infantryRole();
        if(getWorld().isClient||!isAlive()||isRemoved()||isAiDisabled()||attackCooldown>0||!hasInfantryGun()
            ||com.terminaldetector.drmd.world.threat.RocketSiegeRuntime.ordered(this)||energy<role.weaponCost||!validEnemy(target))return false;
        var from=muzzle(.35,.72);
        if(!ShapesCombatGoal.clearShot(this,from,target)||!canSee(target))return false;
        var cfg=shot(from,aimAt(from,target),role.weaponSpeed,role.weaponDamage,role.weaponClass,4);
        cfg.splashDamage=role.weaponSplash;cfg.splashRadius=role.weaponSplashRadius;
        cfg.homing=role.weaponHoming;cfg.homeTarget=role.weaponHoming?target:null;
        cfg.meshKind=role==AiRole.RPG?ProjectileEntity.MESH_ROCKET:ProjectileEntity.MESH_BOLT;
        if(WeaponCore.fireProjectile(cfg)==null)return false;
        drawEnergy(role.weaponCost);attackCooldown=Math.max(1,MathHelper.ceil(role.weaponCooldown*20));
        return true;
    }
    /** Existing projectile pipeline, with a current nonserializable order capability.
     * Normal target goals cannot issue an ordered pair's shots or bypass its finite allowance. */
    public ProjectileEntity tryFireSiege(com.terminaldetector.drmd.world.threat.SiegePlan.FirePermit permit){
        var role=infantryRole();
        if(role!=AiRole.RPG||getWorld().isClient||!isAlive()||isRemoved()||isAiDisabled()||attackCooldown>0||energy<role.weaponCost
            ||!com.terminaldetector.drmd.world.threat.RocketSiegeRuntime.current(this,permit))return null;
        var p=permit.point();var point=new net.minecraft.util.math.Vec3d(p.x(),p.y(),p.z());var from=muzzle(.35,.72);
        if(!FlightRouteSpace.clearSight(this,getEyePos(),from,64))return null;
        var cfg=shot(from,point.subtract(from).normalize(),role.weaponSpeed,role.weaponDamage,role.weaponClass,4);
        cfg.inheritedVelocity=getVelocity();cfg.splashDamage=role.weaponSplash;cfg.splashRadius=role.weaponSplashRadius;cfg.meshKind=ProjectileEntity.MESH_ROCKET;
        var aim=com.terminaldetector.drmd.weapon.core.InterceptAim.solve(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(point.x-from.x,point.y-from.y,point.z-from.z),
            new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(-cfg.inheritedVelocity.x,-cfg.inheritedVelocity.y,-cfg.inheritedVelocity.z),
            (float)(com.terminaldetector.drmd.DescentMod.su(cfg.speed)/com.terminaldetector.drmd.DescentMod.TICKS_PER_SECOND),cfg.life*20);
        if(aim.isPresent()){var d=aim.get().direction();cfg.dir=new net.minecraft.util.math.Vec3d(d.x(),d.y(),d.z());}
        var projectile=WeaponCore.fireProjectile(cfg);if(projectile==null)return null;
        drawEnergy(role.weaponCost);attackCooldown=Math.max(1,MathHelper.ceil(role.weaponCooldown*20));return projectile;
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt){
        super.writeCustomDataToNbt(nbt);nbt.putInt("shapesAttackCooldown",attackCooldown);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt){
        super.readCustomDataFromNbt(nbt);
        attackCooldown=MathHelper.clamp(nbt.getInt("shapesAttackCooldown"),0,Math.max(1,MathHelper.ceil(infantryRole().weaponCooldown*20)));
        // Optional old fields keep defaults; malformed energy never grants unlimited fire.
        if(!Float.isFinite(energy))energy=0;energy=MathHelper.clamp(energy,0,energyMax);
    }
}
