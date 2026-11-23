package com.terminaldetector.drmd.weapon.projectile;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.flight.ShipAttitude;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** A temporary, owner-only piloting link to the ordinary authoritative projectile. */
public final class GuidedMissileControl {
    public static final int SNAPSHOT_INTERVAL = 2;
    public static final int LINK_TIMEOUT = 20;
    private static final int MAX_LINK_DISTANCE = 256;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public enum Phase { OPEN, FLYING, RELEASED, FINISHED, LOST }

    public record Input(UUID token, int sequence, boolean release, float yaw, float pitch) implements CustomPayload {
        public static final Id<Input> ID = new Id<>(Identifier.of("drmd", "guided_input"));
        public static final PacketCodec<RegistryByteBuf, Input> CODEC = PacketCodec.of(
                (p, b) -> {
                    b.writeUuid(p.token);
                    b.writeVarInt(p.sequence);
                    b.writeBoolean(p.release);
                    b.writeFloat(p.yaw);
                    b.writeFloat(p.pitch);
                },
                b -> new Input(b.readUuid(), b.readVarInt(), b.readBoolean(), b.readFloat(), b.readFloat()));

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record State(UUID token, Identifier dimension, Phase phase, int entityId, int lifeTicks,
                        Vec3d position, Quat attitude) implements CustomPayload {
        public static final Id<State> ID = new Id<>(Identifier.of("drmd", "guided_state"));
        public static final PacketCodec<RegistryByteBuf, State> CODEC = PacketCodec.of(
                (p, b) -> {
                    b.writeUuid(p.token);
                    b.writeIdentifier(p.dimension);
                    b.writeEnumConstant(p.phase);
                    if (!p.isOpen()) return;
                    b.writeVarInt(p.entityId);
                    b.writeVarInt(p.lifeTicks);
                    b.writeDouble(p.position.x);
                    b.writeDouble(p.position.y);
                    b.writeDouble(p.position.z);
                    b.writeFloat((float) p.attitude.x());
                    b.writeFloat((float) p.attitude.y());
                    b.writeFloat((float) p.attitude.z());
                    b.writeFloat((float) p.attitude.w());
                },
                b -> {
                    UUID token = b.readUuid();
                    Identifier dimension = b.readIdentifier();
                    Phase phase = b.readEnumConstant(Phase.class);
                    if (phase != Phase.OPEN && phase != Phase.FLYING) {
                        return new State(token, dimension, phase, 0, 0, Vec3d.ZERO, Quat.IDENTITY);
                    }
                    return new State(token, dimension, phase, b.readVarInt(), b.readVarInt(),
                            new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()),
                            new Quat(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()).normalized());
                });

