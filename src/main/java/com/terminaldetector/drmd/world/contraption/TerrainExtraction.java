package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.geometry.NaturalGeometry;
import com.terminaldetector.drmd.world.geometry.OrganicBlock;
import com.terminaldetector.drmd.world.profile.WorldProfile;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;

/** Transfers a small, exposed piece of loaded terrain to the existing compound-body runtime. */
public final class TerrainExtraction {
    private TerrainExtraction() {}

    static boolean supports(BlockState state) {
        return !state.hasBlockEntity() && state.getFluidState().isEmpty()
            && !state.isIn(BlockTags.LOGS) && !state.isIn(BlockTags.LEAVES)
            && (state.getBlock() instanceof OrganicBlock || NaturalGeometry.applies(state));
    }

    public static BlockBodyEntity extract(ServerWorld world, ServerPlayerEntity player, BlockHitResult hit,
                                         int cellLimit, double massLimit, double maxHardness) {
        if (!world.getServer().isOnThread() || !WorldProfile.isUngravity(world) || player.getWorld() != world
            || cellLimit < 1 || cellLimit > 3 || !Double.isFinite(massLimit) || !Double.isFinite(maxHardness)) return null;
        BlockPos faceCell = hit.getBlockPos();
        Direction face = hit.getSide();
        BlockState first = exposedMaterial(world, player, faceCell, face, maxHardness);
        if (first == null || BlockBodyEntity.blockMass(first) > massLimit
            || !BlastStructuralCollapse.canDetach(world, hit.getPos())) return null;

        var selection = new LinkedHashMap<BlockPos, BlockState>();
        selection.put(faceCell, first);
        double mass = BlockBodyEntity.blockMass(first);
        // A rail can move a short strip from the exposed face. The complete selection can move
        // out along that face without asking the body solver to tunnel through remaining terrain.
        for (Direction edge : Direction.values()) {
            if (selection.size() >= cellLimit) break;
            if (edge.getAxis() == face.getAxis()) continue;
            BlockPos next = faceCell.offset(edge);
            BlockState candidate = exposedMaterial(world, player, next, face, maxHardness);
            if (!first.equals(candidate) || mass + BlockBodyEntity.blockMass(candidate) > massLimit) continue;
            selection.put(next, candidate);
            mass += BlockBodyEntity.blockMass(candidate);
        }

        BlockBodyEntity body = BlastStructuralCollapse.detach(world, selection);
        if (body == null) return null;
        body.setPhysicsGravity(true);
        body.applyPhysicsImpulse(hit.getPos(), Vec3d.of(face.getVector()).multiply(4 * mass));
        return body;
    }

    private static BlockState exposedMaterial(ServerWorld world, ServerPlayerEntity player, BlockPos at,
                                              Direction face, double maxHardness) {
        if (!WorldEditSafety.mayChange(world, at, player)) return null;
        var access = WorldAccess.of(world);
        BlockState state = access.readIfLoaded(at).orElse(null);
        if (state == null || !(state.getBlock() instanceof OrganicBlock) || !supports(state)
            || access.readIfLoaded(at.offset(face)).filter(BlockState::isAir).isEmpty()) return null;
        float hardness = state.getHardness(world, at);
        return hardness >= 0 && hardness <= maxHardness ? state : null;
    }
}
