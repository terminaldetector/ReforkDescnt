package com.terminaldetector.drmd.client.presentation;

import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.portal.OffscreenWorldView;

/** Same admission as the existing Pyro variant; GUI, Native, Debug and nested views stay native. */
public final class Ps1WorldAssets {
    private Ps1WorldAssets(){}
    public static boolean coarse(){
        return DescentConfig.ps1Assets.eligible(DescentConfig.presentationProfile,
                ScenePresentation.inLowResolutionWorld(),OffscreenWorldView.busy());
    }
}
