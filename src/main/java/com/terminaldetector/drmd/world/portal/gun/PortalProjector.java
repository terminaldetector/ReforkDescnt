package com.terminaldetector.drmd.world.portal.gun;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.portal.mirror.*;
import net.minecraft.block.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import java.util.*;

/** Portable client of the existing native panel engine and atomic WorldAccess edits. */
public final class PortalProjector {
    public enum Result { PLACED,CLEARED,NO_SURFACE,INVALID_SURFACE,BLOCKED,UNAVAILABLE,DENIED,CONFLICT,FULL }
    public static final int RANGE=48,COOLDOWN=6;
    private PortalProjector() {}
    private record End(BlockPos pos,ServerBlockEdits.Cell cell) {}

    public static Result fire(ServerPlayerEntity player,int slot) {
        return fire(player,slot,com.terminaldetector.drmd.weapon.core.WeaponCore.aimDir(player));
    }
    public static Result fire(ServerPlayerEntity player,int slot,Vec3d direction) {
        if(slot<1 || slot>2)return Result.DENIED;
        Vec3d from=player.getEyePos();
        var ray=PortalGunRay.trace(player,from,direction);
        return ray.hit()!=null?place(player,slot,ray.hit()):ray.unavailable()?Result.UNAVAILABLE:Result.NO_SURFACE;
    }

    public static Result place(ServerPlayerEntity player,int slot,BlockHitResult hit) {
        var world=player.getServerWorld();
        if(slot<1 || slot>2 || !allowed(player,hit.getBlockPos()))return Result.DENIED;
        var state=PortalGunState.get(world);UUID owner=player.getUuid();
        if(!state.hasCapacity(owner))return Result.FULL;
        var pair=state.pair(owner);var access=WorldAccess.of(world);
        BlockPos target=hit.getBlockPos().offset(hit.getSide());
        // Validate loaded old endpoints before changing anything, including persistent pair state.
        for(BlockPos pos:new BlockPos[]{pair.blue(),pair.orange()})if(pos!=null) {
            if(access.snapshotIfLoaded(pos).isEmpty())return Result.UNAVAILABLE;
            if(!allowed(player,pos))return Result.DENIED;
        }
        End old=owned(world,pair.at(slot),owner,slot),other=owned(world,pair.at(3-slot),owner,3-slot);
        if(old!=null && target.equals(old.pos()) && old.cell().state().get(PortalPanelBlock.FACING)==hit.getSide())return Result.PLACED;
        if(other!=null && PortalPanelBlock.panelBox(target,hit.getSide()).intersects(PortalPanelBlock.panelBox(other.pos(),other.cell().state().get(PortalPanelBlock.FACING))))return Result.BLOCKED;
        // Large visible faces extend beyond the anchor cell: another owner's anchor can be
        // outside the clearance cells while its face still intersects this one.
        Box proposed=PortalPanelBlock.panelBox(target,hit.getSide());
        for(var indexed:state.pairs())for(BlockPos anchor:new BlockPos[]{indexed.blue(),indexed.orange()}) {
            if(anchor==null || old!=null && anchor.equals(old.pos()) || other!=null && anchor.equals(other.pos())
                || anchor.getSquaredDistance(target)>100)continue;
            var neighbour=access.readIfLoaded(anchor);
            if(neighbour.isEmpty())return Result.UNAVAILABLE;
            if(neighbour.get().isOf(ModWorldBlocks.PORTAL_PANEL)
                && proposed.intersects(PortalPanelBlock.panelBox(anchor,neighbour.get().get(PortalPanelBlock.FACING))))return Result.BLOCKED;
        }
        var validation=surface(player,target,hit.getSide(),old);
        if(validation!=Result.PLACED)return validation;
        var at=access.snapshotIfLoaded(target);if(at.isEmpty())return Result.UNAVAILABLE;
        if(!at.get().state().isAir())return Result.BLOCKED;
        var edits=new ArrayList<ServerBlockEdits.Edit>();
        edits.add(new ServerBlockEdits.Edit(target,at.get(),panel(world,target,hit.getSide(),owner,slot,other==null?null:other.pos())));
        if(old!=null)edits.add(new ServerBlockEdits.Edit(old.pos(),old.cell(),new ServerBlockEdits.Cell(Blocks.AIR.getDefaultState(),null)));
        if(other!=null)edits.add(new ServerBlockEdits.Edit(other.pos(),other.cell(),panel(world,other.pos(),other.cell().state().get(PortalPanelBlock.FACING),owner,3-slot,target)));
        var commit=ServerBlockEdits.commitProjectorPair(world,edits,owner);
        if(!commit.committed())return commit.status()==ServerBlockEdits.Status.UNAVAILABLE?Result.UNAVAILABLE:Result.CONFLICT;
        state.put(owner,slot==1?new PortalGunState.Pair(target,other==null?null:other.pos()):new PortalGunState.Pair(other==null?null:other.pos(),target));
        return Result.PLACED;
    }

