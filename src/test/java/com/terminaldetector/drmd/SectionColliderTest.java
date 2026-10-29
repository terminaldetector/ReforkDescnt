package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.physics.collision.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SectionColliderTest {
    private static Vec3 v(double x,double y,double z){return new Vec3(x,y,z);}
    private static CompoundCollider section(int x) {
        return CompoundCollider.cells(List.of(new CompoundCollider.Cell(x,0,0),new CompoundCollider.Cell(x+2,0,0)));
    }
    @Test void directoryBoundsAreNeverMistakenForMaterial() {
        var collider=CompoundCollider.sections(List.of(section(0),section(112)));
        assertEquals(2,collider.sectionCount());assertEquals(4,collider.boxes().size());
        assertTrue(collider.raycast(v(1.5,.5,-2),v(1.5,.5,2)).isEmpty());
        assertTrue(collider.raycast(v(50,.5,-2),v(50,.5,2)).isEmpty());
        assertTrue(collider.query(new BodyBounds(v(113.1,.1,.1),v(113.9,.9,.9))).isEmpty());
        assertEquals(.5,collider.raycast(v(114.5,.5,-2),v(114.5,.5,2)).orElseThrow().fraction(),1e-12);
    }
    @Test void oldPhysicsSnapshotsKeepTheirGeometryAfterASectionReplacement() {
        var untouched=section(112);var original=section(0);
        var old=CompoundCollider.sections(List.of(original,untouched));
        var next=CompoundCollider.sections(List.of(new CompoundCollider(List.of()),untouched));
        assertTrue(old.raycast(v(.5,.5,-2),v(.5,.5,2)).isPresent());
        assertTrue(next.raycast(v(.5,.5,-2),v(.5,.5,2)).isEmpty());
        assertEquals(old.raycast(v(114.5,.5,-2),v(114.5,.5,2)),next.raycast(v(114.5,.5,-2),v(114.5,.5,2)));
    }
    @Test void hierarchicalQueriesAndClosestRayMatchTheFlatBvhAcrossSparseSpace() {
        var parts=new ArrayList<CompoundCollider>();for(int x=-32;x<128;x+=16)parts.add(section(x));
        var nested=CompoundCollider.sections(parts);var flat=new CompoundCollider(nested.boxes());var r=new Random(12829);
        for(int i=0;i<1000;i++) {
            var a=v(r.nextDouble()*180-40,r.nextDouble()*4-2,r.nextDouble()*4-2);
            var b=v(r.nextDouble()*180-40,r.nextDouble()*4-2,r.nextDouble()*4-2);
            assertEquals(flat.raycast(a,b),nested.raycast(a,b));
            var bounds=new BodyBounds(a,a.plus(v(3,3,3)));
            assertEquals(new HashSet<>(flat.query(bounds)),new HashSet<>(nested.query(bounds)));
        }
    }
    @Test void aFull32768CellBodyRetainsAnEditedInteriorHole() {
        var parts=new ArrayList<CompoundCollider>();int count=0;
        for(int cx=0;cx<2;cx++)for(int cy=0;cy<2;cy++)for(int cz=0;cz<2;cz++) {
            var cells=new ArrayList<CompoundCollider.Cell>();
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++) {
                if(cx==0&&cy==0&&cz==0&&x==8&&y==8&&z==8)continue;
                cells.add(new CompoundCollider.Cell(cx*16+x,cy*16+y,cz*16+z));count++;
            }
            parts.add(CompoundCollider.cells(cells));
        }
        var collider=CompoundCollider.sections(parts);assertEquals(32767,count);assertEquals(8,collider.sectionCount());
        assertTrue(collider.query(new BodyBounds(v(8.1,8.1,8.1),v(8.9,8.9,8.9))).isEmpty());
        assertEquals(32767,collider.boxes().stream().mapToDouble(b->{var d=b.max().minus(b.min());return d.x()*d.y()*d.z();}).sum(),1e-9);
    }
}
