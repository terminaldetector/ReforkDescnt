package com.terminaldetector.drmd.boardingcheck;
import net.fabricmc.api.ClientModInitializer;
public final class BoardingCheck implements ClientModInitializer {
    public void onInitializeClient(){if(Boolean.getBoolean("drmd.gate.shipGravity"))com.terminaldetector.drmd.gate.NativeShipGravityCheck.register();else com.terminaldetector.drmd.gate.NativeBoardingCheck.register();}
}
