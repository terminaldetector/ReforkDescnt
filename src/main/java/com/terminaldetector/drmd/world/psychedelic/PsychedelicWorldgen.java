package com.terminaldetector.drmd.world.psychedelic;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.DescentWorldState;
import com.terminaldetector.drmd.world.base.DescentSession;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Stock psychedelic campaign: weightless void dock + fractal field (18 kinds).
 */
public final class PsychedelicWorldgen {
	/** Orbital void start — weightless thruster flight (ORBITAL band). */
	public static final int SPAWN_Y = WorldLevels.SKY_TOP + 48; // 688

	private PsychedelicWorldgen() {}

	/**
	 * Seed psychedelic stock instead of lunar hub / surface districts.
	 * Call once from {@link DescentSession#seedWorld}.
	 */
	public static void seed(MinecraftServer server, DescentWorldState state) {
		ServerWorld world = server.getOverworld();
		if (world == null) return;

		long seed = world.getSeed();
		int variant = state.getPsychedelicVariant();
		if (variant < 0) {
			variant = (int) Math.floorMod(seed ^ 0x95C4E11CL, PsychedelicFractal.count());
			state.setPsychedelicVariant(variant);
		}
		state.setPsychedelic(true);

		PsychedelicFractal primary = PsychedelicFractal.fromIndex(variant);
		BlockPos dock = new BlockPos(0, SPAWN_Y, 0);
		world.setSpawnPos(dock, 0f);

		DescentSession.enqueueLandmark(dock, () -> buildVoidDock(world, dock));

		// Primary fractal near dock + satellite fractals of other kinds.
		Random random = Random.create(seed ^ 0xF4AC7A1L);
		DescentSession.enqueueLandmark(dock.add(40, 0, 0), () ->
				PsychedelicFractalGenerator.generate(world, dock.add(48, 8, 12), primary, random));

		for (int i = 1; i <= 5; i++) {
			PsychedelicFractal kind = PsychedelicFractal.fromIndex(variant + i * 3);
			double ang = i * (Math.PI * 2 / 5);
			int dist = 80 + i * 28;
			BlockPos at = dock.add(
					(int) (Math.cos(ang) * dist),
					(i % 3 - 1) * 24,
					(int) (Math.sin(ang) * dist));
			long salt = seed ^ (i * 0x9E3779B97F4A7C15L);
			DescentSession.enqueueLandmark(at, () ->
					PsychedelicFractalGenerator.generate(world, at, kind, Random.create(salt)));
		}

		DescentMod.LOGGER.info("Psychedelic world seeded — primary={} (#{}) dock={}",
				primary.label, variant, dock.toShortString());
	}

	/** Glass / end-rod void dock — no gravity pads (start is weightless). */
	public static void buildVoidDock(ServerWorld world, BlockPos center) {
		int r = 8;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > r * r) continue;
				BlockPos p = center.add(x, 0, z);
				world.setBlockState(p, Blocks.PURPLE_STAINED_GLASS.getDefaultState(), Block.NOTIFY_LISTENERS);
				if ((x + z) % 4 == 0) {
					world.setBlockState(p.up(), Blocks.END_ROD.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
				// Clear flight volume
				for (int y = 1; y <= 12; y++) {
					world.setBlockState(p.up(y), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
				for (int y = 1; y <= 6; y++) {
					world.setBlockState(p.down(y), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		world.setBlockState(center, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(center.up(), Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(center.up(2), Blocks.BEACON.getDefaultState(), Block.NOTIFY_ALL);

		// Ring of tinted glass markers
		for (int a = 0; a < 8; a++) {
			double ang = a * (Math.PI / 4);
			BlockPos m = center.add((int) (Math.cos(ang) * 14), 2, (int) (Math.sin(ang) * 14));
			world.setBlockState(m, Blocks.MAGENTA_STAINED_GLASS.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(m.up(), Blocks.SHROOMLIGHT.getDefaultState(), Block.NOTIFY_LISTENERS);
		}

		PyroShipEntity ship = ModEntities.PYRO_SHIP.create(world);
		if (ship != null) {
			ship.refreshPositionAndAngles(center.getX() + 0.5, center.getY() + 6, center.getZ() + 10.5, 0, 0);
			world.spawnEntity(ship);
		}
	}

	/** First-join teleport into the weightless dock + tips. */
	public static void onPlayerJoin(ServerPlayerEntity player, DescentWorldState state) {
		DescentPlayerData data = DescentPlayerData.get(player);
		data.ensureInit();
		data.setEnabled(true);
		data.setGravityFactor(0f);
		data.setSessionWelcomed(true);

		BlockPos dock = new BlockPos(0, SPAWN_Y, 0);
		if (player.getWorld().getRegistryKey() == World.OVERWORLD) {
			player.requestTeleport(dock.getX() + 0.5, dock.getY() + 2.0, dock.getZ() + 0.5);
		}

		PsychedelicFractal primary = PsychedelicFractal.fromIndex(Math.max(0, state.getPsychedelicVariant()));
		player.sendMessage(Text.literal(
				"§dPSYCHEDELIC VOID §f— weightless start · fractal field online."), false);
		player.sendMessage(Text.literal(
				"§7Primary fractal: §f" + primary.label + " §8(#" + state.getPsychedelicVariant() + ")"), false);
		player.sendMessage(Text.literal(
				"§7No ground gravity here — thrusters only · §fH§7 6DoF · hold §fR§7 afterburner."), false);

		if (player.isCreative()) {
			player.giveItemStack(new ItemStack(ModItems.PYRO_GX));
		}
	}
}
