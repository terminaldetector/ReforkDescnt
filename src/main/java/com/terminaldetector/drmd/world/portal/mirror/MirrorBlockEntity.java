package com.terminaldetector.drmd.world.portal.mirror;

import com.terminaldetector.drmd.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** Saved legacy attachment UUID and connection refresh for native mirrors. */
public class MirrorBlockEntity extends BlockEntity {
	private UUID mirrorEntityId;
    private int connectionAge;
    public void refreshConnections() {
        if(world!=null && connectionAge++%20==0)MirrorEdges.refresh(world,pos,getCachedState());
    }

	public MirrorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MIRROR, pos, state);
	}

	public UUID getMirrorEntityId() { return mirrorEntityId; }
	public void setMirrorEntityId(UUID id) { this.mirrorEntityId = id; markDirty(); }

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		if (mirrorEntityId != null) nbt.putUuid("mirror", mirrorEntityId);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		mirrorEntityId = nbt.containsUuid("mirror") ? nbt.getUuid("mirror") : null;
	}
}
