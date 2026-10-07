package com.terminaldetector.drmd.world.geometry;

import com.terminaldetector.drmd.world.micro.BlockDamage;
import com.terminaldetector.drmd.world.micro.CarvedBlock;
import com.terminaldetector.drmd.world.micro.CarvedBlockEntity;
import com.terminaldetector.drmd.world.micro.MicroGrid;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import static net.minecraft.server.command.CommandManager.*;

/** Explicit authoring mode until generators can retain terrain/building provenance. */
public final class HybridTerrainCommands {
    public static final TagKey<Block> ORGANIC_MATERIALS=TagKey.of(RegistryKeys.BLOCK,Identifier.of("drmd","organic_materials"));
    private HybridTerrainCommands() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> {
            var root=literal("d6terrain").requires(source -> source.hasPermissionLevel(2));
            for (String mode:new String[]{"smooth","rigid"}) {
                root.then(literal(mode).then(argument("from",BlockPosArgumentType.blockPos())
                    .then(argument("to",BlockPosArgumentType.blockPos()).executes(ctx -> {
                        try {
                            int count=apply(ctx.getSource().getWorld(),
                                BlockPosArgumentType.getLoadedBlockPos(ctx,"from"),
                                BlockPosArgumentType.getLoadedBlockPos(ctx,"to"),mode.equals("smooth"));
                            ctx.getSource().sendFeedback(() -> Text.literal("Terrain "+mode+": "+count+" blocks changed (unsupported materials skipped)"),false);
                            return count;
                        } catch (IllegalArgumentException e) {
                            ctx.getSource().sendError(Text.literal(e.getMessage())); return 0;
                        }
                    }))));
            }
            dispatcher.register(root);
        });
    }
    public static int apply(ServerWorld world,BlockPos from,BlockPos to,boolean organic) {
        long dx=Math.abs((long)from.getX()-to.getX())+1,dy=Math.abs((long)from.getY()-to.getY())+1,dz=Math.abs((long)from.getZ()-to.getZ())+1;
        if (dx>4096 || dy>4096 || dz>4096 || dx*dy*dz>4096)
            throw new IllegalArgumentException("Select at most 4096 blocks per operation");
        for (BlockPos p:BlockPos.iterate(from,to)) {
            if (world.isOutOfHeightLimit(p) || !world.isChunkLoaded(p))
                throw new IllegalArgumentException("The entire selection must be loaded and inside the current world height");
        }
        int changed=0;
        for (BlockPos cursor:BlockPos.iterate(from,to)) {
            BlockPos p=cursor.toImmutable();var state=world.getBlockState(p);
            var existing=world.getBlockEntity(p) instanceof CarvedBlockEntity c?c:null;
            if (!organic) {
                if (existing!=null && existing.organic()) {
                    if (existing.mask()==MicroGrid.FULL) CarvedBlock.restore(world,p);
                    else existing.setOrganic(false);
                    changed++;
                }
                continue;
            }
            var source=existing==null?state:existing.source();
            if (!source.isIn(ORGANIC_MATERIALS) || !source.isOpaque() || !source.getFluidState().isEmpty()) continue;
            if (existing!=null && existing.organic()) continue;
            if (existing==null) {
                if (!BlockDamage.isDamageable(world,p,state)) continue;
                existing=CarvedBlock.replace(world,p,state,MicroGrid.FULL);
            }
            if (existing!=null) { existing.setOrganic(true); changed++; }
        }
        return changed;
    }
}
