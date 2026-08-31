package com.terminaldetector.drmd.world.portal.mirror;

import com.terminaldetector.drmd.world.portal.PortalComplexity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Right-click a {@link ChargedMirrorBlock} to anchor, right-click a second one to link them into a
 * real two-way portal at this item's {@link MirrorLinkerTier} — the pending anchor lives in the
 * stack's own NBT (matching {@code ConstructLaserItem}'s {@code DataComponentTypes.CUSTOM_DATA}
 * idiom) rather than new per-player session state, so it is visible to the player in their hotbar
 * and survives a relog with no extra bookkeeping.
 */
public class MirrorLinkerItem extends Item {
	private static final String ANCHOR_KEY = "drmd_mirror_anchor";
	private final MirrorLinkerTier tier;

	public MirrorLinkerItem(Settings settings, MirrorLinkerTier tier) {
		super(settings.maxCount(1));
		this.tier = tier;
	}

	public MirrorLinkerTier tier() { return tier; }

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient) return ActionResult.SUCCESS;
		BlockPos pos = context.getBlockPos();
		if (!(world.getBlockState(pos).getBlock() instanceof ChargedMirrorBlock)) return ActionResult.PASS;
		PlayerEntity user = context.getPlayer();
		if (user == null || !(world instanceof ServerWorld sw)) return ActionResult.FAIL;

		ItemStack stack = context.getStack();
		Anchor anchor = readAnchor(stack);

		if (anchor == null) {
			writeAnchor(stack, pos, sw.getRegistryKey());
			user.sendMessage(Text.literal(tier.colorCode + tier.label + " §7anchor set §8— right-click the far mirror to link."), true);
			return ActionResult.SUCCESS;
		}

		ServerWorld anchorWorld = sw.getServer().getWorld(anchor.dim);
		if (anchorWorld == null || (anchorWorld == sw && anchor.pos.equals(pos))) {
			clearAnchor(stack);
			user.sendMessage(Text.literal("§7Anchor cleared §8— that mirror is gone or it's the one you just clicked."), true);
			return ActionResult.FAIL;
		}
		if (!(anchorWorld.getBlockState(anchor.pos).getBlock() instanceof ChargedMirrorBlock)) {
			clearAnchor(stack);
			user.sendMessage(Text.literal("§7Anchor cleared §8— the anchored mirror was replaced."), true);
			return ActionResult.FAIL;
		}
		if (!tier.allowsCrossDimension && anchorWorld != sw) {
			user.sendMessage(Text.literal(tier.colorCode + tier.label + " §7can't cross dimensions §8— need a Transdimensional Key."), false);
			return ActionResult.FAIL;
		}

		boolean immPtl = PortalComplexity.hasImmersivePortals();
		if (!immPtl && anchorWorld != sw) {
			// The native path is same-dimension only, and deliberately: the far end's chunks may not be
			// loaded and a player's own dimension change is a different call with its own failure modes.
			user.sendMessage(Text.literal("§6Cross-dimension links need the Immersive Portals stack §7— see docs/IMMPTL_STACK.md."), false);
			return ActionResult.FAIL;
		}
		if (!immPtl && tier.scale != 1.0) {
			// Refused rather than silently downgraded: this key is spent by the link, and getting an
			// ordinary one back for a rare item is a worse answer than being told why not.
			user.sendMessage(Text.literal(tier.colorCode + tier.label + " §7rescales space §8— that needs the "
					+ "Immersive Portals stack. A Resonance Key links natively."), false);
			return ActionResult.FAIL;
		}

		Direction facingHere = world.getBlockState(pos).get(ChargedMirrorBlock.FACING);
		Direction facingAnchor = anchorWorld.getBlockState(anchor.pos).get(ChargedMirrorBlock.FACING);
		if (immPtl) {
			ImmPtlMirrorBridge.linkPortals(
					anchorWorld, anchor.pos, facingAnchor, entityIdAt(anchorWorld, anchor.pos),
					sw, pos, facingHere, entityIdAt(sw, pos),
					tier);
		} else {
			// Without ImmPtl there is no portal entity to spawn, only the link to record — the pair is
			// then carried by ChargedMirrorBlockEntity.tick. Rotation needs no permission here: the
			// native transform always turns a traveller from one face onto the other, which is what
			// walking through a portal means, so no tier gates it.
			ChargedMirrorBlock.linkNatively(sw, anchor.pos, pos);
			user.sendMessage(Text.literal("§8No Immersive Portals §7— linked, walkable, but not see-through."), false);
		}

		sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				30, 0.4, 0.4, 0.4, 0.02);
		sw.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.8f, 1.1f);
		user.sendMessage(Text.literal(tier.colorCode + tier.label + " §7spent §8— link established."), true);
		if (!user.getAbilities().creativeMode) stack.decrement(1);
		return ActionResult.SUCCESS;
	}

	private static java.util.UUID entityIdAt(ServerWorld world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof ChargedMirrorBlockEntity be ? be.getAttachedEntityId() : null;
	}

	private record Anchor(BlockPos pos, RegistryKey<World> dim) {}

	private static Anchor readAnchor(ItemStack stack) {
		NbtComponent custom = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		NbtCompound root = custom.copyNbt();
		if (!root.contains(ANCHOR_KEY)) return null;
		NbtCompound a = root.getCompound(ANCHOR_KEY);
		BlockPos pos = new BlockPos(a.getInt("x"), a.getInt("y"), a.getInt("z"));
		RegistryKey<World> dim = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(a.getString("dim")));
		return new Anchor(pos, dim);
	}

	private static void writeAnchor(ItemStack stack, BlockPos pos, RegistryKey<World> dim) {
		NbtComponent existing = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		NbtCompound nbt = existing.copyNbt();
		NbtCompound a = new NbtCompound();
		a.putInt("x", pos.getX());
		a.putInt("y", pos.getY());
		a.putInt("z", pos.getZ());
		a.putString("dim", dim.getValue().toString());
		nbt.put(ANCHOR_KEY, a);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	private static void clearAnchor(ItemStack stack) {
		NbtComponent existing = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		NbtCompound nbt = existing.copyNbt();
		nbt.remove(ANCHOR_KEY);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}
}
