package com.terminaldetector.drmd.world.build;

import com.terminaldetector.drmd.world.LocalOrientation;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Shared, server-owned placement transaction for tools and construction lasers. */
public final class AdaptivePlacement {
    public static final int MAX_BLOCKS_PER_ACTION=256;
    private static final double MAX_REACH=256;
    private AdaptivePlacement() {}

    public static Vec3d localUp(PlayerEntity player) {
        return FootGravitySystem.isActive(player)?FootGravitySystem.getUp(player):LocalOrientation.getUp(player.getUuid());
    }

    public static Vec3d aim(PlayerEntity player) {
        return com.terminaldetector.drmd.weapon.core.WeaponCore.aimDir(player);
    }

    public static BlockPos placeOnSurface(World world,PlayerEntity player,BlockPos hit,Direction face) {
        Block material=resolveMaterial(player);
        if(material==null)return null;
        BlockPos at=hit.offset(face);
        return placeState(world,player,at,orient(material.getDefaultState(),face),false)?at:null;
    }

    public static BlockState orient(BlockState state,Direction face) {
        if(state.contains(Properties.FACING))return state.with(Properties.FACING,face);
        if(state.contains(Properties.HORIZONTAL_FACING))return state.with(Properties.HORIZONTAL_FACING,face.getAxis().isHorizontal()?face:Direction.NORTH);
        return state;
    }

    private static ItemStack materialStack(PlayerEntity player,boolean leftHandOnly) {
        ItemStack off=player.getOffHandStack();
        if(!off.isEmpty() && off.getItem() instanceof BlockItem)return off;
        if(leftHandOnly)return ItemStack.EMPTY;
        for(int i=0;i<9;i++) {
            ItemStack stack=player.getInventory().getStack(i);
            if(!stack.isEmpty() && stack.getItem() instanceof BlockItem)return stack;
        }
        return ItemStack.EMPTY;
    }

    public static Block resolveMaterial(PlayerEntity player) {return resolveMaterial(player,false);}
    public static Block resolveMaterial(PlayerEntity player,boolean leftHandOnly) {
        ItemStack stack=materialStack(player,leftHandOnly);
        if(stack.getItem() instanceof BlockItem block)return block.getBlock();
        return !leftHandOnly && player.getAbilities().creativeMode?Blocks.IRON_BLOCK:null;
    }

    /** Validation precedes all reads of destination block state and every inventory mutation. */
    public static boolean placeState(World world,PlayerEntity player,BlockPos at,BlockState state,boolean leftHandOnly) {
        if(world.isClient || !player.isAlive() || player.isSpectator() || !player.getAbilities().allowModifyWorld)return false;
        if(world.isOutOfHeightLimit(at) || !world.getWorldBorder().contains(at) || !world.isChunkLoaded(at))return false;
        if(player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(at))>MAX_REACH*MAX_REACH || !world.canPlayerModifyAt(player,at))return false;
        ItemStack material=materialStack(player,leftHandOnly);
        Block selected=resolveMaterial(player,leftHandOnly);
        if(selected==null || state.getBlock()!=selected)return false;
        // Their onPlaced callback writes a second cell. Keep the item intact until the
        // construction tool can validate and commit the entire footprint atomically.
        if(state.contains(Properties.DOUBLE_BLOCK_HALF) || selected instanceof net.minecraft.block.BedBlock)return false;
        boolean operator=state.isOf(Blocks.COMMAND_BLOCK) || state.isOf(Blocks.CHAIN_COMMAND_BLOCK)
            || state.isOf(Blocks.REPEATING_COMMAND_BLOCK) || state.isOf(Blocks.STRUCTURE_BLOCK) || state.isOf(Blocks.JIGSAW);
        if(operator && !(player.getAbilities().creativeMode && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && serverPlayer.hasPermissionLevel(2)))return false;
        // The direct tool path cannot safely transfer container/block-entity item data yet.
        // Leave such stacks intact instead of placing an empty copy and destroying their contents.
        if(material.contains(DataComponentTypes.BLOCK_ENTITY_DATA) || material.contains(DataComponentTypes.CONTAINER))return false;
        if(!world.getBlockState(at).isReplaceable() || !state.canPlaceAt(world,at) || !world.canPlace(state,at,ShapeContext.absent()))return false;
        if(!world.setBlockState(at,state,Block.NOTIFY_ALL))return false;
        state.getBlock().onPlaced(world,at,state,player,material);
        if(!player.getAbilities().creativeMode)material.decrement(1);
        return true;
    }

    /** The caller's ray and the actual placement share full ship-forward / local-gravity eyes. */
    public static BlockPos placeAlongLook(World world,PlayerEntity player,double range) {return placeAlongLook(world,player,range,false);}
    public static BlockPos placeAlongLook(World world,PlayerEntity player,double range,boolean leftHandOnly) {
        if(world.isClient || !Double.isFinite(range) || range<=0)return null;
        Block material=resolveMaterial(player,leftHandOnly);
        if(material==null)return null;
        range=Math.min(range,MAX_REACH);
        Vec3d eye=player.getEyePos(),direction=aim(player);
        // A long ray must not synchronously pull unloaded chunks into the server.
        double loadedRange=range;
        for(double distance=0;distance<=range;distance+=1) {
            if(!world.isChunkLoaded(BlockPos.ofFloored(eye.add(direction.multiply(distance))))) {loadedRange=Math.max(0,distance-1);break;}
        }
        var hit=world.raycast(new net.minecraft.world.RaycastContext(eye,eye.add(direction.multiply(loadedRange)),
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
        Vec3d up=localUp(player);
        Direction face=hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK?hit.getSide():Direction.getFacing(up.x,up.y,up.z);
        BlockPos at=hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK?hit.getBlockPos().offset(face):BlockPos.ofFloored(eye.add(direction.multiply(Math.min(4,loadedRange))));
        return placeState(world,player,at,orient(material.getDefaultState(),face),leftHandOnly)?at:null;
    }

    public static int placeMany(World world,PlayerEntity player,java.util.List<BlockPos> cells,boolean leftHandOnly) {
        if(world.isClient || cells==null || cells.isEmpty())return 0;
        Vec3d up=localUp(player);Direction face=Direction.getFacing(up.x,up.y,up.z);
        int placed=0,examined=0;
        for(BlockPos at:cells) {
            if(++examined>MAX_BLOCKS_PER_ACTION)break;
            Block material=resolveMaterial(player,leftHandOnly);
            if(material==null)break;
            if(placeState(world,player,at,orient(material.getDefaultState(),face),leftHandOnly))placed++;
        }
        return placed;
    }
}
