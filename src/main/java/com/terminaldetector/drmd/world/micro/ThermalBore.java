package com.terminaldetector.drmd.world.micro;

/** Bounded block-local capsule field reused by the existing near mesh and its collision proxy. */
public record ThermalBore(double ax,double ay,double az,double bx,double by,double bz,double radius) {
    public static final double SHELL=Math.sqrt(3)*MicroGrid.CELL_SIZE*.5;
    public record Point(double x,double y,double z){}
    public ThermalBore {
        if(!valid(ax)||!valid(ay)||!valid(az)||!valid(bx)||!valid(by)||!valid(bz))throw new IllegalArgumentException("local bore bounds");
        double dx=bx-ax,dy=by-ay,dz=bz-az;
        if(!Double.isFinite(radius)||radius<.5||radius>3||dx*dx+dy*dy+dz*dz>16.0000001)throw new IllegalArgumentException("bore radius/length");
    }
    private static boolean valid(double value){return Double.isFinite(value)&&Math.abs(value)<=16;}
    private static double fraction(double x,double y,double z,double ax,double ay,double az,double bx,double by,double bz){
        double dx=bx-ax,dy=by-ay,dz=bz-az,len=dx*dx+dy*dy+dz*dz;
        return len<1e-12?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy+(z-az)*dz)/len));
    }
    private Point axis(double x,double y,double z){
        double dx=bx-ax,dy=by-ay,dz=bz-az;
        double t=fraction(x,y,z,ax,ay,az,bx,by,bz);
        return new Point(ax+t*dx,ay+t*dy,az+t*dz);
    }
    public double distance(double x,double y,double z){return distanceTo(x,y,z,ax,ay,az,bx,by,bz);}
    public static double distanceTo(double x,double y,double z,double ax,double ay,double az,double bx,double by,double bz){
        double t=fraction(x,y,z,ax,ay,az,bx,by,bz),dx=x-(ax+t*(bx-ax)),dy=y-(ay+t*(by-ay)),dz=z-(az+t*(bz-az));return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    public Point project(double x,double y,double z){
        var p=axis(x,y,z);double distance=distance(x,y,z);
        if(distance<1e-12||Math.abs(distance-radius)>SHELL)return new Point(x,y,z);
        double scale=radius/distance;return new Point(p.x+(x-p.x)*scale,p.y+(y-p.y)*scale,p.z+(z-p.z)*scale);
    }
    public long mask(){return MicroGrid.build((x,y,z)->distance((x+.5)*MicroGrid.CELL_SIZE,(y+.5)*MicroGrid.CELL_SIZE,(z+.5)*MicroGrid.CELL_SIZE)>radius);}
    public ThermalBore withRadius(double value){return new ThermalBore(ax,ay,az,bx,by,bz,value);}
    public boolean sameAxis(ThermalBore other){
        return Math.abs(ax-other.ax)+Math.abs(ay-other.ay)+Math.abs(az-other.az)+Math.abs(bx-other.bx)+Math.abs(by-other.by)+Math.abs(bz-other.bz)<1e-9;
    }
    /** Prototype material response derived from the actual source hardness; no new weapon stats. */
    public static int softenDose(float hardness){return Math.max(3,Math.min(12,2+(int)Math.ceil(Math.max(0,hardness))));}
    public static int meltDose(float hardness){return softenDose(hardness)+2;}
}
