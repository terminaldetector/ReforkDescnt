package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.world.compat.*;
import net.minecraft.client.MinecraftClient;
import java.nio.file.*;
import java.util.*;

/** Observe the real upstream instances; never simulate or invoke generation on their behalf. */
public final class NativeScheduleBudgetCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.scheduleBudgetProof");
    private static final Map<Object,CubicScheduleBudget.Snapshot> INSTANCES=new IdentityHashMap<>();
    private static volatile CubicScheduleBudget.Snapshot accepted=new CubicScheduleBudget.Snapshot(0,0,0,0);
    private NativeScheduleBudgetCheck(){}
    public static void register(){net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->write("server-stopped",false));}
    public static void observed(Object scheduler){
        if(!ENABLED)return;
        if(!(scheduler instanceof CubicScheduleBudgetAccess access))throw new IllegalStateException("Actual native scheduler budget mixin missing");
        INSTANCES.put(scheduler,access.drmd$generationBudgetSnapshot());
        long passes=0,deferred=0,maxNanos=0;int maxCalls=0;
        for(var s:INSTANCES.values()){passes+=s.passes();deferred+=s.deferred();maxNanos=Math.max(maxNanos,s.maxPassNanos());maxCalls=Math.max(maxCalls,s.maxCalls());}
        accepted=new CubicScheduleBudget.Snapshot(passes,deferred,maxCalls,maxNanos);
    }
    public static void write(String scene,boolean requireDeferral){
        if(!ENABLED)return;var s=accepted;
        if(s.passes()==0||s.maxCalls()==0||(requireDeferral&&s.deferred()==0))throw new IllegalStateException("Native budget not exercised: "+s);
        try{Files.writeString(MinecraftClient.getInstance().runDirectory.toPath().resolve("generation-budget-"+scene+".json"),new com.google.gson.Gson().toJson(s)+"\n");}catch(Exception e){throw new IllegalStateException(e);}
    }
}
