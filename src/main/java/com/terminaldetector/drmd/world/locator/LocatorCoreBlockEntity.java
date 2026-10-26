package com.terminaldetector.drmd.world.locator;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.ModBlockEntities;
import com.terminaldetector.drmd.world.surface.MegacityRegions;
import com.terminaldetector.drmd.world.surface.ScorchedLandsRegions;
import com.terminaldetector.drmd.world.surface.TechnogenicSeaRegions;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Ticks locator ping: particles, brief glowing, action-bar tip for nearby 6DoF pilots.
 */
public class LocatorCoreBlockEntity extends BlockEntity {
	private int tick;

	public LocatorCoreBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LOCATOR_CORE, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, LocatorCoreBlockEntity be) {
		if (world.isClient || !(world instanceof ServerWorld sw)) return;
		be.tick++;
		if (be.tick % 80 != 0) return;

		sw.spawnParticles(ParticleTypes.END_ROD,
				pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
				14, 0.45, 0.6, 0.45, 0.02);
		sw.spawnParticles(ParticleTypes.GLOW,
				pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
				6, 0.3, 0.3, 0.3, 0.01);

		Box box = new Box(pos).expand(18);
		for (PlayerEntity player : sw.getEntitiesByClass(PlayerEntity.class, box, p -> true)) {
			DescentPlayerData data = DescentPlayerData.get(player);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, true, false, true));
			if (data.isEnabled()) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100, 0, true, false, true));
				player.sendMessage(Text.literal("§bLocator ping §8· §7" + be.describePing(player)), true);
			}
		}
	}

	public String describePing(PlayerEntity player) {
		int x = player.getBlockX();
		int z = player.getBlockZ();
		String city = MegacityRegions.isBound() ? MegacityRegions.describeNearest(x, z) : "megacity unbound";
		String sea = TechnogenicSeaRegions.isBound() ? TechnogenicSeaRegions.describeNearest(x, z) : "sea unbound";
		String scorched = ScorchedLandsRegions.isBound() ? ScorchedLandsRegions.describeNearest(x, z) : "scorched unbound";
		return city + " · " + sea + " · " + scorched;
	}
}
