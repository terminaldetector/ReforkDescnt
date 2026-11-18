package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import com.terminaldetector.drmd.world.gravity.ActorFrames;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** First living adapter: native dry upright Cow travel, followed by existing material contacts.
 * AI, jump, native acceleration and post-move gravity keep their ordinary owners. No pose anchor.
 * Arbitrary actor frames/ship fields and other species deliberately remain outside this adapter.
 * Like existing hull/player/item contact, this core collision path remains active in STANDARD;
 * PhysicsProfile controls optional destruction/conversion, not already existing hull material. */
public final class LivingBodyContactSystem {
    private static final int MAX_ACTORS = 64, MAX_BODIES = 16;
    private static final double REACH = 4 + 512 * .05;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Map<ServerWorld, Map<UUID, Pending>> PENDING = new WeakHashMap<>();
    private static final Map<ServerWorld, Map<UUID, Support>> SUPPORTS = new WeakHashMap<>();
    private record Support(WeakReference<LivingEntity> actor, long tick, Vec3d surfaceVelocity) {}
    private static final class Pending {
        final WeakReference<LivingEntity> actor;
        final long tick;
        final Vec3d start, moving, previousSurface;
        final double drag;
        Vec3d nativeEnd;
        Pending(LivingEntity actor, Vec3d moving, double drag, Support support) {
            this.actor = new WeakReference<>(actor); tick = actor.getWorld().getTime();
            start = actor.getPos(); this.moving = moving; this.drag = drag;
            previousSurface = support == null ? Vec3d.ZERO : support.surfaceVelocity;
        }
    }
    private LivingBodyContactSystem() {}
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LivingBodyContactSystem::tick);
        ServerWorldEvents.UNLOAD.register((server, world) -> {PENDING.remove(world); SUPPORTS.remove(world);});
    }
    private static boolean eligible(LivingEntity actor) {
        return actor.getClass() == CowEntity.class && actor.getWorld() instanceof ServerWorld
            && actor.isAlive() && actor.getPose() == EntityPose.STANDING && !actor.isBaby()
            && !actor.hasNoGravity() && !actor.hasNoDrag() && !actor.hasVehicle() && !actor.hasPassengers()
            && !actor.noClip && !actor.isTouchingWater() && !actor.isInLava() && !actor.isFallFlying()
            && !actor.isClimbing() && !actor.inPowderSnow && !ActorFrames.active(actor)
            && !actor.hasStatusEffect(StatusEffects.LEVITATION) && !actor.hasStatusEffect(StatusEffects.SLOW_FALLING)
            && GravityFields.sample(actor.getWorld(), actor.getPos()) == null
            && Double.isFinite(actor.getVelocity().lengthSquared()) && actor.getVelocity().lengthSquared() <= 64
            && actor.getWidth() > 0 && actor.getWidth() <= 2 && actor.getHeight() > 0 && actor.getHeight() <= 3;
    }
    private static List<BlockBodyEntity> nearby(LivingEntity actor, Vec3d delta) {
        return BodySpatialIndex.query(actor.getWorld(), actor.getBoundingBox().stretch(delta).expand(REACH));
    }
    private static boolean upright(BodyMotion body) {
        // Refuse roll/pitch and finite fields, rather than quietly applying world-down inside them.
        return !body.hasGravity() && body.rotation(0).rotate(UP).dot(UP) > .999999
            && body.rotation(1).rotate(UP).dot(UP) > .999999;
    }
    private static boolean interacts(LivingEntity actor, BodyMotion body, Vec3d delta) {
        var box = DynamicBodyAttachment.box(BoardingSystem.pure(actor.getPos()), actor.getWidth(), actor.getHeight());
        double margin = .01 + body.travelBound();
        return !body.query(0).contacts(box, margin).isEmpty() || !body.query(1).contacts(box, margin).isEmpty()
            || body.query(0).sweep(box, BoardingSystem.pure(delta), body.position(1).minus(body.position(0))).isPresent();
    }
    /** One actual native applyMovementInput move; its acceleration and later drag/gravity still run.
     * False means unowned native behavior, including UNKNOWN and all unsupported orientations. */
    public static boolean deferMove(LivingEntity actor, Vec3d delta, float slipperiness, boolean grounded) {
        if (!eligible(actor)) return false;
        var world = (ServerWorld)actor.getWorld();
        if (!world.getServer().isOnThread()) throw new IllegalStateException("Living contact is server-owned");
        var pending = PENDING.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
        pending.entrySet().removeIf(e -> e.getValue().tick != world.getTime());
        // A second native travel/move in one tick has another owner: never silently discard it.
        if (pending.size() >= MAX_ACTORS || pending.containsKey(actor.getUuid())) return false;
        var supports = SUPPORTS.get(world);
        var support = supports == null ? null : supports.get(actor.getUuid());
        if (support != null && (support.actor.get() != actor || support.tick != world.getTime() - 1)) support = null;
        var bodies = nearby(actor, delta);
        if (bodies.size() > MAX_BODIES) return false;
        boolean touched = false;
        double reach = .5;
        for (var body : bodies) {
            var motion = body.boardingMotion();
            if (interacts(actor, motion, delta)) {
                if (!upright(motion)) return false;
                touched = true;
            }
            reach = Math.max(reach, .5 + motion.travelBound());
        }
        if (!touched && support == null) return false;
        var geometry = new LooseItemWorldMotion(actor);
        if (!geometry.available(actor.getBoundingBox().stretch(delta).expand(reach))
            || !world.isSpaceEmpty(actor, actor.getBoundingBox().contract(1e-7))) return false;
        double drag = grounded ? slipperiness * .91f : .91f; // same dry native float operator
        if (!Double.isFinite(drag) || drag < 0 || drag > 1) return false;
        pending.put(actor.getUuid(), new Pending(actor, delta, drag, support));
        return true;
    }
    public static void nativeFinished(LivingEntity actor) {
        if (!(actor.getWorld() instanceof ServerWorld world)) return;
        var map = PENDING.get(world); var p = map == null ? null : map.get(actor.getUuid());
        if (p != null && p.actor.get() == actor && p.tick == world.getTime()) p.nativeEnd = actor.getVelocity();
    }
    /** Admission can change after native travel. Finish its withheld move once through the same
     * loaded-only native geometry; keep the already computed post-travel velocity/any later impulse.
     * No hull force/carrier, second travel, gravity or damping runs on this fallback path. */
    private static void nativeFallback(LivingEntity actor, Pending p) {
        var postTravel = actor.getVelocity();
        var geometry = new LooseItemWorldMotion(actor);
        geometry.move(BoardingSystem.pure(actor.getPos()), Quat.IDENTITY, BoardingSystem.pure(p.moving));
        actor.setVelocity(postTravel);
        actor.velocityModified = true; actor.velocityDirty = true;
    }
    /** Runs once, after hull/joint solving. No second living tick, manual AI, player transport or pose following. */
    public static void tick(ServerWorld world) {
        var map = PENDING.remove(world);
        var supports = SUPPORTS.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
        supports.entrySet().removeIf(e -> e.getValue().tick < world.getTime() - 1 || e.getValue().actor.get() == null);
        if (map == null) return;
        var ordered = new ArrayList<>(map.values());
        ordered.sort(Comparator.comparingInt(p -> p.actor.get() == null ? Integer.MAX_VALUE : p.actor.get().getId()));
        var motions = new HashMap<Integer, BodyMotion>();
        var changed = new LinkedHashMap<Integer, BlockBodyEntity>();
        for (var p : ordered) {
            var actor = p.actor.get();
            if (actor == null) continue;
            supports.remove(actor.getUuid());
            if (actor.getWorld() != world || !actor.isAlive() || p.tick != world.getTime() || p.nativeEnd == null
                || !actor.getPos().equals(p.start)) continue; // external transfer/pose wins, never rewind
            if (!eligible(actor)) {nativeFallback(actor, p); continue;}
            var bodies = nearby(actor, p.moving);
            if (bodies.size() > MAX_BODIES) {nativeFallback(actor, p); continue;}
            var near = new ArrayList<BodyMotion>(); boolean supported = true;
            for (var body : bodies) {
                var motion = motions.computeIfAbsent(body.getId(), ignored -> body.boardingMotion());
                if (interacts(actor, motion, p.moving)) {
                    if (!upright(motion)) {supported = false; break;}
                    near.add(motion); changed.put(body.getId(), body);
                }
            }
            if (!supported) {nativeFallback(actor, p); continue;}
            var geometry = new LooseItemWorldMotion(actor);
            var external = actor.getVelocity().subtract(p.nativeEnd);
            var moving = p.moving.add(external);
            double reach = near.stream().mapToDouble(BodyMotion::travelBound).max().orElse(0) + .5;
            if (!geometry.available(actor.getBoundingBox().stretch(moving).expand(reach))) {nativeFallback(actor, p); continue;}
            var state = new DynamicBodyAttachment.State(BoardingSystem.pure(actor.getPos()),
                BoardingSystem.pure(moving).scaled(20), false, -1);
            var after = DynamicBodyAttachment.passiveContact(state, near, actor.getWidth(), actor.getHeight(), .05, geometry);
            Vec3d carrier = p.previousSurface;
            boolean contact = false;
            if (after.grounded()) for (var body : near) if (body.id == after.support()) {
                var hits = body.query(1).contacts(DynamicBodyAttachment.box(after.feet(), actor.getWidth(), actor.getHeight()), .01);
                var hit = hits.stream().filter(h -> h.normal().y() > .95 && h.penetration() <= .01)
                    .min(Comparator.comparingDouble(h -> h.point().minus(after.feet()).lengthSquared())).orElse(null);
                if (hit != null) {
                    carrier = BoardingSystem.mc(body.surfaceVelocity(hit.point(), 1).scaled(.05));
                    contact = true;
                }
            }
            // Native post-move gravity/effects retain their result. Only real contact velocity
            // changes are damped, in the surface's rest frame. The carrier is NEVER added twice.
            var resolved = BoardingSystem.mc(after.velocity().scaled(.05));
            var drag = new Vec3d(p.drag, .98, p.drag);
            var next = p.nativeEnd.add(external).add(resolved.subtract(moving).multiply(drag))
                .add(carrier.multiply(1 - p.drag, 0, 1 - p.drag));
            actor.setVelocity(next);
            actor.setOnGround(actor.isOnGround() || contact);
            if (contact) {
                actor.fallDistance = 0;
                if (supports.size() < MAX_ACTORS)
                    supports.put(actor.getUuid(), new Support(new WeakReference<>(actor), world.getTime(), carrier));
            }
            actor.velocityModified = true; actor.velocityDirty = true;
        }
        changed.values().forEach(BlockBodyEntity::publishPhysicsState);
    }
}
