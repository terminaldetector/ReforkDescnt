package com.terminaldetector.drmd.boardingcheck;
import net.fabricmc.api.ClientModInitializer;
public final class BoardingCheck implements ClientModInitializer {
    public void onInitializeClient(){if(Boolean.getBoolean("drmd.gate.contacts")){com.terminaldetector.drmd.gate.NativeBodyCollisionCheck.register();return;}if(!Boolean.getBoolean("drmd.gate.bodyIndex")&&!Boolean.getBoolean("drmd.gate.bodyEdit")&&!Boolean.getBoolean("drmd.gate.shipGravity"))System.setProperty("drmd.gate.boarding","true");if(Boolean.getBoolean("drmd.gate.bodyIndex")){com.terminaldetector.drmd.gate.NativeBodyIndexCheck.register();return;}else if(Boolean.getBoolean("drmd.gate.bodyEdit")){com.terminaldetector.drmd.gate.NativeBodyEditCheck.register();return;}else if(Boolean.getBoolean("drmd.gate.shipGravity"))com.terminaldetector.drmd.gate.NativeShipGravityCheck.register();else com.terminaldetector.drmd.gate.NativeBoardingCheck.register();}
}
