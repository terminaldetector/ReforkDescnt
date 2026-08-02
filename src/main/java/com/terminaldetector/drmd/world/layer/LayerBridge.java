package com.terminaldetector.drmd.world.layer;

import com.terminaldetector.drmd.DescentPlayerData;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Three narrative layers form one parallelepiped in scale — Core · Surface · Sky/Orbit/End —
 * but we do <em>not</em> build three solid volumes. The engine already gives the height; seams are
 * thin {@linkplain #SEAM_HALF teleport zones}, and clients show the boundary (block curtain, sky
 * belt drift like Oblivion's sky motion) through render hooks.
 *
 * <p>Optional Immersive Portals remains a soft-dep for true see-through stacks.
 */
public final class LayerBridge {
	/** Half-thickness of the teleport trigger around each layer Y edge. */
	public static final int SEAM_HALF = 3;
	private static final int FADE_IN = 8;
	private static final int FADE_STAY = 20;
	private static final int FADE_OUT = 12;
	private static final int COOLDOWN_TICKS = 20 * 6;

	private static final Map<UUID, WorldLayer> LAST = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> COOLDOWN = new ConcurrentHashMap<>();

	private LayerBridge() {}

	/**
	 * Always tick (not only while 6DoF is on): announce layer changes for every pilot.
	 * Seam teleport still requires Descent armed — otherwise creative walk would yoyo at edges.
	 */
	public static void tick(ServerPlayerEntity player, DescentPlayerData data) {
		UUID id = player.getUuid();
		int cd = COOLDOWN.getOrDefault(id, 0);
		if (cd > 0) COOLDOWN.put(id, cd - 1);

		if (player.getWorld().getRegistryKey() != World.OVERWORLD) {
			LAST.put(id, WorldLayer.at(player.getWorld(), player.getY()));
			return;
		}

		WorldLayer now = WorldLayer.at(player.getWorld(), player.getY());
		WorldLayer prev = LAST.put(id, now);
		if (prev == null || prev == now) return;
		if (cd > 0) return;

		announce(player, now);
		COOLDOWN.put(id, COOLDOWN_TICKS);
		// Teleport hop only when 6DoF owns motion — walking uses announce + display hooks alone.
		if (data != null && data.isEnabled()) {
			seamTeleport(player, data, prev, now);
		}
	}

	/** True when {@code y} sits inside a layer boundary teleport zone. */
	public static boolean inSeamZone(double y) {
		int yi = MathHelper.floor(y);
		for (WorldLayer layer : WorldLayer.values()) {
			if (layer == WorldLayer.CORE) continue;
			if (Math.abs(yi - layer.yMin) <= SEAM_HALF) return true;
		}
		return false;
	}

	/** Nearest seam Y to the player, or {@link Integer#MIN_VALUE}. */
	public static int nearestSeamY(double y) {
		int yi = MathHelper.floor(y);
		int best = Integer.MIN_VALUE;
		int bestDist = Integer.MAX_VALUE;
		for (WorldLayer layer : WorldLayer.values()) {
			if (layer == WorldLayer.CORE) continue;
			int d = Math.abs(yi - layer.yMin);
			if (d < bestDist) {
				bestDist = d;
				best = layer.yMin;
			}
		}
		return best;
	}

	/** Major parallelepiped face Ys — lower / mid / upper narrative cuts. */
	public static int[] parallelepipedFaces() {
		return new int[] {
				WorldLayer.DUNGEON.yMin,   // −240
				WorldLayer.SURFACE.yMin,   // 40
				WorldLayer.ORBIT.yMin,     // 320
				WorldLayer.OBLIVION.yMin   // 880
		};
	}

	private static void announce(ServerPlayerEntity player, WorldLayer layer) {
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(FADE_IN, FADE_STAY, FADE_OUT));
		player.networkHandler.sendPacket(new TitleS2CPacket(
				Text.literal("§b" + layer.label.toUpperCase())));
		player.sendMessage(Text.literal("§7Layer · §f" + layer.label
				+ " §8seam Y " + layer.yMin + "…" + layer.yMax), true);
		if (layer == WorldLayer.ORBIT) {
			player.sendMessage(Text.literal(
					"§a◉ Orbit §7— sky-belt hook (Oblivion motion) · boundary display · teleport seam."), false);
		}
		if (layer == WorldLayer.CORE) {
			player.sendMessage(Text.literal(
					"§c◉ Core §7— diggable mantle. No bedrock border."), false);
		}
		if (layer == WorldLayer.OBLIVION) {
			player.sendMessage(Text.literal(
					"§d◉ Oblivion §7— End band. Seam teleport + sky/display hooks, not a built cube."), false);
		}
	}

	/** Keep XZ; land just past the seam inside the destination band. */
	private static void seamTeleport(ServerPlayerEntity player, DescentPlayerData data,
									 WorldLayer from, WorldLayer to) {
		if (!(player.getWorld() instanceof ServerWorld world)) return;
		double x = player.getX();
		double z = player.getZ();
		boolean ascending = to.ordinal() > from.ordinal();
		int y;
		if (ascending) {
			y = to.yMin + SEAM_HALF + 4;
		} else {
			y = to.yMax - SEAM_HALF - 6;
		}
		y = MathHelper.clamp(y, to.yMin + 2, to.yMax - 4);

		BlockPos probe = BlockPos.ofFloored(x, y, z);
		for (int dy = 0; dy < 16; dy++) {
			BlockPos p = ascending ? probe.up(dy) : probe.down(dy);
			if (p.getY() < to.yMin + 1 || p.getY() > to.yMax - 2) break;
			if (world.getBlockState(p).isAir() && world.getBlockState(p.up()).isAir()) {
				y = p.getY();
				break;
			}
		}

		player.requestTeleport(x, y, z);
		Vec3d v = data.getFlightVelocity().multiply(0.4);
		double nudge = ascending ? 2.0 : -2.0;
		data.setFlightVelocity(new Vec3d(v.x, nudge, v.z));
		player.setVelocity(v.x / 20.0, nudge / 20.0, v.z / 20.0);
		player.velocityModified = true;
		// Creative must not re-arm vanilla fly mid-hop.
		if (player.getAbilities().flying) {
			player.getAbilities().flying = false;
			player.sendAbilitiesUpdate();
		}
		LAST.put(player.getUuid(), WorldLayer.at(world, y));
	}

	public static void clear(UUID id) {
		LAST.remove(id);
		COOLDOWN.remove(id);
	}

	public static void clearAll() {
		LAST.clear();
		COOLDOWN.clear();
	}
}