        public boolean isOpen() {
            return phase == Phase.OPEN || phase == Phase.FLYING;
        }

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private GuidedMissileControl() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Input.ID, Input.CODEC);
        PayloadTypeRegistry.playS2C().register(State.ID, State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Input.ID, (p, c) -> receive(c.player(), p));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session session = SESSIONS.get(handler.player.getUuid());
            if (session != null && session.pilot == handler.player) SESSIONS.remove(handler.player.getUuid());
        });
        ServerTickEvents.END_SERVER_TICK.register(GuidedMissileControl::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                SESSIONS.values().removeIf(session -> session.world.getServer() == server));
    }

    public static void launch(PlayerEntity owner, ProjectileEntity projectile) {
        if (!(owner instanceof ServerPlayerEntity pilot) || projectile == null
                || !projectile.isAlive() || !pilot.isAlive() || pilot.isSpectator()
                || pilot.getWorld() != projectile.getWorld() || !pilot.getUuid().equals(projectile.getOwnerUuid())
                || !ServerPlayNetworking.canSend(pilot, State.ID)) return;

        var session = new Session(pilot, projectile);
        var previous = SESSIONS.put(pilot.getUuid(), session);
        if (previous != null) previous.send(Phase.RELEASED);
        session.send(Phase.OPEN);
    }

    private static void receive(ServerPlayerEntity pilot, Input input) {
        Session session = SESSIONS.get(pilot.getUuid());
        if (session == null || session.pilot != pilot || pilot.isSpectator() || !pilot.isAlive()
                || pilot.getWorld() != session.world || !session.projectile.isAlive()) return;
        if (!session.commands.accept(input, session.world.getTime())) return;
        if (input.release) {
            SESSIONS.remove(pilot.getUuid());
            session.send(Phase.RELEASED);
        }
    }

    public static Vec3d steer(ProjectileEntity projectile, Vec3d velocity, float degreesPerSecond) {
        Session session = SESSIONS.get(projectile.getOwnerUuid());
        if (session == null || session.projectile != projectile) return velocity;
        if (!session.pilotPresent() || session.timedOut() || !session.inRange()) return velocity;

        Input command = session.commands.take();
        if (command == null || velocity.lengthSquared() < 1e-8) return velocity;
        turn(session.attitude, command.yaw, command.pitch, degreesPerSecond);
        return session.attitude.forward().multiply(velocity.length());
    }

    /** A single axis-angle turn, with diagonal input sharing the same angular budget. */
    static void turn(ShipAttitude attitude, float yaw, float pitch, float degreesPerSecond) {
        double magnitude = Math.hypot(yaw, pitch);
        if (magnitude <= 1e-6 || !Double.isFinite(magnitude) || degreesPerSecond <= 0
                || !Float.isFinite(degreesPerSecond)) return;
        Vec3d axis = attitude.up().multiply(yaw).add(attitude.right().multiply(pitch)).normalize();
        double degrees = degreesPerSecond / 20.0 * Math.min(1.0, magnitude);
        attitude.setForwardUp(ShipAttitude.rotate(attitude.forward(), axis, degrees),
                ShipAttitude.rotate(attitude.up(), axis, degrees));
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Session> sessions = SESSIONS.values().iterator(); sessions.hasNext();) {
            Session session = sessions.next();
            if (session.world.getServer() != server) continue;
            Phase closed = session.closed();
            if (closed != null) {
                sessions.remove();
                session.send(closed);
            } else if (session.world.getTime() % SNAPSHOT_INTERVAL == 0) {
                session.send(Phase.FLYING);
            }
        }
    }

    /** Sequence and per-tick admission are independent of rendering and entity motion. */
    static final class Commands {
        private final UUID token;
        private int sequence = -1;
        private long lastReceived;
        private long lastTurn = Long.MIN_VALUE;
        private Input pending;

        Commands(UUID token, long launchedAt) {
            this.token = token;
            lastReceived = launchedAt;
        }

        boolean accept(Input command, long tick) {
            if (!token.equals(command.token) || command.sequence <= sequence) return false;
            if (!command.release && (tick == lastTurn || !validAxis(command.yaw) || !validAxis(command.pitch))) {
                return false;
            }
            sequence = command.sequence;
            lastReceived = tick;
            pending = command.release ? null : command;
            if (!command.release) lastTurn = tick;
            return true;
        }

        Input take() {
            Input command = pending;
            pending = null;
            return command;
        }

        private static boolean validAxis(float value) {
            return Float.isFinite(value) && value >= -1 && value <= 1;
        }
    }

    private static final class Session {
        private final UUID token = UUID.randomUUID();
        private final ServerPlayerEntity pilot;
        private final ServerWorld world;
        private final ProjectileEntity projectile;
        private final ShipAttitude attitude = new ShipAttitude();
        private final Commands commands;

        Session(ServerPlayerEntity pilot, ProjectileEntity projectile) {
            this.pilot = pilot;
            this.world = pilot.getServerWorld();
            this.projectile = projectile;
            commands = new Commands(token, world.getTime());
            Vec3d forward = projectile.getVelocity();
            if (forward.lengthSquared() < 1e-8) forward = DescentPlayerData.get(pilot).shipForward(pilot);
            attitude.setForwardUp(forward, DescentPlayerData.get(pilot).shipUp(pilot));
        }

        boolean pilotPresent() {
            return pilot.getWorld() == world && pilot.isAlive() && !pilot.isSpectator()
                    && world.getServer().getPlayerManager().getPlayer(pilot.getUuid()) == pilot;
        }

        boolean timedOut() {
            return world.getTime() - commands.lastReceived > LINK_TIMEOUT;
        }

        boolean inRange() {
            int viewDistance = Math.min(pilot.getViewDistance(), world.getServer().getPlayerManager().getViewDistance());
            int range = Math.min(MAX_LINK_DISTANCE, Math.max(1, viewDistance - 1) * 16);
            return projectile.squaredDistanceTo(pilot) <= range * range;
        }

        Phase closed() {
            if (!pilotPresent() || timedOut() || !inRange()) return Phase.LOST;
            if (!projectile.isAlive()) return Phase.FINISHED;
            var access = WorldAccess.of(world);
            if (projectile.getWorld() != world
                    || !access.isVolumeAvailable(access.cubeAt(BlockPos.ofFloored(projectile.getPos())))) return Phase.LOST;
            return null;
        }

        void send(Phase phase) {
            if (world.getServer().getPlayerManager().getPlayer(pilot.getUuid()) != pilot
                    || !ServerPlayNetworking.canSend(pilot, State.ID)) return;
            ServerPlayNetworking.send(pilot, new State(token, world.getRegistryKey().getValue(), phase,
                    projectile.getId(), projectile.remainingLifeTicks(), projectile.getPos(), attitude.orientation()));
        }
    }
}
