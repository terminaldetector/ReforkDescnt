package com.terminaldetector.drmd.aeris;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ÆRis/Mirai — step 6 PoC. Interprets {@link AerisDensity#density} directly as solid/air, per block, in
 * {@link #populateNoise}. No {@code NoiseRouter}, no {@code DensityFunction}, no aquifer/surface-rule
 * machinery — those all exist to shape a single height-derived surface (doc 01, section 1.3), which is
 * exactly the assumption this experiment is testing the absence of. Deliberately does not touch
 * {@code Chunk}/{@code ChunkSection}/{@code ChunkPos} — writes through the same {@code Chunk#setBlockState}
 * every ordinary generator uses, per doc 01/02's shared conclusion that the storage layer does not need
 * to change for this experiment.
 *
 * <p><b>Confidence note:</b> this file has no local Minecraft/Fabric classpath to compile against in
 * this sandbox (confirmed earlier this session — no Loom/Yarn decompile cache present), so every method
 * signature was reconstructed from memory and WebSearch snippets of the pinned {@code yarn-1.21.1+build.3}
 * javadoc (the page itself is not directly fetchable — blocked by this environment's proxy, same as
 * every other {@code maven.fabricmc.net} lookup this session) and confirmed against GitHub Actions CI,
 * the real compiler here. Two rounds were wrong before this one compiled: {@code NoiseConfig} lives at
 * {@code net.minecraft.world.gen.noise}, not {@code .gen.chunk}; {@link #populateNoise} takes no
 * {@code Executor} parameter; and the debug-HUD method is named {@link #getDebugHudText}, not
 * {@code addDebugScreenInfo}. {@link #getCodec()}'s name was a guess that turned out right.
 */
public final class AerisDensityChunkGenerator extends ChunkGenerator {
	public static final MapCodec<AerisDensityChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
					.apply(instance, AerisDensityChunkGenerator::new));

	/** Must match {@code data/drmd/dimension_type/aeris_test.json}'s min_y/height exactly. */
	public static final int MIN_Y = -128;
	public static final int HEIGHT = 384;

	private static final BlockState SPHERE_BLOCK = Blocks.GLOWSTONE.getDefaultState();
	private static final BlockState FOAM_BLOCK = Blocks.STONE.getDefaultState();

	public AerisDensityChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> getCodec() {
		return CODEC;
	}

	@Override
	public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
			StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver generationStep) {
		// No carvers — the density field already decides solid/air directly, nothing to carve out of it.
	}

	@Override
	public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
		// No surface rules — populateNoise already chose the final block per voxel; there is no separate
		// "topsoil" pass because there is no single surface for one to sit on top of.
	}

	@Override
	public void populateEntities(ChunkRegion region) {
		// No mob spawning wired up for this experiment — it is about terrain shape, not gameplay.
	}

	@Override
	public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig,
			StructureAccessor structureAccessor, Chunk chunk) {
		// No Executor parameter on this signature (confirmed by CI — an earlier guess included one) —
		// filled synchronously and wrapped, rather than dispatched to a thread pool this method is never
		// handed.
		fillChunk(chunk);
		return CompletableFuture.completedFuture(chunk);
	}

	private static void fillChunk(Chunk chunk) {
		ChunkPos pos = chunk.getPos();
		int baseX = pos.getStartX(), baseZ = pos.getStartZ();
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int x = baseX + lx, z = baseZ + lz;
				for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
					double sphere = AerisDensity.sphereDensity(x, y, z);
					double foam = AerisDensity.foamDensity(x, y, z);
					if (sphere < 0 && foam < 0) continue; // air — Chunk starts empty, nothing to write
					mutable.set(x, y, z);
					// Whichever field actually produced this voxel picks the block — lets a spectator
					// tell the two unioned fields apart on sight (glowstone spheres vs. stone foam).
					chunk.setBlockState(mutable, sphere >= foam ? SPHERE_BLOCK : FOAM_BLOCK, false);
				}
			}
		}
	}

	@Override
	public int getHeight(int x, int z, Heightmap.Type heightmapType, HeightLimitView world, NoiseConfig noiseConfig) {
		// Deliberately the weakest method in this file: a Heightmap-style "topmost solid Y" answer
		// cannot represent a column with several independent surfaces (doc 01, table row Heightmap) —
		// this returns the single topmost one, same limited contract vanilla's own Heightmap has.
		for (int y = MIN_Y + HEIGHT - 1; y >= MIN_Y; y--) {
			if (AerisDensity.isSolid(x, y, z)) return y + 1;
		}
		return MIN_Y;
	}

	@Override
	public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
		BlockState[] states = new BlockState[HEIGHT];
		for (int i = 0; i < HEIGHT; i++) {
			int y = MIN_Y + i;
			states[i] = AerisDensity.isSolid(x, y, z) ? FOAM_BLOCK : Blocks.AIR.getDefaultState();
		}
		return new VerticalBlockSample(MIN_Y, states);
	}

	@Override
	public void getDebugHudText(List<String> info, NoiseConfig noiseConfig, BlockPos pos) {
		info.add("ÆRis density: " + AerisDensity.density(pos.getX(), pos.getY(), pos.getZ()));
	}

	@Override
	public int getSeaLevel() {
		return MIN_Y - 1; // nothing is ever "underwater" — this experiment has no fluid
	}

	@Override
	public int getMinimumY() {
		return MIN_Y;
	}
}
