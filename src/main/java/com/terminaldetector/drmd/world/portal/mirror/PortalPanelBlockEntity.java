package com.terminaldetector.drmd.world.portal.mirror;

import com.terminaldetector.drmd.entity.ModBlockEntities;
import com.terminaldetector.drmd.world.portal.PortalComplexity;
import com.terminaldetector.drmd.world.portal.PortalTravel;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Link bookkeeping for {@link PortalPanelBlock} — an exact structural copy of
 * {@link ChargedMirrorBlockEntity}, kept as its own class rather than reused directly because
 * {@link net.minecraft.block.entity.BlockEntity}'s constructor is tied to one fixed
 * {@link net.minecraft.block.entity.BlockEntityType}, the same reason {@code ChargedMirrorBlockEntity}
 * itself isn't just {@code MirrorBlockEntity} with extra fields.
 */
public class PortalPanelBlockEntity extends BlockEntity {
	private UUID attachedEntityId;
	private boolean linked;
	private BlockPos linkPartnerPos;
	private RegistryKey<World> linkPartnerDim;

	public PortalPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PORTAL_PANEL, pos, state);
	}

	public UUID getAttachedEntityId() { return attachedEntityId; }
	public void setAttachedEntityId(UUID id) { this.attachedEntityId = id; markDirty(); }

	public boolean isLinked() { return linked; }
	@Nullable public BlockPos getLinkPartnerPos() { return linkPartnerPos; }
	@Nullable public RegistryKey<World> getLinkPartnerDim() { return linkPartnerDim; }

	public void setLink(UUID attachedEntityId, BlockPos partnerPos, RegistryKey<World> partnerDim) {
		this.attachedEntityId = attachedEntityId;
		this.linked = true;
		this.linkPartnerPos = partnerPos;
		this.linkPartnerDim = partnerDim;
		markDirty();
	}

	/**
	 * Forget the link: the far end is gone, so there is nothing left to travel to.
	 *
	 * <p>Without this a survivor keeps {@code LINKED} and a partner position pointing at nothing. The
	 * native tick reads that and does nothing, which is safe but leaves a block that says it is linked
	 * and is not. With ImmPtl it is worse than untidy — the far portal entity outlives its own block.
	 */
	public void clearLink() {
		this.attachedEntityId = null;
		this.linked = false;
		this.linkPartnerPos = null;
		this.linkPartnerDim = null;
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		if (attachedEntityId != null) nbt.putUuid("attached", attachedEntityId);
		nbt.putBoolean("linked", linked);
		if (linkPartnerPos != null) {
			nbt.putInt("partnerX", linkPartnerPos.getX());
			nbt.putInt("partnerY", linkPartnerPos.getY());
			nbt.putInt("partnerZ", linkPartnerPos.getZ());
		}
		if (linkPartnerDim != null) nbt.putString("partnerDim", linkPartnerDim.getValue().toString());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		attachedEntityId = nbt.containsUuid("attached") ? nbt.getUuid("attached") : null;
		linked = nbt.getBoolean("linked");
		linkPartnerPos = nbt.contains("partnerX")
				? new BlockPos(nbt.getInt("partnerX"), nbt.getInt("partnerY"), nbt.getInt("partnerZ"))
				: null;
		linkPartnerDim = nbt.contains("partnerDim")
				? RegistryKey.of(RegistryKeys.WORLD, Identifier.of(nbt.getString("partnerDim")))
				: null;
	}

	/**
	 * Native travel through a linked pair — the same call {@code ChargedMirrorBlockEntity.tick} makes,
	 * with the panel's own span. Everything but these guards lives in {@link PortalTravel}: a mirror is
	 * a one-block face and a panel is {@link PortalPanelBlock#HALF_SPAN} either side of its anchor, and
	 * that is the whole difference between them.
	 *
	 * <p>Stands aside for a real ImmPtl portal by asking whether one is attached, not whether the mod is
	 * installed — see the mirror's own tick for why that is the honest question.
	 */
	public static void tick(World world, BlockPos pos, BlockState state, PortalPanelBlockEntity be) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		if (!be.linked || be.linkPartnerPos == null) return;
		if (be.attachedEntityId != null && PortalComplexity.hasImmersivePortals()) return;
		// Same dimension only — see PortalTravel for why a cross-dimension hop is not this call.
		if (be.linkPartnerDim != null && !be.linkPartnerDim.equals(world.getRegistryKey())) return;
		if (!(state.getBlock() instanceof PortalPanelBlock)) return;

		BlockState partnerState = world.getBlockState(be.linkPartnerPos);
		if (!(partnerState.getBlock() instanceof PortalPanelBlock)) return;

		PortalTravel.carry(serverWorld, pos, state.get(PortalPanelBlock.FACING),
				be.linkPartnerPos, partnerState.get(PortalPanelBlock.FACING), PortalPanelBlock.HALF_SPAN);
	}

	/**
	 * Send the link to the client, not just the {@code LINKED} bit the blockstate already carries.
	 *
	 * <p>That bit was enough while the client only had to draw a linked block differently. Drawing
	 * <em>through</em> one needs to know where it goes — the partner's position, and whether a live
	 * ImmPtl portal is already doing the job — and none of that is in a blockstate. This is
	 * {@code CarvedBlockEntity}'s existing sync idiom, unchanged, on the data this block already writes.
	 */
	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}
}
