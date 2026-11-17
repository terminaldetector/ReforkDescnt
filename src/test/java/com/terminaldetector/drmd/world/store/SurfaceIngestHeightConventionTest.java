package com.terminaldetector.drmd.world.store;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Numeric boundaries of the real convention mapper; actual callback tests are separate. */
class SurfaceIngestHeightConventionTest {
    @Test void actualTopBlockBecomesItsPhysicalTopFace(){assertEquals(2301,SurfaceIngest.storedSurfaceHeight(2300,-16384));}
    @Test void solidAtWorldBottomHasATopFace(){assertEquals(-63,SurfaceIngest.storedSurfaceHeight(-64,-64));}
    @Test void belowBottomEmptySentinelIsNotPromotedIntoASurface(){assertEquals(-65,SurfaceIngest.storedSurfaceHeight(-65,-64));}
    @Test void missingSampleSentinelDoesNotBecomeAHeight(){assertEquals(Integer.MIN_VALUE,SurfaceIngest.storedSurfaceHeight(Integer.MIN_VALUE,-64));}
    @Test void invalidMaximumCannotWrapIntoAPhantomNegativeSurface(){assertThrows(ArithmeticException.class,()->SurfaceIngest.storedSurfaceHeight(Integer.MAX_VALUE,-64));}
}
