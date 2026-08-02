package com.terminaldetector.drmd.world.base;

import com.terminaldetector.drmd.ai.AiRole;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.entity.ReactorDisplayEntity;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.WorldRules;
import com.terminaldetector.drmd.world.gen.IndustrialComplexGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Starter Descent experience: carve a reactor room, place core + docks + ship + drones,
 * give the player a starter kit. One command → playable Descent-like session.
 */
public final class ReactorRoomStarter {
	private ReactorRoomStarter() {}

	public static void activate(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		BlockPos center = player.getBlockPos().up(8);

		// 1) Reactor chamber (volume-first)
		IndustrialComplexGenerator.generateAt(world, center, WorldRules.ComplexStyle.ANCIENT_POWER, world.getRandom());

		// 2) Clear spawn pocket + landing pad
		clearSphere(world, center.add(0, -6, 0), 5);
		BlockPos pad = center.add(0, -8, 0);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				world.setBlockState(pad.add(x, 0, z), Blocks.IRON_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
				if (Math.abs(x) == 3 || Math.abs(z) == 3) {
					world.setBlockState(pad.add(x, 1, z), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		world.setBlockState(pad.up(), Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);

		// 3) Reactor display entity at core
		ReactorDisplayEntity reactor = ModEntities.REACTOR_DISPLAY.create(world);
		if (reactor != null) {
			reactor.refreshPositionAndAngles(center.getX() + 0.5, center.getY() - 1, center.getZ() + 0.5, 0, 0);
			world.spawnEntity(reactor);
		}

		// 4) Magnetic anomaly + unstable accents
		world.setBlockState(center.add(10, 0, 0), ModWorldBlocks.MAGNETIC_ANOMALY.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(center.add(-10, 2, 4), ModWorldBlocks.VOLUME_TURRET.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(center.add(0, 8, 0), ModWorldBlocks.UNSTABLE_REACTOR.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(pad.add(2, 1, 0), ModWorldBlocks.SIX_D_SOIL.getDefaultState(), Block.NOTIFY_ALL);

		// 5) Dock marker
		world.setBlockState(pad.add(0, 1, 3), com.terminaldetector.drmd.entity.ModBlocks.DOCK.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(pad.add(0, 1, -3), com.terminaldetector.drmd.entity.ModBlocks.CHECKPOINT.getDefaultState(), Block.NOTIFY_ALL);

		// 6) Spawn Pyro transport
		PyroShipEntity ship = ModEntities.PYRO_SHIP.create(world);
		if (ship != null) {
			BlockPos shipPos = pad.up(2);
			ship.refreshPositionAndAngles(shipPos.getX() + 0.5, shipPos.getY(), shipPos.getZ() + 0.5, player.getYaw(), 0);
			world.spawnEntity(ship);
		}

		// 7) Enemy drones around the chamber
		AiRole[] roles = {AiRole.ASSAULT, AiRole.INTERCEPTOR, AiRole.MG, AiRole.RPG, AiRole.LASER};
		for (int i = 0; i < roles.length; i++) {
			Direction dir = Direction.fromHorizontal(i);
			BlockPos dp = center.offset(dir, 14).up((i % 3) - 1);
			DroneEntity drone = ModEntities.DRONE.create(world);
			if (drone == null) continue;
			drone.refreshPositionAndAngles(dp.getX() + 0.5, dp.getY(), dp.getZ() + 0.5, 0, 0);
			drone.applyRole(roles[i]);
			drone.setTarget(player);
			world.spawnEntity(drone);
		}

		// 8) Teleport player to pad + enable 6DoF + kit
		player.requestTeleport(pad.getX() + 0.5, pad.getY() + 2, pad.getZ() + 0.5);
		var data = com.terminaldetector.drmd.DescentPlayerData.get(player);
		data.setEnabled(true);
		data.ensureInit();
		data.setEnergy(100);
		data.setShield(100);

		giveKit(player);
		player.sendMessage(Text.literal("§bDRMD §fReactor Room online. §7Board the Pyro ship (ПКМ) or fly free (H)."), false);
		player.sendMessage(Text.literal("§7Weapons in inventory · M = Workshop · /6dof_spawn assault for more drones"), false);
	}

	private static void giveKit(ServerPlayerEntity player) {
		player.giveItemStack(new ItemStack(ModItems.PYRO_GX));
		player.giveItemStack(new ItemStack(ModItems.LASER));
		player.giveItemStack(new ItemStack(ModItems.VULCAN));
		player.giveItemStack(new ItemStack(ModItems.SPREAD));
		player.giveItemStack(new ItemStack(ModItems.ROCKET_DUAL));
		player.giveItemStack(new ItemStack(ModItems.MINE_PROX));
		player.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.CONSTRUCTION_LASER));
		player.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.MINING_LASER));
		player.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.SIX_D_SOIL, 16));
		player.giveItemStack(new ItemStack(Blocks.IRON_BLOCK, 64));
	}

	private static void clearSphere(ServerWorld world, BlockPos c, int r) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		int r2 = r * r;
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z > r2) continue;
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}
}
