package com.terminaldetector.drmd.world.soil;

import com.terminaldetector.drmd.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class SixDSoilBlockEntity extends BlockEntity {
	private final Map<Direction, SixDSoilBlock.FaceCrop> crops = new EnumMap<>(Direction.class);

	public SixDSoilBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SIX_D_SOIL, pos, state);
		for (Direction d : Direction.values()) crops.put(d, SixDSoilBlock.FaceCrop.MOSS);
	}

	public void setCrop(Direction face, SixDSoilBlock.FaceCrop crop) {
		crops.put(face, crop);
		markDirty();
	}

	public SixDSoilBlock.FaceCrop getCrop(Direction face) {
		return crops.getOrDefault(face, SixDSoilBlock.FaceCrop.MOSS);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		NbtCompound c = new NbtCompound();
		for (var e : crops.entrySet()) {
			c.putString(e.getKey().asString(), e.getValue().name());
		}
		nbt.put("crops", c);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		if (!nbt.contains("crops")) return;
		NbtCompound c = nbt.getCompound("crops");
		for (Direction d : Direction.values()) {
			String key = d.asString();
			if (c.contains(key)) {
				try {
					crops.put(d, SixDSoilBlock.FaceCrop.valueOf(c.getString(key)));
				} catch (IllegalArgumentException ignored) {}
			}
		}
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createNbt(registryLookup);
	}
}
