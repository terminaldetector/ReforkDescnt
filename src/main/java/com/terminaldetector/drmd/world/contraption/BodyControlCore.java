package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6ControlFrame;
import com.terminaldetector.drmd.d6.D6ThrusterMixer;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.mega.UfoControlSystem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.lang.ref.WeakReference;
import java.util.*;

/** Runtime-only generic core/actuator state on the existing physical body.
 * The material grid/UUID persist normally; a network lease and a force frame do not. */
public final class BodyControlCore {
    /** Fixed rating of one actual vector pod; adding armor does not increase capacity. */
    public static final double FORCE_PER_MOTOR = 300;
    private BlockPos watchedCore;
    private long generation, layoutRevision = Long.MIN_VALUE;
    private int epoch;
    private WeakReference<ServerPlayerEntity> pilot = new WeakReference<>(null);
    private D6ThrusterMixer mixer;
    private List<BlockPos> motors = List.of();
    private D6ControlFrame frame;
    private String layoutStatus = "NO_LAYOUT";

    public static boolean isCore(net.minecraft.block.BlockState state) {
        return state.isOf(ModWorldBlocks.SHIP_CONTROL_CORE);
    }
    public String layoutStatus() { return layoutStatus; }
    public boolean bound() { return watchedCore != null; }
    public void discardFrame() { frame = null; }
    /** O(1) lookup of this core's existing connection, never a pilot-map scan. */
    public boolean drive(BlockBodyEntity body) {
        var owner=pilot.get();
        return owner!=null && UfoControlSystem.driveCore(owner,body,epoch);
    }
    /** Empty means no active core lease; present zero suppresses ordinary fallback.
     * The existing connection owns the freshness/sequence/epoch and fire clock. */
    public OptionalInt scheduledFireMask(BlockBodyEntity body) {
        var owner=pilot.get();
        return watchedCore==null||owner==null?OptionalInt.empty()
                :UfoControlSystem.scheduledFireMask(owner,body,epoch);
    }

    /** Called only after the existing control system has admitted the exclusive owner. */
    public long bind(ServerPlayerEntity owner, BlockPos core, int authorityEpoch) {
        watchedCore = core.toImmutable(); generation++; epoch = authorityEpoch;
        pilot = new WeakReference<>(owner); frame = null;
        return generation;
    }
    public boolean sameMaterial(BlockBodyEntity body, BlockPos core, long identity) {
        return watchedCore != null && watchedCore.equals(core) && generation == identity
                && isCore(body.getLocalBlock(core));
    }
    public void release(int authorityEpoch) {
        if (epoch != authorityEpoch) return;
        watchedCore = null; generation++; pilot.clear(); frame = null;
    }

    /** Commit notification only: rejected/speculative edits cannot revoke authority. */
    public void materialChanged(BlockBodyEntity body, List<BlockPos> changed) {
        frame = null;
        if (watchedCore != null && changed.contains(watchedCore)) {
            generation++;
            UfoControlSystem.releaseBlockBody(body); // A detached core does not copy the pilot onto debris.
        }
    }
    public static boolean contains(BlockBodyEntity body, Vec3d worldPoint) {
        var bounds = body.collider().bounds();
        if (bounds.isEmpty()) return false;
        var p = body.worldToLocal(worldPoint); var b = bounds.get();
        // Includes hollow rooms and the standing surface of a thin deck, in body axes.
        return p.x >= b.min().x()-2.5 && p.x <= b.max().x()+2.5
                && p.y >= b.min().y()-2.5 && p.y <= b.max().y()+2.5
                && p.z >= b.min().z()-2.5 && p.z <= b.max().z()+2.5;
    }

    private void layout(BlockBodyEntity body) {
        if (layoutRevision == body.shapeRevision()) return;
        layoutRevision = body.shapeRevision(); mixer = null;
        motors = body.cells().stream().filter(c -> c.state().isOf(ModWorldBlocks.SHIP_THRUSTER))
                .map(BlockBodyEntity.Cell::pos).sorted().toList();
        if (motors.size() < 3 || motors.size() > 16) { layoutStatus = "REQUIRES_3_TO_16_MOTORS"; return; }
        var points = motors.stream().map(p -> new Vec3(p.getX()+.5,p.getY()+.5,p.getZ()+.5)).toList();
        try { mixer = new D6ThrusterMixer(points, FORCE_PER_MOTOR); layoutStatus = "READY"; }
        catch (IllegalArgumentException singular) { layoutStatus = "NONCOLLINEAR_MOTORS_REQUIRED"; }
    }
    private D6ControlFrame.Key currentKey(BlockBodyEntity body) {
        var p = pilot.get();
        if (!(body.getWorld() instanceof ServerWorld world) || !world.getServer().isOnThread()
                || watchedCore == null || !isCore(body.getLocalBlock(watchedCore))
                || p == null || UfoControlSystem.epoch(p) != epoch || !UfoControlSystem.controlsBody(p,body)) return null;
        return new D6ControlFrame.Key(body.getUuid(),world.getTime(),body.shapeRevision(),
                watchedCore.asLong(),generation,epoch);
    }

    /** Queue a fresh latest-input wrench; never mutate D6 momentum before a rollback snapshot. */
    public void request(BlockBodyEntity body, Vec3 worldForce, Vec3 worldTorque) {
        frame = null; var key = currentKey(body); if (key == null) return;
        layout(body); if (mixer == null) return; // Explicit unsupported layout; no synthetic force/torque.
        var inverse = body.rotation().inverse();
        var outputs = mixer.mix(body.localCentre(),inverse.rotate(worldForce),inverse.rotate(worldTorque),
                i -> body.getLocalBlock(motors.get(i)).isOf(ModWorldBlocks.SHIP_THRUSTER));
        frame = new D6ControlFrame(key,body.localCentre(),outputs);
    }
    /** The caller must have taken its ordinary/joint rollback snapshot first. */
    public void accumulateForces(BlockBodyEntity body) {
        if (frame == null) return;
        var key = currentKey(body);
        if (key != null) frame.accumulate(body.physics(),key);
    }
}
