package com.terminaldetector.drmd.world.profile;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terminaldetector.drmd.world.geometry.OrganicMaterials;
import com.terminaldetector.drmd.world.volume.PresetVolumeFields;
import com.terminaldetector.drmd.world.volume.VolumeField;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** The native backend owns scheduling, cube application, lighting and saves. */
public final class UngravityChunkGenerator extends ChunkGenerator {
    public static final int SPAWN_Y = 80;
    public static final MapCodec<UngravityChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            WorldProfile.CODEC.fieldOf("profile").forGetter(UngravityChunkGenerator::profile)
    ).apply(instance, UngravityChunkGenerator::new));
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private final WorldProfile profile;
    private final VolumeField field;

    public UngravityChunkGenerator(BiomeSource biomeSource, WorldProfile profile) {
        super(biomeSource);
        this.profile = profile;
        field = PresetVolumeFields.forProfile(profile);
    }

    public WorldProfile profile() { return profile; }
    @Override protected MapCodec<? extends ChunkGenerator> getCodec() { return CODEC; }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig,
            StructureAccessor structures, Chunk chunk) {
        HeightLimitView window = WorldProfileNative.generationWindow(chunk);
        // Vanilla can prepare column shells before the backend asks for a native window.
        if (window == null) return CompletableFuture.completedFuture(chunk);
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        int minY = Math.max(window.getBottomY(), getMinimumY());
        int topY = Math.min(window.getTopY(), getMinimumY() + getWorldHeight());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = minY; y < topY; y++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int x = startX; x < startX + 16; x++) {
                    if (field.density(x, y, z) > 0) {
                        chunk.setBlockState(pos.set(x, y, z), material(x, y, z), false);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private BlockState material(int x, int y, int z) {
        BlockState source = switch (field.material(x, y, z)) {
            case BASALT -> Blocks.BASALT.getDefaultState();
            case DEEPSLATE -> Blocks.DEEPSLATE.getDefaultState();
            case TUFF -> Blocks.TUFF.getDefaultState();
            case CALCITE -> Blocks.CALCITE.getDefaultState();
            case AMETHYST -> Blocks.AMETHYST_BLOCK.getDefaultState();
            case ENGINEERED -> Blocks.POLISHED_DEEPSLATE.getDefaultState();
            case LIGHT -> Blocks.OCHRE_FROGLIGHT.getDefaultState();
            case STONE -> Blocks.STONE.getDefaultState();
            case DIRT -> Blocks.DIRT.getDefaultState();
            case GRASS -> Blocks.GRASS_BLOCK.getDefaultState();
        };
        BlockState authoredNatural = OrganicMaterials.intact(source);
        return authoredNatural == null ? source : authoredNatural;
    }

    @Override public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noise, Chunk chunk) {}
    @Override public void carve(ChunkRegion region, long seed, NoiseConfig noise, BiomeAccess biomes,
            StructureAccessor structures, Chunk chunk, GenerationStep.Carver step) {}
    @Override public void generateFeatures(StructureWorldAccess region, Chunk chunk, StructureAccessor structures) {}
    @Override public void populateEntities(ChunkRegion region) {}
    @Override public void setStructureStarts(DynamicRegistryManager registries, StructurePlacementCalculator placement,
            StructureAccessor structures, Chunk chunk, StructureTemplateManager templates) {}
    @Override public void addStructureReferences(StructureWorldAccess region, StructureAccessor structures, Chunk chunk) {}

    @Override public int getSpawnHeight(HeightLimitView world) { return SPAWN_Y; }

    /** Column-only compatibility queries are bounded around the intended ground spawn. */
    @Override public int getHeight(int x, int z, Heightmap.Type kind, HeightLimitView world, NoiseConfig noise) {
        for (int y = Math.min(SPAWN_Y + 32, world.getTopY() - 1); y >= Math.max(SPAWN_Y - 32, world.getBottomY()); y--) {
            if (field.density(x, y, z) > 0) return y + 1;
        }
        return Math.max(world.getBottomY(), SPAWN_Y - 32);
    }

    @Override public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noise) {
        int minY = Math.max(world.getBottomY(), SPAWN_Y - 32);
        int topY = Math.min(world.getTopY(), SPAWN_Y + 33);
        BlockState[] states = new BlockState[Math.max(0, topY - minY)];
        for (int i = 0; i < states.length; i++) {
            int y = minY + i;
            states[i] = field.density(x, y, z) > 0 ? material(x, y, z) : AIR;
        }
        return new VerticalBlockSample(minY, states);
    }

    @Override public void getDebugHudText(List<String> lines, NoiseConfig noise, BlockPos pos) {
        lines.add("Ungravity " + profile.preset() + " · " + profile.scale());
    }

    @Override public int getSeaLevel() { return 63; }
    @Override public int getMinimumY() { return WorldProfileNative.minY(profile.scale()); }
    @Override public int getWorldHeight() { return WorldProfileNative.height(profile.scale()); }
}
