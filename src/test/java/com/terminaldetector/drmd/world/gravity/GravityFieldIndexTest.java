package com.terminaldetector.drmd.world.gravity;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GravityFieldIndexTest {
    private static final RegistryKey<World> A=RegistryKey.of(RegistryKeys.WORLD,Identifier.of("drmd","index_a"));
    private static final RegistryKey<World> B=RegistryKey.of(RegistryKeys.WORLD,Identifier.of("drmd","index_b"));
    @AfterEach void clear(){GravityFields.clear();}
    private static GravityFields.Field field(int id,RegistryKey<World> world,BlockPos pos,double radius,FieldShape shape,Vec3d down) {
        return new GravityFields.Field(new UUID(0,id),world,pos,down,radius,1,shape,"field"+id);
    }

    @Test void broadPhaseNeverLosesInfluenceAcrossNegativeAndHighCubeSeams() {
        var random=new Random(261027);var index=new GravityFieldIndex();
        List<GravityFields.Field> fields=new ArrayList<>();
        for(int i=1;i<=600;i++) {
            var f=field(i,i%7==0?null:i%2==0?A:B,
                new BlockPos(random.nextInt(1024)-512,random.nextInt(4096)-1024,random.nextInt(1024)-512),
                Math.scalb(1+random.nextDouble(),random.nextInt(9)),FieldShape.values()[i%3],
                new Vec3d(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).normalize());
            fields.add(f);index.put(f);
        }
        for(var f:fields)for(int i=0;i<16;i++) {
            var p=Vec3d.ofCenter(f.origin()).add((random.nextDouble()*2-1)*f.radius(),
                (random.nextDouble()*2-1)*f.radius(),(random.nextDouble()*2-1)*f.radius());
            var dimension=i%2==0?A:B;
            Set<GravityFields.Field> actual=new HashSet<>(index.candidates(dimension,p));
            for(var expected:fields)
                if((expected.worldKey()==null || expected.worldKey().equals(dimension)) && expected.influenceAt(p)>1e-4)
                    assertTrue(actual.contains(expected),()->"Missing "+expected+" at "+p);
        }
    }

    @Test void tiltedCylinderCanInfluenceOutsideASphericalRadius() {
        var index=new GravityFieldIndex();var axis=new Vec3d(1,1,0).normalize();
        var f=field(1,A,new BlockPos(15,2159,-17),16,FieldShape.CYLINDER,axis);index.put(f);
        var p=Vec3d.ofCenter(f.origin()).add(axis.multiply(15.9)).add(new Vec3d(1,-1,0).normalize().multiply(8));
        assertTrue(p.distanceTo(Vec3d.ofCenter(f.origin()))>f.radius());
        assertTrue(f.influenceAt(p)>0);assertTrue(index.candidates(A,p).contains(f));
    }

    @Test void replacementAndRemovalRetireOldDimensionAndBuckets() {
        var index=new GravityFieldIndex();var first=field(1,A,BlockPos.ORIGIN,8,FieldShape.SPHERE,new Vec3d(0,-1,0));
        index.put(first);var moved=field(1,B,new BlockPos(-16,-512,2150),8,FieldShape.PLANE,new Vec3d(1,0,0));index.put(moved);
        assertTrue(index.candidates(A,Vec3d.ofCenter(first.origin())).isEmpty());
        assertEquals(List.of(moved),index.candidates(B,Vec3d.ofCenter(moved.origin())));
        index.remove(first.id());assertTrue(index.all().isEmpty());
        assertTrue(index.candidates(B,Vec3d.ofCenter(moved.origin())).isEmpty());
        index.remove(first.id());index.clear();
    }

    @Test void distantEmittersDoNotEnterLocalCandidateWork() {
        var index=new GravityFieldIndex();
        for(int i=1;i<=10000;i++)index.put(field(i,A,new BlockPos(i*256,2150,i*256),8,FieldShape.SPHERE,new Vec3d(0,-1,0)));
        assertTrue(index.candidates(A,new Vec3d(256.5,2150.5,256.5)).size()<=2);
        assertTrue(index.candidates(A,new Vec3d(256.5,-512.5,256.5)).isEmpty());
        assertTrue(index.candidates(B,new Vec3d(256.5,2150.5,256.5)).isEmpty());
    }

    @Test void hugeFieldsHaveABoundedFallbackAndAreStillEffective() {
        var index=new GravityFieldIndex();var f=field(1,A,BlockPos.ORIGIN,1e200,FieldShape.SPHERE,new Vec3d(0,-1,0));
        index.put(f);assertEquals(List.of(f),index.candidates(A,new Vec3d(-100,2150,100)));
        index.remove(f.id());assertTrue(index.candidates(A,Vec3d.ZERO).isEmpty());
    }

    @Test void fieldSnapshotsMutableOriginAndRejectsNonFiniteData() {
        var origin=new BlockPos.Mutable(1,2159,-16);var f=field(1,A,origin,8,FieldShape.SPHERE,new Vec3d(0,-1,0));
        GravityFields.put(f);origin.set(1000,-512,1000);
        assertEquals(new BlockPos(1,2159,-16),f.origin());
        assertNotNull(GravityFields.sample(A,new Vec3d(1.5,2159.5,-15.5)));
        assertNull(GravityFields.sample(A,new Vec3d(Double.NaN,0,0)));
        assertThrows(IllegalArgumentException.class,()->field(2,A,BlockPos.ORIGIN,Double.NaN,FieldShape.SPHERE,new Vec3d(0,-1,0)));
        assertThrows(IllegalArgumentException.class,()->field(2,A,BlockPos.ORIGIN,8,FieldShape.SPHERE,Vec3d.ZERO));
    }

    @Test void indexedBlendMatchesLinearInfluenceAndIsInsertionOrderIndependent() {
        List<GravityFields.Field> fields=new ArrayList<>();var random=new Random(1024);
        for(int i=1;i<=80;i++)fields.add(field(i,A,new BlockPos(random.nextInt(50)-25,random.nextInt(50)+2130,
            random.nextInt(50)-25),32,FieldShape.values()[i%3],new Vec3d(i%2==0?1:-1,1,0).normalize()));
        for(var f:fields)GravityFields.put(f);
        for(int i=0;i<200;i++) {
            var p=new Vec3d(random.nextInt(100)-50,random.nextInt(100)+2100,random.nextInt(100)-50);
            Vec3d sum=Vec3d.ZERO;double weight=0;
            for(var f:fields) {double w=f.influenceAt(p);if(w<=1e-4)continue;sum=sum.add(f.downDir().normalize().multiply(w));weight+=w;}
            var actual=GravityFields.sample(A,p);
            if(weight<1e-4){assertNull(actual);continue;}
            assertNotNull(actual);assertEquals(Math.min(2,weight),actual.strength(),1e-6);
            assertTrue(sum.normalize().distanceTo(actual.downDir())<1e-9);
        }
        var p=new Vec3d(0,2150,0);var first=GravityFields.sample(A,p);GravityFields.clear();
        Collections.shuffle(fields,random);for(var f:fields)GravityFields.put(f);
        assertEquals(first,GravityFields.sample(A,p));
    }
}
