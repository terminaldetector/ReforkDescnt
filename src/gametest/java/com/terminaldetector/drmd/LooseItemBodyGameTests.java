package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.physics.collision.OrientedBox;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** First U11 boundary only: native dropped items, ordinary world ticks, no passenger bootstrap. */
public final class LooseItemBodyGameTests implements FabricGameTest {
    private static final int PICKUP_DELAY = 40;
    private static final double NATIVE_GRAVITY = .04, NATIVE_DRAG = .98;
    private static final double FIELD_ACCELERATION = 16; // metres/second squared, .04 blocks/tick squared.
    private static final Quat SIDE = ImmPtlQuaternions.rotationByDegrees(new Vec3(0, 0, 1), 90);

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "loose-item-moving-deck", tickLimit = 160)
    public void aNativeDropContactsATranslatingYawingMaterialDeckWithoutFollowingAPose(TestContext c) {
        var f = new Fixture(c, 0, true, Quat.IDENTITY, new Vec3d(2, 0, 0), new Vec3d(0, .2, 0),
            new Vec3d(2, 2, 2), Vec3d.ZERO, false, false);
        f.drive(12, r -> {
            var contacts = r.body.collisionQuery().contacts(box(r.item), .004);
            c.assertTrue(r.body.age - r.bodySpawnAge >= 12 && r.body.getX() > r.bodyStart.x + .9,
                "ADMISSION: actual body age and translation, not a manually stepped proxy: " + r.state());
            c.assertTrue(r.minimumLocalFeetY >= 1 - 1e-5 && contacts.stream().anyMatch(hit ->
                hit.normal().y() > .95 && hit.penetration() <= .004),
                "GAMEPLAY: native item must not cross real moving deck material; final real contact required: " + r.state());
            c.assertTrue(r.item.getVelocity().x > .03,
                "GAMEPLAY: finite contact friction must transmit some genuine carrier speed: " + r.state());
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "loose-item-local-field", tickLimit = 160)
    public void aRealRotatedBodyFieldSuppliesItsVectorOnceToAnAirborneNativeDrop(TestContext c) {
        var f = new Fixture(c, 128, true, SIDE, Vec3d.ZERO, Vec3d.ZERO,
            new Vec3d(2, 2.5, 2), Vec3d.ZERO, false, true);
        f.drive(4, r -> {
            double expectedVelocity = 0, expectedDistance = 0;
            for (int tick = 0; tick < 4; tick++) {
                expectedVelocity += FIELD_ACCELERATION * .05 * .05;
                expectedDistance += expectedVelocity;
                expectedVelocity *= NATIVE_DRAG;
            }
            c.assertTrue(Math.abs(r.item.getVelocity().x - expectedVelocity) < 1e-6
                && Math.abs(r.item.getX() - r.itemStart.x - expectedDistance) < 1e-6
                && Math.abs(r.item.getY() - r.itemStart.y) < 1e-6 && Math.abs(r.item.getVelocity().y) < 1e-6,
                "GAMEPLAY: rotated +X16m/s^2 field replaces dry world acceleration exactly once, native drag retained: " + r.state());
            c.assertTrue(r.body.collisionQuery().contacts(box(r.item), .004).isEmpty(),
                "ADMISSION: field assertion is airborne, not a collision impulse: " + r.state());
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "loose-item-world-control", tickLimit = 160)
    public void anOutsideNativeDropKeepsVanillaGravityDragAgeAndPickupDelay(TestContext c) {
        var f = new Fixture(c, 256, false, Quat.IDENTITY, Vec3d.ZERO, Vec3d.ZERO,
            new Vec3d(2, 3, 2), Vec3d.ZERO, false, false);
        f.drive(4, r -> {
            double expectedVelocity = 0, expectedDistance = 0;
            for (int tick = 0; tick < 4; tick++) {
                expectedVelocity -= NATIVE_GRAVITY;
                expectedDistance += expectedVelocity;
                expectedVelocity *= NATIVE_DRAG;
            }
            c.assertTrue(Math.abs(r.item.getY() - r.itemStart.y - expectedDistance) < 1e-6
                && Math.abs(r.item.getVelocity().y - expectedVelocity) < 1e-6
                && r.item.getVelocity().horizontalLengthSquared() < 1e-12,
                "CONTROL: no body leaves actual native dry integration unchanged: " + r.state());
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "loose-item-nogravity-control", tickLimit = 160)
    public void anExplicitNoGravityDropDoesNotAcquireAFieldForceOrAnArtificialCarrier(TestContext c) {
        var f = new Fixture(c, 384, true, SIDE, Vec3d.ZERO, Vec3d.ZERO,
            new Vec3d(2, 2.5, 2), Vec3d.ZERO, true, true);
        f.drive(4, r -> c.assertTrue(r.item.getPos().squaredDistanceTo(r.itemStart) < 1e-12
            && r.item.getVelocity().lengthSquared() < 1e-12 && r.item.hasNoGravity(),
            "CONTROL: explicit native NoGravity ownership and inert airborne motion survive body-field overlap: " + r.state()));
    }

    private static OrientedBox box(ItemEntity item) {
        var b = item.getBoundingBox();
        return new OrientedBox(new Vec3((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2),
            new Vec3(b.getLengthX() / 2, b.getLengthY() / 2, b.getLengthZ() / 2), Quat.IDENTITY);
    }

    private static final class Fixture implements AutoCloseable {
        final TestContext c;
        final ServerWorld world;
        final BlockPos base;
        final AiFixtureChunks chunks;
        final boolean withBody, noGravity, field;
        final Quat rotation;
        final Vec3d bodyVelocity, bodySpin, localItem, initialVelocity;
        final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        ItemEntity item;
        BlockBodyEntity body;
        Vec3d itemStart, bodyStart;
        int admissionTicks, itemSpawnAge, bodySpawnAge, lastSampleAge, samples;
        long spawnWorldTick;
        double minimumLocalFeetY = Double.POSITIVE_INFINITY;
        boolean spawned, closed;

        Fixture(TestContext c, int offset, boolean withBody, Quat rotation, Vec3d bodyVelocity, Vec3d bodySpin,
                Vec3d localItem, Vec3d initialVelocity, boolean noGravity, boolean field) {
            this.c = c; world = c.getWorld(); base = c.getAbsolutePos(new BlockPos(240000 + offset, 100, 28000));
            this.withBody = withBody; this.rotation = rotation; this.bodyVelocity = bodyVelocity;
            this.bodySpin = bodySpin; this.localItem = localItem; this.initialVelocity = initialVelocity;
            this.noGravity = noGravity; this.field = field;
            chunks = new AiFixtureChunks(world, base, 0);
        }

        private boolean admit() {
            if (spawned) return true;
            if (!chunks.entityTicking(base)) {
                c.assertTrue(++admissionTicks <= 80, "ADMISSION: native entity-ticking column unavailable: " + chunks.readiness(base));
                return false;
            }
            var access = WorldAccess.of(world);
            // Fixture owns its own finite tickets; production must never use these to load UNKNOWN.
            for (var p : BlockPos.iterate(base.add(-5, -5, -5), base.add(9, 8, 9))) {
                c.assertTrue(access.isVolumeAvailable(D6ChunkPos.ofBlock(p.getX(), p.getY(), p.getZ())),
                    "ADMISSION: fixture's XYZ cube is actually FULL before edit: " + p);
                var state = access.readIfLoaded(p);
                c.assertTrue(state.isPresent(), "ADMISSION: known fixture state must be readable");
                if (!state.orElseThrow().isAir()) {
                    originalBlocks.put(p.toImmutable(), state.orElseThrow());
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                }
            }
            if (withBody) {
                var cells = new ArrayList<BlockBodyEntity.Cell>();
                for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++)
                    cells.add(new BlockBodyEntity.Cell(new BlockPos(x, 0, z), Blocks.IRON_BLOCK.getDefaultState()));
                body = BlockBodyEntity.createLocal(world, cells, Vec3d.of(base), rotation, bodyVelocity, bodySpin);
                if (field) body.setPassengerGravity(new Vec3(0, -FIELD_ACCELERATION, 0));
                c.assertTrue(BodySpatialIndex.find(world, body.getId()).orElse(null) == body,
                    "ADMISSION: actual real-body index identity exists, no test registration");
                itemStart = body.localToWorld(localItem); bodyStart = body.getPos(); bodySpawnAge = body.age;
            } else itemStart = Vec3d.of(base).add(localItem);
            item = new ItemEntity(world, itemStart.x, itemStart.y, itemStart.z,
                new ItemStack(Items.DIAMOND), initialVelocity.x, initialVelocity.y, initialVelocity.z);
            item.setNoGravity(noGravity); item.setPickupDelay(PICKUP_DELAY);
            c.assertTrue(world.spawnEntity(item) && world.getEntity(item.getUuid()) == item,
                "ADMISSION: actual native item factory/spawn owns one UUID");
            itemSpawnAge = lastSampleAge = item.age; spawnWorldTick = world.getTime(); spawned = true;
            return false;
        }

        void drive(int requiredSamples, Consumer<Fixture> assertions) {
            c.runAtEveryTick(() -> {
                if (closed) return;
                try {
                    world.resetIdleTimeout();
                    if (!admit()) return;
                    if (item.age == lastSampleAge) return;
                    c.assertTrue(item.age == lastSampleAge + 1, "ADMISSION: no skipped/manual actor ticks: " + state());
                    lastSampleAge = item.age; samples++;
                    c.assertTrue(item.isAlive() && world.getEntity(item.getUuid()) == item
                        && chunks.entityTicking(item.getBlockPos()) && item.hasNoGravity() == noGravity,
                        "ADMISSION: same live ticking native identity/flags: " + state());
                    if (body != null) minimumLocalFeetY = Math.min(minimumLocalFeetY, body.worldToLocal(item.getPos()).y);
                    var saved = new NbtCompound(); item.writeCustomDataToNbt(saved);
                    c.assertTrue(item.getItemAge() == samples && saved.getShort("PickupDelay") == PICKUP_DELAY - samples
                        && item.getStack().isOf(Items.DIAMOND) && item.getStack().getCount() == 1,
                        "CONTROL: original age/pickup/stack lifecycle remains native: " + state());
                    System.out.println("LOOSE_ITEM_SAMPLE " + state());
                    if (samples < requiredSamples) return;
                    assertions.accept(this); close(); c.complete();
                } catch (Throwable failure) {
                    close(); throw failure;
                }
            });
        }

        String state() {
            return "uuid=" + (item == null ? "absent" : item.getUuid()) + " samples=" + samples
                + " age=" + (item == null ? -1 : item.age) + " spawnAge=" + itemSpawnAge
                + " worldDelta=" + (world.getTime() - spawnWorldTick) + " pos=" + (item == null ? null : item.getPos())
                + " velocity=" + (item == null ? null : item.getVelocity()) + " noGravity=" + noGravity
                + " localMinimumY=" + minimumLocalFeetY + " bodyAge=" + (body == null ? -1 : body.age)
                + " body=" + (body == null ? null : body.getPos()) + " bodyRotation=" + (body == null ? null : body.rotation());
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (item != null) item.discard();
            if (body != null) body.discard();
            for (var entry : originalBlocks.entrySet()) world.setBlockState(entry.getKey(), entry.getValue(), 2);
            chunks.close();
        }
    }
}
