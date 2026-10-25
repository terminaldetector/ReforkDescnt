package com.terminaldetector.drmd.world.portal.mirror;

import com.terminaldetector.drmd.world.portal.PortalComplexity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** A single Fusion impact powers both ends of a ready, loaded corridor pair. */
public final class FusionPortalActivation {
    public static final int RANGE=24;
    private FusionPortalActivation() {}
    public static boolean isSurface(BlockState state) {
        return state.getBlock() instanceof PortalPanelBlock || state.getBlock() instanceof ChargedMirrorBlock;
    }
    public static boolean activate(ServerWorld world,BlockPos pos,Direction hitSide,LivingEntity owner) {
        if (!world.isChunkLoaded(pos)) return false;
        BlockState state=world.getBlockState(pos);
        if (!isSurface(state)) return false;
        Direction facing=state.get(net.minecraft.state.property.Properties.FACING);
        if (hitSide!=facing || !(owner instanceof PlayerEntity player) || !canModify(world,player,pos)) return false;
        if (isLinked(state)) return true;
        for(int distance=1;distance<=RANGE;distance++) {
            BlockPos partner=pos.offset(facing,distance);
            if(!world.isChunkLoaded(partner)) break;
            BlockState other=world.getBlockState(partner);
            if(other.isAir()) continue;
            if(other.getBlock()!=state.getBlock() || isLinked(other)
                    || other.get(net.minecraft.state.property.Properties.FACING)!=facing.getOpposite()
                    || !canModify(world,player,partner)) break;
            // Only cleanup of old saved optional-backend entities uses the compatibility bridge.
            // Activation and the resulting links always use the native implementation.
            if(state.getBlock() instanceof PortalPanelBlock) {
                if(!(world.getBlockEntity(pos) instanceof PortalPanelBlockEntity a)
                        || !(world.getBlockEntity(partner) instanceof PortalPanelBlockEntity b)) break;
                if(PortalComplexity.hasImmersivePortals()) {
                    ImmPtlMirrorBridge.detach(world,a.getAttachedEntityId());
                    ImmPtlMirrorBridge.detach(world,b.getAttachedEntityId());
                }
                PortalPanelBlock.linkNatively(world,pos,partner);
            } else {
                if(!(world.getBlockEntity(pos) instanceof ChargedMirrorBlockEntity a)
                        || !(world.getBlockEntity(partner) instanceof ChargedMirrorBlockEntity b)) break;
                if(PortalComplexity.hasImmersivePortals()) {
                    ImmPtlMirrorBridge.detach(world,a.getAttachedEntityId());
                    ImmPtlMirrorBridge.detach(world,b.getAttachedEntityId());
                }
                ChargedMirrorBlock.linkNatively(world,pos,partner);
            }
            for(BlockPos end:new BlockPos[]{pos,partner}) {
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,end.getX()+.5,end.getY()+.5,end.getZ()+.5,30,.4,.4,.4,.02);
                world.playSound(null,end,SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,SoundCategory.BLOCKS,.8f,1.1f);
            }
            player.sendMessage(Text.translatable("message.drmd.portal.fusion_activated"),true);
            return true;
        }
        player.sendMessage(Text.translatable("message.drmd.portal.fusion_no_partner",RANGE),true);
        return false;
    }
    private static boolean canModify(ServerWorld world,PlayerEntity player,BlockPos pos) {
        return player.getAbilities().allowModifyWorld && world.getWorldBorder().contains(pos)
                && world.canPlayerModifyAt(player,pos);
    }
    private static boolean isLinked(BlockState state) {
        return state.getBlock() instanceof PortalPanelBlock
                ? state.get(PortalPanelBlock.LINKED) : state.get(ChargedMirrorBlock.LINKED);
    }
}
