package com.terminaldetector.drmd;

import com.terminaldetector.drmd.d6.D6ChunkPos;
import com.terminaldetector.drmd.world.access.VolumetricWorldAccess.Bounds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VolumetricBoundsTest {
    @Test void negativeAndTopCubeEdgesAreHalfOpen() {
        var bounds = new Bounds(-64, 320);
        assertTrue(bounds.containsCube(D6ChunkPos.ofBlock(-17,-64,-1)));
        assertTrue(bounds.containsCube(D6ChunkPos.ofBlock(0,319,0)));
        assertFalse(bounds.containsCube(D6ChunkPos.ofBlock(0,-65,0)));
        assertFalse(bounds.containsCube(D6ChunkPos.ofBlock(0,320,0)));
        assertTrue(bounds.containsBlock(-64));
        assertFalse(bounds.containsBlock(320));
    }
    @Test void extremeCubeIndicesNeverWrapIntoValidHeight() {
        var bounds = new Bounds(-16_384, 16_384);
        assertFalse(bounds.containsCube(new D6ChunkPos(0,Integer.MAX_VALUE,0)));
        assertFalse(bounds.containsCube(new D6ChunkPos(0,Integer.MIN_VALUE,0)));
        assertFalse(bounds.containsCube(new D6ChunkPos(0,1<<28,0)));
    }
    @Test void partialBoundaryCubeIsUnavailableEvenWhenOneBlockIsInside() {
        var bounds = new Bounds(-63, 319);
        assertTrue(bounds.containsBlock(-63));
        assertFalse(bounds.containsCube(new D6ChunkPos(0,-4,0)));
        assertFalse(bounds.containsCube(new D6ChunkPos(0,19,0)));
        assertTrue(bounds.containsCube(new D6ChunkPos(0,-3,0)));
    }
    @Test void emptyOrReversedBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new Bounds(0,0));
        assertThrows(IllegalArgumentException.class,()->new Bounds(320,-64));
    }
}
