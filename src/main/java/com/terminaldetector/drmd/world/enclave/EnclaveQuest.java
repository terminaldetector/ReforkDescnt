package com.terminaldetector.drmd.world.enclave;

import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

/**
 * Quests that fall out of enclave state — not "bring 10 iron".
 */
public final class EnclaveQuest {
	public enum Kind {
		REPEL_TRIPOD("quest.drmd.repel_tripod"),
		RESTORE_REACTOR("quest.drmd.restore_reactor"),
		EXPLORE_SHAFT("quest.drmd.explore_shaft"),
		FIND_DRONE("quest.drmd.find_drone"),
		SUIT_COMPONENT("quest.drmd.suit_component"),
		GUILD_SCRAP("quest.drmd.guild_scrap");

		public final String langKey;

		Kind(String langKey) {
			this.langKey = langKey;
		}
	}

	public final Kind kind;
	public final long siteSeed;

	public EnclaveQuest(Kind kind, long siteSeed) {
		this.kind = kind;
		this.siteSeed = siteSeed;
	}

	public Text title() {
		return Text.translatable(kind.langKey);
	}

	public static EnclaveQuest offer(EnclaveSite site) {
		Random rng = Random.create(site.seed ^ 0x9E57L);
		return new EnclaveQuest(pick(site, rng), site.seed);
	}

	private static Kind pick(EnclaveSite site, Random rng) {
		return switch (site.origin) {
			case MILITARY -> rng.nextBoolean() ? Kind.REPEL_TRIPOD : Kind.FIND_DRONE;
			case ENGINEERS -> site.techLevel >= 2 ? Kind.RESTORE_REACTOR : Kind.SUIT_COMPONENT;
			case MINERS -> Kind.EXPLORE_SHAFT;
			case CULTISTS -> Kind.FIND_DRONE;
			case SCAVENGERS -> Kind.GUILD_SCRAP;
		};
	}
}
