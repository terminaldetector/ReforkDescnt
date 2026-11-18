package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import com.terminaldetector.drmd.world.contraption.boarding.DynamicBodyAttachment;
import com.terminaldetector.drmd.world.contraption.boarding.BoardingSystem;
import com.terminaldetector.drmd.world.gravity.ActorFrames;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Actual native Cow ticks on material; no passenger registration, forced pose or manual solver. */
public final class LivingBodyContactGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "living-body-deck", tickLimit = 180)
    public void nativeEnabledCowReceivesContactsFromTranslatingYawingDeck(TestContext c) {
        var f = new Fixture(c, 0, true, false);
        f.drive(r -> {
            if (r.samples < 32) return;
            c.assertTrue(r.body.age - r.bodyAge >= 30 && r.body.getX() > r.bodyStart.x + 1,
                "ADMISSION: actual ordinary hull motion and ticking identity: " + r.state());
            c.assertTrue(r.contactSamples >= 12 && r.supported() && r.minimumLocalY > .985
                && r.cow.getX() > r.cowStart.x + .35 && r.maximumStep < .65,
                "GAMEPLAY: material support/friction moves the Cow without a follower pose: " + r.state());
            r.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "living-body-jump", tickLimit = 180)
    public void oneNativeJumpRetainsTheMovingContactSpeedWithoutPinningTheCow(TestContext c) {
        var f = new Fixture(c, 128, true, true);
        f.drive(r -> {
            if (!r.jumpRequested && r.contactSamples >= 12 && r.supported()) {
                r.jumpRequested = true; r.jumpAge = r.cow.age;
                r.departureVelocity = r.cow.getVelocity();
                r.departureSurface = BoardingSystem.mc(r.body.boardingMotion()
                    .surfaceVelocity(BoardingSystem.pure(r.cow.getPos()), 1).scaled(.05));
                // One disclosed public native control request, not jump()/setVelocity()/manual tick.
                r.cow.getJumpControl().setActive();
                return;
            }
            c.assertTrue(r.samples <= 70, "ADMISSION: finite real contact and native jump request: " + r.state());
            if (!r.jumpRequested || r.cow.age == r.jumpAge) return;
            var local = r.body.worldToLocal(r.cow.getPos());
            c.assertTrue(local.y > 1.15 && r.cow.getVelocity().y > .1 && !r.cow.isOnGround() && !r.supported(),
                "GAMEPLAY: ordinary jump actually separates from the real moving deck: " + r.state());
            c.assertTrue(r.departureSurface.x > .07 && r.departureVelocity.x > .05
                && r.cow.getVelocity().x > r.departureVelocity.x * .7
                && r.cow.getVelocity().x < r.departureVelocity.x + r.departureSurface.x * .5,
                "GAMEPLAY: departing world speed is retained, not erased or carrier-added twice: " + r.state());
            r.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "living-body-world-control", tickLimit = 180)
    public void outsideNativeCowKeepsItsWorldFloorAndNativeControllers(TestContext c) {
        var f = new Fixture(c, 256, false, false);
        f.drive(r -> {
            if (r.samples < 20) return;
            c.assertTrue(r.cow.isOnGround() && Math.abs(r.cow.getY() - r.base.getY() - 1) < 1e-6
                && r.cow.fallDistance == 0 && r.maximumStep < .65,
                "CONTROL: no body preserves native static-floor collisions and ordinary travel: " + r.state());
            r.complete();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final TestContext c;
        final ServerWorld world;
        final BlockPos base;
        final AiFixtureChunks chunks;
        final boolean withBody, straight;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        CowEntity cow;
        BlockBodyEntity body;
        Object navigation, control, jumpControl;
        Vec3d cowStart, bodyStart, previous, departureVelocity, departureSurface;
        int admissionTicks, samples, lastAge, spawnAge, bodyAge, contactSamples, jumpAge;
        long spawnTime;
        double minimumLocalY = Double.POSITIVE_INFINITY, maximumStep;
        boolean spawned, closed, jumpRequested;
        Fixture(TestContext c, int offset, boolean withBody, boolean straight) {
            this.c = c; world = c.getWorld(); this.withBody = withBody; this.straight = straight;
            base = c.getAbsolutePos(new BlockPos(242000 + offset, 100, 28000));
            chunks = new AiFixtureChunks(world, base, 1);
        }
        private boolean admit() {
            if (spawned) return true;
            var at = base.add(4, 2, 4);
            if (!chunks.entityTicking(base, at)) {
                c.assertTrue(++admissionTicks <= 80, "ADMISSION: actual spawn columns tick: " + chunks.readiness(base, at));
                return false;
            }
            var access = WorldAccess.of(world);
            for (var p : BlockPos.iterate(base.add(-3, -3, -3), base.add(12, 9, 12))) {
                c.assertTrue(access.isVolumeAvailable(D6ChunkPos.ofBlock(p.getX(), p.getY(), p.getZ())),
                    "ADMISSION: FULL XYZ fixture before finite edits: " + p);
                var state = access.readIfLoaded(p);
                c.assertTrue(state.isPresent(), "ADMISSION: actual loaded fixture state");
                if (!state.orElseThrow().isAir()) {
                    original.put(p.toImmutable(), state.orElseThrow());
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                }
            }
            if (withBody) {
                var cells = new ArrayList<BlockBodyEntity.Cell>();
                for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++)
                    cells.add(new BlockBodyEntity.Cell(new BlockPos(x, 0, z), Blocks.IRON_BLOCK.getDefaultState()));
                body = BlockBodyEntity.createLocal(world, cells, Vec3d.of(base), Quat.IDENTITY,
                    new Vec3d(2, 0, 0), straight ? Vec3d.ZERO : new Vec3d(0, .2, 0));
                c.assertTrue(BodySpatialIndex.find(world, body.getId()).orElse(null) == body,
                    "ADMISSION: actual registered material body identity");
                bodyAge = body.age; bodyStart = body.getPos();
            } else for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
                var p = base.add(x, 0, z); original.putIfAbsent(p, world.getBlockState(p));
                world.setBlockState(p, Blocks.IRON_BLOCK.getDefaultState(), 2);
            }
            cow = EntityType.COW.create(world);
            c.assertTrue(cow != null && !cow.isAiDisabled() && !cow.hasNoGravity(), "ADMISSION: native enabled Cow factory");
            cow.setPersistent(); cow.setPosition(Vec3d.of(base).add(4, 1.2, 4));
            c.assertTrue(world.spawnEntity(cow) && world.getEntity(cow.getUuid()) == cow,
                "ADMISSION: real native Cow spawn, no passenger bootstrap");
            c.assertTrue(chunks.entityTicking(cow.getBlockPos(), body == null ? base : body.getBlockPos()),
                "ADMISSION: actual Cow and hull centre columns both tick");
            navigation = cow.getNavigation(); control = cow.getMoveControl(); jumpControl = cow.getJumpControl();
            cowStart = previous = cow.getPos(); spawnAge = lastAge = cow.age; spawnTime = world.getTime(); spawned = true;
            return false;
        }
        boolean supported() {
            if (body == null) return false;
            return body.collisionQuery().contacts(DynamicBodyAttachment.box(BoardingSystem.pure(cow.getPos()),
                cow.getWidth(), cow.getHeight()), .012).stream().anyMatch(h -> h.normal().y() > .95 && h.penetration() <= .012);
        }
        void drive(Consumer<Fixture> assertions) {
            c.runAtEveryTick(() -> {
                if (closed) return;
                try {
                    world.resetIdleTimeout();
                    if (!admit() || cow.age == lastAge) return;
                    c.assertTrue(cow.age == lastAge + 1, "ADMISSION: one ordinary native Cow tick per sample: " + state());
                    lastAge = cow.age; samples++;
                    maximumStep = Math.max(maximumStep, cow.getPos().distanceTo(previous)); previous = cow.getPos();
                    c.assertTrue(cow.isAlive() && world.getEntity(cow.getUuid()) == cow && !cow.isAiDisabled()
                        && !cow.hasNoGravity() && !ActorFrames.active(cow) && cow.getNavigation() == navigation
                        && cow.getMoveControl() == control && cow.getJumpControl() == jumpControl
                        && chunks.entityTicking(cow.getBlockPos()),
                        "CONTROL: native live AI/input/jump/flags/frame identity preserved: " + state());
                    if (body != null) {
                        minimumLocalY = Math.min(minimumLocalY, body.worldToLocal(cow.getPos()).y);
                        if (supported()) contactSamples++;
                    }
                    System.out.println("LIVING_BODY_SAMPLE " + state());
                    assertions.accept(this);
                } catch (Throwable failure) {close(); throw failure;}
            });
        }
        String state() {
            return "uuid=" + (cow == null ? "absent" : cow.getUuid()) + " sample=" + samples + " age=" + (cow == null ? -1 : cow.age)
                + " ageDelta=" + (cow == null ? -1 : cow.age - spawnAge) + " timeDelta=" + (world.getTime() - spawnTime)
                + " pos=" + (cow == null ? null : cow.getPos()) + " v=" + (cow == null ? null : cow.getVelocity())
                + " ground=" + (cow != null && cow.isOnGround()) + " supportSamples=" + contactSamples
                + " minLocalY=" + minimumLocalY + " maximumStep=" + maximumStep + " jumpRequested=" + jumpRequested
                + " body=" + (body == null ? null : body.getPos()) + " bodyAge=" + (body == null ? -1 : body.age);
        }
        void complete() {close(); c.complete();}
        @Override public void close() {
            if (closed) return; closed = true;
            if (cow != null) cow.discard(); if (body != null) body.discard();
            original.forEach((p, state) -> world.setBlockState(p, state, 2)); chunks.close();
        }
    }
}
