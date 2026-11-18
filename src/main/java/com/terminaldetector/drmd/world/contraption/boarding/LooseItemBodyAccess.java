package com.terminaldetector.drmd.world.contraption.boarding;

/** Native item operations, invoked once after its deferred physical move; never a second item tick. */
public interface LooseItemBodyAccess {
    boolean drmd$nativeCanMerge();
    void drmd$nativeTryMerge();
    void drmd$finishBodyMove();
}
