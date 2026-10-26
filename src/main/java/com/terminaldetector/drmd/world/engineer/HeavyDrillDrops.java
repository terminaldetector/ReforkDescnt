package com.terminaldetector.drmd.world.engineer;

import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.ArrayList;
import java.util.List;

/** One heavy hand-drill pulse: bounded loot stacks instead of one item entity per block. */
public final class HeavyDrillDrops implements AutoCloseable {
    private final ServerWorld world;
    private final LivingEntity breaker;
    private final Vec3d output;
    private final List<ItemStack> stacks=new ArrayList<>();
    public static final int MAX_NEW_ENTITIES=8,MAX_NEARBY_ENTITIES=32;
    public HeavyDrillDrops(ServerWorld world,LivingEntity breaker,Vec3d output) {
        this.world=world;this.breaker=breaker;this.output=output;
    }
    public boolean breakBlock(BlockPos pos) {
        var state=world.getBlockState(pos);
        boolean creative=breaker instanceof PlayerEntity player && player.isCreative();
        var loot=creative?List.<ItemStack>of():Block.getDroppedStacks(state,world,pos,world.getBlockEntity(pos),breaker,ItemStack.EMPTY);
        if(!world.breakBlock(pos,false,breaker))return false;
        for(var stack:loot)add(stack);
        if(!creative)state.onStacksDropped(world,pos,ItemStack.EMPTY,true);
        com.terminaldetector.drmd.world.store.SurfaceIngest.onGroundChanged(world,pos);
        return true;
    }
    private void add(ItemStack incoming) {
        var remaining=incoming.copy();
        for(var stack:stacks)if(ItemStack.areItemsAndComponentsEqual(stack,remaining)) {
            int move=Math.min(remaining.getCount(),stack.getMaxCount()-stack.getCount());
            stack.increment(move);remaining.decrement(move);if(remaining.isEmpty())return;
        }
        // The heavy tool deliberately caps recovery, rather than retaining an unbounded backlog.
        while(!remaining.isEmpty() && stacks.size()<MAX_NEW_ENTITIES) {
            int count=Math.min(remaining.getCount(),remaining.getMaxCount());
            stacks.add(remaining.copyWithCount(count));remaining.decrement(count);
        }
    }
    @Override public void close() {
        if(stacks.isEmpty())return;
        var nearby=new ArrayList<ItemEntity>();
        world.collectEntitiesByType(net.minecraft.util.TypeFilter.instanceOf(ItemEntity.class),
            new Box(BlockPos.ofFloored(output)).expand(12),e->e.isAlive(),nearby,MAX_NEARBY_ENTITIES);
        int slots=Math.max(0,MAX_NEARBY_ENTITIES-nearby.size());
        for(var stack:stacks) {
            if(slots--<=0)break;
            var entity=new ItemEntity(world,output.x,output.y,output.z,stack);entity.setToDefaultPickupDelay();
            world.spawnEntity(entity);
        }
    }
}
