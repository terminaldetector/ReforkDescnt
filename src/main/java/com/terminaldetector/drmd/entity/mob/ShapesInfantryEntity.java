package com.terminaldetector.drmd.entity.mob;

import com.terminaldetector.drmd.ai.*;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.entity.model.ShapesWreckRig;
import com.terminaldetector.drmd.physics.GravyPhysics;
import com.terminaldetector.drmd.physics.ragdoll.ActorImpact;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.gravity.ActorFrameMath;
import com.terminaldetector.drmd.world.gravity.ActorFrames;
import com.terminaldetector.drmd.world.gravity.LocalSurfaceNavigation;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Armed Enderman-derived infantry: physical ground movement and ordinary CyberMob damage, no teleport. */
public final class ShapesInfantryEntity extends CyberMobEntity {
    private static final TrackedData<Integer> HIT_REACTION =
            DataTracker.registerData(ShapesInfantryEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final int REACTION_TICKS = 7;
    private int attackCooldown;
    private boolean wreckAttempted;
    private int reactionAge;
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
        if(!getWorld().isClient){
            if(attackCooldown>0)attackCooldown--;
            if (dataTracker.get(HIT_REACTION) != 0 && age - reactionAge >= REACTION_TICKS)
                dataTracker.set(HIT_REACTION, 0);
            if (controlSuspended()) getNavigation().stop();
            else DroneSquadCoordinator.tick(this);
        }
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(HIT_REACTION, 0);
    }

    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (HIT_REACTION.equals(data)) reactionAge = age;
    }

    @Override public boolean damage(DamageSource source, float amount) {
        float beforeShield = shield, beforeArmor = armor;
        boolean damaged = super.damage(source, amount);
        if ((!damaged && beforeShield <= shield && beforeArmor <= armor) || !isAlive() || !(source instanceof ActorImpact hit)
                || !RagdollEntity.combatPhysicsActive(getWorld())) return damaged;

        hit.applyLiveRecoil(this, ShapesWreckRig.mass(getScale()));
        ShapesWreckRig.Part part = ShapesWreckRig.nearestPart(this, hit.point());
        Vec3d side = Vec3d.fromPolar(0, bodyYaw - 90);
        if (ActorFrames.active(this)) side = ActorFrameMath.toWorld(side, ActorFrames.up(this));
        double sideImpulse = hit.impulse().dotProduct(side);
        int magnitude = MathHelper.clamp((int) Math.ceil(hit.impulse().length()), 1, 15);
        int event = (dataTracker.get(HIT_REACTION) & ~0xff) + 0x100;
        dataTracker.set(HIT_REACTION, event | (part.ordinal() << 5) | (sideImpulse < 0 ? 0x10 : 0) | magnitude);
        return damaged;
    }

    /** One packed part reaction per accepted hit, decaying over seven client ticks. */
    public float hitTilt(ShapesWreckRig.Part part, float animationAge) {
        int data = dataTracker.get(HIT_REACTION);
        if (data == 0 || ((data >>> 5) & 7) != part.ordinal()) return 0;
        float remaining = MathHelper.clamp(1 - (animationAge - reactionAge) / REACTION_TICKS, 0, 1);
        return ((data & 0x10) == 0 ? 1 : -1) * (data & 15) * .028f * remaining * remaining;
    }

    @Override public void onDeath(DamageSource source) {
        boolean replaced = false;
        if (!getWorld().isClient && !wreckAttempted) {
            wreckAttempted = true;
            // Capture the last armed pose before normal equipment drops and death cleanup.
            replaced = RagdollEntity.spawnFromShapes(this, source) != null;
        }
        super.onDeath(source);
        if (replaced) setInvisible(true);
    }

    public float getWeaponEnergy(){return energy;}
    public int getAttackCooldownTicks(){return attackCooldown;}
    public AiRole infantryRole(){return getMainHandStack().isOf(ModItems.ROCKET_DUAL)?AiRole.RPG:AiRole.LASER;}
    public boolean hasInfantryGun(){return getMainHandStack().isOf(ModItems.LASER)||getMainHandStack().isOf(ModItems.ROCKET_DUAL);}
    public boolean controlSuspended() { return GravyPhysics.isCaptured(this); }
    public void navigateInfantry(Vec3d point) {
        if (controlSuspended()) { getNavigation().stop(); return; }
        var start=getPos();
        if(ActorFrames.active(this)&&ActorFrames.up(this).dotProduct(ActorFrameMath.WORLD_UP)<.99999){
            if(!LocalSurfaceNavigation.framed(this)){getNavigation().stop();return;}
            // A copied observation can be above the support plane. Plan on the actor's current
            // chart and let the shared path check material, support, and the full body sweep.
            point=start.add(ActorFrames.tangent(this,point.subtract(start)));
        }
        Vec3d route = aroundWreckage(start, point);
        if (route == null) getNavigation().stop();
        else if(WorldEditSafety.loadedSegment(getWorld(),start,route))getNavigation().startMovingTo(route.x,route.y,route.z,1);
    }

    private Vec3d aroundWreckage(Vec3d feet, Vec3d destination) {
        if (!RagdollEntity.combatPhysicsActive(getWorld())) return destination;
        Vec3d direction = ActorFrames.tangent(this, destination.subtract(feet));
        double distance = direction.length();
        if (distance < .1) return destination;
        direction = direction.normalize();
        Vec3d up = ActorFrames.active(this) ? ActorFrames.up(this) : ActorFrameMath.WORLD_UP;
        Vec3d start = feet.add(up.multiply(.15));
        Vec3d end = start.add(direction.multiply(Math.min(5, distance)));
        var boxes = getWorld().getEntitiesByClass(RagdollEntity.class, new net.minecraft.util.math.Box(start, end)
                .expand(1.75), Entity::isCollidable).stream().limit(8)
                .flatMap(body -> body.partBounds().stream()).map(box -> box.expand(getWidth() * .5)).toList();
        if (boxes.stream().noneMatch(box -> box.raycast(start, end).isPresent())) return destination;
        Vec3d lateral = up.crossProduct(direction).multiply(1.4);
        for (Vec3d offset : new Vec3d[]{lateral, lateral.negate()}) {
            Vec3d detour = feet.add(offset).add(direction.multiply(.7));
            Vec3d step = detour.add(up.multiply(.15));
            if (WorldEditSafety.loadedSegment(getWorld(), feet, detour) && ActorFrames.canOccupy(this, detour, up)
                    && boxes.stream().noneMatch(box -> box.raycast(start, step).isPresent())) return detour;
        }
        return null;
    }
    @Override public boolean cannotDespawn(){return com.terminaldetector.drmd.world.threat.RocketSiegeRuntime.leasesDespawn(this)||super.cannotDespawn();}
    public boolean validEnemy(LivingEntity target){
        return target!=null&&target.isAlive()&&!target.isRemoved()&&target.getWorld()==getWorld()
            &&HostileEnvironment.isHostileTarget(this,target)&&!isTeammate(target)&&canTarget(target);
    }
    /** Admission stays in the actor; even a direct caller cannot fire through unknown geometry or at an ally. */
    public boolean tryFireAt(LivingEntity target){
        var role=infantryRole();
        if(getWorld().isClient||!isAlive()||isRemoved()||isAiDisabled()||attackCooldown>0||!hasInfantryGun()
            ||controlSuspended()||com.terminaldetector.drmd.world.threat.RocketSiegeRuntime.ordered(this)||energy<role.weaponCost||!validEnemy(target))return false;
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
        if(role!=AiRole.RPG||getWorld().isClient||!isAlive()||isRemoved()||isAiDisabled()||controlSuspended()||attackCooldown>0||energy<role.weaponCost
            ||!com.terminaldetector.drmd.world.threat.RocketSiegeRuntime.current(this,permit))return null;
        var p=permit.point();var point=new net.minecraft.util.math.Vec3d(p.x(),p.y(),p.z());var from=muzzle(.35,.72);
        if(!FlightRouteSpace.clearSight(this,getEyePos(),from,64))return null;
        var cfg=shot(from,point.subtract(from).normalize(),role.weaponSpeed,role.weaponDamage,role.weaponClass,4);
        cfg.inheritedVelocity=getVelocity();cfg.splashDamage=role.weaponSplash;cfg.splashRadius=role.weaponSplashRadius;cfg.meshKind=ProjectileEntity.MESH_ROCKET;
        cfg.worldBlast=true; // Finite ordered base pressure uses the existing bounded small crater path.
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
        wreckAttempted = getHealth() <= 0;
        attackCooldown=MathHelper.clamp(nbt.getInt("shapesAttackCooldown"),0,Math.max(1,MathHelper.ceil(infantryRole().weaponCooldown*20)));
        // Optional old fields keep defaults; malformed energy never grants unlimited fire.
        if(!Float.isFinite(energy))energy=0;energy=MathHelper.clamp(energy,0,energyMax);
    }
}
