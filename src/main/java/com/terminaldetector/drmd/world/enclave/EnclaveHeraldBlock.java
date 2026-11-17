package com.terminaldetector.drmd.world.enclave;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Interactable enclave contact — procedural dialogue + quest offer.
 * Sneak-use accepts an explicitly bound playable contract or extracts its completed reward.
 */
public class EnclaveHeraldBlock extends Block {
	public EnclaveHeraldBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) {
			return ActionResult.CONSUME;
		}

		ActionResult action = EnclaveMissionSystem.useHerald(sw, pos, sp, hit);
		try {
		EnclaveSite site = EnclaveSite.generate(sw.getSeed(), pos);
		FactionMemory mem = FactionMemory.of(sw);
		int rep = mem.getRep(sp.getUuid(), site.origin);

		player.sendMessage(Text.literal("§6" + site.origin.labelEn + "§7 — " + site.attitude.gloss
				+ " · tech " + site.techLevel + " · rep " + rep), false);
		player.sendMessage(Text.literal("§e« ").append(EnclaveDialogue.line(site, sw.getTime())).append(" §e»"), false);

		EnclaveQuest quest = EnclaveQuest.offer(site);
		// Retain authored/legacy offer titles even where no playable contract is bound yet.
		player.sendMessage(Text.literal("§7Offer: ").append(quest.title())
				.append(Text.literal(" §8(sneak-use to accept or extract a bound mission)")), false);
		if (player.isSneaking()) {
			if (site.origin == EnclaveOrigin.ENGINEERS && mem.engineersHelpSuit(sp)) {
				player.sendMessage(Text.translatable("dialogue.drmd.engineer.suit_help"), false);
			}
			if (site.origin == EnclaveOrigin.CULTISTS && mem.cultGrantsRelics(sp)) {
				player.sendMessage(Text.translatable("dialogue.drmd.cult.relics"), false);
			}
		}
		} catch (RuntimeException lostFeedback) {
			// State already committed/refused; legacy feedback neither retries nor suppresses it.
		}
		return action;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState replacement, boolean moved) {
		if (!state.isOf(replacement.getBlock()) && world instanceof ServerWorld serverWorld) {
			EnclaveMissionSystem.invalidateHerald(serverWorld, pos);
		}
		super.onStateReplaced(state, world, pos, replacement, moved);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(8) != 0) return;
		world.addParticle(ParticleTypes.SMOKE,
				pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
				pos.getY() + 1.05,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
				0, 0.01, 0);
	}
}
