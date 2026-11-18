package com.terminaldetector.drmd.world.contraption;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.collision.BodyBounds;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.*;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.GameMode;
import java.util.*;
import java.util.function.Function;

/** One vanilla wooden door is two ordinary cells in the existing moving local grid. */
public final class BodyLocalDoors {
    private BodyLocalDoors(){}
    public record Pair(BlockPos lower,BlockPos upper,BlockState lowerState,BlockState upperState){}
    public static boolean supports(BlockState state){return state.getBlock() instanceof DoorBlock&&state.isIn(BlockTags.WOODEN_DOORS);}
    /** Exact vanilla leaf, including OPEN/FACING/HINGE, never the enclosing full cube. */
    public static BodyBounds collisionBounds(BlockPos cell,BlockState state){
        if(!supports(state))throw new IllegalArgumentException("unsupported local door");
        var boxes=state.getCollisionShape(EmptyBlockView.INSTANCE,BlockPos.ORIGIN).getBoundingBoxes();
        if(boxes.size()!=1)throw new IllegalStateException("wooden door must have one collision slab per half");
        var b=boxes.getFirst().offset(cell);return new BodyBounds(new Vec3(b.minX,b.minY,b.minZ),new Vec3(b.maxX,b.maxY,b.maxZ));
    }
    private static Optional<Pair> pair(Function<BlockPos,BlockState> cells,BlockPos clicked){
        var state=cells.apply(clicked);if(state==null||!supports(state))return Optional.empty();
        var lower=state.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.LOWER?clicked:clicked.down();var upper=lower.up();
        var a=cells.apply(lower);var b=cells.apply(upper);
        if(a==null||b==null||!supports(a)||a.getBlock()!=b.getBlock()
            ||a.get(Properties.DOUBLE_BLOCK_HALF)!=DoubleBlockHalf.LOWER||b.get(Properties.DOUBLE_BLOCK_HALF)!=DoubleBlockHalf.UPPER
            ||a.get(Properties.HORIZONTAL_FACING)!=b.get(Properties.HORIZONTAL_FACING)||a.get(Properties.DOOR_HINGE)!=b.get(Properties.DOOR_HINGE)
            ||a.get(Properties.OPEN)!=b.get(Properties.OPEN)||a.get(Properties.POWERED)!=b.get(Properties.POWERED))return Optional.empty();
        return Optional.of(new Pair(lower.toImmutable(),upper.toImmutable(),a,b));
    }
    public static Optional<Pair> pair(BlockBodyEntity body,BlockPos clicked){return pair(body::getLocalBlock,clicked);}
    /** Selecting only one world half must not delete its unselected neighbour on publication. */
    public static boolean completeSelection(Map<BlockPos,BlockState> cells){
        for(var e:cells.entrySet())if(supports(e.getValue())&&pair(cells::get,e.getKey()).isEmpty())return false;return true;
    }
    public static boolean canModify(ServerPlayerEntity player,BlockBodyEntity body,Collection<BlockPos> cells){
        for(var pos:cells){
            if(!BlockBodyEntity.validLocal(pos))return false;var point=body.localToWorld(Vec3d.ofCenter(pos));
            if(!player.getServerWorld().canPlayerModifyAt(player,BlockPos.ofFloored(point)))return false;
        }return true;
    }
    /** Actual local horizontal facing; world yaw cannot orient a door on a rolled ship. */
    public static BlockState placementState(BlockState state,BlockBodyEntity body,Vec3d worldLook){
        var local=body.rotation().inverse().rotate(new Vec3(worldLook.x,worldLook.y,worldLook.z));
        var facing=local.x()*local.x()+local.z()*local.z()>1e-8?Direction.getFacing(local.x(),0,local.z()):Direction.NORTH;
        return state.with(Properties.HORIZONTAL_FACING,facing).with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER);
    }
    public static boolean place(BlockBodyEntity body,BlockPos lower,BlockState state){
        if(!(body.getWorld() instanceof net.minecraft.server.world.ServerWorld world)||!world.getServer().isOnThread()||body.isRemoved())return false;
        if(!supports(state)||state.get(Properties.DOUBLE_BLOCK_HALF)!=DoubleBlockHalf.LOWER||!BlockBodyEntity.validLocal(lower)||!BlockBodyEntity.validLocal(lower.up())
            ||!body.getLocalBlock(lower).isAir()||!body.getLocalBlock(lower.up()).isAir())return false;
        var upper=state.with(Properties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER);var access=new BlockBodyWorldAccess(body);
        if(!access.canOccupyLocalBounds(collisionBounds(lower,state))||!access.canOccupyLocalBounds(collisionBounds(lower.up(),upper)))return false;
        return body.setLocalBlocks(Map.of(lower,state,lower.up(),upper));
    }
    public static boolean breakDoor(BlockBodyEntity body,BlockPos clicked){
        if(!(body.getWorld() instanceof net.minecraft.server.world.ServerWorld world)||!world.getServer().isOnThread()||body.isRemoved())return false;
        var pair=pair(body,clicked);
        return pair.isPresent()?body.setLocalBlocks(Map.of(pair.get().lower(),Blocks.AIR.getDefaultState(),pair.get().upper(),Blocks.AIR.getDefaultState()))
            :body.setLocalBlock(clicked,Blocks.AIR.getDefaultState()); // Torn/orphan material remains removable, never invented.
    }
    public static BlockState dropState(BlockBodyEntity body,BlockPos clicked){return pair(body,clicked).map(Pair::lowerState).orElseGet(()->body.getLocalBlock(clicked));}
    public static boolean use(ServerPlayerEntity player,BlockBodyEntity body,BlockPos clicked){
        if(!player.getServer().isOnThread()||player.getWorld()!=body.getWorld()||!player.isAlive()||player.isSpectator()||player.hasVehicle()||body.isRemoved())return false;
        var mode=player.interactionManager.getGameMode();if(mode!=GameMode.CREATIVE&&mode!=GameMode.SURVIVAL)return false;
        var door=pair(body,clicked);if(door.isEmpty())return false;var pair=door.get();
        var target=BodyBlockInteraction.target(player,player.getEyePos(),BodyBlockInteraction.viewDirection(player),Math.min(16,player.getBlockInteractionRange()));
        if(target.isEmpty()||target.get().body()!=body||!target.get().hit().localBlock().equals(clicked)||target.get().hit().revision()!=body.shapeRevision())return false;
        var hit=target.get().hit().worldPoint();
        if(!player.getServerWorld().canPlayerModifyAt(player,BlockPos.ofFloored(hit.x(),hit.y(),hit.z()))||!canModify(player,body,List.of(pair.lower(),pair.upper())))return false;
        var lower=pair.lowerState().cycle(Properties.OPEN);var upper=pair.upperState().cycle(Properties.OPEN);var access=new BlockBodyWorldAccess(body);
        if(!access.canOccupyLocalBounds(collisionBounds(pair.lower(),lower))||!access.canOccupyLocalBounds(collisionBounds(pair.upper(),upper)))return false;
        return body.setLocalBlocks(Map.of(pair.lower(),lower,pair.upper(),upper));
    }
}
