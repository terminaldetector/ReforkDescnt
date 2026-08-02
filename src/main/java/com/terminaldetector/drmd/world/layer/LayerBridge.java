package com.terminaldetector.drmd.world.layer;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.world.WorldFeatures;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HL2-style layer transitions without a seamless-world mod.
 *
 * <p>Minecraft cannot host a kilometre-tall Euclidean column safely. Path A (optional):
 * Immersive Portals dimension stack for true see-through seams. Path B (this class):
 * stay in the max-height Overworld column and, when the pilot crosses a narrative
 * boundary at speed, run a short fade → soft reposition → fade-in so the set never
 * collapses into a hard loading screen.
 *
 * <p>When {@link WorldFeatures#MACRO_WORLDGEN} is off, transitions only announce the
 * layer (HUD title) — no teleport — so empty bands stay flyable.
 */
public final class LayerBridge {
	/** Seconds of title fade (in + stay + out). */
	private static final int FADE_IN = 8;
	private static final int FADE_STAY = 20;
	private static final int FADE_OUT = 12;
	/** Cooldown between bridge hops so we do not thrash at the seam. */
	private static final int COOLDOWN_TICKS = 20 * 8;

	private static final Map<UUID, WorldLayer> LAST = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> COOLDOWN = new ConcurrentHashMap<>();

	private LayerBridge() {}

	public static void tick(ServerPlayerEntity player, DescentPlayerData data) {
		UUID id = player.getUuid();
		int cd = COOLDOWN.getOrDefault(id, 0);
		if (cd > 0) COOLDOWN.put(id, cd - 1);

		WorldLayer now = WorldLayer.at(player.getWorld(), player.getY());
		WorldLayer prev = LAST.put(id, now);
		if (prev == null || prev == now) return;
		if (cd > 0) return;

		announce(player, now);
		COOLDOWN.put(id, COOLDOWN_TICKS);

		// Soft seam when districts/macro are live and the pilot is afterburning across a band.
		if ((WorldFeatures.SURFACE_DISTRICTS || WorldFeatures.MACRO_WORLDGEN)
				&& data.isEnabled() && data.isAlwaysRun()) {
			softArrive(player, now);
		}
	}

	private static void announce(ServerPlayerEntity player, WorldLayer layer) {
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(FADE_IN, FADE_STAY, FADE_OUT));
		player.networkHandler.sendPacket(new TitleS2CPacket(
				Text.literal("§b" + layer.label.toUpperCase())));
		player.sendMessage(Text.literal("§7Layer · §f" + layer.label
				+ " §8Y " + layer.yMin + "…" + layer.yMax), true);
		// Survival cue: orbital belt on the sky means surface bases are about to lose cover.
		if (layer == WorldLayer.ORBIT) {
			player.sendMessage(Text.literal(
					"§a◉ Orbital belt §7ahead — junk A/B + techno-ring (not the End)."), false);
			player.sendMessage(Text.literal("§8" + com.terminaldetector.drmd.world.orbit.OrbitBands.describe(
					player.getBlockX(), player.getBlockY(), player.getBlockZ())), true);
		}
		if (layer == WorldLayer.CORE) {
			player.sendMessage(Text.literal(
					"§c◉ Core §7— diggable mantle / continuous nether. No bedrock border."), false);
			player.sendMessage(Text.literal(
					"§8Dig up through granite→mantle for surface, or craft a Nether Gate Catalyst. Sync keeps aftermath linked."), true);
		}
		if (layer == WorldLayer.DUNGEON && player.getY() < com.terminaldetector.drmd.world.level.WorldLevels.ABYSS_TOP + 8) {
			player.sendMessage(Text.literal(
					"§7Mantle crust §8— plasma-resistant granite. Nether blocks appear as you dig deeper."), true);
		}
		if (layer == WorldLayer.OBLIVION || player.getY() >= com.terminaldetector.drmd.world.level.WorldLevels.ORBITAL_TOP - 16) {
			player.sendMessage(Text.literal(
					"§dTechno-ring vista §7— End seam. Gate catalysts required; ImmPtl stack optional."), false);
		}
	}

	/**
	 * HL2-ish: keep XZ, place at a comfortable mid altitude of the destination band,
	 * clear velocity slightly so the camera settle reads as a load stitch — not a death.
	 */
	private static void softArrive(ServerPlayerEntity player, WorldLayer layer) {
		if (!(player.getWorld() instanceof ServerWorld)) return;
		if (player.getWorld().getRegistryKey() != World.OVERWORLD) return;
		double x = player.getX();
		double z = player.getZ();
		int y = layer.midY();
		BlockPos probe = BlockPos.ofFloored(x, y, z);
		// Prefer open air near midY.
		for (int dy = 0; dy < 24; dy++) {
			BlockPos p = probe.up(dy);
			if (player.getWorld().getBlockState(p).isAir()
					&& player.getWorld().getBlockState(p.up()).isAir()) {
				y = p.getY();
				break;
			}
		}
		player.requestTeleport(x, y, z);
		DescentPlayerData data = DescentPlayerData.get(player);
		Vec3d v = data.getFlightVelocity().multiply(0.35);
		data.setFlightVelocity(v);
	}

	public static void clear(UUID id) {
		LAST.remove(id);
		COOLDOWN.remove(id);
	}
}
