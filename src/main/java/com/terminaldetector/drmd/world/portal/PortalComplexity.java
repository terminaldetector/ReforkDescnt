package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.weapon.items.ModItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Harder End / Nether portal activation — catalysts required.
 *
 * <p>Optional Immersive Portals is detected for HUD only; stack config is in
 * {@code docs/IMMPTL_STACK.md}. Digging the column remains the seamless Path B.
 */
public final class PortalComplexity {
	private static Boolean immptlPresent;

	private PortalComplexity() {}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient) return ActionResult.PASS;
			ItemStack stack = player.getStackInHand(hand);
			BlockPos pos = hit.getBlockPos();
			BlockState state = world.getBlockState(pos);
			BlockPos place = pos.offset(hit.getSide());

			if (isFireStarter(stack) && looksLikePortalFrame(world, place)) {
				if (!consumeCatalyst(player, ModItems.NETHER_GATE_CATALYST, "nether")) {
					return ActionResult.FAIL;
				}
				return ActionResult.PASS;
			}

			if (stack.isOf(Items.ENDER_EYE) && state.isOf(Blocks.END_PORTAL_FRAME)
					&& !state.get(EndPortalFrameBlock.EYE)) {
				if (!consumeCatalyst(player, ModItems.END_GATE_CATALYST, "end")) {
					return ActionResult.FAIL;
				}
				return ActionResult.PASS;
			}
			return ActionResult.PASS;
		});

		DescentMod.LOGGER.info("Portal complexity online — ImmPtl present={}", hasImmersivePortals());
	}

	public static boolean hasImmersivePortals() {
		if (immptlPresent == null) {
			immptlPresent = FabricLoader.getInstance().isModLoaded("immersive_portals")
					|| FabricLoader.getInstance().isModLoaded("imm_ptl_core")
					|| FabricLoader.getInstance().isModLoaded("immersive-portals");
		}
		return immptlPresent;
	}

	private static boolean isFireStarter(ItemStack stack) {
		return stack.isOf(Items.FLINT_AND_STEEL) || stack.isOf(Items.FIRE_CHARGE);
	}

	private static boolean looksLikePortalFrame(World world, BlockPos firePos) {
		for (BlockPos p : BlockPos.iterate(firePos.add(-2, -1, -2), firePos.add(2, 2, 2))) {
			if (world.getBlockState(p).isOf(Blocks.OBSIDIAN)) return true;
		}
		return false;
	}

	private static boolean consumeCatalyst(PlayerEntity player, Item catalyst, String kind) {
		if (player.getAbilities().creativeMode) return true;
		if (hasImmersivePortals()) {
			player.sendMessage(Text.literal("§8ImmPtl stack detected — catalyst still required."), true);
		}
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack s = player.getInventory().getStack(i);
			if (!s.isOf(catalyst)) continue;
			s.decrement(1);
			player.getWorld().playSound(null, player.getBlockPos(),
					SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.7f, 0.6f);
			player.sendMessage(Text.literal(kind.equals("end")
					? "§dEnd gate catalyst consumed §7— frame may accept the eye."
					: "§cNether gate catalyst consumed §7— frame may ignite."), true);
			return true;
		}
		player.sendMessage(Text.literal(kind.equals("end")
				? "§dEnd gate locked §7— craft an §fEnd Gate Catalyst§7."
				: "§cNether gate locked §7— craft a §fNether Gate Catalyst§7."), false);
		player.sendMessage(Text.literal(
				"§8Or dig the column: granite → mantle → Core. Sync keeps the surface path open."), true);
		return false;
	}
}
