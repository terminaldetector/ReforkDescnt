package com.terminaldetector.drmd.world.build;

import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import java.util.List;

/** The same immutable footprint describes client prediction and the server's writes. */
public record PlacementPlan(List<Cell> cells,Reason reason,boolean handlesPair) {
    public enum Reason { READY,NO_MATERIAL,NO_PERMISSION,OUTSIDE_WORLD,UNLOADED,OCCUPIED,COLLISION,NO_SUPPORT,ITEM_DATA,UNSUPPORTED,CHANGED }
    public record Cell(BlockPos position,BlockState before,BlockState after) {
        public Cell {position=position.toImmutable();}
    }
    public PlacementPlan {cells=List.copyOf(cells);}
    public boolean ready(){return reason==Reason.READY;}
    public static PlacementPlan rejected(Reason reason){return new PlacementPlan(List.of(),reason,false);}
    private PlacementPlan rejectedWith(Reason why){return new PlacementPlan(cells,why,handlesPair);}

    public static PlacementPlan resolve(World world,PlayerEntity player,BlockPos at,BlockState desired) {
        Block selected=desired.getBlock();
        boolean bed=selected instanceof BedBlock;
        boolean vertical=desired.contains(Properties.DOUBLE_BLOCK_HALF);
        // Crops with an age-dependent second part need their own growth-aware resolver.
        if(vertical && !(selected instanceof DoorBlock || selected instanceof TallPlantBlock)
            || vertical && desired.getProperties().stream().anyMatch(p->p.getName().equals("age")))return rejected(Reason.UNSUPPORTED);
        if(bed && !desired.contains(Properties.HORIZONTAL_FACING))return rejected(Reason.UNSUPPORTED);
        BlockPos other=bed?at.offset(desired.get(Properties.HORIZONTAL_FACING)):at.up();
        List<BlockPos> positions=bed || vertical?List.of(at,other):List.of(at);
        // Check the entire footprint before reading any target state or redstone/fluid neighbour.
        for(var pos:positions) {
            if(player.getEyePos().squaredDistanceTo(pos.toCenterPos())>AdaptivePlacement.MAX_REACH*AdaptivePlacement.MAX_REACH)return rejected(Reason.OUTSIDE_WORLD);
            if(world.isOutOfHeightLimit(pos) || !world.getWorldBorder().contains(pos))return rejected(Reason.OUTSIDE_WORLD);
            if(!com.terminaldetector.drmd.world.edit.WorldEditSafety.neighbourhoodLoaded(world,pos))return rejected(Reason.UNLOADED);
        }
        BlockState first=bed?desired.with(Properties.BED_PART,BedPart.FOOT):vertical?desired.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER):desired;
        if(selected instanceof DoorBlock) {
            boolean powered=world.isReceivingRedstonePower(at) || world.isReceivingRedstonePower(other);
            first=first.with(Properties.POWERED,powered).with(Properties.OPEN,powered);
        }
        BlockState second=bed?first.with(Properties.BED_PART,BedPart.HEAD):vertical?first.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER):null;
        var cells=new java.util.ArrayList<Cell>(positions.size());
        for(int i=0;i<positions.size();i++) {
            var pos=positions.get(i);BlockState state=i==0?first:second;
            if(state.contains(Properties.WATERLOGGED))state=state.with(Properties.WATERLOGGED,world.getFluidState(pos).isIn(FluidTags.WATER));
            cells.add(new Cell(pos,world.getBlockState(pos),state));
        }
        var plan=new PlacementPlan(cells,Reason.READY,bed || vertical);
        for(int i=0;i<cells.size();i++) {
            var cell=cells.get(i);var pos=cell.position();
            if(!world.canPlayerModifyAt(player,pos))return plan.rejectedWith(Reason.NO_PERMISSION);
            if(!cell.before().isReplaceable() || world.getBlockEntity(pos)!=null)return plan.rejectedWith(Reason.OCCUPIED);
            // A vertical upper part is supported by its matching planned lower part, not by air.
            if(!(vertical && i==1) && !cell.after().canPlaceAt(world,pos))return plan.rejectedWith(Reason.NO_SUPPORT);
            if(!world.canPlace(cell.after(),pos,ShapeContext.absent()))return plan.rejectedWith(Reason.COLLISION);
        }
        return plan;
    }
}
