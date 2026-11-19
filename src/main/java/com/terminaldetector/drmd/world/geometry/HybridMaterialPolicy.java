package com.terminaldetector.drmd.world.geometry;

import com.terminaldetector.drmd.world.micro.MicroGrid;

/** Explicit material/damage mask shared by chunk meshing and collision snapshots. */
public record HybridMaterialPolicy(Mask mask, boolean smooth, boolean anchor,
                                   boolean quarterDamage, boolean thermal) {
    public enum Mask { SMOOTH_NATURAL, HARD_ENGINEERED, TRANSITION }

    /** Damage does not turn an engineered hull into soil. The saved organic choice remains authoritative. */
    public static HybridMaterialPolicy of(boolean organic, long cells, int heat, boolean bores, boolean anchorEmpty) {
        boolean damaged = cells != MicroGrid.FULL;
        boolean melting = heat > 0 || bores;
        Mask mask = damaged || melting ? Mask.TRANSITION : organic ? Mask.SMOOTH_NATURAL : Mask.HARD_ENGINEERED;
        return new HybridMaterialPolicy(mask, organic, !organic && (anchorEmpty || cells != 0), damaged, melting);
    }
}
