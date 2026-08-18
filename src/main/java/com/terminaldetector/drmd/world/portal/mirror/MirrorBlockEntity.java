package com.terminaldetector.drmd.world.portal.mirror;

import com.terminaldetector.drmd.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Remembers which live ImmPtl {@link qouteall.imm_ptl.core.portal.Mirror} entity, if any, is
 * attached to this block — so it can be detached when the block breaks. One level simpler than
 * {@code GravityGeneratorBlockEntity}'s field bookkeeping: a spawned Mirror entity just sits and
 * saves with its own chunk, no periodic DRMD-side re-registration is needed to keep it alive.
 */
public class MirrorBlockEntity extends BlockEntity {
	private UUID mirrorEntityId;

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
