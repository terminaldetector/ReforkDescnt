package com.terminaldetector.drmd.d6;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A palette-backed 16³ volume. No heightmap, world ceiling or Minecraft dependency. */
public final class D6Cube<T> {
    public static final int SIZE = 16;
    public static final int CELLS = SIZE * SIZE * SIZE;
    private final T empty;
    private final List<T> palette = new ArrayList<>();
    private final Map<T, Integer> reverse = new HashMap<>();
    private final char[] cells = new char[CELLS];
    private int occupied;

    public D6Cube(T empty) {
        this.empty = Objects.requireNonNull(empty);
        palette.add(empty);
        reverse.put(empty, 0);
    }

    public T get(int x, int y, int z) { return palette.get(cells[index(x, y, z)]); }

    public void set(int x, int y, int z, T value) {
        Objects.requireNonNull(value);
        int index = index(x, y, z);
        T old = palette.get(cells[index]);
        if (old.equals(value)) return;
        if (old.equals(empty)) occupied++;
        if (value.equals(empty)) occupied--;
        Integer id = reverse.get(value);
        // A long-lived editable cube can cycle through more states than it has cells.
        if (id == null && palette.size() >= Character.MAX_VALUE) {
            compact();
            id = reverse.get(value);
        }
        if (id == null) {
            id = palette.size();
            palette.add(value);
            reverse.put(value, id);
        }
        cells[index] = (char) (int) id;
    }

    public int occupiedCount() { return occupied; }
    public boolean isEmpty() { return occupied == 0; }

    /** Snapshot for persistence/workers: no mutable arrays are shared with the writer. */
    public D6Cube<T> copy() {
        D6Cube<T> copy = new D6Cube<>(empty);
        copy.palette.clear(); copy.palette.addAll(palette);
        copy.reverse.clear(); copy.reverse.putAll(reverse);
        System.arraycopy(cells, 0, copy.cells, 0, CELLS);
        copy.occupied = occupied;
        return copy;
    }

    public List<T> palette() { return List.copyOf(palette); }
    public int[] indices() {
        int[] result = new int[CELLS];
        for (int i = 0; i < CELLS; i++) result[i] = cells[i];
        return result;
    }

    public static <T> D6Cube<T> decode(T empty, List<T> palette, int[] indices) {
        if (palette.isEmpty() || palette.size() > CELLS + 1 || indices.length != CELLS)
            throw new IllegalArgumentException("invalid cube palette/data length");
        D6Cube<T> cube = new D6Cube<>(empty);
        for (int i = 0; i < CELLS; i++) {
            int id = indices[i];
            if (id < 0 || id >= palette.size()) throw new IllegalArgumentException("invalid cube palette index");
            cube.set(i & 15, (i >>> 8) & 15, (i >>> 4) & 15, palette.get(id));
        }
        return cube;
    }

    public void compact() {
        List<T> old = List.copyOf(palette);
        palette.clear(); reverse.clear();
        palette.add(empty); reverse.put(empty, 0);
        for (int i = 0; i < CELLS; i++) {
            T value = old.get(cells[i]);
            int id = reverse.computeIfAbsent(value, key -> { palette.add(key); return palette.size() - 1; });
            cells[i] = (char) id;
        }
    }

    private static int index(int x, int y, int z) {
        if ((x | y | z) < 0 || x >= SIZE || y >= SIZE || z >= SIZE)
            throw new IndexOutOfBoundsException("cube local coordinate outside 0..15");
        return (y << 8) | (z << 4) | x;
    }
}
