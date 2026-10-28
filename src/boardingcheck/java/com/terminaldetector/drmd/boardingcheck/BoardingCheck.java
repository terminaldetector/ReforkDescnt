package com.terminaldetector.drmd.boardingcheck;
import net.fabricmc.api.ClientModInitializer;
public final class BoardingCheck implements ClientModInitializer {
    public void onInitializeClient(){com.terminaldetector.drmd.gate.NativeBoardingCheck.register();}
}
