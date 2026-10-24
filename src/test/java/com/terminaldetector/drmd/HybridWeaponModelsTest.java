package com.terminaldetector.drmd;

import com.terminaldetector.drmd.workshop.*;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HybridWeaponModelsTest {
    private final List<String> ids=List.of(HybridWeaponModels.IMPULSE,HybridWeaponModels.PLASMA,HybridWeaponModels.RAIL);
    @Test void completeMeshesContainPolygonShellsVoxelFittingsAndMovingMechanisms() {
        for(var id:ids) {
            var parts=HybridWeaponModels.parts(id);
            assertTrue(parts.size()>=20,id);
            assertTrue(parts.stream().anyMatch(p->p.name().contains("voxel")),id);
            assertTrue(parts.stream().anyMatch(p->p.name().contains("chamfered")),id);
            assertTrue(parts.stream().anyMatch(HybridWeaponModels.Part::moving),id);
            assertTrue(parts.stream().anyMatch(p->p.material().emissive),id);
            assertTrue(parts.stream().flatMap(p->p.faces().stream()).flatMap(f->f.points().stream())
                    .allMatch(p->Float.isFinite(p.x()) && Float.isFinite(p.y()) && p.z()>=0),id);
        }
    }
    @Test void muzzleIsInvariantUnderEveryEditableTransformAndMechanicalRecoil() {
        for(var id:ids) for(var m:HybridWeaponModels.sample(id)) {
            m.pitch=47; m.yaw=-31; m.roll=128; m.scaleX=.7f; m.scaleY=1.6f; m.scaleZ=2.3f;
            assertEquals(new Vector3f(),HybridWeaponModels.transform(m,new HybridWeaponModels.Point(0,0,0),1,false));
            var offset=WeaponClusters.extractMuzzles(List.of(m)).get(m.muzzleIdx);
            assertEquals(m.posFwd,offset.fwd()); assertEquals(m.posRgt,offset.rgt()); assertEquals(m.posUp,offset.up());
        }
    }
    @Test void rotationAndNonuniformScaleAffectActualMesh() {
        var m=HybridWeaponModels.sample(HybridWeaponModels.RAIL).getFirst();
        m.scaleX=2; m.scaleY=3; m.scaleZ=4; m.roll=90;
        var v=HybridWeaponModels.transform(m,new HybridWeaponModels.Point(1,2,3),0,false);
        assertEquals(-6,v.x,1e-5); assertEquals(2,v.y,1e-5); assertEquals(12,v.z,1e-5);
    }
    @Test void recoilOnlyMovesInternalMechanisms() {
        var m=HybridWeaponModels.sample(HybridWeaponModels.IMPULSE).getFirst();
        var p=new HybridWeaponModels.Point(0,0,12);
        assertEquals(12,HybridWeaponModels.transform(m,p,1,false).z,1e-5);
        assertEquals(12+m.kick,HybridWeaponModels.transform(m,p,1,true).z,1e-5);
    }
    @Test void samplesAreIndependentAndLeaveCentralAimOpenAtAllTestFovs() {
        for(var id:ids) {
            var sample=HybridWeaponModels.sample(id);
            assertEquals(2,WeaponClusters.extractMuzzles(sample).size());
            for(int fov:new int[]{70,80,90}) for(var m:sample) {
                double half=Math.tan(Math.toRadians(fov/2.0));
                assertTrue(Math.abs(m.posUp/m.posFwd)<half);
                assertTrue(Math.abs(m.posRgt/m.posFwd)<half*16/9);
                assertTrue(Math.abs(m.posRgt)>10);
            }
            sample.getFirst().scaleX=99;
            assertEquals(1,HybridWeaponModels.sample(id).getFirst().scaleX);
        }
    }
    @Test void rejectsUnsafeConstructionBeforeWorldPersistence() {
        assertFalse(ConstructionValidation.validId("../../other-world"));
        var modules=HybridWeaponModels.sample(HybridWeaponModels.PLASMA);
        assertTrue(ConstructionValidation.valid(modules));
        modules.getFirst().scaleY=Float.NaN; assertFalse(ConstructionValidation.valid(modules));
    }
}
