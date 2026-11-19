package com.terminaldetector.drmd.gate;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.flight.DescentCamera;
import com.terminaldetector.drmd.client.render.WeaponViewRenderer;
import com.terminaldetector.drmd.gate.mixin.CameraHeightCacheProbe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import java.nio.file.*;
import java.util.*;

/** Invoke ONCE after an existing qualified local rendered frame, passing an already tracked
 * real foreign actor/body. No bootstrap/controller, actor edits, new field or per-frame loop. */
public final class NativeCameraOwnershipProbe {
    private static boolean attempted;
    private NativeCameraOwnershipProbe(){}
    public static void check(Entity foreign,float delta) {
        if(attempted)throw new IllegalStateException("Native camera owner probe is single-shot");attempted=true;
        var c=MinecraftClient.getInstance();var camera=c.gameRenderer.getCamera();
        if(c.world==null||c.player==null||foreign==null||foreign==c.player||foreign.getWorld()!=c.world
            ||c.world.getEntityById(foreign.getId())!=foreign||!DescentClientState.enabled||!DescentCamera.ownsView(camera))
            throw new IllegalStateException("UNOBSERVED native camera owner premise; needs actual local enabled lens + registered foreign actor");
        var oldEntity=c.getCameraEntity();var perspective=c.options.getPerspective();
        var source=(CameraHeightCacheProbe)camera;var vanilla=new Camera();var target=(CameraHeightCacheProbe)vanilla;
        target.gate$cameraY(source.gate$cameraY());target.gate$lastCameraY(source.gate$lastCameraY());
        var rows=new ArrayList<Map<String,Object>>();boolean passed=false;
        try {
            for(boolean retarget:new boolean[]{false,true}) {
                if(retarget)c.setCameraEntity(foreign);
                vanilla.update(c.world,foreign,false,false,delta);camera.update(c.world,foreign,false,false,delta);
                var a=camera.getPos();var b=vanilla.getPos();double error=a.squaredDistanceTo(b);
                double dot=Math.abs(camera.getRotation().dot(vanilla.getRotation()));
                var row=new LinkedHashMap<String,Object>();row.put("retarget",retarget);row.put("foreignId",foreign.getId());row.put("foreignUUID",foreign.getUuid().toString());row.put("foreignClass",foreign.getClass().getName());
                row.put("actualFocus",camera.getFocusedEntity().getUuid().toString());row.put("cameraEntity",c.getCameraEntity().getUuid().toString());row.put("flightMirror",DescentClientState.enabled);
                row.put("actualEye",List.of(a.x,a.y,a.z));row.put("nativeEye",List.of(b.x,b.y,b.z));row.put("positionSquaredError",Double.isFinite(error)?error:"NONFINITE");row.put("absoluteQuaternionDot",Double.isFinite(dot)?dot:"NONFINITE");
                row.put("cameraY",source.gate$cameraY());row.put("lastCameraY",source.gate$lastCameraY());row.put("ownsView",DescentCamera.ownsView(camera));row.put("localWeaponReplacement",WeaponViewRenderer.replacesVanillaHand(c));rows.add(row);
                if(!Double.isFinite(error)||!Double.isFinite(dot)||error>1e-12||dot<1-1e-6||retarget&&(DescentCamera.ownsView(camera)||WeaponViewRenderer.replacesVanillaHand(c)))
                    throw new IllegalStateException("Native foreign camera was hijacked by a local flight mirror: "+row);
            }
            passed=true;
        } finally {
            c.setCameraEntity(oldEntity);
            camera.update(c.world,oldEntity,!perspective.isFirstPerson(),perspective.isFrontView(),delta);
            try {
                var folder=c.runDirectory.toPath().resolve("camera-owner-proof");Files.createDirectories(folder);
                var result=new LinkedHashMap<String,Object>();result.put("passed",passed);result.put("restoredLocalOwner",DescentCamera.ownsView(camera));result.put("restoredActualFocus",camera.getFocusedEntity().getUuid().toString());result.put("rows",rows);
                Files.writeString(folder.resolve("native-camera-owner.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        }
    }
}
