package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.ai.FlightAttitude;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.FlightEnemyKind;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.physics.collision.OrientedBox;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Four persistent deployment identities and their positions in the carrier's physical frame. */
public final class UfoEscortFormation {
    private static final int SCHEMA = 1;

    public enum Slot {
        GUNNER("gunner", FlightEnemyKind.ORB, AiRole.MG, -16, 3, 5),
        MISSILE("missile", FlightEnemyKind.ROCKET, AiRole.ARTILLERY, 16, 2, -5),
        INTERCEPT("intercept", FlightEnemyKind.JET, AiRole.LASER, -5, 5, -17),
        GRAVITY("gravity", null, AiRole.GRAV, 5, 4, 17);

        private final String id;
        private final FlightEnemyKind hull;
        private final AiRole role;
        private final Vec3d localPosition;

        Slot(String id, FlightEnemyKind hull, AiRole role, int x, int y, int z) {
            this.id = id;
            this.hull = hull;
            this.role = role;
            this.localPosition = new Vec3d(x, y, z);
        }

        public String id() { return id; }
        public AiRole role() { return role; }
        public Vec3d position(SkyUfoEntity carrier) { return carrier.localToWorld(localPosition); }

        private DroneEntity create(ServerWorld world) {
            return hull == null ? ModEntities.DRONE.create(world) : hull.create(world);
        }

        public static Slot fromId(String id) {
            for (Slot slot : values()) if (slot.id.equals(id)) return slot;
            return null;
        }
    }

    private final EnumMap<Slot, UUID> deployed = new EnumMap<>(Slot.class);
    private NbtCompound opaque;
    private long nextDeployment;

    public Map<Slot, UUID> deployed() { return Map.copyOf(deployed); }
    public boolean owns(Slot slot, UUID drone) { return slot != null && drone.equals(deployed.get(slot)); }

    void tick(ServerWorld world, SkyUfoEntity carrier) {
        if (opaque != null || deployed.size() == Slot.values().length || world.getTime() < nextDeployment) return;
        nextDeployment = world.getTime() + 20;
        boolean nearbyPlayer = world.getPlayers().stream()
                .anyMatch(player -> player.isAlive() && !player.isSpectator() && carrier.squaredDistanceTo(player) < 96 * 96);
        if (!nearbyPlayer) return;
        for (Slot slot : Slot.values()) {
            if (deployed.containsKey(slot)) continue;
            Vec3d position = slot.position(carrier);
            if (!WorldEditSafety.loadedSegment(world, position, position)) continue;
            DroneEntity drone = slot.create(world);
            if (drone == null) continue;
            Vec3d heading = position.subtract(carrier.physicsCentre());
            drone.refreshPositionAndAngles(position.x, position.y - drone.getHeight() * .5, position.z,
                    FlightAttitude.yawOf(heading), FlightAttitude.pitchOf(heading));
            var box = drone.getBoundingBox();
            if (box.minY < world.getBottomY() || box.maxY > world.getTopY() || !world.getWorldBorder().contains(box)
                    || !world.isSpaceEmpty(drone, box)) continue;
            var hull = new OrientedBox(pure(box.getCenter()),
                    new Vec3(box.getLengthX() * .5, box.getLengthY() * .5, box.getLengthZ() * .5), Quat.IDENTITY);
            if (BodySpatialIndex.query(world, box).stream().anyMatch(body -> body.collisionQuery().hasContact(hull, .001)))
                continue;
            drone.applyRole(slot.role);
            drone.assignEscort(carrier.getUuid(), slot);
            Vec3 velocity = carrier.physics().velocityAtPoint(pure(position).minus(carrier.massPosition())).scaled(.05);
            drone.setVelocity(velocity.x(), velocity.y(), velocity.z());
            if (world.spawnEntity(drone)) deployed.put(slot, drone.getUuid());
        }
    }

    void release(ServerWorld world, SkyUfoEntity carrier) {
        for (UUID id : deployed.values()) {
            if (world.getEntity(id) instanceof DroneEntity drone) drone.releaseEscort(carrier.getUuid());
        }
    }

    void read(NbtCompound nbt) {
        deployed.clear();
        opaque = null;
        if (nbt.isEmpty()) return;
        if (!nbt.contains("schema", NbtElement.INT_TYPE) || nbt.getInt("schema") != SCHEMA) {
            opaque = nbt.copy();
            return;
        }
        for (Slot slot : Slot.values()) {
            if (!nbt.contains(slot.id)) continue;
            if (!nbt.containsUuid(slot.id) || deployed.containsValue(nbt.getUuid(slot.id))) {
                deployed.clear();
                opaque = nbt.copy();
                return;
            }
            deployed.put(slot, nbt.getUuid(slot.id));
        }
    }

    NbtCompound write() {
        if (opaque != null) return opaque.copy();
        var nbt = new NbtCompound();
        if (!deployed.isEmpty()) {
            nbt.putInt("schema", SCHEMA);
            deployed.forEach((slot, id) -> nbt.putUuid(slot.id, id));
        }
        return nbt;
    }

    private static Vec3 pure(Vec3d v) { return new Vec3(v.x, v.y, v.z); }
}
