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
					.then(CommandManager.literal("afterburner")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								DescentPlayerData d = DescentPlayerData.get(p);
								int t = d.getAfterburnerTier();
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Afterburner tier " + t + " ("
												+ com.terminaldetector.drmd.flight.AfterburnerTiers.colorName(t)
												+ ") · cost "
												+ com.terminaldetector.drmd.flight.AfterburnerTiers.costPerSec(t)
												+ "/s · hold R · menu: N"), false);
								return 1;
							})
							.then(CommandManager.argument("tier", IntegerArgumentType.integer(1, 4)).executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								DescentPlayerData d = DescentPlayerData.get(p);
								int t = IntegerArgumentType.getInteger(ctx, "tier");
								d.setAfterburnerTier(t);
								ModNetworking.syncPlayer(p, d);
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Afterburner → " + t + " "
												+ com.terminaldetector.drmd.flight.AfterburnerTiers.colorName(t)), false);
								return 1;
							})))
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
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.CONSTRUCTION_LASER_GREEN));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.CONSTRUCTION_LASER_YELLOW));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.CONSTRUCTION_LASER_BLUE));
								p.giveItemStack(new ItemStack(com.terminaldetector.drmd.entity.ModWorldBlocks.CONSTRUCTION_LASER_PURPLE));
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
									case "seeker", "oblivion", "ryskatel" -> {
										var e = com.terminaldetector.drmd.entity.ModEntities.OBLIVION_SEEKER.create(world);
										if (e != null) {
											e.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw(), 0);
											world.spawnEntity(e);
										}
									}
									default -> {
										ctx.getSource().sendError(Text.literal(
												"Use: worm | swarm | keeper | ufo | seeker"));
										return 0;
									}
								}
								ctx.getSource().sendFeedback(() -> Text.literal("Spawned mega creature: " + type), true);
								return 1;
							}))
							.then(CommandManager.literal("ufodestroy")
									.then(CommandManager.argument("mode", StringArgumentType.word()).executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										String modeArg = StringArgumentType.getString(ctx, "mode").toLowerCase();
										com.terminaldetector.drmd.world.structure.DestructionMode mode = switch (modeArg) {
											case "crash" -> com.terminaldetector.drmd.world.structure.DestructionMode.CRASH;
											case "shockwave", "shock" -> com.terminaldetector.drmd.world.structure.DestructionMode.SHOCKWAVE;
											default -> null;
										};
										if (mode == null) {
											ctx.getSource().sendError(Text.literal("Use: crash | shockwave"));
											return 0;
										}
										var world = p.getServerWorld();
										var ufo = com.terminaldetector.drmd.world.mega.SkyUfoEntity.findContaining(world, p.getPos());
										if (ufo == null) {
											ufo = com.terminaldetector.drmd.world.mega.SkyUfoEntity.findNear(world, p.getBlockPos(), 5);
										}
										if (ufo == null) {
											ctx.getSource().sendError(Text.literal("No Sky UFO nearby"));
											return 0;
										}
										ufo.forceDestructionMode(mode);
										ufo.destroyFromCore(world, p, "debug " + modeArg);
										ctx.getSource().sendFeedback(() -> Text.literal("Sky UFO destruction forced: " + modeArg), true);
										return 1;
									}))))
					.then(CommandManager.literal("scars")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								var ow = p.getServer().getOverworld();
								int n = ow == null ? 0
										: com.terminaldetector.drmd.world.scar.ScarMapState.get(ow).size();
								boolean dh = com.terminaldetector.drmd.world.compat.DistantHorizonsCompat.isPresent();
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Reactor scars=" + n + " cells · far view = "
												+ (dh ? "Distant Horizons"
												: "vanilla only — install modrinth.com/mod/distanthorizons")),
										false);
								return 1;
							})
							.then(CommandManager.literal("paint")
									.requires(s -> s.hasPermissionLevel(2))
									.executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										var ow = p.getServer().getOverworld();
										if (ow == null) return 0;
										com.terminaldetector.drmd.world.scar.ScarMapState.get(ow)
												.scarBlock(p.getBlockX(), p.getBlockZ());
										ctx.getSource().sendFeedback(() -> Text.literal(
												"Reactor scar recorded under you — cut in on next chunk load"), true);
										return 1;
									})))
					.then(CommandManager.literal("megacity")
							.executes(ctx -> plateStatus(ctx, "megacity",
									com.terminaldetector.drmd.world.surface.MegacityRegions
											.describeNearest(ctx.getSource().getPlayer().getBlockX(),
													ctx.getSource().getPlayer().getBlockZ()),
									com.terminaldetector.drmd.world.surface.MegacityRegions
											.isInBiome(ctx.getSource().getPlayer().getBlockX(),
													ctx.getSource().getPlayer().getBlockZ())))
							.then(CommandManager.literal("tp")
									.requires(s -> s.hasPermissionLevel(2))
									.executes(ctx -> plateTp(ctx,
											com.terminaldetector.drmd.world.surface.MegacityRegions
													.findNearest(ctx.getSource().getPlayer().getBlockX(),
															ctx.getSource().getPlayer().getBlockZ()),
											"megacity"))))
					.then(CommandManager.literal("technogenic")
							.executes(ctx -> plateStatus(ctx, "technogenic_sea",
									com.terminaldetector.drmd.world.surface.TechnogenicSeaRegions
											.describeNearest(ctx.getSource().getPlayer().getBlockX(),
													ctx.getSource().getPlayer().getBlockZ()),
									com.terminaldetector.drmd.world.surface.TechnogenicSeaRegions
											.isInBiome(ctx.getSource().getPlayer().getBlockX(),
													ctx.getSource().getPlayer().getBlockZ())))
							.then(CommandManager.literal("tp")
									.requires(s -> s.hasPermissionLevel(2))
									.executes(ctx -> plateTp(ctx,
											com.terminaldetector.drmd.world.surface.TechnogenicSeaRegions
													.findNearest(ctx.getSource().getPlayer().getBlockX(),
															ctx.getSource().getPlayer().getBlockZ()),
											"technogenic sea"))))
					.then(CommandManager.literal("scorched")
							.executes(ctx -> plateStatus(ctx, "scorched_lands",
									com.terminaldetector.drmd.world.surface.ScorchedLandsRegions
											.describeNearest(ctx.getSource().getPlayer().getBlockX(),
													ctx.getSource().getPlayer().getBlockZ()),
									com.terminaldetector.drmd.world.surface.ScorchedLandsRegions
											.isInBiome(ctx.getSource().getPlayer().getBlockX(),
													ctx.getSource().getPlayer().getBlockZ())))
							.then(CommandManager.literal("tp")
									.requires(s -> s.hasPermissionLevel(2))
									.executes(ctx -> plateTp(ctx,
											com.terminaldetector.drmd.world.surface.ScorchedLandsRegions
													.findNearest(ctx.getSource().getPlayer().getBlockX(),
															ctx.getSource().getPlayer().getBlockZ()),
											"scorched lands"))))
					.then(CommandManager.literal("guild")
							.executes(ctx -> plateStatus(ctx, "iron_guild",
									com.terminaldetector.drmd.world.surface.IronGuildRegions
											.describeNearest(ctx.getSource().getPlayer().getBlockX(),
													ctx.getSource().getPlayer().getBlockZ()),
									com.terminaldetector.drmd.world.surface.IronGuildRegions
											.isInBiome(ctx.getSource().getPlayer().getBlockX(),
													ctx.getSource().getPlayer().getBlockZ())))
							.then(CommandManager.literal("tp")
									.requires(s -> s.hasPermissionLevel(2))
									.executes(ctx -> plateTp(ctx,
											com.terminaldetector.drmd.world.surface.IronGuildRegions
													.findNearest(ctx.getSource().getPlayer().getBlockX(),
															ctx.getSource().getPlayer().getBlockZ()),
											"iron guild"))))
					.then(CommandManager.literal("enclave")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								var site = com.terminaldetector.drmd.world.enclave.EnclaveSite
										.generate(p.getServerWorld().getSeed(), p.getBlockPos());
								var mem = com.terminaldetector.drmd.world.enclave.FactionMemory
										.of(p.getServerWorld());
								int rep = mem.getRep(p.getUuid(), site.origin);
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Enclave cell — " + site.shortLabel()
												+ " · rep=" + rep
												+ " · " + site.anchor.getX() + " " + site.anchor.getZ()
												+ " | herald: use / sneak-use for dialogue+quest"), false);
								return 1;
							}))
					.then(CommandManager.literal("orbit")
							.executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								String desc = com.terminaldetector.drmd.world.orbit.OrbitBands.describe(
										p.getBlockX(), p.getBlockY(), p.getBlockZ());
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Orbit — " + desc
												+ " | junk A Y=" + com.terminaldetector.drmd.world.orbit.OrbitBands.LAYER_A_Y
												+ " B Y=" + com.terminaldetector.drmd.world.orbit.OrbitBands.LAYER_B_Y
												+ " ring Y=" + com.terminaldetector.drmd.world.orbit.OrbitBands.RING_Y
												+ " | End = above orbital top (separate)"), false);
								return 1;
							})
							.then(CommandManager.literal("ring")
									.requires(s -> s.hasPermissionLevel(2))
									.executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										double ang = Math.atan2(p.getZ(), p.getX());
										int x = (int) (Math.cos(ang) * com.terminaldetector.drmd.world.orbit.OrbitBands.RING_RADIUS);
										int z = (int) (Math.sin(ang) * com.terminaldetector.drmd.world.orbit.OrbitBands.RING_RADIUS);
										p.requestTeleport(x, com.terminaldetector.drmd.world.orbit.OrbitBands.RING_Y, z);
										ctx.getSource().sendFeedback(() -> Text.literal(
												"Teleported to techno-ring deck"), true);
										return 1;
									})))
					.then(CommandManager.literal("reactor")
							.executes(ctx -> {
								int breaches = com.terminaldetector.drmd.world.dungeon.FacilityReactorFight.activeBreaches();
								int falls = com.terminaldetector.drmd.world.dungeon.ReactorAftermath.pendingFalls();
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Reactor — active breaches=" + breaches
												+ " pending meteor falls=" + falls
												+ " | vitality ALIVE/SEMI_ALIVE/DEAD on technogenic dungeons"
												+ " | destroy core → 90s escape → detonation"), false);
								return 1;
							}))
					.then(CommandManager.literal("fate")
							.executes(ctx -> {
								var fate = com.terminaldetector.drmd.world.fate.WorldEndings.fate(
										ctx.getSource().getServer());
								ctx.getSource().sendFeedback(() -> Text.literal(
										"World fate=" + fate
												+ " | SILENCE←giga-reactor | VOID←dark_energy_bomb @ core"
												+ " | see docs/WORLD_PHILOSOPHY.md"), false);
								return 1;
							})
							.then(CommandManager.argument("set", StringArgumentType.word())
									.requires(s -> s.hasPermissionLevel(2))
									.executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										String set = StringArgumentType.getString(ctx, "set").toUpperCase();
										var server = ctx.getSource().getServer();
										switch (set) {
											case "SILENCE" -> com.terminaldetector.drmd.world.fate.WorldEndings
													.applySilence(server, p.getBlockPos());
											case "VOID" -> com.terminaldetector.drmd.world.fate.WorldEndings
													.applyVoid(server, p, p.getBlockPos());
											case "CONTINUING", "RESET" -> {
												var st = com.terminaldetector.drmd.world.fate.WorldEndings.state(server);
												if (st != null) {
													st.setFate(com.terminaldetector.drmd.world.fate.WorldFate.CONTINUING,
															server.getOverworld().getTime());
													com.terminaldetector.drmd.world.fate.WorldEndings.broadcast(server);
												}
											}
											default -> {
												ctx.getSource().sendError(Text.literal("Use: SILENCE | VOID | CONTINUING"));
												return 0;
											}
										}
										ctx.getSource().sendFeedback(() -> Text.literal("Fate → " + set), true);
										return 1;
									})))
					.then(CommandManager.literal("psychedelic")
							.executes(ctx -> {
								var server = ctx.getSource().getServer();
								var ow = server.getOverworld();
								boolean cfg = com.terminaldetector.drmd.world.DrmdServerConfig.psychedelicEnabled();
								if (ow == null) {
									ctx.getSource().sendFeedback(() -> Text.literal(
											"Psychedelic config=" + cfg + " (no overworld)"), false);
									return 1;
								}
								var st = com.terminaldetector.drmd.world.DescentWorldState.get(ow);
								String variant = st.getPsychedelicVariant() < 0 ? "unset"
										: com.terminaldetector.drmd.world.psychedelic.PsychedelicFractal
										.fromIndex(st.getPsychedelicVariant()).label
										+ " (#" + st.getPsychedelicVariant() + ")";
								ctx.getSource().sendFeedback(() -> Text.literal(
										"Psychedelic — config=" + cfg
												+ " world=" + st.isPsychedelic()
												+ " variant=" + variant
												+ " | kinds=" + com.terminaldetector.drmd.world.psychedelic.PsychedelicFractal.count()
												+ " | dock Y=" + com.terminaldetector.drmd.world.psychedelic.PsychedelicWorldgen.SPAWN_Y
												+ " | set config/drmd-server.properties psychedelicWorlds=true before world create"), false);
								return 1;
							})
							.then(CommandManager.literal("seed")
									.requires(s -> s.hasPermissionLevel(2))
									.executes(ctx -> {
										var server = ctx.getSource().getServer();
										var ow = server.getOverworld();
										if (ow == null) return 0;
										var st = com.terminaldetector.drmd.world.DescentWorldState.get(ow);
										if (st.isStockSeeded() && st.isPsychedelic()) {
											ctx.getSource().sendFeedback(() -> Text.literal(
													"Already psychedelic-seeded — variant #" + st.getPsychedelicVariant()), false);
											return 1;
										}
										if (st.isStockSeeded() && !st.isPsychedelic()) {
											ctx.getSource().sendError(Text.literal(
													"World already stock-seeded as non-psychedelic. Use a fresh world + psychedelicWorlds=true."));
											return 0;
										}
										st.setPsychedelic(true);
										com.terminaldetector.drmd.world.psychedelic.PsychedelicWorldgen.seed(server, st);
										st.setStockSeeded(true);
										ctx.getSource().sendFeedback(() -> Text.literal(
												"Forced psychedelic seed — " + com.terminaldetector.drmd.world.psychedelic.PsychedelicFractal
														.fromIndex(st.getPsychedelicVariant()).label
														+ " (#" + st.getPsychedelicVariant() + ")"), true);
										return 1;
									}))
							.then(CommandManager.literal("dock")
									.executes(ctx -> {
										ServerPlayerEntity p = ctx.getSource().getPlayer();
										int y = com.terminaldetector.drmd.world.psychedelic.PsychedelicWorldgen.SPAWN_Y;
										p.requestTeleport(0.5, y + 2.0, 0.5);
										DescentPlayerData d = DescentPlayerData.get(p);
										FlightSystem.enable(p, d);
										d.setGravityFactor(0f);
										ModNetworking.syncPlayer(p, d);
										ctx.getSource().sendFeedback(() -> Text.literal(
												"Teleported to psychedelic void dock @ y=" + y), false);
										return 1;
									})))
					.then(CommandManager.literal("bomb")
							.requires(s -> s.hasPermissionLevel(2))
							.then(CommandManager.argument("type", StringArgumentType.word()).executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								String name = StringArgumentType.getString(ctx, "type").toLowerCase();
								ItemStack stack = switch (name) {
									case "cluster" -> new ItemStack(ModItems.BOMB_CLUSTER, 8);
									case "heavy", "heavy_cluster", "heavycluster" -> new ItemStack(ModItems.BOMB_HEAVY_CLUSTER, 6);
									case "virus_nether", "nether_virus", "virusnether" -> new ItemStack(ModItems.BOMB_CLUSTER_VIRUS_NETHER, 8);
									case "virus_sculk", "sculk_virus", "virussculk", "sculk" -> new ItemStack(ModItems.BOMB_CLUSTER_VIRUS_SCULK, 8);
									case "cluster_laser", "laser_cluster", "clusterlaser" -> new ItemStack(ModItems.BOMB_CLUSTER_LASER, 8);
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
								p.giveItemStack(new ItemStack(ModItems.BOMB_CLUSTER_VIRUS_NETHER, 4));
								p.giveItemStack(new ItemStack(ModItems.BOMB_CLUSTER_VIRUS_SCULK, 4));
								p.giveItemStack(new ItemStack(ModItems.BOMB_CLUSTER_LASER, 4));
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

	private static int plateStatus(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx,
								   String name, String tip, boolean here)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ctx.getSource().getPlayer(); // require player
		ctx.getSource().sendFeedback(() -> Text.literal(
				(here ? "§aInside " + name + " · " : "§7")
						+ tip
						+ " §8· biome plate · /d6 " + name.split("_")[0] + " tp"), false);
		return 1;
	}

	private static int plateTp(com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx,
							   net.minecraft.util.math.BlockPos anchor, String label)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity p = ctx.getSource().getPlayer();
		if (anchor == null) {
			ctx.getSource().sendError(Text.literal("No " + label + " plate found"));
			return 0;
		}
		var world = p.getServerWorld();
		int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				anchor.getX(), anchor.getZ()) + 8;
		p.requestTeleport(anchor.getX() + 0.5, y, anchor.getZ() + 0.5);
		ctx.getSource().sendFeedback(() -> Text.literal(
				"Teleported to " + label + " @ " + anchor.getX() + " " + anchor.getZ()), true);
		return 1;
	}
}
