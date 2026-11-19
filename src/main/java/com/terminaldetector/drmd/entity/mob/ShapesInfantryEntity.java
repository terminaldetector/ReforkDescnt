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
    public boolean hasInfantryGun(){return getMainHandStack().isOf(ModItems.LASER);}
    public boolean validEnemy(LivingEntity target){
        return target!=null&&target.isAlive()&&!target.isRemoved()&&target.getWorld()==getWorld()
            &&HostileEnvironment.isHostileTarget(this,target)&&!isTeammate(target)&&canTarget(target);
    }
    /** Admission stays in the actor; even a direct caller cannot fire through unknown geometry or at an ally. */
    public boolean tryFireAt(LivingEntity target){
        var role=AiRole.LASER;
        if(getWorld().isClient||!isAlive()||isRemoved()||isAiDisabled()||attackCooldown>0||!hasInfantryGun()
            ||energy<role.weaponCost||!validEnemy(target))return false;
        var from=muzzle(.35,.72);
        if(!ShapesCombatGoal.clearShot(this,from,target)||!canSee(target))return false;
        var cfg=shot(from,aimAt(from,target),role.weaponSpeed,role.weaponDamage,role.weaponClass,4);
        cfg.splashDamage=role.weaponSplash;cfg.splashRadius=role.weaponSplashRadius;
        cfg.homing=role.weaponHoming;cfg.homeTarget=role.weaponHoming?target:null;
        cfg.meshKind=ProjectileEntity.MESH_BOLT;
        if(WeaponCore.fireProjectile(cfg)==null)return false;
        drawEnergy(role.weaponCost);attackCooldown=Math.max(1,MathHelper.ceil(role.weaponCooldown*20));
        return true;
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt){
        super.writeCustomDataToNbt(nbt);nbt.putInt("shapesAttackCooldown",attackCooldown);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt){
        super.readCustomDataFromNbt(nbt);
        attackCooldown=MathHelper.clamp(nbt.getInt("shapesAttackCooldown"),0,Math.max(1,MathHelper.ceil(AiRole.LASER.weaponCooldown*20)));
        // Optional old fields keep defaults; malformed energy never grants unlimited fire.
        if(!Float.isFinite(energy))energy=0;energy=MathHelper.clamp(energy,0,energyMax);
    }
}
