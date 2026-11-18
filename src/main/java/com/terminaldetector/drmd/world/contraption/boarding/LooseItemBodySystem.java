package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
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

/** One deferred native drop move after hull/joint contacts, before player motors. No follower anchor. */
public final class LooseItemBodySystem {
    public enum Phase {NATIVE, DEFERRED, HOLD}
    private static final int MAX_PENDING = 128, MAX_BODIES = 16;
    private static final double HULL_REACH = 4 + 512 * .05;
    private static final Map<ServerWorld, Map<UUID, Pending>> PENDING = new WeakHashMap<>();
    private static final class Pending {
        final WeakReference<ItemEntity> item;
        final long tick;
        final int age;
        final Vec3d start, velocity;
        final double gravity;
        final boolean canMerge;
        Vec3d drag, nativeEndVelocity;
        boolean nativeFinished;
        Pending(ItemEntity item) {
            this.item = new WeakReference<>(item); tick = item.getWorld().getTime(); age = item.age;
            start = item.getPos(); velocity = item.getVelocity(); gravity = item.getFinalGravity();
            canMerge = ((LooseItemBodyAccess)item).drmd$nativeCanMerge();
        }
    }
    private LooseItemBodySystem() {}
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LooseItemBodySystem::tick);
        ServerWorldEvents.UNLOAD.register((server, world) -> PENDING.remove(world));
    }
    private static boolean eligible(ItemEntity item) {
        return item.getClass() == ItemEntity.class && item.getWorld() instanceof ServerWorld && item.isAlive()
            && !item.hasNoGravity() && !item.hasVehicle() && !item.noClip
            && !item.isTouchingWater() && !item.isInLava() && item instanceof LooseItemBodyAccess
            && Double.isFinite(item.getVelocity().lengthSquared()) && item.getWidth() > 0 && item.getWidth() <= 1
            && item.getHeight() > 0 && item.getHeight() <= 1;
    }
    private static List<BlockBodyEntity> nearby(ItemEntity item, Vec3d velocity) {
        return BodySpatialIndex.query(item.getWorld(), item.getBoundingBox().stretch(velocity).expand(HULL_REACH));
    }
    /** Called at the native dry-gravity boundary; water/lava/NoGravity/subclasses keep native behavior. */
    public static Phase prepare(ItemEntity item) {
        if (!eligible(item)) return Phase.NATIVE;
        var world = (ServerWorld)item.getWorld();
        if (!world.getServer().isOnThread()) throw new IllegalStateException("Loose item physics is server-owned");
        var bodies = nearby(item, item.getVelocity());
        if (bodies.isEmpty()) return Phase.NATIVE;
        if (bodies.size() <= MAX_BODIES && bodies.stream().noneMatch(body -> interacts(item, body)))
            return Phase.NATIVE;
        var pending = PENDING.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
        pending.entrySet().removeIf(e -> e.getValue().tick != world.getTime());
        if (pending.size() >= MAX_PENDING || bodies.size() > MAX_BODIES) return Phase.HOLD;
        var motion = new LooseItemWorldMotion(item);
        if (!motion.available(item.getBoundingBox())) return Phase.HOLD;
        // An embedded/native noclip escape is outside this first adapter; retain its native owner.
        if (!world.isSpaceEmpty(item, item.getBoundingBox().contract(1e-7))) return Phase.NATIVE;
        pending.put(item.getUuid(), new Pending(item));
        return Phase.DEFERRED;
    }
    private static boolean interacts(ItemEntity item, BlockBodyEntity body) {
        var motion = body.boardingMotion();
        var centre = BoardingSystem.pure(item.getBoundingBox().getCenter());
        if (motion.gravity(centre, 0).isPresent() || motion.gravity(centre, 1).isPresent()) return true;
        var box = PassengerFrame.box(BoardingSystem.pure(item.getPos()), item.getWidth(), item.getHeight(), Quat.IDENTITY);
        var delta = BoardingSystem.pure(item.getVelocity().add(0, -item.getFinalGravity(), 0));
        double reach = .002 + motion.travelBound();
        if (body.boardingMotionPaused()) {
            // A later entity-order hull may not have published its current tick yet. Admit only
            // material within its bounded inertial reach, not the entire broadphase field sphere.
            reach += Math.min(512, body.physics().linearVelocity().length()) * .05
                + body.radius() * Math.min(2, body.physics().angularVelocity().length()) * .05;
        }
        return !motion.query(0).contacts(box, reach).isEmpty() || !motion.query(1).contacts(box, reach).isEmpty()
            || motion.query(0).sweep(box, delta, motion.position(1).minus(motion.position(0))).isPresent();
    }
    private static Pending pending(ItemEntity item) {
        if (!(item.getWorld() instanceof ServerWorld world)) return null;
        var map = PENDING.get(world); var p = map == null ? null : map.get(item.getUuid());
        return p != null && p.item.get() == item && p.tick == world.getTime() ? p : null;
    }
    public static void drag(ItemEntity item, double x, double y, double z) {
        var p = pending(item); if (p != null) p.drag = new Vec3d(x, y, z);
    }
    public static void nativeFinished(ItemEntity item) {
        var p = pending(item);
        if (p != null) {p.nativeFinished = true; p.nativeEndVelocity = item.getVelocity();}
    }
    public static void tick(ServerWorld world) {
        if (!world.getServer().isOnThread()) throw new IllegalStateException("Loose item contacts require the world thread");
        var map = PENDING.remove(world); if (map == null) return;
        var ordered = new ArrayList<>(map.values());
        ordered.sort(Comparator.comparingInt(p -> p.item.get() == null ? Integer.MAX_VALUE : p.item.get().getId()));
        var motions = new HashMap<Integer, BodyMotion>(); var changed = new LinkedHashMap<Integer, BlockBodyEntity>();
        var merges = new ArrayList<Pending>();
        for (var p : ordered) {
            var item = p.item.get();
            if (item == null || item.getWorld() != world || !item.isAlive() || p.tick != world.getTime()) continue;
            // External teleport/piston/transfer/pickup wins. Never rewind a real item to an old pose.
            if (!p.nativeFinished || p.drag == null || !item.getPos().equals(p.start) || !eligible(item)) continue;
            var bodies = nearby(item, p.velocity); if (bodies.size() > MAX_BODIES) continue;
            var near = new ArrayList<BodyMotion>();
            for (var body : bodies) {near.add(motions.computeIfAbsent(body.getId(), id -> body.boardingMotion())); changed.put(body.getId(), body);}
            var geometry = new LooseItemWorldMotion(item);
            if (!geometry.available(item.getBoundingBox())) continue;
            double reach = near.stream().mapToDouble(BodyMotion::travelBound).max().orElse(0) + .5;
            // Prove the whole prospective envelope before a contact can react on a hull.
            if (!geometry.available(item.getBoundingBox().stretch(p.velocity).expand(reach))) {
                item.setVelocity(p.velocity.add(item.getVelocity().subtract(p.nativeEndVelocity)));
                item.velocityModified = true; item.velocityDirty = true;
                continue;
            }
            Vec3 acceleration = new Vec3(0, -p.gravity * 400, 0);
            var centre = BoardingSystem.pure(item.getBoundingBox().getCenter());
            for (var b : near) {
                var field = b.gravity(centre, 1);
                if (field.isPresent()) {acceleration = field.orElseThrow(); break;}
            }
            // Native launch/knockback received after tick is an external delta, not a new owner or carrier addition.
            var external = item.getVelocity().subtract(p.nativeEndVelocity);
            var velocity = BoardingSystem.pure(p.velocity.add(external)).scaled(20).plus(acceleration.scaled(.05));
            var state = new DynamicBodyAttachment.State(BoardingSystem.pure(item.getPos()), velocity, false, -1);
            var after = DynamicBodyAttachment.passiveContact(state, near, item.getWidth(), item.getHeight(), .05, geometry);
            var result = BoardingSystem.mc(after.velocity().scaled(.05));
            item.setVelocity(result.multiply(p.drag.x, p.drag.y, p.drag.z));
            item.setOnGround(item.isOnGround() || after.grounded());
            if (!geometry.blockedUnknown()) ((LooseItemBodyAccess)item).drmd$finishBodyMove();
            item.velocityModified = true; item.velocityDirty = true;
            if (p.canMerge && item.age % (BlockPos.ofFloored(p.start).equals(item.getBlockPos()) ? 40 : 2) == 0)
                merges.add(p);
        }
        // All nearby drops finish physical movement first, then native stack merging sees final positions.
        for (var p : merges) {
            var item = p.item.get();
            if (item != null && item.isAlive() && item.getWorld() == world) ((LooseItemBodyAccess)item).drmd$nativeTryMerge();
        }
        changed.values().forEach(BlockBodyEntity::publishPhysicsState);
    }
}
