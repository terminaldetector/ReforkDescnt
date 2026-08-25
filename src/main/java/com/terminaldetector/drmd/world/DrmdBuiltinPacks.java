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
 * <p>{@code overworld.json} lives under {@code resourcepacks/advanced_column/} (moved out of
 * {@code data/minecraft/dimension_type/}, where it used to be unconditional base mod data) and is
 * registered here as a built-in data pack whose default activation follows the current
 * {@link DrmdServerConfig#worldModLevel}: enabled for {@link DrmdServerConfig.WorldModLevel#ADVANCED}
 * (identical to every world this mod has ever produced), disabled — so vanilla's own −64…320 Overworld
 * wins instead — for {@link DrmdServerConfig.WorldModLevel#VANILLA}.
 *
 * <p>{@code the_end.json} ships in this same pack, same toggle, not a separate one: the real End's
 * reactor fight (Layer 1) is itself gated on {@code WorldLevels.isAdvancedColumn}, so under
 * {@code VANILLA} the fight that is Layer 2's only unlock path never runs at all — a tall End with no
 * way to reach Layer 2, or a reactor fight with nowhere taller to send its gateways, would both be
 * strictly worse than one switch that raises both ceilings together.
 *
 * <p><strong>Load-bearing limitation:</strong> a built-in pack's registered activation type is fixed
 * once, at this call, from whatever the config held at that moment — it does not react to the config
 * changing again later in the same session. {@code DrmdWorldGenScreen} says so directly rather than
 * implying its usual apply-now behaviour for this one control.
 *
 * <p><strong>Fixed: the pack could never actually register, at all, regardless of mode.</strong>
 * {@link ResourceManagerHelper#registerBuiltinResourcePack} resolves a built-in pack's on-disk location
 * as {@code resourcepacks/<identifier's path segment>/} — the namespace is dropped. The identifier
 * below has always been {@code drmd:advanced_column}, but the directory used to be named
 * {@code resourcepacks/drmd_advanced_column/} — one path segment off from what this call actually looks
 * for. The pack was never found, {@code registered} was always {@code false}, and the Overworld was
 * always vanilla height regardless of which mode was selected. The directory is renamed to match; this
 * comment (and the warning string below) are what's left pointing at it now.
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
					+ "resourcepacks/advanced_column may be missing from the jar.");
		}
	}
}
