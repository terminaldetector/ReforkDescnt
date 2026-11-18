package com.terminaldetector.drmd.world.bombardment;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Optional versioned extension; old bombs without it retain their established lifecycle. */
public final class ClusterFlight {
    public static final String KEY="clusterFlight",ITEM_KEY="clusterProgram";
    private static final Set<String> PROGRAM_KEYS=Set.of("schema","pattern","arm","split","stages","stagger","life","safety","spread");
    public final ClusterProgram program;
    private Vec3 forward,up;
    private double travelled;
    private int stage;
    private boolean armed,disarmed;
    public ClusterFlight(ClusterProgram program,Vec3 forward,Vec3 up){
        this.program=Objects.requireNonNull(program);this.forward=forward.normalized();this.up=up.minus(this.forward.scaled(up.dot(this.forward))).normalized();
        if(!finite(this.forward)||!finite(this.up)||this.forward.lengthSquared()<.9||this.up.lengthSquared()<.9)throw new IllegalArgumentException("finite cluster frame");
    }
    public Vec3 forward(){return forward;}public Vec3 up(){return up;}public int stage(){return stage;}public boolean armed(){return armed;}public boolean disarmed(){return disarmed;}public double travelled(){return travelled;}
    public void moved(double distance,int age){if(!Double.isFinite(distance)||distance<0||distance>256){disarm();return;}travelled=Math.min(65536,travelled+distance);if(age>=program.armTicks()&&travelled>=program.safetyDistance())armed=true;}
    public boolean due(int age){return !disarmed&&armed&&stage<program.stages()&&age>=program.due(stage);}
    public boolean complete(){return stage==program.stages();}
    public void released(){if(!armed||disarmed||complete())throw new IllegalStateException("unarmed or spent cluster order");stage++;}
    public void disarm(){disarmed=true;armed=false;}
    public void rotate(Quat q){forward=q.rotate(forward).normalized();up=q.rotate(up).normalized();}
    public static boolean finite(Vec3 v){return Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    public static NbtCompound writeProgram(ClusterProgram p){var n=new NbtCompound();n.putInt("schema",1);n.putString("pattern",p.pattern().name());n.putInt("arm",p.armTicks());n.putInt("split",p.splitTicks());n.putInt("stages",p.stages());n.putInt("stagger",p.staggerTicks());n.putInt("life",p.lifetime());n.putDouble("safety",p.safetyDistance());n.putDouble("spread",p.spread());return n;}
    public static Optional<ClusterProgram> readProgram(NbtElement raw){
        if(!(raw instanceof NbtCompound n)||!n.getKeys().equals(PROGRAM_KEYS)||!n.contains("schema",NbtElement.INT_TYPE)||n.getInt("schema")!=1||!n.contains("pattern",NbtElement.STRING_TYPE))return Optional.empty();
        for(var key:List.of("arm","split","stages","stagger","life"))if(!n.contains(key,NbtElement.INT_TYPE))return Optional.empty();
        for(var key:List.of("safety","spread"))if(!n.contains(key,NbtElement.DOUBLE_TYPE))return Optional.empty();
        try{return Optional.of(new ClusterProgram(ClusterProgram.Pattern.valueOf(n.getString("pattern")),n.getInt("arm"),n.getInt("split"),n.getInt("stages"),n.getInt("stagger"),n.getInt("life"),n.getDouble("safety"),n.getDouble("spread")));}catch(IllegalArgumentException refused){return Optional.empty();}
    }
    private static NbtCompound vector(Vec3 v){var n=new NbtCompound();n.putDouble("x",v.x());n.putDouble("y",v.y());n.putDouble("z",v.z());return n;}
    private static Vec3 vector(NbtElement raw){if(!(raw instanceof NbtCompound n)||!n.getKeys().equals(Set.of("x","y","z")))throw new IllegalArgumentException("vector");for(var k:List.of("x","y","z"))if(!n.contains(k,NbtElement.DOUBLE_TYPE))throw new IllegalArgumentException("vector type");return new Vec3(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));}
    public NbtCompound write(){var n=new NbtCompound();n.putInt("schema",1);n.put("program",writeProgram(program));n.put("forward",vector(forward));n.put("up",vector(up));n.putDouble("travelled",travelled);n.putInt("stage",stage);n.putBoolean("armed",armed);n.putBoolean("disarmed",disarmed);return n;}
    public static Optional<ClusterFlight> read(NbtElement raw){
        if(!(raw instanceof NbtCompound n)||!n.getKeys().equals(Set.of("schema","program","forward","up","travelled","stage","armed","disarmed"))||!n.contains("schema",NbtElement.INT_TYPE)||n.getInt("schema")!=1||!n.contains("travelled",NbtElement.DOUBLE_TYPE)||!n.contains("stage",NbtElement.INT_TYPE)||!n.contains("armed",NbtElement.BYTE_TYPE)||!n.contains("disarmed",NbtElement.BYTE_TYPE))return Optional.empty();
        try{var program=readProgram(n.get("program")).orElseThrow();var f=vector(n.get("forward"));var u=vector(n.get("up"));if(Math.abs(f.lengthSquared()-1)>1e-5||Math.abs(u.lengthSquared()-1)>1e-5||Math.abs(f.dot(u))>1e-5) return Optional.empty();for(var key:List.of("armed","disarmed"))if(n.getByte(key)!=0&&n.getByte(key)!=1)return Optional.empty();var state=new ClusterFlight(program,f,u);state.travelled=n.getDouble("travelled");state.stage=n.getInt("stage");state.armed=n.getBoolean("armed");state.disarmed=n.getBoolean("disarmed");
            if(!Double.isFinite(state.travelled)||state.travelled<0||state.travelled>65536||state.stage<0||state.stage>program.stages()||state.armed&&state.disarmed||state.armed&&state.travelled<program.safetyDistance()||state.stage>0&&!state.armed&&!state.disarmed)return Optional.empty();return Optional.of(state);
        }catch(IllegalArgumentException|NoSuchElementException refused){return Optional.empty();}
    }
}
