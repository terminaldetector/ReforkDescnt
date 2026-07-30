package com.terminaldetector.drmd.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergyPreset;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.weapon.registry.WeaponRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class DescentCommands {
	private DescentCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("6dof")
					.then(CommandManager.literal("toggle").executes(ctx -> {
						FlightSystem.toggle(ctx.getSource().getPlayer());
						return 1;
					}))
					.then(CommandManager.literal("dash").executes(ctx -> {
						FlightSystem.tryDash(ctx.getSource().getPlayer());
						return 1;
					}))
					.then(CommandManager.literal("alwaysrun").executes(ctx -> {
						ServerPlayerEntity p = ctx.getSource().getPlayer();
						DescentPlayerData d = DescentPlayerData.get(p);
						d.setAlwaysRun(!d.isAlwaysRun());
						ModNetworking.syncPlayer(p, d);
						return 1;
					}))
					.then(CommandManager.literal("flightassist").executes(ctx -> {
						ServerPlayerEntity p = ctx.getSource().getPlayer();
						DescentPlayerData d = DescentPlayerData.get(p);
						d.setFlightAssist(!d.isFlightAssist());
						ctx.getSource().sendFeedback(() -> Text.literal("Flight Assist: " + d.isFlightAssist()), false);
						return 1;
					}))
			);

			dispatcher.register(CommandManager.literal("d6")
					.then(CommandManager.literal("energy")
							.then(CommandManager.literal("preset")
									.then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										DescentPlayerData d = DescentPlayerData.get(p);
										EnergySystem.setPreset(d, EnergyPreset.fromId(StringArgumentType.getString(ctx, "name")));
										ModNetworking.syncPlayer(p, d);
										ctx.getSource().sendFeedback(() -> Text.literal("Preset: " + d.getPreset().id), false);
										return 1;
									})))
							.then(CommandManager.literal("set")
									.then(CommandManager.argument("w", FloatArgumentType.floatArg(0))
											.then(CommandManager.argument("s", FloatArgumentType.floatArg(0))
													.then(CommandManager.argument("e", FloatArgumentType.floatArg(0)).executes(ctx -> {
														ServerPlayerEntity p = ctx.getSource().getPlayer();
														DescentPlayerData d = DescentPlayerData.get(p);
														d.setAlloc(FloatArgumentType.getFloat(ctx, "w"),
																FloatArgumentType.getFloat(ctx, "s"),
																FloatArgumentType.getFloat(ctx, "e"));
														ModNetworking.syncPlayer(p, d);
														return 1;
													}))))))
					.then(CommandManager.literal("set")
							.requires(s -> s.hasPermissionLevel(2))
							.then(CommandManager.argument("key", StringArgumentType.word())
									.then(CommandManager.argument("value", FloatArgumentType.floatArg()).executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										DescentPlayerData d = DescentPlayerData.get(p);
										String key = StringArgumentType.getString(ctx, "key");
										float val = FloatArgumentType.getFloat(ctx, "value");
										switch (key.toLowerCase()) {
											case "gravity" -> d.setGravity(val);
											case "accel" -> d.setAccel(val);
											case "drag" -> d.setDrag(val);
											case "maxspeed" -> d.setMaxSpeed(val);
											default -> {
												ctx.getSource().sendError(Text.literal("Unknown key: " + key));
												return 0;
											}
										}
										ctx.getSource().sendFeedback(() -> Text.literal("Set " + key + " = " + val), true);
										return 1;
									}))))
					.then(CommandManager.literal("weapons")
							.then(CommandManager.literal("list").executes(ctx -> {
								WeaponRegistry.all().forEach(w ->
										ctx.getSource().sendFeedback(() -> Text.literal("- " + w.id + " (" + w.displayName + ")"), false));
								return 1;
							}))
							.then(CommandManager.literal("give_all")
									.requires(s -> s.hasPermissionLevel(2))
									.executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										p.giveItemStack(new ItemStack(ModItems.MG));
										p.giveItemStack(new ItemStack(ModItems.PLASMA));
										p.giveItemStack(new ItemStack(ModItems.HEAVY));
										p.giveItemStack(new ItemStack(ModItems.LASER));
										p.giveItemStack(new ItemStack(ModItems.ROCKETS));
										p.giveItemStack(new ItemStack(ModItems.GRAVY_RAILGUN));
										p.giveItemStack(new ItemStack(ModItems.VULCAN));
										p.giveItemStack(new ItemStack(ModItems.FLAK));
										p.giveItemStack(new ItemStack(ModItems.BFG));
										ctx.getSource().sendFeedback(() -> Text.literal("Gave core weapon set"), false);
										return 1;
									})))
					.then(CommandManager.literal("radar").executes(ctx -> {
						ServerPlayerEntity p = ctx.getSource().getPlayer();
						DescentPlayerData d = DescentPlayerData.get(p);
						d.setRadarEnabled(!d.isRadarEnabled());
						return 1;
					}))
					.then(CommandManager.literal("reset_roll").executes(ctx -> {
						ServerPlayerEntity p = ctx.getSource().getPlayer();
						DescentPlayerData d = DescentPlayerData.get(p);
						d.setRoll(0); d.setRollVel(0);
						return 1;
					}))
					.then(CommandManager.literal("orient")
							.then(CommandManager.literal("reset").executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								com.terminaldetector.drmd.world.LocalOrientation.clear(p.getUuid());
								ctx.getSource().sendFeedback(() -> Text.literal("Local UP reset to world +Y"), false);
								return 1;
							})))
					.then(CommandManager.literal("worldgen")
							.requires(s -> s.hasPermissionLevel(2))
							.then(CommandManager.literal("industrial")
									.then(CommandManager.argument("style", StringArgumentType.word()).executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										String styleName = StringArgumentType.getString(ctx, "style");
										com.terminaldetector.drmd.world.WorldRules.ComplexStyle style;
										try {
											style = com.terminaldetector.drmd.world.WorldRules.ComplexStyle.valueOf(styleName.toUpperCase());
										} catch (Exception e) {
											style = com.terminaldetector.drmd.world.WorldRules.ComplexStyle.ABANDONED_RESEARCH;
										}
										var pos = p.getBlockPos();
										com.terminaldetector.drmd.world.gen.ModWorldgen.forceGenerate(p.getServerWorld(), pos, style);
										com.terminaldetector.drmd.world.WorldRules.ComplexStyle finalStyle = style;
										ctx.getSource().sendFeedback(() -> Text.literal(
												"Generated Industrial Underground [" + finalStyle + "] at " + pos.toShortString()), true);
										return 1;
									}))
									.executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										com.terminaldetector.drmd.world.gen.ModWorldgen.forceGenerate(
												p.getServerWorld(), p.getBlockPos(),
												com.terminaldetector.drmd.world.WorldRules.ComplexStyle.ANCIENT_POWER);
										ctx.getSource().sendFeedback(() -> Text.literal("Generated Industrial Underground nearby"), true);
										return 1;
									})))
					.then(CommandManager.literal("kit")
							.requires(s -> s.hasPermissionLevel(2))
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.BUILD_TOOL));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.SIX_D_SOIL));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.MAGNETIC_ANOMALY));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.VOLUME_TURRET));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.LASER_BARRIER));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.HERMETIC_GATE));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.UNSTABLE_REACTOR));
								ctx.getSource().sendFeedback(() -> Text.literal("Gave 6DoF world-design kit"), false);
								return 1;
							}))
					.then(CommandManager.literal("start")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								com.terminaldetector.drmd.world.base.ReactorRoomStarter.activate(p);
								ctx.getSource().sendFeedback(() -> Text.literal("Descent Reactor Room activated"), true);
								return 1;
							}))
					.then(CommandManager.literal("ship")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								var ship = com.terminaldetector.drmd.entity.ModEntities.PYRO_SHIP.create(p.getServerWorld());
								if (ship != null) {
									var pos = p.getPos().add(p.getRotationVec(1f).multiply(3));
									ship.refreshPositionAndAngles(pos.x, pos.y, pos.z, p.getYaw(), 0);
									p.getServerWorld().spawnEntity(ship);
									ctx.getSource().sendFeedback(() -> Text.literal("Spawned Pyro transport — ПКМ to board"), false);
								}
								return 1;
							}))
			);
		});
	}
}
