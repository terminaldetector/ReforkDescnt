package com.terminaldetector.drmd.world;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Makes the tall-column {@code dimension_type} override optional instead of permanently merged mod
 * data, so {@link DrmdServerConfig.WorldModLevel#VANILLA} can mean a genuinely vanilla-height
 * Overworld rather than the same column with only content generation switched off.
 *
 * <p>{@code overworld.json} lives under {@code resourcepacks/drmd_advanced_column/} (moved out of
 * {@code data/minecraft/dimension_type/}, where it used to be unconditional base mod data) and is
 * registered here as a built-in data pack whose default activation follows the current
 * {@link DrmdServerConfig#worldModLevel}: enabled for {@link DrmdServerConfig.WorldModLevel#ADVANCED}
 * (identical to every world this mod has ever produced), disabled — so vanilla's own −64…320 Overworld
 * wins instead — for {@link DrmdServerConfig.WorldModLevel#VANILLA}.
 *
 * <p><strong>Load-bearing limitation:</strong> a built-in pack's registered activation type is fixed
 * once, at this call, from whatever the config held at that moment — it does not react to the config
 * changing again later in the same session. {@code DrmdWorldGenScreen} says so directly rather than
 * implying its usual apply-now behaviour for this one control.
 */
public final class DrmdBuiltinPacks {
	private DrmdBuiltinPacks() {}

	public static void register() {
		ModContainer mod = FabricLoader.getInstance().getModContainer(DescentMod.MOD_ID)
				.orElseThrow(() -> new IllegalStateException(DescentMod.MOD_ID + " has no ModContainer at init"));
		ResourcePackActivationType activation = DrmdServerConfig.worldModLevel == DrmdServerConfig.WorldModLevel.ADVANCED
				? ResourcePackActivationType.DEFAULT_ENABLED
				: ResourcePackActivationType.NORMAL;
		boolean registered = ResourceManagerHelper.registerBuiltinResourcePack(
				Identifier.of(DescentMod.MOD_ID, "advanced_column"), mod,
				Text.translatable("pack.drmd.advanced_column"), activation);
		if (registered) {
			DescentMod.LOGGER.info("Advanced-column pack registered, default activation={}", activation);
		} else {
			// Not fatal — see the class doc on ResourcePackActivationType. Worst case the pack is
			// simply absent from the Data Packs list, which is exactly today's always-on behaviour
			// for a fresh Advanced world and a same-message warning for a Vanilla one.
			DescentMod.LOGGER.warn("Could not register the advanced_column built-in pack — "
					+ "resourcepacks/drmd_advanced_column may be missing from the jar.");
		}
	}
}
