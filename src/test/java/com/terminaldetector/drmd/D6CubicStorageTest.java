package com.terminaldetector.drmd;

import com.terminaldetector.drmd.d6.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class D6CubicStorageTest {
    @Test void everyAxisIncludingNegativeSeamsKeepsIndependentCells() {
        D6Volume<String> space = new D6Volume<>("air");
        int[] axes = {-100001, -17, -16, -1, 0, 15, 16, 100000};
        for (int x : axes) for (int y : axes) for (int z : axes) space.set(x,y,z,x+","+y+","+z);
        for (int x : axes) for (int y : axes) for (int z : axes) assertEquals(x+","+y+","+z,space.get(x,y,z));
        assertEquals(512,space.occupiedCount());
        for (int x : axes) for (int y : axes) for (int z : axes) space.set(x,y,z,"air");
        assertEquals(0,space.cubeCount());
    }
    @Test void paletteCompactionRoundTripAndSnapshotIsolation() {
        D6Cube<Integer> cube = new D6Cube<>(0);
        // Force char-palette exhaustion through edits of a single cell.
        for (int i=1;i<70000;i++) cube.set(3,4,5,i);
        cube.set(15,15,15,42);
        D6Cube<Integer> snapshot=cube.copy();
        cube.set(3,4,5,0); cube.compact();
        assertEquals(69999,snapshot.get(3,4,5));
        assertEquals(1,cube.occupiedCount());
        snapshot.compact();
        D6Cube<Integer> decoded=D6Cube.decode(0,snapshot.palette(),snapshot.indices());
        assertEquals(69999,decoded.get(3,4,5));
        assertEquals(42,decoded.get(15,15,15));
    }
    @Test void malformedPaletteCannotAliasBlocks() {
        int[] bad=new int[4096];bad[4095]=2;
        assertThrows(IllegalArgumentException.class,()->D6Cube.decode("air",List.of("air"),bad));
        assertThrows(IllegalArgumentException.class,()->D6Cube.decode("air",List.of(),new int[4096]));
        assertThrows(IndexOutOfBoundsException.class,()->new D6Cube<>(0).set(-1,0,0,1));
    }
}
