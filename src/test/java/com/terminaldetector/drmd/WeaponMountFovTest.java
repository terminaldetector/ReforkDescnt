package com.terminaldetector.drmd;

import com.terminaldetector.drmd.workshop.DefaultLayouts;
import com.terminaldetector.drmd.workshop.WeaponClusters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponMountFovTest {
    @Test void everyDefaultMountFitsOrdinaryFovWithMargin() {
        for (int fov : new int[]{70, 80, 90}) {
            double vertical = Math.tan(Math.toRadians(fov / 2.0));
            for (var entry : DefaultLayouts.all().entrySet()) {
                for (var module : entry.getValue()) {
                    assertTrue(module.posFwd > 0, entry.getKey());
                    assertTrue(Math.abs(module.posUp / module.posFwd) < vertical * .9, entry.getKey());
                    assertTrue(Math.abs(module.posRgt / module.posFwd) < vertical * (16.0 / 9) * .9, entry.getKey());
                }
            }
        }
    }

    @Test void visualModulesAndRealMuzzlesShareTheSameMounts() {
        var modules = DefaultLayouts.forWeapon("laser");
        var muzzles = WeaponClusters.extractMuzzles(modules);
        for (var module : modules) if (module.muzzle) {
            var muzzle = muzzles.get(module.muzzleIdx);
            assertEquals(module.posFwd / 16f, muzzle.mcFwd());
            assertEquals(module.posRgt / 16f, muzzle.mcRgt());
            assertEquals(module.posUp / 16f, muzzle.mcUp());
        }
    }

    @Test void callerCopiesDoNotMutateDefaults() {
        var modules = DefaultLayouts.forWeapon("laser");
        float original = modules.getFirst().posRgt;
        modules.getFirst().posRgt = -123;
        assertEquals(original, DefaultLayouts.forWeapon("laser").getFirst().posRgt);
    }
}
