package com.terminaldetector.drmd.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergyPreset;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.weapon.core.DescentLaserFire;
import com.terminaldetector.drmd.weapon.items.DescentWeaponItem;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.weapon.registry.WeaponRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.Item;
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
										for (Item w : com.terminaldetector.drmd.weapon.registry.ArsenalCatalog.creativeWeapons()) {
											p.giveItemStack(new ItemStack(w));
										}
										p.giveItemStack(new ItemStack(ModItems.SHIELD_ORB, 8));
										p.giveItemStack(new ItemStack(ModItems.ENERGY_ORB_PICKUP, 8));
										ctx.getSource().sendFeedback(() -> Text.literal("Gave closed Descent arsenal"), false);
										return 1;
									})))
					.then(CommandManager.literal("laserlevel")
							.then(CommandManager.argument("level", IntegerArgumentType.integer(1, 4))
									.executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										ItemStack stack = p.getMainHandStack();
										if (!(stack.getItem() instanceof DescentWeaponItem dwi)
												|| !"laser".equals(dwi.getDef().behavior)) {
											ctx.getSource().sendError(Text.literal("Hold weapon_d6_laser in main hand"));
											return 0;
										}
										int lvl = IntegerArgumentType.getInteger(ctx, "level");
										DescentLaserFire.setLevel(stack, lvl);
										ctx.getSource().sendFeedback(
												() -> Text.literal("LASER LVL: " + DescentLaserFire.levelOf(stack)), false);
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
					.then(CommandManager.literal("construct")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								com.terminaldetector.drmd.world.build.ConstructionMode.toggle(p);
								return 1;
							}))
					// The Nether and the End are bands of this world, so travel is a lift, not a portal.
					.then(CommandManager.literal("level")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								var level = com.terminaldetector.drmd.world.level.WorldLevels.at(p.getY());
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Level: " + level.label + " (" + level.yMin + " … " + level.yMax + ")"
												+ " | column " + p.getWorld().getBottomY()
												+ " … " + (p.getWorld().getBottomY() + p.getWorld().getHeight())), false);
								return 1;
							})
							.then(CommandManager.argument("name", StringArgumentType.word())
									.executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										var level = com.terminaldetector.drmd.world.level.WorldLevels
												.byName(StringArgumentType.getString(ctx, "name"));
										p.requestTeleport(p.getX(), level.travelY(), p.getZ());
										ctx.getSource().sendFeedback(() -> Text.literal(
												"Ascending to " + level.label + " @ y=" + level.travelY()), false);
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
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.CONSTRUCTION_LASER));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.REPAIR_LASER));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.MINING_LASER));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.TUNNEL_LASER));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.DRILL_RIG));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.TUNNEL_DRILL_RIG));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_SCANNER));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.SIX_D_SOIL));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_GENERATOR));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH, 16));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.MAGNETIC_ANOMALY));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.VOLUME_TURRET));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.LASER_TURRET));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.PLASMA_TURRET));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.POINT_DEFENSE_TURRET));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.LASER_BARRIER));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.CYCLIC_LASER_KIT));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.HERMETIC_GATE));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.UNSTABLE_REACTOR));
								ctx.getSource().sendFeedback(() -> Text.literal("Gave Phase 3 engineer / gravity / turret kit"), false);
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
					.then(CommandManager.literal("worldgen2")
							.requires(s -> s.hasPermissionLevel(2))
							.then(CommandManager.argument("kind", StringArgumentType.word()).executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								String name = StringArgumentType.getString(ctx, "kind").toUpperCase();
								com.terminaldetector.drmd.world.gen2.MacroEntry.Kind kind;
								try {
									kind = com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.valueOf(name);
								} catch (Exception e) {
									kind = switch (name) {
										case "CONTINENT" -> com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.FLOATING_CONTINENT;
										case "SPIRAL" -> com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.SPIRAL_RANGE;
										case "INVERTED" -> com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.INVERTED_ISLAND;
										case "LUNAR", "MOON" -> com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.LUNAR_BASE;
										case "CRASH", "CRASHED" -> com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.CRASHED_UFO;
										case "SAUCER", "FLYING" -> com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.UFO;
									case "CITY", "MEGACITY" -> com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.MEGACITY;
									case "COMPLEX", "REACTOR" -> com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.INDUSTRIAL_COMPLEX;
										default -> com.terminaldetector.drmd.world.gen2.MacroEntry.Kind.ARCH;
									};
								}
								var pos = p.getBlockPos().up(8);
								var entry = com.terminaldetector.drmd.world.gen2.ModWorldgen2.forceGenerate(
										p.getServerWorld(), pos, kind);
								com.terminaldetector.drmd.world.gen2.MacroEntry.Kind finalKind = kind;
								ctx.getSource().sendFeedback(() -> Text.literal(
										"WG2.0 " + finalKind + " [" + entry.label + "] at " + pos.toShortString()), true);
								return 1;
							})))
					.then(CommandManager.literal("mega")
							.requires(s -> s.hasPermissionLevel(2))
							.then(CommandManager.argument("type", StringArgumentType.word()).executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								String type = StringArgumentType.getString(ctx, "type").toLowerCase();
								var world = p.getServerWorld();
								var at = p.getPos().add(p.getRotationVec(1f).multiply(12));
								switch (type) {
									case "worm" -> {
										var e = com.terminaldetector.drmd.entity.ModEntities.MEGA_WORM.create(world);
										if (e != null) {
											e.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw(), 0);
											world.spawnEntity(e);
										}
									}
									case "swarm" -> {
										var e = com.terminaldetector.drmd.entity.ModEntities.DRONE_SWARM.create(world);
										if (e != null) {
											e.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
											world.spawnEntity(e);
										}
									}
									case "keeper" -> {
										var e = com.terminaldetector.drmd.entity.ModEntities.REACTOR_KEEPER.create(world);
										if (e != null) {
											e.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
											world.spawnEntity(e);
										}
									}
									case "ufo", "saucer" -> {
										var e = com.terminaldetector.drmd.entity.ModEntities.SKY_UFO.create(world);
										if (e != null) {
											e.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
											world.spawnEntity(e);
										}
									}
									default -> {
										ctx.getSource().sendError(Text.literal("Use: worm | swarm | keeper | ufo"));
										return 0;
									}
								}
								ctx.getSource().sendFeedback(() -> Text.literal("Spawned mega creature: " + type), true);
								return 1;
							})))
					.then(CommandManager.literal("llod")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								ModNetworking.syncLlod(p);
								int n = com.terminaldetector.drmd.world.gen2.MacroWorld.size();
								var bands = com.terminaldetector.drmd.world.llod.LlodRegistry.queryVisible(p.getBlockPos(), 64);
								long c0 = bands.stream().filter(s -> s.level() == com.terminaldetector.drmd.world.llod.LlodLevel.LLOD0).count();
								long c1 = bands.stream().filter(s -> s.level() == com.terminaldetector.drmd.world.llod.LlodLevel.LLOD1).count();
								long c2 = bands.stream().filter(s -> s.level() == com.terminaldetector.drmd.world.llod.LlodLevel.LLOD2).count();
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Voxel LLOD sync — macros=" + n
												+ " visible LLOD0=" + c0 + " LLOD1=" + c1 + " LLOD2=" + c2), false);
								return 1;
							}))
					.then(CommandManager.literal("bomb")
							.requires(s -> s.hasPermissionLevel(2))
							.then(CommandManager.argument("type", StringArgumentType.word()).executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								String name = StringArgumentType.getString(ctx, "type").toLowerCase();
								ItemStack stack = switch (name) {
									case "cluster" -> new ItemStack(ModItems.BOMB_CLUSTER, 8);
									case "heavy", "heavy_cluster", "heavycluster" -> new ItemStack(ModItems.BOMB_HEAVY_CLUSTER, 6);
									case "rocket", "rockets" -> new ItemStack(ModItems.BOMB_ROCKET, 8);
									case "incendiary", "fire" -> new ItemStack(ModItems.BOMB_INCENDIARY, 8);
									case "guided", "laser" -> new ItemStack(ModItems.BOMB_GUIDED, 8);
									default -> new ItemStack(ModItems.BOMB_TNT, 8);
								};
								p.giveItemStack(stack);
								p.giveItemStack(new ItemStack(ModItems.LASER_DESIGNATOR));
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Bomb bay: " + name + " + laser designator"), false);
								return 1;
							}))
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								p.giveItemStack(new ItemStack(ModItems.BOMB_TNT, 8));
								p.giveItemStack(new ItemStack(ModItems.BOMB_CLUSTER, 4));
								p.giveItemStack(new ItemStack(ModItems.BOMB_HEAVY_CLUSTER, 4));
								p.giveItemStack(new ItemStack(ModItems.BOMB_ROCKET, 4));
								p.giveItemStack(new ItemStack(ModItems.BOMB_INCENDIARY, 4));
								p.giveItemStack(new ItemStack(ModItems.BOMB_GUIDED, 4));
								p.giveItemStack(new ItemStack(ModItems.LASER_DESIGNATOR));
								p.giveItemStack(new ItemStack(ModItems.MEGA_LASER));
								ctx.getSource().sendFeedback(() -> Text.literal("Full bomb bay + mega laser issued"), false);
								return 1;
							}))
					.then(CommandManager.literal("laser")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								p.giveItemStack(new ItemStack(ModItems.LASER_DESIGNATOR));
								ctx.getSource().sendFeedback(() -> Text.literal("Laser designator ready"), false);
								return 1;
							}))
					.then(CommandManager.literal("atmosphere")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								var band = com.terminaldetector.drmd.world.atmosphere.AtmosphereBand.at(p.getWorld(), p.getY());
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Atmosphere: " + band.label
												+ (p.getWorld().getRegistryKey() == net.minecraft.world.World.END ? " [END vacuum]" : "")
												+ " | drag=" + band.airDrag
												+ " thrust×" + band.thrustScale
												+ " blast×" + band.blastScale
												+ " | smoke=" + com.terminaldetector.drmd.world.smoke.SmokeSystem.all().size()
												+ " fire=" + com.terminaldetector.drmd.world.fire.FireSystem.focusCount()), false);
								return 1;
							}))
					.then(CommandManager.literal("endreactor")
							.requires(s -> s.hasPermissionLevel(2))
							.executes(ctx -> {
								var server = ctx.getSource().getServer();
								var end = server.getWorld(net.minecraft.world.World.END);
								if (end == null) {
									ctx.getSource().sendError(Text.literal("End dimension unavailable"));
									return 0;
								}
								com.terminaldetector.drmd.world.end.EndReactorState st =
										com.terminaldetector.drmd.world.end.EndReactorState.get(end);
								st.setBaseGenerated(false);
								st.setPhase(com.terminaldetector.drmd.world.end.EndReactorState.Phase.SHIELDED);
								com.terminaldetector.drmd.world.end.EndReactorSession.ensureBase(end);
								ctx.getSource().sendFeedback(() -> Text.literal(
										"End giga-reactor base forced — phase " + st.getPhase()), true);
								return 1;
							}))
			);
		});
	}
}
