package com.terminaldetector.drmd.d6;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Sparse cubic space, used identically for terrain snapshots and local moving structures. */
public final class D6Volume<T> {
    private final T empty;
    private final Map<Long, D6Cube<T>> cubes = new HashMap<>();
    public D6Volume(T empty) { this.empty = empty; }

    public T get(int x, int y, int z) {
        D6Cube<T> cube = cubes.get(D6ChunkPos.ofBlock(x, y, z).asLong());
        return cube == null ? empty : cube.get(x & 15, y & 15, z & 15);
    }

    public void set(int x, int y, int z, T value) {
        long key = D6ChunkPos.ofBlock(x, y, z).asLong();
        D6Cube<T> cube = cubes.get(key);
        if (cube == null && value.equals(empty)) return;
        if (cube == null) { cube = new D6Cube<>(empty); cubes.put(key, cube); }
        cube.set(x & 15, y & 15, z & 15, value);
        if (cube.isEmpty()) cubes.remove(key);
    }

    public D6Cube<T> cube(D6ChunkPos pos) { return cubes.get(pos.asLong()); }
    public void put(D6ChunkPos pos, D6Cube<T> cube) { cubes.put(pos.asLong(), cube.copy()); }
    public Set<Long> keys() { return Set.copyOf(cubes.keySet()); }
    public int cubeCount() { return cubes.size(); }
    public int occupiedCount() { return cubes.values().stream().mapToInt(D6Cube::occupiedCount).sum(); }
}
