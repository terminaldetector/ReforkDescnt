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
 * Server-side link bookkeeping for {@link ChargedMirrorBlock}: which live ImmPtl entity is currently
 * attached (a plain {@code Mirror} before linking, a linked {@code Portal} after), and — once
 * linked — where the other end of the link is. Whether this block is linked at all stays a
 * {@code LINKED} blockstate property, because ordinary blockstate updates sync that bit for free; the
 * link itself is synced as block entity data ({@code toInitialChunkDataNbt}/{@code toUpdatePacket},
 * {@code CarvedBlockEntity}'s idiom), because a client drawing <em>through</em> a portal needs to know
 * where it goes, and a bit cannot say that.
 */
public class ChargedMirrorBlockEntity extends BlockEntity {
	/**
	 * How far from the face centre a crossing may land and still count. Half a block would be the
	 * geometric face; a little over that is forgiving at the corners without reaching the next block
	 * along, and stops someone walking through the wall beside the mirror being carried with it.
	 */
	private static final double FACE_HALF_SPAN = 0.75;

	private UUID attachedEntityId;
	private boolean linked;
	private BlockPos linkPartnerPos;
	private RegistryKey<World> linkPartnerDim;

	public ChargedMirrorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CHARGED_MIRROR, pos, state);
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
	 * Native travel through a linked pair — no Immersive Portals needed.
	 *
	 * <p>Until this existed a linked charged mirror stored its partner and did nothing with it: the
	 * travel was performed by an ImmPtl {@code Portal} entity spawned in
	 * {@code ImmPtlMirrorBridge.linkPortals}, so without that mod the block was decorative and said so.
	 *
	 * <p>Everything but the guards lives in {@link PortalTravel}, shared with {@code PortalPanelBlock}:
	 * a mirror is a one-block face and a panel is several across, but nothing else about travelling
	 * through them differs, so only the span is passed in.
	 *
	 * <p>Stands aside for a real ImmPtl portal rather than testing whether the mod is installed. The
	 * attached-entity id is the honest question — it is set only when a live {@code Portal} was spawned
	 * here, and that portal does its own carrying, so both doing it would fight over the same traveller.
	 * Asking it this way also gets both migrations right on its own: a world linked with ImmPtl and
	 * opened without it has no portal to lose, and one linked natively keeps working if ImmPtl is
	 * installed later.
	 */
	public static void tick(World world, BlockPos pos, BlockState state, ChargedMirrorBlockEntity be) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		if (!be.linked || be.linkPartnerPos == null) return;
		if (be.attachedEntityId != null && PortalComplexity.hasImmersivePortals()) return;
		// Same dimension only — see PortalTravel for why a cross-dimension hop is not this call.
		if (be.linkPartnerDim != null && !be.linkPartnerDim.equals(world.getRegistryKey())) return;
		if (!(state.getBlock() instanceof ChargedMirrorBlock)) return;

		BlockState partnerState = world.getBlockState(be.linkPartnerPos);
		if (!(partnerState.getBlock() instanceof ChargedMirrorBlock)) return;

		PortalTravel.carry(serverWorld, pos, state.get(ChargedMirrorBlock.FACING),
				be.linkPartnerPos, partnerState.get(ChargedMirrorBlock.FACING), FACE_HALF_SPAN);
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
