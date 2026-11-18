package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Native static-world movement, with a loaded-only full XYZ/shape-halo admission before any read.
 * The existing hull motor supplies dynamic contacts; this adapter never generates or tickets. */
final class LooseItemWorldMotion implements DynamicBodyAttachment.Motion {
    static final int MAX_CUBES = 216;
    private final Entity item;
    private final WorldAccess access;
    private final Map<D6ChunkPos, Boolean> cubes = new HashMap<>();
    private boolean blockedUnknown;

    LooseItemWorldMotion(ItemEntity item) {this((Entity)item);}
    LooseItemWorldMotion(Entity entity) {this.item = entity; access = WorldAccess.of(entity.getWorld());}
    boolean blockedUnknown() {return blockedUnknown;}

    boolean available(Box box) {
        var world = item.getWorld();
        if (box.minY < world.getBottomY() || box.maxY > world.getTopY() || !world.getWorldBorder().contains(box))
            return false;
        // Native shape/fluid providers can inspect their immediate neighbours.
        var halo = box.expand(1);
        int x0 = MathHelper.floor(halo.minX) >> 4, x1 = MathHelper.floor(Math.nextDown(halo.maxX)) >> 4;
        int y0 = MathHelper.floor(halo.minY) >> 4, y1 = MathHelper.floor(Math.nextDown(halo.maxY)) >> 4;
        int z0 = MathHelper.floor(halo.minZ) >> 4, z1 = MathHelper.floor(Math.nextDown(halo.maxZ)) >> 4;
        if ((long)(x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_CUBES) return false;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            var cube = new D6ChunkPos(x, y, z);
            if (!cubes.containsKey(cube)) {
                if (cubes.size() >= MAX_CUBES) return false;
                cubes.put(cube, access.isVolumeAvailable(cube));
            }
            if (!cubes.get(cube)) return false;
        }
        return true;
    }

    @Override public DynamicBodyAttachment.Move move(Vec3 feet, Quat frame, Vec3 requested) {
        var delta = BoardingSystem.mc(requested);
        if (!available(item.getBoundingBox().stretch(delta))) {
            blockedUnknown = true;
            return new DynamicBodyAttachment.Move(new Vec3(0, 0, 0),
                requested.lengthSquared() > 1e-20 ? java.util.List.of(requested.normalized().scaled(-1)) : java.util.List.of());
        }
        var before = item.getPos();
        item.move(MovementType.SELF, delta);
        var actual = item.getPos().subtract(before);
        var normals = new ArrayList<Vec3>();
        if (requested.lengthSquared() > 1e-7) for (int axis = 0; axis < 3; axis++) {
            double want = axis == 0 ? delta.x : axis == 1 ? delta.y : delta.z;
            double got = axis == 0 ? actual.x : axis == 1 ? actual.y : actual.z;
            if (Math.abs(want - got) > 1e-7) normals.add(axis == 0 ? new Vec3(-Math.signum(want), 0, 0)
                : axis == 1 ? new Vec3(0, -Math.signum(want), 0) : new Vec3(0, 0, -Math.signum(want)));
        }
        return new DynamicBodyAttachment.Move(BoardingSystem.pure(actual), normals);
    }
}
