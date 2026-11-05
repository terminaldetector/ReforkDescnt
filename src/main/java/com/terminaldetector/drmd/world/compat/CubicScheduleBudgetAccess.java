package com.terminaldetector.drmd.world.compat;

/** Optional diagnostics on the actual native scheduler, without replacing its geometry or queue. */
public interface CubicScheduleBudgetAccess {
    CubicScheduleBudget.Snapshot drmd$generationBudgetSnapshot();
}
