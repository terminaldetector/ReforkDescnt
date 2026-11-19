package com.terminaldetector.drmd.client.planet;
import com.terminaldetector.drmd.world.gen2.MacroEntry;
import com.terminaldetector.drmd.world.planet.OrbitalObjectGeometry;
import java.util.List;
/** Only certified authored object kinds reconstruct the shared, physically placed voxel definition. */
public final class HorizonOrbitalProxy {
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int colour){}
    private HorizonOrbitalProxy(){}
    public static boolean authored(MacroEntry.Kind kind){return kind==MacroEntry.Kind.ORBITAL_STATION||kind==MacroEntry.Kind.ASTEROID;}
    public static OrbitalObjectGeometry.Tier tier(double trueDistance){
        if(!Double.isFinite(trueDistance)||trueDistance<0)throw new IllegalArgumentException("orbital eye distance");
        return trueDistance<=2048?OrbitalObjectGeometry.Tier.MID:OrbitalObjectGeometry.Tier.FAR;
    }
    /** Same true 3D decision for initial emission and cached-source invalidation. */
    public static OrbitalObjectGeometry.Tier tierAt(HorizonLandmark mark,double eyeX,double eyeY,double eyeZ){
        double dx=mark.x()-eyeX,dy=mark.y()-eyeY,dz=mark.z()-eyeZ;
        return tier(Math.sqrt(dx*dx+dy*dy+dz*dz));
    }
    public static List<Box> boxes(HorizonLandmark mark,double eyeX,double eyeY,double eyeZ){
        if(!authored(mark.kind())||!Double.isFinite(eyeX)||!Double.isFinite(eyeY)||!Double.isFinite(eyeZ))return List.of();
        var shape=mark.kind()==MacroEntry.Kind.ORBITAL_STATION?OrbitalObjectGeometry.Shape.STATION:OrbitalObjectGeometry.Shape.ASTEROID;
        if(!OrbitalObjectGeometry.supports(shape,mark.sizeX(),mark.sizeY(),mark.sizeZ()))return List.of();
        double x=mark.x()-mark.sizeX()*.5,y=mark.y()-mark.sizeY()*.5,z=mark.z()-mark.sizeZ()*.5;
        return OrbitalObjectGeometry.boxes(shape,mark.sizeX(),mark.sizeY(),mark.sizeZ(),tierAt(mark,eyeX,eyeY,eyeZ)).stream()
            .map(b->new Box(x+b.minX(),y+b.minY(),z+b.minZ(),x+b.maxX(),y+b.maxY(),z+b.maxZ(),b.colorRgb())).toList();
    }
}
