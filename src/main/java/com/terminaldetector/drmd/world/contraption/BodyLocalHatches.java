package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.collision.BodyBounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.GameMode;

/** Existing vanilla oak hatches in a moving local grid. No world block or independent body. */
public final class BodyLocalHatches {
    private BodyLocalHatches() {}

    // Fluid-bearing moving cells need a separate fluid contract. Never silently discard water.
    public static boolean supports(BlockState state) {
        return state.isOf(Blocks.OAK_TRAPDOOR) && !state.get(Properties.WATERLOGGED);
    }

    /** Vanilla's exact single slab changes axis with OPEN/FACING/HALF, in body coordinates. */
    public static BodyBounds collisionBounds(BlockPos cell, BlockState state) {
        if (!supports(state)) throw new IllegalArgumentException("Unsupported local hatch");
        var shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        var boxes = shape.getBoundingBoxes();
        if (boxes.size() != 1) throw new IllegalStateException("Oak hatch must have one collision slab");
        var box = boxes.getFirst().offset(cell);
        return new BodyBounds(new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ));
    }

    /** Ordinary USE retains the existing authoritative ray/revision/protection route. */
    public static boolean use(ServerPlayerEntity player, BlockBodyEntity body, BlockPos cell) {
        if (!player.getServer().isOnThread() || player.getWorld() != body.getWorld()
                || !player.isAlive() || player.isSpectator() || player.hasVehicle() || body.isRemoved()) return false;
        var mode = player.interactionManager.getGameMode();
        if (!(mode == GameMode.CREATIVE || mode == GameMode.SURVIVAL)) return false;
        var state = body.getLocalBlock(cell);
        if (!supports(state)) return false;
        var hit = BodyBlockInteraction.target(player, player.getEyePos(), BodyBlockInteraction.viewDirection(player),
                Math.min(16, player.getBlockInteractionRange()));
        if (hit.isEmpty() || hit.get().body() != body || !hit.get().hit().localBlock().equals(cell)
                || hit.get().hit().revision() != body.shapeRevision()) return false;
        var point = hit.get().hit().worldPoint();
        if (!player.getServerWorld().canPlayerModifyAt(player, BlockPos.ofFloored(point.x(), point.y(), point.z()))) return false;
        var next = state.cycle(Properties.OPEN);
        // Refuse a closing hatch through a head, item, other hull or unloaded/solid terrain.
        if (!new BlockBodyWorldAccess(body).canOccupyLocalBounds(collisionBounds(cell, next))) return false;
        return body.setLocalBlock(cell, next); // Existing atomic state/mass/geometry/patch transaction.
    }
}