    public static Result clear(ServerPlayerEntity player) {
        if(!allowed(player,player.getBlockPos()))return Result.DENIED;
        var world=player.getServerWorld();var state=PortalGunState.get(world);var pair=state.pair(player.getUuid());
        var edits=new ArrayList<ServerBlockEdits.Edit>();
        for(int slot=1;slot<=2;slot++) {
            var pos=pair.at(slot);if(pos==null)continue;
            if(WorldAccess.of(world).snapshotIfLoaded(pos).isEmpty())return Result.UNAVAILABLE;
            if(!allowed(player,pos))return Result.DENIED;
            End end=owned(world,pos,player.getUuid(),slot);
            if(end!=null)edits.add(new ServerBlockEdits.Edit(pos,end.cell(),new ServerBlockEdits.Cell(Blocks.AIR.getDefaultState(),null)));
        }
        if(!edits.isEmpty() && !ServerBlockEdits.commitProjectorPair(world,edits,player.getUuid()).committed())return Result.CONFLICT;
        state.put(player.getUuid(),new PortalGunState.Pair(null,null));return Result.CLEARED;
    }

    private static End owned(ServerWorld world,BlockPos pos,UUID owner,int slot) {
        if(pos==null)return null;
        var cell=WorldAccess.of(world).snapshotIfLoaded(pos);
        if(cell.isEmpty() || !cell.get().state().isOf(ModWorldBlocks.PORTAL_PANEL))return null;
        var data=cell.get().entityData();
        return data!=null && data.containsUuid("projectorOwner") && owner.equals(data.getUuid("projectorOwner"))
            && data.getInt("projectorSlot")==slot?new End(pos,cell.get()):null;
    }

    private static Result surface(ServerPlayerEntity player,BlockPos target,Direction normal,End old) {
        var world=player.getServerWorld();var access=WorldAccess.of(world);
        Direction[] axes=Arrays.stream(Direction.values()).filter(d->d.getAxis()!=normal.getAxis() && d.getDirection()==Direction.AxisDirection.POSITIVE).toArray(Direction[]::new);
        // Existing native panels are four blocks across, centred on an anchor block: the two
        // half-width edge cells must also be clear and supported. No wall is carved or replaced.
        for(int u=-2;u<=2;u++)for(int v=-2;v<=2;v++) {
            BlockPos front=target.offset(axes[0],u).offset(axes[1],v),back=front.offset(normal.getOpposite());
            if(!allowed(player,front) || !allowed(player,back))return Result.DENIED;
            if(!WorldEditSafety.neighbourhoodLoaded(world,front) || !WorldEditSafety.neighbourhoodLoaded(world,back))return Result.UNAVAILABLE;
            var a=access.readIfLoaded(front);var b=access.readIfLoaded(back);
            if(a.isEmpty() || b.isEmpty())return Result.UNAVAILABLE;
            if(!a.get().isAir() && (old==null || !front.equals(old.pos())))return Result.BLOCKED;
            if(b.get().hasBlockEntity() || !b.get().isSideSolidFullSquare(world,back,normal)
                || !b.get().getFluidState().isEmpty() || b.get().getHardness(world,back)<0)return Result.INVALID_SURFACE;
        }
        return Result.PLACED;
    }
    private static boolean allowed(ServerPlayerEntity player,BlockPos pos) {
        var world=player.getServerWorld();
        return world.getServer().isOnThread() && player.isAlive() && !player.isSpectator()
            && player.getAbilities().allowModifyWorld && !world.isOutOfHeightLimit(pos)
            && world.getWorldBorder().contains(pos) && world.canPlayerModifyAt(player,pos);
    }
    private static ServerBlockEdits.Cell panel(ServerWorld world,BlockPos pos,Direction facing,UUID owner,int slot,BlockPos partner) {
        var block=ModWorldBlocks.PORTAL_PANEL.getDefaultState().with(PortalPanelBlock.FACING,facing)
            .with(PortalPanelBlock.CHANNEL,slot).with(PortalPanelBlock.LINKED,partner!=null);
        var entity=new PortalPanelBlockEntity(pos,block);entity.claimProjector(owner,slot);
        if(partner!=null)entity.setLink(null,partner,world.getRegistryKey());
        return new ServerBlockEdits.Cell(block,entity.createNbtWithIdentifyingData(world.getRegistryManager()));
    }
}
