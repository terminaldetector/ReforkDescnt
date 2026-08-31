package com.terminaldetector.drmd.world.portal.mirror;

import com.terminaldetector.drmd.entity.ModBlockEntities;
import com.terminaldetector.drmd.world.portal.PortalComplexity;
import com.terminaldetector.drmd.world.portal.PortalTravel;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
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
}
