package com.terminaldetector.drmd.world.mega;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.world.contraption.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;

/** Adapter for the one existing connection/input/epoch control system.
 * No implementation owns a body strongly or starts a second simulation/driver. */
interface BodyControlTarget {
    BlockPos console();
    boolean valid(BlockBodyEntity body);
    boolean contains(BlockBodyEntity body, Vec3d point);
    boolean canClaim(ServerPlayerEntity pilot, BlockBodyEntity body);
    default boolean sameStation(BodyControlTarget other) { return getClass() == other.getClass() && console().equals(other.console()); }
    default BodyControlTarget bind(ServerPlayerEntity p, BlockBodyEntity body, int epoch) { return this; }
    default boolean autopilot(BlockBodyEntity body) { return false; }
    default void autopilot(BlockBodyEntity body, boolean value) {}
    default void aim(BlockBodyEntity body, float yaw) {}
    default boolean supportsFire() { return false; }
    default int allowedFireMask() { return 0; }
    default boolean scheduledWeapons() { return false; }
    default boolean supportsAim() { return false; }
    void requestWrench(BlockBodyEntity body, Vec3 worldForce, Vec3 worldTorque);
    default void fire(BlockBodyEntity body, ServerPlayerEntity pilot, int mask) {}
    default void release(BlockBodyEntity body, int epoch) {}

    static BodyControlTarget sky() { return Sky.INSTANCE; }
    static BodyControlTarget core(BlockPos cell) { return new Core(cell.toImmutable(),-1); }

    final class Sky implements BodyControlTarget {
        private static final Sky INSTANCE = new Sky();
        public BlockPos console() { return UfoControlSystem.CONSOLE; }
        public boolean valid(BlockBodyEntity b) { return b instanceof SkyUfoEntity s && !s.isCrashing() && s.hasControlConsole(); }
        public boolean contains(BlockBodyEntity b, Vec3d p) { return ((SkyUfoEntity)b).containsPos(p); }
        public boolean canClaim(ServerPlayerEntity p, BlockBodyEntity b) { return p.getEyePos().distanceTo(b.localToWorld(Vec3d.ofCenter(console()))) <= p.getBlockInteractionRange(); }
        public boolean autopilot(BlockBodyEntity b) { return ((SkyUfoEntity)b).autopilotEnabled(); }
        public void autopilot(BlockBodyEntity b, boolean value) { ((SkyUfoEntity)b).setAutopilot(value); }
        public void aim(BlockBodyEntity b, float yaw) { ((SkyUfoEntity)b).aimTurret(yaw); }
        public boolean supportsFire() { return true; }
        public int allowedFireMask() { return 7; }
        public boolean supportsAim() { return true; }
        public void requestWrench(BlockBodyEntity b, Vec3 force, Vec3 torque) { ((SkyUfoEntity)b).applyThrusterWrench(force,torque); }
        public void fire(BlockBodyEntity b, ServerPlayerEntity p, int mask) { ((SkyUfoEntity)b).fireMountedWeapons(p,mask); }
    }
    record Core(BlockPos console, long materialIdentity) implements BodyControlTarget {
        public boolean supportsFire() { return true; }
        public int allowedFireMask() { return 3; } // Actual laser / rocket cells; no invented bomb bank.
        public boolean scheduledWeapons() { return true; }
        public boolean valid(BlockBodyEntity b) {
            return !(b instanceof SkyUfoEntity) && BodyControlCore.isCore(b.getLocalBlock(console))
                    && BodySpatialIndex.find(b.getWorld(),b.getId()).orElse(null) == b
                    && (materialIdentity < 0 || b.controlCore().sameMaterial(b,console,materialIdentity));
        }
        public boolean contains(BlockBodyEntity b, Vec3d p) { return BodyControlCore.contains(b,p); }
        public boolean canClaim(ServerPlayerEntity p, BlockBodyEntity b) {
            var world = b.localToWorld(Vec3d.ofCenter(console));
            var mode = p.interactionManager.getGameMode();
            if (!(mode == net.minecraft.world.GameMode.CREATIVE || mode == net.minecraft.world.GameMode.SURVIVAL)
                    || BodySpatialIndex.find(b.getWorld(),b.getId()).orElse(null) != b
                    || !p.getServerWorld().canPlayerModifyAt(p,BlockPos.ofFloored(world))) return false;
            return BodyBlockInteraction.target(p,p.getEyePos(),BodyBlockInteraction.viewDirection(p),Math.min(16,p.getBlockInteractionRange()))
                    .filter(hit -> hit.body() == b && hit.hit().localBlock().equals(console)).isPresent();
        }
        public BodyControlTarget bind(ServerPlayerEntity p, BlockBodyEntity b, int epoch) {
            return new Core(console,b.controlCore().bind(p,console,epoch));
        }
        public void requestWrench(BlockBodyEntity b, Vec3 force, Vec3 torque) { b.controlCore().request(b,force,torque); }
        public void release(BlockBodyEntity b, int epoch) { b.controlCore().release(epoch); }
    }
}
