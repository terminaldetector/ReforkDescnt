package com.terminaldetector.drmd.world.cubic;

import com.terminaldetector.drmd.d6.D6Cube;
import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.d6.D6Volume;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import java.util.ArrayList;

/** Registry-stable palette persistence: never stores session-specific raw block IDs. */
public final class CubicBlockCodec {
    private CubicBlockCodec() {}
    public static NbtCompound write(D6Cube<BlockState> source) {
        D6Cube<BlockState> cube = source.copy();
        cube.compact();
        NbtCompound tag = new NbtCompound();
        tag.putInt("version", 1);
        NbtList palette = new NbtList();
        for (BlockState state : cube.palette()) palette.add(NbtHelper.fromBlockState(state));
        tag.put("palette", palette);
        tag.putIntArray("cells", cube.indices());
        return tag;
    }
    public static D6Cube<BlockState> read(NbtCompound tag) {
        if (tag.getInt("version") != 1) throw new IllegalArgumentException("unsupported cubic format");
        var palette = new ArrayList<BlockState>();
        NbtList entries = tag.getList("palette", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < entries.size(); i++)
            palette.add(NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), entries.getCompound(i)));
        return D6Cube.decode(Blocks.AIR.getDefaultState(), palette, tag.getIntArray("cells"));
    }
    public static NbtCompound writeVolume(D6Volume<BlockState> volume) {
        NbtCompound tag = new NbtCompound();
        NbtList cubes = new NbtList();
        for (long key : volume.keys()) {
            D6ChunkPos pos = D6ChunkPos.fromLong(key);
            NbtCompound entry = write(volume.cube(pos));
            entry.putInt("x", pos.x()); entry.putInt("y", pos.y()); entry.putInt("z", pos.z());
            cubes.add(entry);
        }
        tag.put("cubes", cubes);
        return tag;
    }
    public static D6Volume<BlockState> readVolume(NbtCompound tag, int maxCubes) {
        NbtList cubes = tag.getList("cubes", NbtElement.COMPOUND_TYPE);
        if (cubes.size() > maxCubes) throw new IllegalArgumentException("volume exceeds cube budget");
        D6Volume<BlockState> volume = new D6Volume<>(Blocks.AIR.getDefaultState());
        for (int i = 0; i < cubes.size(); i++) {
            NbtCompound cube = cubes.getCompound(i);
            volume.put(new D6ChunkPos(cube.getInt("x"), cube.getInt("y"), cube.getInt("z")), read(cube));
        }
        return volume;
    }
}
