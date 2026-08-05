package com.terminaldetector.drmd.world.micro;

import com.terminaldetector.drmd.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/**
 * What a block that has been shot at is now: the block it used to be, and which of its sixty-four
 * cells survive.
 *
 * <p>The source state is kept rather than a material name, so a carved oak plank is still oak
 * planks — it drops the right thing, sounds right, and can be put back. The mask is one long.
 *
 * <p>Both are sent to clients, because the shape has to be the same on both sides: the server
 * decides what a player can fly through, and a client drawing something else is a wall you bounce
 * off that is not there.
 */
public class CarvedBlockEntity extends BlockEntity {
	private BlockState source = Blocks.STONE.getDefaultState();
	private long mask = MicroGrid.FULL;

	public CarvedBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CARVED, pos, state);
	}

	public BlockState source() {
		return source;
	}

	public long mask() {
		return mask;
	}

	public void init(BlockState source, long mask) {
		this.source = source;
		this.mask = mask;
		markDirty();
		sync();
	}

	public void setMask(long mask) {
		if (this.mask == mask) return;
		this.mask = mask;
		markDirty();
		sync();
	}

	private void sync() {
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.put("source", NbtHelper.fromBlockState(source));
		nbt.putLong("mask", mask);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		if (nbt.contains("source")) {
			source = NbtHelper.toBlockState(
					registries.getWrapperOrThrow(net.minecraft.registry.RegistryKeys.BLOCK),
					nbt.getCompound("source"));
		}
		if (nbt.contains("mask")) mask = nbt.getLong("mask");
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}
}
