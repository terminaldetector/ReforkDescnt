package com.terminaldetector.drmd.mixin;

import com.terminaldetector.drmd.world.cubic.CubicWorldSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.gen.chunk.BlendingData;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class CubicChunkMixin extends Chunk {
    protected CubicChunkMixin(ChunkPos pos, UpgradeData upgradeData, HeightLimitView height,
            Registry<Biome> biomes, long inhabitedTime, ChunkSection[] sections, BlendingData blending) {
        super(pos, upgradeData, height, biomes, inhabitedTime, sections, blending);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void drmd$cubeChanged(BlockPos pos, BlockState state, boolean moved,
            CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null && ((WorldChunk) (Object) this).getWorld() instanceof ServerWorld world) {
            if (world.getServer().isOnThread()) drmd$removeReplacedPendingOwner(pos, cir.getReturnValue(), state);
            CubicWorldSystem.changed(world, pos);
            if (state.isAir() && !cir.getReturnValue().isAir()) {
                com.terminaldetector.drmd.world.compat.NativeCubicSkylight.removed(world, pos);
            }
            com.terminaldetector.drmd.world.store.SurfaceIngest.onGroundChanged(world, pos);
        }
    }

    /** Native removeBlockEntity detaches the live owner. An unmaterialized marker needs the same lifetime. */
    @Unique
    private void drmd$removeReplacedPendingOwner(BlockPos pos, BlockState previous, BlockState replacement) {
        if (!previous.hasBlockEntity() || replacement.hasBlockEntity() || !getBlockState(pos).equals(replacement)) return;
        var pending = blockEntityNbts.get(pos);
        if (pending == null || !pending.contains("x", NbtElement.INT_TYPE) || !pending.contains("y", NbtElement.INT_TYPE)
                || !pending.contains("z", NbtElement.INT_TYPE) || pending.getInt("x") != pos.getX()
                || pending.getInt("y") != pos.getY() || pending.getInt("z") != pos.getZ()) return;
        String id = pending.getString("id");
        // Other typed records may have inventories or other mod-specific data. Keep their native path.
        if ("DUMMY".equals(id) || "minecraft:mob_spawner".equals(id) && previous.isOf(Blocks.SPAWNER)) {
            blockEntityNbts.remove(pos, pending);
        }
    }
}
