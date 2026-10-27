package com.terminaldetector.drmd.world.build;

import com.terminaldetector.drmd.world.LocalOrientation;
import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
    public static final int MAX_BLOCKS_PER_ACTION=PlacementTransaction.MAX_CELLS;
    static final double MAX_REACH=256;
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

    /** Read-only prediction; the server recomputes the same plan against its own world/inventory. */
    public static PlacementPlan plan(World world,PlayerEntity player,BlockPos at,BlockState state,boolean leftHandOnly) {
        if(!player.isAlive() || player.isSpectator() || !player.getAbilities().allowModifyWorld)return PlacementPlan.rejected(PlacementPlan.Reason.NO_PERMISSION);
        if(player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(at))>MAX_REACH*MAX_REACH)return PlacementPlan.rejected(PlacementPlan.Reason.OUTSIDE_WORLD);
        ItemStack material=materialStack(player,leftHandOnly);
        Block selected=resolveMaterial(player,leftHandOnly);
        if(selected==null || state.getBlock()!=selected)return PlacementPlan.rejected(PlacementPlan.Reason.NO_MATERIAL);
        boolean operator=state.isOf(Blocks.COMMAND_BLOCK) || state.isOf(Blocks.CHAIN_COMMAND_BLOCK)
            || state.isOf(Blocks.REPEATING_COMMAND_BLOCK) || state.isOf(Blocks.STRUCTURE_BLOCK) || state.isOf(Blocks.JIGSAW);
        if(operator && !(player.getAbilities().creativeMode && player.hasPermissionLevel(2)))return PlacementPlan.rejected(PlacementPlan.Reason.NO_PERMISSION);
        if(material.contains(DataComponentTypes.BLOCK_ENTITY_DATA) || material.contains(DataComponentTypes.CONTAINER))return PlacementPlan.rejected(PlacementPlan.Reason.ITEM_DATA);
        return PlacementPlan.resolve(world,player,at,state);
    }

    public static boolean placeState(World world,PlayerEntity player,BlockPos at,BlockState state,boolean leftHandOnly) {
        if(world.isClient)return false;
        return commit(world,player,plan(world,player,at,state,leftHandOnly),leftHandOnly)>0;
    }

    static int commit(World world,PlayerEntity player,PlacementPlan plan,boolean leftHandOnly) {
        if(world.isClient || !plan.ready())return 0;
        ItemStack material=materialStack(player,leftHandOnly),placedStack=material.copy();
        int available=material.getCount();Block selected=resolveMaterial(player,leftHandOnly);
        if(selected!=plan.cells().getFirst().after().getBlock())return 0;
        if(!plan.handlesPair()) {
            var cell=plan.cells().getFirst();
            if(plan.cells().size()!=1 || !world.getBlockState(cell.position()).equals(cell.before()))return 0;
            // Single vanilla blocks can legitimately react during insertion (e.g. powered TNT).
            // Preserve that path and charge the successful insertion even if it becomes an entity.
            if(!world.setBlockState(cell.position(),cell.after(),Block.NOTIFY_ALL))return 0;
            if(!player.getAbilities().creativeMode)material.decrement(1);
            if(world.getBlockState(cell.position()).isOf(selected))selected.onPlaced(world,cell.position(),cell.after(),player,placedStack);
            return 1;
        }
        var changes=plan.cells().stream().map(cell->new PlacementTransaction.Change<>(cell.position(),cell.before(),cell.after())).toList();
        var result=PlacementTransaction.commit(changes,new PlacementTransaction.Storage<BlockPos,BlockState>() {
            public BlockState read(BlockPos position){return world.getBlockState(position);}
            public boolean write(BlockPos position,BlockState value){return world.setBlockState(position,value,Block.FORCE_STATE);}
        },()->player.getWorld()==world && player.isAlive() && !player.isSpectator() && player.getAbilities().allowModifyWorld
            && materialStack(player,leftHandOnly)==material && material.getCount()==available
            && resolveMaterial(player,leftHandOnly)==selected);
        if(!result.committed()) {
            if(result.status()==PlacementTransaction.Status.ROLLBACK_FAILED) {
                com.terminaldetector.drmd.DescentMod.LOGGER.error("Construction rollback failed for {}",player.getUuid(),result.failure());
                for(var cell:plan.cells())world.updateListeners(cell.position(),cell.before(),world.getBlockState(cell.position()),Block.NOTIFY_ALL);
            }
            return 0;
        }
        if(!player.getAbilities().creativeMode)material.decrement(1);
        // Both parts already exist: their vanilla onPlaced callback would duplicate the write.
        for(var cell:plan.cells()) {
            world.updateListeners(cell.position(),cell.before(),world.getBlockState(cell.position()),Block.NOTIFY_ALL);
            world.updateNeighbors(cell.position(),cell.after().getBlock());
        }
        return plan.cells().size();
    }

    /** The caller's ray and the actual placement share full ship-forward / local-gravity eyes. */
    public static BlockPos placeAlongLook(World world,PlayerEntity player,double range) {return placeAlongLook(world,player,range,false);}
    public static BlockPos placeAlongLook(World world,PlayerEntity player,double range,boolean leftHandOnly) {
        if(world.isClient || !Double.isFinite(range) || range<=0)return null;
        Block material=resolveMaterial(player,leftHandOnly);
        if(material==null)return null;
        range=Math.min(range,MAX_REACH);
        Vec3d eye=player.getEyePos(),direction=aim(player);
        if(!com.terminaldetector.drmd.world.edit.WorldEditSafety.neighbourhoodLoaded(world,BlockPos.ofFloored(eye)))return null;
        Vec3d end=com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedEnd(world,eye,eye.add(direction.multiply(range)));
        double loadedRange=end.distanceTo(eye);
        var hit=world.raycast(new net.minecraft.world.RaycastContext(eye,end,
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
            var proposal=plan(world,player,at,orient(material.getDefaultState(),face),leftHandOnly);
            if(proposal.ready() && placed+proposal.cells().size()>MAX_BLOCKS_PER_ACTION)break;
            placed+=commit(world,player,proposal,leftHandOnly);
        }
        return placed;
    }
}
