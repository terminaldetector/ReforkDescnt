package com.terminaldetector.drmd.weapon.items;

import com.terminaldetector.drmd.DescentMod;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.energy.EnergyPreset;
import com.terminaldetector.drmd.energy.EnergySystem;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.world.base.ReactorRoomStarter;
import com.terminaldetector.drmd.world.build.ConstructionMode;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Creative / console-free session tools. Live at the top of the DRMD creative tab
 * so players can start 6DoF without typing /d6.
 */
public final class SessionControlItems {
	public static Item REACTOR_STARTER;
	public static Item SIXDOF_CORE;
	public static Item STARTER_KIT;
	public static Item LEVEL_LIFT;
	public static Item CONSTRUCTION_PAD;
	public static Item ENERGY_DIAL;

	private SessionControlItems() {}

	public static void register() {
		REACTOR_STARTER = reg("reactor_starter", new ActionItem(new Item.Settings().maxCount(1), Action.REACTOR));
		SIXDOF_CORE = reg("sixdof_core", new ActionItem(new Item.Settings().maxCount(1), Action.SIXDOF));
		STARTER_KIT = reg("starter_kit", new ActionItem(new Item.Settings().maxCount(1), Action.KIT));
		LEVEL_LIFT = reg("level_lift", new ActionItem(new Item.Settings().maxCount(1), Action.LEVEL));
		CONSTRUCTION_PAD = reg("construction_pad", new ActionItem(new Item.Settings().maxCount(1), Action.CONSTRUCT));
		ENERGY_DIAL = reg("energy_dial", new ActionItem(new Item.Settings().maxCount(1), Action.ENERGY));
		DescentMod.LOGGER.info("Registered DRMD session control items (creative / no-console)");
	}

	private static Item reg(String id, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(DescentMod.MOD_ID, id), item);
	}

	enum Action {
		REACTOR, SIXDOF, KIT, LEVEL, CONSTRUCT, ENERGY
	}

	static final class ActionItem extends Item {
		private final Action action;

		ActionItem(Settings settings, Action action) {
			super(settings);
			this.action = action;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (world.isClient) return TypedActionResult.success(stack);
			if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.fail(stack);

			switch (action) {
				case REACTOR -> {
					ReactorRoomStarter.activate(player);
					world.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 1.1f);
				}
				case SIXDOF -> {
					FlightSystem.toggle(player);
					boolean on = DescentPlayerData.get(player).isEnabled();
					player.sendMessage(Text.literal(on
							? "§b6DoF §aON §7— thrusters armed (H also toggles)"
							: "§b6DoF §7OFF §8— press again / H to fly"), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.8f, on ? 1.4f : 0.7f);
				}
				case KIT -> {
					FlightSystem.enable(player);
					DescentPlayerData data = DescentPlayerData.get(player);
					data.setEnergy(100);
					data.setShield(100);
					ModNetworking.syncPlayer(player, data);
					ReactorRoomStarter.giveKit(player);
					player.sendMessage(Text.literal("§aStarter kit issued — Pyro GX + weapons + build tools"), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.9f, 1.2f);
				}
				case LEVEL -> {
					WorldLevels.Level[] levels = WorldLevels.Level.values();
					WorldLevels.Level cur = WorldLevels.at(player.getY());
					int next = (cur.ordinal() + 1) % levels.length;
					WorldLevels.Level target = levels[next];
					player.requestTeleport(player.getX(), target.travelY(), player.getZ());
					player.sendMessage(Text.literal(
							"§bLevel lift → §f" + target.label + " §7(y=" + target.travelY() + ")"), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.7f, 1.3f);
				}
				case CONSTRUCT -> {
					ConstructionMode.toggle(player);
					world.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.6f, 1.1f);
				}
				case ENERGY -> {
					DescentPlayerData data = DescentPlayerData.get(player);
					EnergyPreset[] presets = EnergyPreset.values();
					int idx = 0;
					for (int i = 0; i < presets.length; i++) {
						if (presets[i] == data.getPreset()) {
							idx = (i + 1) % presets.length;
							break;
						}
					}
					EnergySystem.setPreset(data, presets[idx]);
					ModNetworking.syncPlayer(player, data);
					player.sendMessage(Text.literal("§eEnergy preset: §f" + data.getPreset().id), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.7f, 1.0f + idx * 0.1f);
				}
			}
			return TypedActionResult.success(stack);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable(getTranslationKey() + ".desc"));
		}
	}
}
