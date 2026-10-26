package com.terminaldetector.drmd.world.build;

import com.terminaldetector.drmd.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player scaffold session: frame positions locked, then commit → real blocks.
 */
public final class ConstructScaffold {
	public record Draft(ConstructLaserTier tier, ConstructShape shape, List<BlockPos> positions) {}

	private static final Map<UUID, Draft> DRAFTS = new ConcurrentHashMap<>();

	private ConstructScaffold() {}

	public static Draft get(UUID id) {
		return DRAFTS.get(id);
	}

	public static boolean hasDraft(UUID id) {
		return DRAFTS.containsKey(id);
	}

	public static void setDraft(ServerPlayerEntity player, ConstructLaserTier tier,
			ConstructShape shape, List<BlockPos> positions) {
		List<BlockPos> copy = new ArrayList<>(positions.size());
		for (BlockPos p : positions) copy.add(p.toImmutable());
		DRAFTS.put(player.getUuid(), new Draft(tier, shape, Collections.unmodifiableList(copy)));
		sync(player);
	}

	public static Draft take(UUID id) {
		Draft d = DRAFTS.remove(id);
		return d;
	}

	public static void clear(ServerPlayerEntity player) {
		DRAFTS.remove(player.getUuid());
		sync(player);
	}

	public static void sync(ServerPlayerEntity player) {
		Draft d = DRAFTS.get(player.getUuid());
		if (d == null || d.positions().isEmpty()) {
			ServerPlayNetworking.send(player, new ModNetworking.ScaffoldPayload(
					false, d == null ? "" : d.shape().id, List.of()));
			return;
		}
		int cap = Math.min(d.positions().size(), 384);
		List<BlockPos> slim = d.positions().subList(0, cap);
		ServerPlayNetworking.send(player, new ModNetworking.ScaffoldPayload(true, d.shape().id, slim));
	}

	public static void clearAll() {
		DRAFTS.clear();
	}
}
