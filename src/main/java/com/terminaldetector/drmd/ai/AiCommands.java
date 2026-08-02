package com.terminaldetector.drmd.ai;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.terminaldetector.drmd.entity.AirMineEntity;
import com.terminaldetector.drmd.entity.DroneEntity;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class AiCommands {
	private AiCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("6dof_spawn");
			for (AiRole role : AiRole.values()) {
				AiRole r = role;
				root.then(CommandManager.literal(r.id).executes(ctx -> {
					spawn(ctx.getSource().getPlayer(), r, 1);
					return 1;
				}));
			}
			dispatcher.register(root);

			dispatcher.register(CommandManager.literal("6dof_spawn_squad")
					.then(CommandManager.argument("n", IntegerArgumentType.integer(1, 16)).executes(ctx -> {
						int n = IntegerArgumentType.getInteger(ctx, "n");
						ServerPlayerEntity p = ctx.getSource().getPlayer();
						AiRole[] pool = {AiRole.ASSAULT, AiRole.INTERCEPTOR, AiRole.MG, AiRole.RPG, AiRole.LASER};
						for (int i = 0; i < n; i++) spawn(p, pool[i % pool.length], 1);
						return n;
					})));

			dispatcher.register(CommandManager.literal("6dof_spawn_mine").executes(ctx -> {
				ServerPlayerEntity p = ctx.getSource().getPlayer();
				AirMineEntity mine = ModEntities.AIR_MINE.create(p.getServerWorld());
				if (mine != null) {
					Vec3d pos = p.getPos().add(p.getRotationVec(1f).multiply(4));
					mine.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
					p.getServerWorld().spawnEntity(mine);
				}
				return 1;
			}));

			dispatcher.register(CommandManager.literal("d6_kill_npcs")
					.requires(s -> s.hasPermissionLevel(2))
					.executes(ctx -> {
						ServerPlayerEntity p = ctx.getSource().getPlayer();
						int n = 0;
						for (var e : p.getServerWorld().iterateEntities()) {
							if (e instanceof DroneEntity || e instanceof AirMineEntity) {
								e.discard();
								n++;
							}
						}
						int finalN = n;
						ctx.getSource().sendFeedback(() -> Text.literal("Removed " + finalN + " NPCs"), true);
						return n;
					}));
		});
	}

	private static void spawn(ServerPlayerEntity player, AiRole role, int count) {
		for (int i = 0; i < count; i++) {
			DroneEntity drone = ModEntities.DRONE.create(player.getServerWorld());
			if (drone == null) continue;
			Vec3d look = player.getRotationVec(1f);
			Vec3d pos = player.getPos().add(look.multiply(6 + i)).add(
					(player.getRandom().nextDouble() - 0.5) * 3,
					1 + player.getRandom().nextDouble() * 2,
					(player.getRandom().nextDouble() - 0.5) * 3);
			drone.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), 0);
			drone.applyRole(role);
			drone.setTarget(player);
			player.getServerWorld().spawnEntity(drone);
		}
	}
}
